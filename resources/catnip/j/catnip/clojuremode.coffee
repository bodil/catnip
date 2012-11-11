# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["ace/mode/clojure", "ace/mode/behaviour/cstyle", "ace/range"],
(clojure_mode, cstyle, range) ->
  ClojureMode = clojure_mode.Mode
  Range = range.Range

  openparens = ["(", "[", "{"]
  closeparens = [")", "]", "}"]
  unalign = ["text", "keyword"]

  spaces = (n) -> new Array(n + 1).join(" ")
  getIndent = (line) ->
    match = line.match(/^(\s+)/)
    if match then match[1].length else 0

  class ExtendedCljMode extends ClojureMode
    constructor: ->
      super
      @$behaviour = new cstyle.CstyleBehaviour()

    _tokenise: (line, state) =>
      tokens = @$tokenizer.getLineTokens(line, state).tokens
      pos = 0
      parens = 0
      indent = 0
      lastcloseparen = null
      pindent = []
      for token in tokens
        newpos = pos + token.value.length
        if token.type not in unalign
          indent = pos
        else if token.type == "keyword"
          if token.value in openparens
            parens++
            pindent.push(pos)
          else if token.value in closeparens
            parens--
            if parens >= 0
              indent = pindent.pop()
            lastcloseparen = newpos
        pos = newpos

      [tokens, indent, parens, lastcloseparen]

    getNextLineIndent: (state, line, tab) =>
      [tokens, indent, parens] = @_tokenise(line, state)
      return spaces(indent)

    checkOutdent: (state, line, input) =>
      if input != "\n" then return false
      [tokens, indent, parens] = @_tokenise(line, state)
      return parens < 0

    autoOutdent: (state, doc, row) =>
      line = doc.getLine(row)
      [tokens, oldindent, parens, lastcloseparen] = @_tokenise(line, state)
      if lastcloseparen != null
        {column: indent} = doc.findMatchingBracket(
          {row: row, column: lastcloseparen})
        doc.replace(new Range(row+1, 0, row+1, oldindent), spaces(indent))

    indentForRow: (doc, row) =>
      if row == 0 then return 0
      line = doc.getLine(row - 1)
      [tokens, indent, parens, lastcloseparen] = @_tokenise(line, "start")
      if lastcloseparen != null
        {column: indent} = doc.findMatchingBracket(
          {row: row - 1, column: lastcloseparen})
      indent

    autoIndentRow: (doc, row) =>
      indent = @indentForRow(doc, row)
      line = doc.getLine(row)
      current = getIndent(line)
      doc.replace(new Range(row, 0, row, current), spaces(indent))
      [current, indent]

    autoIndentCurrentRow: (doc) =>
      pos = doc.selection.getCursor()
      [current, indent] = @autoIndentRow(doc, pos.row)
      if pos.column < current
        doc.selection.moveCursorTo(pos.row, indent)

    autoIndentBuffer: (doc) =>
      for row in [0...doc.doc.getLength()]
        @autoIndentRow(doc, row)
