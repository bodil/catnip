# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/lib/event_emitter"
], ($, event_emitter) ->

  EventEmitter = event_emitter.EventEmitter

  class BufferMenu
    constructor: (@list, @editor) ->
      this[key] = EventEmitter[key] for own key of EventEmitter
      @attachToEditor(@editor) if @editor?
      @list.on("click", "li.item", @onClick)
      @updateList()

    attachToEditor: =>
      @editor.on("openBuffer", @updateList)

    updateList: =>
      @list.html("")
      @buffers = @editor.getBufferHistory()
      @nodes = @buffers.map (buffer) =>
        node = $("<li></li>").addClass("item").text(buffer)
        @list.append(node)
        node[0]
      @list.append($("<li></li>").addClass("divider")) if @buffers.length
      @openItem = $("<li></li>").addClass("item").text("Open...")
        .append($("<span></span>").addClass("shortcut").text("Ctrl-F"))
      @nodes.push(@openItem)
      @list.append(@openItem)
      @newItem = $("<li></li>").addClass("item").text("New...")
        .append($("<span></span>").addClass("shortcut").text("Ctrl-Shift-F"))
      @nodes.push(@newItem)
      @list.append(@newItem)

    onClick: (e) =>
      index = @nodes.indexOf(e.target)
      if index < 0
        if (e.target == @openItem[0])
          @editor.selectFile()
        else if (e.target == @newItem[0])
          @editor.createFile()
      else
        @editor.loadBuffer(@buffers[index])
