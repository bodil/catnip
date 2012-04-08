# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

caret = require "mizugorou/caret"
keybindings = require "mizurogou/keybindings"

class REPL
  constructor: (@input, @display, @prompt, @editor) ->
    @input.on "keydown", @onKeyDown

    @history = []
    @historyPos = 0
    @historyTemp = null

    WebSocket = window.MozWebSocket || window.WebSocket
    @socket = new WebSocket("ws://" + window.location.host + "/repl")
    @socket.onmessage = @onSocketMessage

  onKeyDown: (e) =>
    keymap =
      "return": @onSubmit
      "up": @onHistoryBack
      "down": @onHistoryForward
      "C-r": @focusEditor
      "tab": @complete
      "C-s": @evalBuffer

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
    @sendToSocket
      eval: cmd

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
    cmd = @input.val()[...@input.caret().begin].match(/[^\s()\[\]{},\'`~\#]*$/)[0]
    if cmd
      @sendToSocket
        complete: cmd

  getCaretBounds: ->
    { begin, end } = @input.caret()
    caret.getTextBoundingRect @input[0], begin, end

  onSocketMessage: (e) =>
    msg = JSON.parse(e.data)
    if msg.complete?
      @onCompleteMessage msg
    else if msg.eval?
      @onEvalMessage msg
    else
      console.log "Unknown message received:", msg

  sendToSocket: (msg) =>
    @socket.send JSON.stringify msg

  onCompleteMessage: (msg) =>
    if @suggestBox? then @suggestBox.close()
    if msg.complete.length
      @suggestBox = new SuggestBox this, msg.complete, @getCaretBounds()

  onEvalMessage: (msg) =>
    @prompt.text(msg.ns)
    for i in msg.eval
      @display.append($('<p class="code"></p>')
        .text("#{i.code.ns}» ")
        .append($('<span class="clojure"></span>').text(i.code.text)))
      if i.out then @display.append($('<p class="output"></p>').text(i.out))
      if i.error
        result = $('<p class="error"></p>').text(i.error)
      else
        result = $('<p class="result"></p>').text(i.result)
      @display.append(result)
    result.scrollintoview()



class SuggestBox
  constructor: (@repl, @items, bounds) ->
    @pageSize = 4
    @box = $('<ul class="repl-suggest"></ul>')
    @box.css("left", bounds.left + "px")
    @box.css("bottom", ($("body").height() - bounds.top) + "px")
    @itemNodes = @items.map (item) -> $("<li>#{item}</li>")
    @itemNodes.forEach (node) => @box.append(node)
    $("body").append(@box)
    @box.on("click", @onClick)
    @activate 0
    @keymap =
      "return": @select
      "tab": @select
      "esc": @close
      "up": @up
      "down": @down
      "pageup": @pageUp
      "pagedown": @pageDown
      "M-x": @onFocusEditor
      "all": @resuggest

  activate: (i) =>
    @activeNode()?.removeClass("active")
    @active = i
    @activeNode().addClass("active").scrollintoview({duration: 50})

  activeNode: =>
    @itemNodes[@active]

  activeItem: =>
    @items[@active]

  select: (e) =>
    e?.preventDefault()
    val = @repl.input.val()
    caretPos = @repl.input.caret().begin
    cmd = val[...caretPos].match(/[^\s()\[\]{},\'`~\#]*$/)[0]
    start = caretPos - cmd.length
    afterCaret = val[caretPos..]
    beforeCaret = val[...start] + @activeItem()
    @repl.input.val(beforeCaret + afterCaret)
    @repl.input.caret(beforeCaret.length)
    @close()

  onClick: (e) =>
    domNodes = @itemNodes.map (i) -> i[0]
    index = domNodes.indexOf(e.target)
    if index >= 0
      @activate(index)
      @select()

  up: (e) =>
    e?.preventDefault()
    @activate(if @active == 0 then @items.length - 1 else @active - 1)

  down: (e) =>
    e?.preventDefault()
    @activate(if @active == @items.length - 1 then 0 else @active + 1)

  pageUp: (e) =>
    e?.preventDefault()
    @activate(Math.max(@active - @pageSize, 0))

  pageDown: (e) =>
    e?.preventDefault()
    @activate(Math.min(@active + @pageSize, @items.length - 1))

  close: (e) =>
    e?.preventDefault()
    @box.remove()
    @repl.suggestBox = null

  onFocusEditor: (e) =>
    @close()
    @repl.onFocusEditor(e)

  resuggest: (e) =>
    @close()
    window.setTimeout((=> @repl.complete()), 0)



$(document).ready ->
  editor = ace.edit "editor"
  editor.setTheme "ace/theme/chrome"
  ClojureMode = require("ace/mode/clojure").Mode
  editor.getSession().setMode new ClojureMode
  editor.getSession().setUseSoftTabs true
  editor.getSession().setTabSize 2
  editor.focus()
  editor.navigateFileEnd()

  repl = new REPL $("#repl-input"), $("#repl-display"), $("#repl-prompt"), editor

  editor.commands.addCommand
    name: "evalBuffer"
    bindKey: "Ctrl-S"
    exec: (env, args, request) -> repl.evalBuffer()

  editor.commands.addCommand
    name: "focusRepl"
    bindKey: "Ctrl-R"
    exec: (env, args, request) -> $("#repl-input").focus()

  editor.commands.addCommand
    name: "removetolineend"
    bindKey: "Ctrl-K"
    exec: (env, args, request) -> editor.removeToLineEnd()

  editor.commands.addCommand
    name: "splitLine"
    bindKey: "Ctrl-Return"
    exec: (env, args, request) -> editor.splitLine()

  window.editor = editor
  window.repl = repl
