# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/lib/event_emitter"], ($, event_emitter) ->

  EventEmitter = event_emitter.EventEmitter

  class Browser
    constructor: (@frame, @locationBar, @refreshButton) ->
      this[key] = EventEmitter[key] for own key of EventEmitter
      @syncLocationBar()

      @frame.on "load", (e) => @syncLocationBar()
      @locationBar.parent().parent().on "submit", (e) =>
        e.preventDefault()
        @load(@locationBar.val())
      @refreshButton.on "click", (e) => @reload()

    syncLocationBar: =>
      @locationBar.val(@frame[0].src)

    load: (url) =>
      if not url.match(/^\w+:\/\//i)
        url = "http://#{url}"
      @frame[0].src = url

    reload: =>
      @frame[0].src = @frame[0].src if @frame[0].src

    nextSlide: (e) =>
      e?.preventDefault()
      @frame[0].contentWindow.postMessage("nextSlide", "*")

    previousSlide: (e) =>
      e?.preventDefault()
      @frame[0].contentWindow.postMessage("previousSlide", "*")
