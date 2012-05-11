# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery"
], ($) ->

  class Doctip
    constructor: (doc, cover) ->
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
        @box.fadeOut(400, (=> @box.remove()))

    renderDoc: (doc) ->
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
