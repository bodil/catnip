# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/editor", "ace/virtual_renderer"
        "ace/theme/chrome", "cs!./keybindings", "cs!./suggestbox"
], ($, ace_editor, virtual_renderer, theme_chrome, keybindings, SuggestBox) ->

  AceEditor = ace_editor.Editor
  Renderer = virtual_renderer.VirtualRenderer

  class Editor extends AceEditor
    constructor: (element, @socket) ->
      super(new Renderer(element, theme_chrome))

      $(window).on("resize", (=> @resize()))
      @resize()
      @focus()

      @socket.on "message", @onSocketMessage

      @keyBinding.addKeyboardHandler
        handleKeyboard: @keyboardDelegate

      @commands.addCommand
        name: "removetolineend"
        bindKey: "Ctrl-K"
        exec: (env, args, request) => @removeToLineEnd()

      @commands.addCommand
        name: "splitLine"
        bindKey: "Ctrl-Return"
        exec: (env, args, request) => @splitLine()

      @commands.addCommand
        name: "tabOrComplete"
        bindKey: "Tab"
        exec: (env, args, request) => @tabOrComplete()

    keyboardDelegate: (data, hashId, keystring, keyCode, e) =>
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

    complete: =>
      pos = @getCursorPosition()
      cmd = @session.getLine(pos.row)[...pos.column].match(keybindings.completeRe)[0]
      if cmd
        # FIXME: Detect buffer's namespace
        @socket.complete(null, cmd, "editor")
        true
      else false

    tabOrComplete: =>
      if @lastWasInsert and @complete() then return
      pos = @getCursorPosition()
      @session.indentRows(pos.row, pos.row, @session.getTabString())

    onSocketMessage: (e) =>
      msg = e.message
      if msg.complete? and msg.tag == "editor"
        @onCompleteMessage msg
        e.stopPropagation()

    onCompleteMessage: (msg) =>
      if @suggestBox? then @suggestBox.close()
      if msg.complete.length
        cursor = @getCursorPosition()
        coords = @renderer.textToScreenCoordinates(cursor.row, cursor.column)
        pos =
          x: coords.pageX
          y: coords.pageY + @renderer.lineHeight
          anchor: "top-left"
        @suggestBox = new SuggestBox(msg.complete, pos)
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
