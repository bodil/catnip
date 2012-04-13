# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/lib/event_emitter"
], ($, event_emitter) ->

  EventEmitter = event_emitter.EventEmitter

  class SuggestBox
    constructor: (@items, pos) ->
      this[key] = EventEmitter[key] for own key of EventEmitter

      @pageSize = 4
      @box = $('<ul class="repl-suggest"></ul>')
      if pos.anchor == "bottom-left"
        @box.css("left", pos.x + "px")
        @box.css("bottom", ($("body").height() - pos.y) + "px")
      else if pos.anchor == "top-left"
        @box.css("left", pos.x + "px")
        @box.css("top", pos.y + "px")
      @itemNodes = @items.map (item) -> $("<li>#{item}</li>")
      @itemNodes.forEach (node) => @box.append(node)
      $("body").append(@box)
      @box.on("click", @onClick)
      @activate 0
      @keymap =
        "return": @select
        "tab": @select
        "esc": @close
        "up": @up
        "down": @down
        "pageup": @pageUp
        "pagedown": @pageDown
        "all": @resuggest

    activate: (i) =>
      @activeNode()?.removeClass("active")
      @active = i
      @activeNode().addClass("active").scrollintoview({duration: 50})

    activeNode: =>
      @itemNodes[@active]

    activeItem: =>
      @items[@active]

    select: (e) =>
      e?.preventDefault()
      @_emit "selected",
        selected: @activeItem()
      @close()

    onClick: (e) =>
      domNodes = @itemNodes.map (i) -> i[0]
      index = domNodes.indexOf(e.target)
      if index >= 0
        @activate(index)
        @select()

    up: (e) =>
      e?.preventDefault()
      @activate(if @active == 0 then @items.length - 1 else @active - 1)

    down: (e) =>
      e?.preventDefault()
      @activate(if @active == @items.length - 1 then 0 else @active + 1)

    pageUp: (e) =>
      e?.preventDefault()
      @activate(Math.max(@active - @pageSize, 0))

    pageDown: (e) =>
      e?.preventDefault()
      @activate(Math.min(@active + @pageSize, @items.length - 1))

    close: (e) =>
      e?.preventDefault()
      @box.remove()
      @_emit "closed"

    resuggest: (e) =>
      window.setTimeout((=> @_emit("resuggest")), 10)
      @close()
