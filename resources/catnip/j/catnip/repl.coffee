# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "cs!./keybindings", "./caret"
        "cs!./suggestbox", "ace/lib/event_emitter"
        "cs!./pprint", "cs!./doctip", "cs!./unpprint"
], ($, keybindings, caret, SuggestBox, event_emitter, pprint, Doctip, unpprint) ->

  EventEmitter = event_emitter.EventEmitter

  fileExtension = (path) -> path.split(".").pop()

  breakable = (t) ->
    t.replace /([.$/-])/g, (c) -> "\u200B#{c}"

  class REPL
    constructor: (@input, @display, @prompt, @editor, @browser, @socket) ->
      this[key] = EventEmitter[key] for own key of EventEmitter

      @input.on "keydown", @onKeyDown
      @display.on "click", "div.exception a", @onExceptionClick
      @display.on "mouseover", "a.clojure", @onMouseOverFunction
      @display.on "mouseout", "a.clojure", @onMouseOutFunction
      @display.on "mouseover", ".lparen, .rparen, .whitespace", @onMouseOverParen
      @display.on "mouseout", ".lparen, .rparen, .whitespace", @onMouseOutParen
      @display.on "click", ".clojure", @onFormClick
      @editor.on "sexp-to-repl", @onSexpToRepl

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
        "C-,": @editor.saveAndTest
        "C-f": @editor.selectFile
        "C-b": @editor.selectBuffer
        "C-M-f": @editor.createFile

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
      if msg.error?
        e.stopPropagation()
        @onErrorMessage msg
      else if msg.complete? and msg.tag == "repl"
        e.stopPropagation()
        @onCompleteMessage msg
      else if msg.fs? and msg.fs.command == "save"
        e.stopPropagation()
        @onSaveBuffer msg
      else if msg.eval?
        e.stopPropagation()
        @onEvalMessage msg
      else if msg.fs? and msg.fs.command == "cljsc"
        e.stopPropagation()
        @onCljscMessage(msg)

    onCompleteMessage: (msg) =>
      if @suggestBox? then @suggestBox.close()
      if msg.complete.length
        bounds = @getCaretBounds()
        pos =
          x: bounds.left
          y: bounds.top
          anchor: "bottom-left"
        @suggestBox = new SuggestBox(msg.complete, pos, msg.ns, @socket)
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

    printNode: (node) =>
      @display.append(node)
      @lastOutputNode = node
      if !@replFlushTimeout?
        @replFlushTimeout = window.setTimeout(@onReplFlushTimeout, 50)

    replPrint: (type, msg, ns) =>
      node = $("<p></p>").addClass(type)
      if type == "code" and ns
        node.text("#{ns}» ")
        if typeof msg == "string"
          node.append($('<span class="clojure"></span>').text(msg))
        else
          pprint(node, msg)
      else
        if typeof msg == "string"
          node.text(msg)
          node.html(node.html().replace(/https?:\/\/\S*/g, (i) -> "<a href=\"#{i}\" target=\"_blank\">#{i}</a>"))
        else
          pprint(node, msg)
      @printNode(node)

    exceptionNode: (e, posInFile) =>
      if e.cause and e.class.match(/.*clojure\.lang\.Compiler\$CompilerException.*/)
        return @exceptionNode(e.cause, posInFile)

      elemLength = (el) ->
        if el.file and el.line
          "#{el.file}:#{el.line}".length
        else
          "(Unknown Source)".length
      traceWidth = Math.max.apply(this, (elemLength(el) for el in e["trace-elems"]))
      traceElem = (el) ->
        sourcePad = traceWidth - elemLength(el)
        spaces = new Array(sourcePad + 1).join(" ")
        source = if el.file and el.line
          """<span class="source">#{el.file}:#{el.line}</span>"""
        else
          """<span class="source">(Unknown Source)</span>"""
        method = if el.java
          """<span class="method">#{breakable(el.class+"."+el.method)}</span>"""
        else
          fn = el.fn + if el["anon-fn"] then " [fn]" else ""
          """<span class="method">#{breakable(el.ns+"/"+fn)}</span>"""
        a = if el.local then """<a href="#{el.local}" data-line="#{el.line}">""" else ""
        ax = if a != "" then "</a>" else ""
        """<p class="trace-elem" style="margin-left: #{1+traceWidth/2}em; text-indent: -#{1+traceWidth/2}em">#{spaces}#{a}<span class="source">#{source}</span> <span class="method">#{method}</span>#{ax}</p>"""
      errline = """
        <span class="class">#{e.class}</span>:
        <span class="message">#{e.message}</span>
      """
      if posInFile?
        errline = """
          <a href="#{posInFile.path}" data-line="#{posInFile.row}">
            #{errline}
          </a>
        """
      node = $("""
        <div class="exception">
          <p class="message">
            #{errline}
          </p>
          #{(traceElem(el) for el in e["trace-elems"]).join("")}
        </div>
      """)
      if e.cause
        node.append($("""<p class="caused-by">Caused by:</p>"""))
        node.append(@exceptionNode(e.cause))
      node

    replPrintException: (e, posInFile) =>
      @printNode(@exceptionNode(e, posInFile))

    onReplFlushTimeout: =>
      @replFlushTimeout = null
      @lastOutputNode?.scrollintoview({duration: 50})
      @lastOutputNode = null

    onErrorMessage: (msg) =>
      if typeof msg.error == "string"
        @replPrint("error",
          if msg.line? then "Line #{msg.line}: #{msg.error}" else msg.error)
        @replPrintException(msg.exception)
      else
        @replPrintException(msg.error)
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
          @replPrint("code", i.code.form, i.code.ns)
          if i.out then @replPrint("out", i.out)
          if i.error
            @replPrintException(i.error)
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
          annotation = null
          if error.line? and error.annotation?
            annotation =
              path: msg.path
              row: (error.errline or error.line) - 1
              column: null
              text: error.annotation
              type: "error"
            @editor.getSession().setAnnotations([annotation])
          result = @replPrintException(error.error, annotation)
        else
          if msg.tag != "test"
            @replPrint("result", "#{msg.ns} compiled successfully.")
            @browser.reload()
          else
            test = msg.eval[msg.eval.length - 1]
            result = {}
            for i in test.result.value[1..]
              result[i.key.value] = i.value.value
            type = if result.error
              "test-error"
            else if result.fail
              "test-fail"
            else if result.pass
              window.setTimeout (=> @browser.reload()), 1000
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
              timer = =>
                @display.addClass("test-clear")
                timer2 = =>
                  @display.removeClass(type)
                  timer3 = =>
                    @display.removeClass("test-clear")
                  window.setTimeout timer3, 1000
                window.setTimeout timer2, 10
              window.setTimeout(timer, 10)
            else
              @replPrint("test-error", "No tests in #{msg.ns}.")
              @browser.reload()

    correctLines: (msg) =>
      if not msg.eval?.length then return msg
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

    onSaveBuffer: (msg) =>
      if msg.fs.success
        @replPrint("result", "#{msg.fs.path} saved.")
        if fileExtension(msg.fs.path) == "clj" and msg.fs.path != "project.clj"
          if msg.tag == "test" then @editor.runTests() else @editor.evalBuffer()
        else if fileExtension(msg.fs.path) == "cljs"
          @socket.compileCljs(msg.fs.path)
        else
          @browser.reload()
      else
        @replPrint("error", "Save error: #{msg.fs.error}")

    onCljscMessage: (msg) =>
      error = null
      for r in msg.fs.result
        if not r.success
          error = r.error
      if not error
        @replPrint("result", "#{msg.fs.path} compiled successfully.")
        window.setTimeout((=> @browser.reload()), 2000)
      else
        @replPrint("error", "cljsc: #{error}")

    onExceptionClick: (e) =>
      e.preventDefault()
      a = $(e.currentTarget)
      @editor.loadBuffer(a.attr("href"), a.attr("data-line"))

    onSexpToRepl: (e) =>
      @input.val(e.sexp).focus()

    onMouseOverFunction: (e) =>
      @hoverDoctip = JSON.parse($(e.target).attr("data-doc"))
      Doctip.push(@hoverDoctip)

    onMouseOutFunction: (e) =>
      if @hoverDoctip
        @hoverDoctip = null
        Doctip.pop()

    onMouseOverParen: (e) =>
      e.stopPropagation()
      $(e.target).parent().addClass("paren-match")

    onMouseOutParen: (e) =>
      e.stopPropagation()
      $(e.target).parent().removeClass("paren-match")

    onFormClick: (e) =>
      e.stopPropagation()
      form = $(e.target)
      if form.hasClass("lparen") or form.hasClass("rparen")
        form = form.parent()
      form = unpprint(form[0])

      val = @input.val()
      caretPos = @input.caret().begin
      val = val[...caretPos] + form + val[caretPos..]
      @input.val(val)
      @input.focus()
