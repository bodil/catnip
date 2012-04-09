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
      "C-,": @runTests

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
      tag: "repl"

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
    cmd = @input.val()[...@input.caret().begin].match(/[^\s()\[\]{},\'`~\#@]*$/)[0]
    if cmd
      @sendToSocket
        complete: cmd

  getCaretBounds: ->
    { begin, end } = @input.caret()
    caret.getTextBoundingRect @input[0], begin, end

  onSocketMessage: (e) =>
    console.log e.data
    msg = JSON.parse(e.data)
    if msg.ns
      @prompt.text(msg.ns)
    if msg.error?
      @onErrorMessage msg
    else if msg.complete?
      @onCompleteMessage msg
    else if msg.eval?
      @onEvalMessage msg
    else
      console.log "Unknown message received:", msg

  sendToSocket: (msg) =>
    @socket.send(JSON.stringify(msg))
    console.log JSON.stringify msg

  onCompleteMessage: (msg) =>
    if @suggestBox? then @suggestBox.close()
    if msg.complete.length
      @suggestBox = new SuggestBox this, msg.complete, @getCaretBounds()

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
          if test.out then @replPrint("out", test.out)
          if type
            @replPrint(type, "#{msg.ns}: #{result.pass} passed, #{result.fail} failed, #{result.error} errors.")
            @display.addClass(type)
            window.setTimeout (=> @display.removeClass(type)), 10
          else
            @replPrint("test-error", "No tests in #{msg.ns}.")

  evalBuffer: (e) =>
    e?.preventDefault()
    @sendToSocket
      eval: @editor.getSession().getValue()
      tag: "compile"

  runTests: (e) =>
    e?.preventDefault()
    @sendToSocket
      eval: @editor.getSession().getValue() + "\n(clojure.test/run-tests)"
      tag: "test"

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
    cmd = val[...caretPos].match(/[^\s()\[\]{},\'`~\#@]*$/)[0]
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
    name: "runTests"
    bindKey: "Ctrl-,"
    exec: (env, args, request) -> repl.runTests()

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
