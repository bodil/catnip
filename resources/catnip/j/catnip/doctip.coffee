# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery"
], ($) ->

  class Doctip
    constructor: (text, cover) ->
      @box = $('<div></div>').addClass("doctip").hide()
      @decorateDoc(text)
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
      e?.preventDefault()
      @box.fadeOut(400, (=> @box.remove()))

    decorateDoc: (doc) ->
      lines = doc.split("\n")
      prelude = (lines.shift().trim() while not lines[0][0].match(/\s/))
      prelude.shift() if prelude[0].match(/-+/)

      paras = (l.trim() for l in lines).join("\n").split(/\n\n+/)
      paras = (l.trim() for l in paras when l.trim().length)

      pp = (type, text) -> $("<p></p>").addClass(type).text(text)

      @box.append(pp("symbol", prelude.shift())) if prelude.length
      @box.append(pp("arguments", prelude.shift())) if prelude.length
      @box.append(pp("form", prelude.shift())) if prelude.length
      @box.append(pp("prelude", prelude.shift())) while prelude.length

      @box.append(pp("body", paras.shift())) while paras.length
