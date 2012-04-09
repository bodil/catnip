# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery",
        "ace/ace", "ace/mode/clojure", "ace/theme/chrome"
        "cs!./repl"], ($, ace, clojure_mode, ace_theme, REPL) ->
  $(document).ready ->
    editor = ace.edit "editor"
    editor.setTheme ace_theme
    ClojureMode = clojure_mode.Mode
    editor.getSession().setMode new ClojureMode
    editor.getSession().setUseSoftTabs true
    editor.getSession().setTabSize 2
    editor.focus()
    editor.navigateFileEnd()

    window.repl = repl = new REPL $("#repl-input"), $("#repl-display"), $("#repl-prompt"), editor

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
