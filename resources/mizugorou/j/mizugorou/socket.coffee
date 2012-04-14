# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/lib/event_emitter"
], ($, event_emitter) ->

  EventEmitter = event_emitter.EventEmitter

  class Socket
    constructor: ->
      this[key] = EventEmitter[key] for own key of EventEmitter

      WebSocket = window.MozWebSocket || window.WebSocket
      @socket = new WebSocket("ws://" + window.location.host + "/repl")
      @socket.onopen = @onSocketOpen
      @socket.onmessage = @onSocketMessage
      @socketQueue = []
      @socketOpen = false

    onSocketOpen: (e) =>
      @socketOpen = true
      @_emit "socketopen"
      for msg in @socketQueue
        @send(null, msg)
      @socketQueue = []

    onSocketMessage: (e) =>
      # console.log "incoming msg:", e.data
      e.message = JSON.parse(e.data)
      @_emit "message", e

    send: (tag, msg) =>
      msg.tag = tag if tag
      if @socketOpen
        @socket.send(JSON.stringify(msg))
      else
        @socketQueue.push(msg)

    saveBuffer: (name, content, tag) =>
      @send tag,
        fs:
          command: "save"
          path: name
          file: content

    eval: (form, tag) =>
      @send tag,
        eval: form

    files: (tag) =>
      @send tag,
        fs:
          command: "files"

    dirs: (tag) =>
      @send tag,
        fs:
          command: "dirs"

    readFile: (path, tag) =>
      @send tag,
        fs:
          command: "read"
          path: path

    complete: (namespace, word, tag) =>
      @send tag,
        namespace: namespace
        complete: word
