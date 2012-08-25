# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/lib/event_emitter", "cs!./dropdown"
], ($, event_emitter, DropdownMenu) ->

  EventEmitter = event_emitter.EventEmitter

  cssClasses = (el) ->
    (x for x in $(el).attr("class").split(" ") when x)

  removeThemes = ->
    $("body").removeClass(x) for x in cssClasses("body") when x.match(/^theme-/)

  class ThemeMenu extends DropdownMenu
    constructor: (@editor) ->
      super()

    updateList: =>
      super
      @item("theme-light", "Light Theme",
        if $("body").hasClass("theme-light") then "ok" else "empty")
      @item("theme-dark", "Dark Theme",
        if $("body").hasClass("theme-dark") then "ok" else "empty")

    onSelected: (id) =>
      switch id
        when "theme-light"
          removeThemes()
          $("body").addClass("theme-light")
          @editor.updateTheme()
          window.CatnipProfile.theme = "light"
          @editor.socket.saveProfile()
        when "theme-dark"
          removeThemes()
          $("body").addClass("theme-dark")
          @editor.updateTheme()
          window.CatnipProfile.theme = "dark"
          @editor.socket.saveProfile()
      @updateList()

  class OptionsMenu extends DropdownMenu
    constructor: (list, @editor) ->
      @themeMenu = new ThemeMenu(@editor)
      super(list)

    updateList: =>
      super
      @submenu(@themeMenu, "Themes")
