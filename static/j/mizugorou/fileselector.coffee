# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "cs!mizugorou/keybindings", "ace/lib/event_emitter"
], ($, keybindings, event_emitter) ->

  EventEmitter = event_emitter.EventEmitter

  filterCache = {}
  filterMatch = (filter, file) ->
    re = filterCache[filter]
    if not re
      re = filter.split("").join(".*")
      filterCache[filter] = re = new RegExp(re, "i")
    if file.match(re) then true else false

  levenshtein = (s, t) ->
    # From http://stackoverflow.com/a/6638467
    n = s.length
    m = t.length
    return m if n is 0
    return n if m is 0
    d       = []
    d[i]    = [] for i in [0..n]
    d[i][0] = i  for i in [0..n]
    d[0][j] = j  for j in [0..m]
    for c1, i in s
      for c2, j in t
        cost = if c1 is c2 then 0 else 1
        d[i+1][j+1] = Math.min d[i][j+1]+1, d[i+1][j]+1, d[i][j] + cost
    d[n][m]

  levenshteinSorter = (filter) ->
    (w1, w2) -> levenshtein(filter, w1) - levenshtein(filter, w2)

  class FileSelector
    constructor: (@fileSet, @bufferHistory) ->
      this[key] = EventEmitter[key] for own key of EventEmitter
      @input = $("<input></input>").attr("type", "text")
                .css({ position: "fixed", top: "-10000px" })
      @box = $("<div></div>").addClass("file-selector")
      @viewport = $("<div></div>").addClass("viewport")
      @list = $("<ul></ul>")
      @viewport.append(@list)
      @box.append(@viewport)
      $("body").append(@box).append(@input)
      @files = @orderByHistory(@fileSet)
      @populateList()
      $(window).on("resize", @onResize)
      @input.on("blur", @close)
            .on("keydown", @onKeyDown)
            .on("keyup", @onFilterChange)
      @input.focus()
      @pageSize = Math.round((@box.height() / @fileNodes[0].height()) / 2)
      @activeFilter = ""
      @selected = null
      @activate(if @bufferHistory.length > 1 then 1 else 0)

    close: (e) =>
      e?.preventDefault()
      @box.remove()
      @input.remove()
      $(window).off("resize", @onResize)
      @_emit "selected",
        selected: @selected
        cancelled: not @selected?

    orderByHistory: (list) =>
      l1 = (x for x in @bufferHistory when x in list)
      l2 = (x for x in list when x not in @bufferHistory)
      l1.concat(l2)

    populateList: =>
      @list.html("")
      if @files.length
        @fileNodes = @files.map (file) ->
          $("<li></li>").text(file)
      else
        @fileNodes = [$("<li></li>").text("No files match filter ")
          .append($("<span class=\"error\"></span>").text(@activeFilter))]
        @files = [null]
      @list.append(file) for file in @fileNodes

    activate: (index) =>
      @active = index
      node = @fileNodes[index]
      @activeNode?.removeClass("active")
      @activeNode = node
      node.addClass("active")
      if !@repositionTimeout?
        @onRepositionTimeout()
        @repositionTimeout = window.setTimeout(@onRepositionTimeout, 100)

    onRepositionTimeout: =>
      @repositionTimeout = null
      @scrollTo(@activeNode, 50)

    scrollTo: (node, speed) =>
      nodePos = node.position().top + (node.height() / 2)
      vpOffset = @box.height() / 2
      newPos = @viewport.scrollTop() + nodePos - vpOffset
      if speed
        @viewport.animate({ scrollTop: newPos }, speed)
      else
        @viewport.scrollTop(newPos)

    onFilterChange: (e) =>
      val = @input.val()
      if val != @activeFilter
        @filter(val)

    filter: (filter) =>
      @activeFilter = filter
      lastActive = @files[@active]
      if filter
        @files = (file for file in @fileSet when filterMatch(filter, file))
        @files.sort(levenshteinSorter(filter))
        activate = 0
      else
        @files = @fileSet
        activate = @files.indexOf(lastActive)
      @files = @orderByHistory(@files)
      @populateList()
      @activate(Math.max(activate, 0))

    onResize: (e) =>
      @scrollTo(@activeNode)

    onKeyDown: (e) =>
      swallow = (e) -> e.preventDefault()
      keybindings.delegate e,
        "up": @up
        "down": @down
        "pageup": @pageUp
        "pagedown": @pageDown
        "home": @top
        "end": @bottom
        "left": swallow
        "right": swallow
        "return": @select
        "tab": @select
        "esc": @close
        "C-g": @close

    up: (e) =>
      e?.preventDefault()
      @activate(if @active == 0 then @files.length - 1 else @active - 1)

    down: (e) =>
      e?.preventDefault()
      @activate(if @active == @files.length - 1 then 0 else @active + 1)

    pageUp: (e) =>
      e?.preventDefault()
      @activate(Math.max(@active - @pageSize, 0))

    pageDown: (e) =>
      e?.preventDefault()
      @activate(Math.min(@active + @pageSize, @files.length - 1))

    top: (e) =>
      e?.preventDefault()
      @activate(0)

    bottom: (e) =>
      e?.preventDefault()
      @activate(@files.length - 1)

    select: (e) =>
      e?.preventDefault()
      @selected = @files[@active] if @files.length and @files[@active]
      @close()
