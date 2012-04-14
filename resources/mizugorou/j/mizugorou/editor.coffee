# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/editor", "ace/virtual_renderer"
        "ace/theme/chrome", "ace/lib/oop"
], ($, ace_editor, virtual_renderer, theme_chrome, oop) ->

  AceEditor = ace_editor.Editor
  Renderer = virtual_renderer.VirtualRenderer

  class Editor
    constructor: (element) ->
      AceEditor.call(this, new Renderer(element, theme_chrome))

      $(window).on("resize", (=> @resize()))
      @resize()
      @focus()

      @commands.addCommand
        name: "removetolineend"
        bindKey: "Ctrl-K"
        exec: (env, args, request) -> editor.removeToLineEnd()

      @commands.addCommand
        name: "splitLine"
        bindKey: "Ctrl-Return"
        exec: (env, args, request) -> editor.splitLine()

  oop.inherits(Editor, AceEditor)

  Editor
