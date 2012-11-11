# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "cs!./socket", "cs!./editor"
        "cs!./repl", "cs!./buffermenu", "cs!./optionsmenu",
        "cs!./browser", "cs!./keybindings"
], ($, Socket, Editor, REPL, BufferMenu, OptionsMenu, Browser, keybindings) ->

  $(document).ready ->
    theme = window.CatnipProfile.theme or "light"
    $("body").addClass("theme-#{theme}")

    socket = new Socket()

    editor = new Editor(document.getElementById("editor"), socket)
    window.browser = browser = new Browser $("#view"), $("#location-bar"), $("#location-refresh"), socket
    window.repl = repl = new REPL $("#repl-input"), $("#repl-display"), $("#repl-prompt"), editor, browser, socket
    new BufferMenu($("#buffer-menu"), editor)
    new OptionsMenu($("#options-menu"), editor)

    editor.loadBuffer(editor.getBufferAccordingToURL())
    setTimeout (-> $("body").removeClass("loading")), 1000

    togglePresentationMode = (e) ->
      e?.preventDefault()
      if $(window.document.body).hasClass("presentation-mode")
        $(window.document.body).removeClass("presentation-mode")
        editor.focus()
      else
        $(window.document.body).removeClass("hide-browser")
        $(window.document.body).addClass("presentation-mode")
        editor.blur()
        repl.input.blur()

    editor.commands.addCommand
      name: "presentationMode"
      bindKey: "Ctrl-P"
      exec: -> togglePresentationMode()

    $(window).on "keydown", (e) ->
      if keybindings.matchBinding e, "C-r"
        $(window.document.body).removeClass("presentation-mode")
        repl.focusEditor(e)
      else if keybindings.matchBinding e, "C-s"
        editor.saveBuffer(e)
      else if keybindings.matchBinding e, "C-1"
        e.preventDefault()
        if $(window.document.body).hasClass("presentation-mode")
          $(window.document.body).removeClass("presentation-mode")
          repl.focusEditor()
        browser.toggle()
        editor.resize()
      else if keybindings.matchBinding e, "C-p"
        togglePresentationMode(e)
      else if $(window.document.body).hasClass("presentation-mode")
        keybindings.delegate e,
          "pagedown": browser.nextSlide
          "space": browser.nextSlide
          "pageup": browser.previousSlide
          "backspace": browser.previousSlide
          "left": browser.slideLeft
          "right": browser.slideRight
          "up": browser.slideUp
          "down": browser.slideDown
          "esc": browser.escape
