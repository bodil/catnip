# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "cs!./keybindings", "ace/lib/event_emitter"
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
    d = ([] for i in [0..n])
    d[i][0] = i  for i in [0..n]
    d[0][j] = j  for j in [0..m]
    for c1, i in s.toLowerCase()
      for c2, j in t.toLowerCase()
        cost = if c1 is c2 then 0 else 1
        d[i+1][j+1] = Math.min d[i][j+1]+1, d[i+1][j]+1, d[i][j] + cost
    d[n][m]

  levenshteinSorter = (filter) ->
    filter = filter.toLowerCase()
    (w1, w2) ->
      w1 = w1.toLowerCase()
      w2 = w2.toLowerCase()
      o1 = w1.indexOf(filter)
      o2 = w2.indexOf(filter)
      # consecutive hits always beat non-consecutive
      if o1 >= 0 and o2 < 0
        -1
      else if o1 < 0 and o2 >= 0
        1
      else
        # use Levenshtein distance if both or none are consecutive
        levenshtein(filter, w1) - levenshtein(filter, w2)

  dumbHtmlEscape =
    "<": "&lt;"
    ">": "&gt;"
    "&": "&amp;"

  quickEscape = (s) -> ((dumbHtmlEscape[c] or c) for c in s).join("")

  highlightFilter = (node, content, filter) ->
    if not filter # no filter -> quick passthrough
      node.text(content)
    else
      whole = content.toLowerCase().indexOf(filter.toLowerCase())
      out = ""
      if whole < 0 # highlight each letter as they occur
        for c in content
          if not filter
            out += dumbHtmlEscape[c] or c
          else
            if c.toLowerCase() == filter[0].toLowerCase()
              out += "<span class=\"filter\">" + (dumbHtmlEscape[c] or c) + "</span>"
              filter = filter[1..]
            else
              out += dumbHtmlEscape[c] or c
      else # filter occurs consecutively -> keep highlights together
        hit = content[whole...(whole + filter.length)]
        out += quickEscape(content[...whole])
        out += "<span class=\"filter\">" + quickEscape(hit) + "</span>"
        out += quickEscape(content[(whole + filter.length)..])
      node.html(out)


  class FileSelector
    constructor: (@fileSet, @bufferHistory, filter) ->
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
      @filter(filter) if filter
      @selected = null
      @box.addClass("fade-in")
      @activate((if @bufferHistory?.length > 1 then 1 else 0), 200)
      swallow = (e) -> e.preventDefault()
      @keymap =
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

    close: (e) =>
      e?.preventDefault()
      @box.fadeOut 200, @delayedClose

    delayedClose: =>
      @box.remove()
      @input.remove()
      $(window).off("resize", @onResize)
      @completeSelection()

    completeSelection: =>
      @_emit "selected",
        selected: @selected
        cancelled: not @selected?

    orderByHistory: (list) =>
      if @bufferHistory
        l1 = (x for x in @bufferHistory when x in list)
        l2 = (x for x in list when x not in @bufferHistory)
        l1.concat(l2)
      else
        list

    populateList: =>
      @list.html("")
      if @files.length
        @fileNodes = @files.map (file) =>
          highlightFilter($("<li></li>"), file, @activeFilter)
      else
        @fileNodes = [$("<li></li>").text("No files match filter ")
          .append($("<span class=\"filter\"></span>").text(@activeFilter))]
        @files = [null]
      @list.append(file) for file in @fileNodes

    activate: (index, speed) =>
      speed = speed or 50
      @active = index
      node = @fileNodes[index]
      @activeNode?.removeClass("active")
      @activeNode = node
      node.addClass("active")
      if !@repositionTimeout?
        @onRepositionTimeout(speed)
        @repositionTimeout = window.setTimeout(@onRepositionTimeout, speed * 2)

    onRepositionTimeout: (speed) =>
      @repositionTimeout = null
      @scrollTo(@activeNode, speed)

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
      keybindings.delegate e, @keymap

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
