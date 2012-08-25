# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/lib/event_emitter"
], ($, event_emitter) ->

  EventEmitter = event_emitter.EventEmitter

  class DropdownMenu
    constructor: (@list) ->
      this[key] = EventEmitter[key] for own key of EventEmitter
      if not @list
        @list = $('<ul></ul>').addClass("dropdown-menu")
      @list.on("click", @onElementClick)
      @updateList()

    entry: (id, label, icon, binding) ->
      li = $("<li></li>").addClass("item").attr("data-menu-id", id)
      i = "<i class=\"icon-#{if icon then icon else "empty"}\"></i> "
      a = $('<a tabindex="-1"></a>').html(i + label)
      if binding
        a.append($("<span></span>").addClass("shortcut").text(binding))
      li.append(a)

    item: (id, label, icon, binding) =>
      @list.append(@entry(id, label, icon, binding))

    divider: =>
      @list.append($("<li></li>").addClass("divider"))

    submenu: (menu, label, icon) =>
      menu.list.remove()
      li = $("<li></li>").addClass("dropdown-submenu")
      i = "<i class=\"icon-#{if icon then icon else "empty"}\"></i> "
      a = $('<a tabindex="-1"></a>').html(i + label)
      li.append(a).append(menu.list)
      @list.append(li)
      @submenus.push(menu)

    updateList: =>
      @submenus = []
      @list.html("")

    onElementClick: (e) =>
      e.preventDefault()
      el = $(e.target)
      el = if el.filter("li").length
        el
      else
        $(el.parents("li")[0])
      @onClick(el)

    onClick: (target) =>
      @list.parent().removeClass("open")
      if target.parent()[0] == @list[0]
        id = target.attr("data-menu-id")
        @onSelected(id)
      else
        for menu in @submenus
          menu.onClick(target)
