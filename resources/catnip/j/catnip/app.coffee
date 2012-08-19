# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "cs!./socket", "cs!./editor"
        "cs!./repl", "cs!./buffermenu", "cs!./browser", "cs!./keybindings"
], ($, Socket, Editor, REPL, BufferMenu, Browser, keybindings) ->
  $(document).ready ->
    socket = new Socket()
    editor = new Editor(document.getElementById("editor"), socket)
    window.browser = browser = new Browser $("#view"), $("#location-bar"), $("#location-refresh"), socket
    window.repl = repl = new REPL $("#repl-input"), $("#repl-display"), $("#repl-prompt"), editor, browser, socket
    new BufferMenu($("#buffer-menu"), editor)

    editor.loadBuffer(editor.getBufferAccordingToURL())

    $(window).on "keydown", (e) ->
      if keybindings.matchBinding e, "C-r"
        repl.focusEditor(e)
      else if keybindings.matchBinding e, "C-s"
        editor.saveBuffer(e)
      else if not $(window.document.body).hasClass("presentation-mode")
          if keybindings.matchBinding e, "C-p"
            e.preventDefault()
            $(window.document.body).addClass("presentation-mode")
            editor.blur()
            repl.input.blur()
      else
        keybindings.delegate e,
          "pagedown": browser.nextSlide
          "down": browser.nextSlide
          "right": browser.nextSlide
          "space": browser.nextSlide
          "pageup": browser.previousSlide
          "up": browser.previousSlide
          "left": browser.previousSlide
          "backspace": browser.previousSlide
          "C-p": (e) ->
            e.preventDefault()
            $(window.document.body).removeClass("presentation-mode")
            editor.focus()
