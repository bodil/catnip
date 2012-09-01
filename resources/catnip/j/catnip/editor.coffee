# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/editor", "ace/virtual_renderer", "ace/edit_session"
        "ace/undomanager", "ace/multi_select"
        "cs!./keybindings", "cs!./suggestbox", "cs!./modemap"
        "cs!./fileselector", "cs!./filecreator", "cs!./doctip"
], ($, ace_editor, virtual_renderer, edit_session, undomanager, multi_select, keybindings, SuggestBox, modemap, FileSelector, FileCreator, Doctip) ->

  AceEditor = ace_editor.Editor
  Renderer = virtual_renderer.VirtualRenderer
  MultiSelect = multi_select.MultiSelect

  fileExtension = (path) -> path.split(".").pop()

  class Editor extends AceEditor
    constructor: (element, @socket) ->
      super(new Renderer(element))
      new MultiSelect(this)
      @setDisplayIndentGuides(false)
      @updateTheme()

      @buffers = {}
      @bufferHistory = []

      $(window).on "resize", => @resize()
      @resize()
      @focus()

      $(window).on "popstate", @onWindowHistoryPopState

      $(window).on "beforeunload", @onWindowBeforeUnload

      @socket.on "message", @onSocketMessage

      @keyBinding.addKeyboardHandler
        handleKeyboard: @keyboardDelegate

      @commands.addCommand
        name: "removetolineend"
        bindKey: "Ctrl-K"
        exec: => @removeToLineEnd()

      @commands.addCommand
        name: "splitLine"
        bindKey: "Ctrl-Return"
        exec: => @splitLine()

      @commands.addCommand
        name: "tabOrComplete"
        bindKey: "Tab"
        exec: => @tabOrComplete()

      @commands.addCommand
        name: "saveBuffer"
        bindKey: "Ctrl-S"
        exec: => @saveBuffer()

      @commands.addCommand
        name: "saveAndTest"
        bindKey: "Ctrl-,"
        exec: => @saveAndTest()

      @commands.addCommand
        name: "focusRepl"
        bindKey: "Ctrl-R"
        exec: (env, args, request) -> $("#repl-input").focus()

      @commands.addCommand
        name: "selectFile"
        bindKey: "Ctrl-F"
        exec: => @selectFile()

      @commands.addCommand
        name: "selectBuffer"
        bindKey: "Ctrl-B"
        exec: => @selectBuffer()

      @commands.addCommand
        name: "createFile"
        bindKey: "Ctrl-Alt-F"
        exec: => @createFile()

      @commands.addCommand
        name: "documentSymbol"
        bindKey: "Ctrl-H"
        exec: => @documentSymbol()

      @commands.addCommand
        name: "expandSnippet"
        bindKey: "Ctrl-I"
        exec: => @expandSnippet()

      @commands.addCommand
        name: "evaluateSexp"
        bindKey: "Ctrl-E"
        exec: => @evaluateSexp()

    updateTheme: =>
      if $("body").hasClass("theme-light")
        @setTheme("ace/theme/chrome")
      else if $("body").hasClass("theme-dark")
        @setTheme("ace/theme/tomorrow_night_eighties")

    keyboardDelegate: (data, hashId, keystring, keyCode, e) =>
      if @doctip
        @doctip.close()
        @doctip = null
      if @suggestBox
        if e?
          func = keybindings.keymapLookup(e, @suggestBox.keymap)
          if func?
            func(e)
            return { command: "null" }
        @suggestBox.keymap["all"](e)
      if not e? or not keybindings.matchBinding(e, "tab")
        @lastWasInsert = false
      null

    insert: =>
      @lastWasInsert = true
      super

    guessNamespace: =>
      @session.getValue().match(/^\s*\(\s*ns\s+([^\s)]+)/m)?[1]

    complete: =>
      pos = @getCursorPosition()
      cmd = @session.getLine(pos.row)[...pos.column].match(keybindings.completeRe)[0]
      if cmd
        @socket.complete(@guessNamespace(), cmd, "editor")
        true
      else false

    tabOrComplete: =>
      if @lastWasInsert and @complete() then return
      pos = @getCursorPosition()
      @indent()

    onSocketMessage: (e) =>
      msg = e.message
      if msg.complete? and msg.tag == "editor"
        e.stopPropagation()
        @onCompleteMessage msg
      else if msg.fs? and msg.fs.command == "read"
        e.stopPropagation()
        @openBuffer(msg.fs.path, msg.fs.file, msg.tag)
      else if msg.fs? and msg.fs.command == "files"
        e.stopPropagation()
        new FileSelector(msg.fs.files, @getBufferHistory()).on("selected", @onFileSelected)
      else if msg.fs? and msg.fs.command == "dirs"
        e.stopPropagation()
        new FileCreator(msg.fs.dirs).on("selected", @onNewFile)
      else if msg.doc? and msg.tag == "editor"
        e.stopPropagation()
        @onDocumentSymbol(msg)

    getCursorAnchor: =>
      cursor = @getCursorPosition()
      coords = @renderer.textToScreenCoordinates(cursor.row, cursor.column)
      return {
        x: coords.pageX
        y: coords.pageY + @renderer.lineHeight
        anchor: "top-left"
      }

    onCompleteMessage: (msg) =>
      if @suggestBox? then @suggestBox.close()
      if msg.complete.length
        cursor = @getCursorPosition()
        coords = @renderer.textToScreenCoordinates(cursor.row, cursor.column)
        @suggestBox = new SuggestBox(msg.complete, @getCursorAnchor(), msg.ns, @socket)
        @suggestBox.on("selected", @insertCompletion)
        @suggestBox.on("resuggest", @complete)
        @suggestBox.on("closed", (=> @suggestBox = null))

    insertCompletion: (e) =>
      @selection.clearSelection()
      pos = @getCursorPosition()
      line = @session.getLine(pos.row)[...pos.column]
      cmd = line.match(keybindings.completeRe)[0]
      @selection.selectTo(pos.row, pos.column - cmd.length)
      @insert(e.selected)

    saveBuffer: (e) =>
      e?.preventDefault()
      @session.dirty = false
      @socket.saveBuffer(@session.bufferName, @session.getValue())

    saveAndTest: (e) =>
      e?.preventDefault()
      @session.dirty = false
      @socket.saveBuffer(@session.bufferName, @session.getValue(), "test")

    evalBuffer: (e) =>
      e?.preventDefault()
      @socket.eval(@session.getValue(), "compile")

    runTests: (e) =>
      e?.preventDefault()
      form = @session.getValue() + "\n(clojure.test/run-tests)"
      @socket.eval(form, "test")

    selectFile: (e) =>
      e?.preventDefault()
      @socket.files()

    createFile: (e) =>
      e?.preventDefault()
      @socket.dirs()

    loadBuffer: (buffer, line) =>
      @socket.readFile(buffer, if line then {line: line} else null)

    onFileSelected: (e) =>
      if not e.cancelled
        @loadBuffer(e.selected)
      else
        @focus()

    onNewFile: (e) =>
      if not e.cancelled
        @openBuffer(e.selected, "")
      else
        @focus()

    selectBuffer: (e) =>
      e?.preventDefault()
      list = @getBufferHistory()
      new FileSelector(list, list).on "selected", (e) =>
        if not e.cancelled
          @openBuffer(e.selected)
        else
          @focus()

    pushBufferHistory: (path, no_window_history) =>
      @bufferHistory = (x for x in @bufferHistory when x != path)
      @bufferHistory.unshift(path)
      if not no_window_history
        url = "/buffers/#{path}"
        if url != window.location.pathname
          window.history.pushState({}, "", url)

    getBufferHistory: =>
      (x for x in @bufferHistory when @buffers[x]?)

    createSession: (path, content) =>
      mode = modemap[fileExtension(path)]
      session = new edit_session.EditSession(content, if mode then new mode)
      session.setUndoManager(new undomanager.UndoManager())
      session.setUseSoftTabs(true)
      session.setTabSize(2)
      session.bufferName = path
      session.dirty = false
      session.on "change", ->
        session.dirty = true
      session

    openBuffer: (path, content, tag) =>
      @session._storedCursorPos = @getCursorPosition()
      filename = path.split("/").pop()
      $("div.navbar a.brand").text(filename)
      window.document.title = "#{path} : Catnip"
      if @buffers[path]?
        session = @buffers[path]
        session.setValue(content) if content
      else
        @buffers[path] = session = @createSession(path, content)
      @setSession(session)
      @pushBufferHistory(path, tag == "window-history")
      if tag?.line
        @navigateTo(tag.line - 1, 0)
        @centerSelection()
      else if session._storedCursorPos?
        @navigateTo(session._storedCursorPos.row, session._storedCursorPos.column)
      @focus()
      @_emit "openBuffer"
        path: path
        session: session

    getBufferAccordingToURL: ->
      m = window.location.href.match(/\/buffers\/(.*)/)
      if m then m[1] else "project.clj"

    onWindowHistoryPopState: (e) =>
      buffer = @getBufferAccordingToURL()
      if buffer and @session.bufferName != buffer
        @socket.readFile(buffer, "window-history")

    getSymbolAtPoint: =>
      { row, column } = @getCursorPosition()
      line = @session.getLine(row)
      before = line[...column].match(keybindings.completeRe)
      after = line[column..].match(/^[^\s()\[\]{},\'`~\#@]*/)
      before + after

    getCharBeforePoint: =>
      { row, column } = @getCursorPosition()
      @session.getLine(row)[column - 1]

    documentSymbol: =>
      @socket.doc(@guessNamespace(), @getSymbolAtPoint(), "editor")

    onDocumentSymbol: (msg) =>
      @doctip?.close()
      if msg.doc
        @doctip = new Doctip(msg.doc, $("#view"))

    snippetKeys: => k for own k of window.CatnipProfile.snippets
    snippet: (s) => window.CatnipProfile.snippets[s]

    expandSnippet: =>
      pos = @getCursorPosition()
      line = @session.getLine(pos.row)[...pos.column]
      cmd = line.match(keybindings.completeRe)[0]
      console.log cmd
      new FileSelector(@snippetKeys(), null, cmd).on "selected", (e) =>
        @focus()
        if not e.cancelled
          @selection.clearSelection()
          pos = @getCursorPosition()
          line = @session.getLine(pos.row)[...pos.column]
          cmd = line.match(keybindings.completeRe)[0]
          @selection.selectTo(pos.row, pos.column - cmd.length)
          @insert(@snippet(e.selected))

    onWindowBeforeUnload: (e) =>
      for own path, session of @buffers
        if session.dirty
          return e.returnValue = "Buffer \"#{path}\" has been modified."

    evaluateSexp: =>
      if @getCharBeforePoint() in ")}]\""
        pos = @getCursorPosition()
        @jumpToMatching(true)
        sel = @session.doc.getTextRange(@getSelectionRange())
        @moveCursorToPosition(pos)
      else
        sel = @getSymbolAtPoint()
      sel = sel.trim()
      if sel
        @socket.eval(sel, "repl")
