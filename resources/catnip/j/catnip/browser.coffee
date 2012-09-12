# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/lib/event_emitter"], ($, event_emitter) ->

  EventEmitter = event_emitter.EventEmitter

  class Browser
    constructor: (@frame, @locationBar, @refreshButton, @socket) ->
      this[key] = EventEmitter[key] for own key of EventEmitter

      window.addEventListener("message", @onWindowMessage, false)

      @frame.on "load", (e) =>
        @syncLocationBar()
        @frame[0].contentWindow.postMessage("hello", "*")
      @locationBar.parent().parent().on "submit", (e) =>
        e.preventDefault()
        @load(@locationBar.val())
      @refreshButton.on "click", (e) => @reload()

      @frame[0].src = @url = window.CatnipProfile["default-browser-url"]

    syncLocationBar: (url) =>
      @url = url or @frame[0].src
      @locationBar.val(@url)

    load: (url) =>
      if not url.match(/^\w+:\/\//i)
        url = "http://#{url}"
      @frame[0].src = @url = url

    reload: =>
      @frame[0].src = @url

    onWindowMessage: (e) =>
      m = e.data.match(/^client-frame:(.*)/)
      if m
        args = JSON.parse(m[1])
        if args.console?
          msg = args.console.arguments.join(" ")
          window.repl.replPrint("out", msg)
        else if args.url?
          @syncLocationBar(args.url)

    nextSlide: (e) =>
      e?.preventDefault()
      @frame[0].contentWindow.postMessage("nextSlide", "*")

    previousSlide: (e) =>
      e?.preventDefault()
      @frame[0].contentWindow.postMessage("previousSlide", "*")

    slideLeft: (e) =>
      e?.preventDefault()
      @frame[0].contentWindow.postMessage("slideLeft", "*")

    slideRight: (e) =>
      e?.preventDefault()
      @frame[0].contentWindow.postMessage("slideRight", "*")

    slideUp: (e) =>
      e?.preventDefault()
      @frame[0].contentWindow.postMessage("slideUp", "*")

    slideDown: (e) =>
      e?.preventDefault()
      @frame[0].contentWindow.postMessage("slideDown", "*")

    escape: (e) =>
      e?.preventDefault()
      @frame[0].contentWindow.postMessage("escape", "*")
