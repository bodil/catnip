# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery",
        "ace/ace", "ace/theme/chrome", "ace/config"
        "cs!./repl", "cs!./buffermenu"], ($, ace, ace_theme, config, REPL, BufferMenu) ->
  $(document).ready ->
    editor = ace.edit "editor"
    editor.setTheme ace_theme

    window.repl = repl = new REPL $("#repl-input"), $("#repl-display"), $("#repl-prompt"), editor
    new BufferMenu($("#buffer-menu"), repl)
    repl.loadBuffer("project.clj")

    editor.focus()
    editor.navigateFileEnd()

    editor.commands.addCommand
      name: "saveBuffer"
      bindKey: "Ctrl-S"
      exec: (env, args, request) -> repl.saveBuffer()

    editor.commands.addCommand
      name: "saveAndTest"
      bindKey: "Ctrl-,"
      exec: (env, args, request) -> repl.saveAndTest()

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

    editor.commands.addCommand
      name: "selectFile"
      bindKey: "Ctrl-F"
      exec: => repl.selectFile()

    editor.commands.addCommand
      name: "selectBuffer"
      bindKey: "Ctrl-B"
      exec: => repl.selectBuffer()

    editor.commands.addCommand
      name: "createFile"
      bindKey: "Ctrl-Alt-F"
      exec: => repl.createFile()
