# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "ace/lib/event_emitter"
], ($, event_emitter) ->

  EventEmitter = event_emitter.EventEmitter

  class Doctip
    constructor: (doc, cover) ->
      this[key] = EventEmitter[key] for own key of EventEmitter
      @box = $('<div></div>').addClass("doctip").hide()
      @renderDoc(doc)
      @box.offset(cover.offset())
      @box.width(cover.width())
      @box.height(cover.height())
      $("body").append(@box)
      marginWidth = @box.innerWidth() - cover.width()
      marginHeight = @box.innerHeight() - cover.height()
      @box.width(cover.width() - marginWidth)
      @box.height(cover.height() - marginHeight)
      @box.on("click", @close)
      @box.fadeIn(400)

    close: (e) =>
      if not e? or not $(e.target).attr("href")?
        e?.preventDefault()
        @_emit("closed")
        @box.fadeOut 400, =>
          @box.remove()

    renderDoc: (doc) ->
      @box.html("")
      pp = (type, text) -> $("<p></p>").addClass(type).text(text)
      renderArgs = (arg) -> "(" + ([doc.name].concat(arg)).join(" ") + ")"
      renderForm = (form) -> "(" + form.join(" ") + ")"

      @box.append(pp("symbol", "#{doc.fqname or doc.name}"))
      @box.append(pp("form", doc["object-type-str"]))

      if doc.arglists
        @box.append(pp("arguments", renderArgs(arg))) for arg in doc.arglists
      if doc.forms
        @box.append(pp("arguments", renderForm(form))) for form in doc.forms

      # @box.append(pp("prelude", prelude.shift())) while prelude.length

      if doc.doc
        lines = doc.doc.split("\n")
        paras = (l.trim() for l in lines).join("\n").split(/\n\n+/)
        paras = (l.trim() for l in paras when l.trim().length)
        @box.append(pp("body", paras.shift())) while paras.length

      if doc.url
        @box.append($("<p class=\"body\">See <a target=\"_blank\" href=\"#{doc.url}\">#{doc.name}</a></p>"))


  stack = []
  tip = null
  timeout = null

  onTimeout = ->
    tip.close()
    tip = timeout = null

  dispatch =
    push: (doc) ->
      if not doc then return
      if not tip?
        stack.push(doc)
        tip = new Doctip(doc, $("#view"))
        tip.on "closed", ->
          if timeout?
            window.clearTimeout(timeout)
            timeout = null
          tip = null
      else
        if timeout?
          window.clearTimeout(timeout)
          timeout = null
        stack.push(doc)
        tip.renderDoc(doc)

    pop: ->
      stack.pop()
      if stack.length > 0
        tip.renderDoc(stack[stack.length - 1])
      else
        timeout = window.setTimeout(onTimeout, 100)
