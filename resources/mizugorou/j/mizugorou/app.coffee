# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "cs!./socket", "cs!./editor"
        "cs!./repl", "cs!./buffermenu", "cs!./browser", "cs!./keybindings"
], ($, Socket, Editor, REPL, BufferMenu, Browser, keybindings) ->
  $(document).ready ->
    socket = new Socket()
    editor = new Editor(document.getElementById("editor"), socket)
    window.browser = browser = new Browser $("#view"), $("#location-bar"), $("#location-refresh")
    window.repl = repl = new REPL $("#repl-input"), $("#repl-display"), $("#repl-prompt"), editor, browser, socket
    new BufferMenu($("#buffer-menu"), editor)
    editor.loadBuffer("project.clj")

    $(window).on "keydown", (e) ->
      if keybindings.matchBinding e, "C-r"
        @repl.focusEditor(e)
