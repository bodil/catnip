window.onload = ->
  editor = ace.edit "editor"
  editor.setTheme "ace/theme/chrome"
  ClojureMode = require("ace/mode/clojure").Mode
  editor.getSession().setMode new ClojureMode
  editor.getSession().setUseSoftTabs true
  editor.getSession().setTabSize 2
  editor.focus()
  editor.navigateFileEnd()

  bindKey = (key) -> { win: key, mac: key, sender: "editor" }

  editor.commands.addCommand
    name: "slideForward"
    bindKey: bindKey("Ctrl-Alt-Right")
    exec: (env, args, request) ->
      env.editor.blur()
      # slideshow.next()

  editor.commands.addCommand
    name: "runCode"
    bindKey: bindKey("Alt-R")
    exec: (env, args, request) -> # slideshow.runCode()

  editor.commands.addCommand
    name: "removetolineend"
    bindKey: bindKey("Ctrl-K")
    exec: (env, args, request) -> editor.removeToLineEnd()

  editor.commands.addCommand
    name: "splitLine"
    bindKey: bindKey("Ctrl-Return")
    exec: (env, args, request) -> editor.splitLine()
