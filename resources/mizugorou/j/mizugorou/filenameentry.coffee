# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/lib/event_emitter", "cs!./keybindings"
], ($, event_emitter, keybindings) ->

  EventEmitter = event_emitter.EventEmitter

  # caretToEnd snippet from http://stackoverflow.com/a/3866442
  caretToEnd = (contentEditableElement) ->
    if document.createRange
      range = document.createRange()
      range.selectNodeContents(contentEditableElement)
      range.collapse(false)
      selection = window.getSelection()
      selection.removeAllRanges()
      selection.addRange(range)
    else if document.selection # IE8-
      range = document.body.createTextRange()
      range.moveToElementText(contentEditableElement)
      range.collapse(false)
      range.select()

  class FilenameEntry
    constructor: (@initial) ->
      this[key] = EventEmitter[key] for own key of EventEmitter
      @box = $("<div></div>").addClass("filename-entry")
      @entry = $("<div></div>").addClass("entry").attr("contenteditable", "true").text(@initial + "/")
      @box.append(@entry)
      $("body").append(@box)
      @entry.focus()
      caretToEnd(@entry[0])
      @entry.on("keydown", @onKeyDown)
      @selected = null

    onKeyDown: (e) =>
      keybindings.delegate e,
        "return": @select
        "C-g": @close
        "esc": @close

    close: (e) =>
      e?.preventDefault()
      @entry.off("keydown", @onKeyDown)
      @box.fadeOut 200, => @box.remove()
      @_emit "selected",
        selected: @selected
        cancelled: not @selected

    select: (e) =>
      e?.preventDefault()
      @selected = @entry.text()
      @close()
