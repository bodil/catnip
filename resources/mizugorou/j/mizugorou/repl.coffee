# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "cs!./keybindings", "./caret"
        "cs!./suggestbox", "cs!./fileselector", "ace/undomanager"
        "ace/edit_session", "ace/lib/event_emitter", "cs!./modemap"
        "cs!./filecreator"
], ($, keybindings, caret, SuggestBox, FileSelector, undomanager, edit_session, event_emitter, modemap, FileCreator) ->

  EventEmitter = event_emitter.EventEmitter

  fileExtension = (path) -> path.split(".").pop()

  class REPL
    constructor: (@input, @display, @prompt, @editor, @browser, @socket) ->
      this[key] = EventEmitter[key] for own key of EventEmitter

      @input.on "keydown", @onKeyDown

      @buffers = {}
      @bufferHistory = []

      @history = []
      @historyPos = 0
      @historyTemp = null

      @socket.on "message", @onSocketMessage

    onKeyDown: (e) =>
      keymap =
        "return": @onSubmit
        "up": @onHistoryBack
        "down": @onHistoryForward
        "C-r": @focusEditor
        "tab": @complete
        "C-s": @saveBuffer
        "C-,": @saveAndTest
        "C-f": => @selectFile
        "C-b": => @selectBuffer
        "C-M-f": => @createFile

      if @suggestBox
        for key of keymap
          action = keymap[key]
          keymap[key] = (e) =>
            @suggestBox.close()
            action(e)
        for key of @suggestBox.keymap
          keymap[key] = @suggestBox.keymap[key]
      keybindings.delegate e, keymap

    onSubmit: (e) =>
      cmd = @input.val()
      @input.val ""
      @pushHistory cmd
      @socket.eval(cmd, "repl")

    pushHistory: (cmd) =>
      unless @history[@history.length - 1] == cmd
        @history.push cmd
      @historyPos = 0

    onHistoryBack: (e) =>
      e.preventDefault()
      if @suggestBox?
        @suggestBox.up()
      else if @historyPos > -@history.length
        if @historyTemp == null then @historyTemp = @input.val()
        @historyPos -= 1
        @input.val @history[@history.length + @historyPos]
        @input.caret(@input.val().length)

    onHistoryForward: (e) =>
      e.preventDefault()
      if @suggestBox?
        @suggestBox.down()
      else if @historyPos < 0
        @historyPos += 1
        if @historyPos == 0
          @input.val @historyTemp
          @historyTemp = null
        else
          @input.val @history[@history.length + @historyPos]
        @input.caret(@input.val().length)

    focusEditor: (e) =>
      e?.preventDefault()
      @editor.focus()

    complete: (e) =>
      e?.preventDefault()
      if @suggestBox? then @suggestBox.close()
      cmd = @input.val()[...@input.caret().begin].match(keybindings.completeRe)[0]
      if cmd
        @socket.complete(null, cmd, "repl")

    getCaretBounds: ->
      { begin, end } = @input.caret()
      caret.getTextBoundingRect @input[0], begin, end

    onSocketMessage: (e) =>
      msg = e.message
      if msg.ns
        @prompt.text(msg.ns)
        e.stopPropagation()
      if msg.error?
        @onErrorMessage msg
        e.stopPropagation()
      else if msg.complete? and msg.tag == "repl"
        @onCompleteMessage msg
        e.stopPropagation()
      else if msg.fs?
        @onFileSystemMessage msg
        e.stopPropagation()
      else if msg.eval?
        @onEvalMessage msg
        e.stopPropagation()

    onCompleteMessage: (msg) =>
      if @suggestBox? then @suggestBox.close()
      if msg.complete.length
        bounds = @getCaretBounds()
        pos =
          x: bounds.left
          y: bounds.top
          anchor: "bottom-left"
        @suggestBox = new SuggestBox(msg.complete, pos)
        @suggestBox.on("selected", @insertCompletion)
        @suggestBox.on("resuggest", @complete)
        @suggestBox.on("closed", (=> @suggestBox = null))

    insertCompletion: (e) =>
      val = @input.val()
      caretPos = @input.caret().begin
      cmd = val[...caretPos].match(keybindings.completeRe)[0]
      start = caretPos - cmd.length
      afterCaret = val[caretPos..]
      beforeCaret = val[...start] + e.selected
      @input.val(beforeCaret + afterCaret)
      @input.caret(beforeCaret.length)
      @suggestBox = null

    replPrint: (type, msg, ns) =>
      node = $("<p></p>").addClass(type)
      if type == "code" and ns
        node.text("#{ns}» ").append($('<span class="clojure"></span>').text(msg))
      else
        node.text(msg)
      @display.append(node)
      @lastOutputNode = node
      if !@replFlushTimeout?
        @replFlushTimeout = window.setTimeout(@onReplFlushTimeout, 50)

    onReplFlushTimeout: =>
      @replFlushTimeout = null
      @lastOutputNode?.scrollintoview({duration: 50})
      @lastOutputNode = null

    onErrorMessage: (msg) =>
      @replPrint("error",
        if msg.line? then "Line #{msg.line}: #{msg.error}" else msg.error)
      if msg.line? and msg.annotation?
        @editor.getSession().setAnnotations([
          row: msg.line - 1
          column: null
          text: msg.annotation
          type: "error"
        ])
      else
        @editor.getSession().clearAnnotations()

    onEvalMessage: (msg) =>
      msg = @correctLines(msg)
      if msg.tag == "repl"
        for i in msg.eval
          @replPrint("code", i.code.text, i.code.ns)
          if i.out then @replPrint("out", i.out)
          if i.error
            @replPrint("error", i.error)
          else
            @replPrint("result", i.result)
      else
        @editor.getSession().clearAnnotations()
        error = null
        for i in msg.eval
          if i.error
            error = i
            break
        if error
          result = @replPrint("error", error.error)
          if error.line? and error.annotation?
            @editor.getSession().setAnnotations([
              row: (error.errline or error.line) - 1
              column: null
              text: error.annotation
              type: "error"
            ])
        else
          @browser.reload()
          if msg.tag != "test"
            result = @replPrint("result", "#{msg.ns} compiled successfully.")
          else
            test = msg.eval[msg.eval.length - 1]
            result = {}
            for i in test.result.match(/{(.*)}/)[1].split(",")
              [key, value] = i.match(/^\s*:(.*)\s+(.*)$/)[1..]
              result[key] = +value
            type = if result.error
              "test-error"
            else if result.fail
              "test-fail"
            else if result.pass
              "test-pass"
            if test.out
              @replPrint("out", test.out)
              annotations = test.out.match(/^(?:FAIL|ERROR).*NO_SOURCE_FILE:\d+\)$\n^\s*expected:.*$\n\s*actual:.*$/mg)
              if annotations
                annotations = (a.match(/^(?:FAIL|ERROR).*NO_SOURCE_FILE:(\d+)\)$\n([\s\S]*)/m)[1..] for a in annotations)
                @editor.getSession().setAnnotations ({
                  row: +a[0] - 1
                  column: null
                  text: a[1]
                  type: "warning"
                } for a in annotations)
            if type
              @replPrint(type, "#{msg.ns}: #{result.pass} passed, #{result.fail} failed, #{result.error} errors.")
              @display.addClass(type)
              window.setTimeout (=> @display.removeClass(type)), 10
            else
              @replPrint("test-error", "No tests in #{msg.ns}.")

    saveBuffer: (e) =>
      e?.preventDefault()
      session = @editor.getSession()
      @socket.saveBuffer(session.bufferName, session.getValue())

    saveAndTest: (e) =>
      e?.preventDefault()
      session = @editor.getSession()
      @socket.saveBuffer(session.bufferName, session.getValue(), "test")

    evalBuffer: (e) =>
      e?.preventDefault()
      @socket.eval(@editor.getSession().getValue(), "compile")

    runTests: (e) =>
      e?.preventDefault()
      form = @editor.getSession().getValue() + "\n(clojure.test/run-tests)"
      @socket.eval(form, "test")

    correctLines: (msg) =>
      lines = (l.trim() for l in @editor.getSession().getValue().split("\n"))
      lines = ((if l[0] == ";" then "" else l) for l in lines)
      closest = (n) ->
        until lines[n] or n >= lines.length
          n += 1
        n
      msg.eval[0].line = closest(0) + 1
      for i in msg.eval[1..]
        i.line = closest(i.line) + 1
      msg

    onFileSystemMessage: (msg) =>
      if msg.fs.command == "files"
        new FileSelector(msg.fs.files, @getBufferHistory()).on("selected", @onFileSelected)
      else if msg.fs.command == "dirs"
        new FileCreator(msg.fs.dirs).on("selected", @onNewFile)
      else if msg.fs.command == "read"
        @openBuffer(msg.fs.path, msg.fs.file)
      else if msg.fs.command == "save"
        if msg.fs.success
          @replPrint("result", "#{msg.fs.path} saved.")
          if fileExtension(msg.fs.path) == "clj" and msg.fs.path != "project.clj"
            if msg.tag == "test" then @runTests() else @evalBuffer()
        else
          @replPrint("error", "Save error: #{msg.fs.error}")

    selectFile: (e) =>
      e?.preventDefault()
      @socket.files()

    createFile: (e) =>
      e?.preventDefault()
      @socket.dirs()

    loadBuffer: (buffer) =>
      @socket.readFile(buffer)

    onFileSelected: (e) =>
      if not e.cancelled
        @loadBuffer(e.selected)
      else
        @editor.focus()

    onNewFile: (e) =>
      if not e.cancelled
        @openBuffer(e.selected, "")
      else
        @editor.focus()

    selectBuffer: (e) =>
      e?.preventDefault()
      list = @getBufferHistory()
      new FileSelector(list, list).on("selected", @onFileSelected)

    pushBufferHistory: (path) =>
      @bufferHistory = (x for x in @bufferHistory when x != path)
      @bufferHistory.unshift(path)

    getBufferHistory: =>
      (x for x in @bufferHistory when @buffers[x]?)

    openBuffer: (path, content) =>
      @editor.getSession()._storedCursorPos = @editor.getCursorPosition()
      filename = path.split("/").pop()
      $("div.navbar a.brand").text(filename)
      window.document.title = "#{path} : Mizugorou"
      if @buffers[path]?
        session = @buffers[path]
        session.setValue(content)
      else
        mode = modemap[fileExtension(path)]
        session = new edit_session.EditSession(content, if mode then new mode)
        session.setUndoManager(new undomanager.UndoManager())
        @buffers[path] = session
        session.setUseSoftTabs(true)
        session.setTabSize(2)
        session.bufferName = path
      @editor.setSession(session)
      @pushBufferHistory(path)
      if session._storedCursorPos?
        @editor.navigateTo(session._storedCursorPos.row, session._storedCursorPos.column)
      @editor.focus()
      @_emit "openBuffer"
        path: path
        session: session
