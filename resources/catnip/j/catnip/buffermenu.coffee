# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/lib/event_emitter", "cs!./dropdown"
], ($, event_emitter, DropdownMenu) ->

  EventEmitter = event_emitter.EventEmitter

  class BufferMenu extends DropdownMenu
    constructor: (list, @editor) ->
      @attachToEditor(@editor) if @editor?
      super(list)

    attachToEditor: =>
      @editor.on("openBuffer", @updateList)

    updateList: =>
      super
      for buffer in @editor.getBufferHistory()[...12]
        @item(buffer, buffer)
      @divider()
      @item("-menu-new", "New...", "file", "Ctrl-Shift-F")
      @item("-menu-open", "Open...", "folder-open", "Ctrl-F")

    onSelected: (id) =>
      switch id
        when "-menu-new"
          @editor.createFile()
        when "-menu-open"
          @editor.selectFile()
        else
          @editor.openBuffer(id)
