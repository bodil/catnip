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
  empty = (line) -> line.match(/^\s*$/)
  isfunc = (token) ->
    token.type == "support.function" and token.value in ["fn", "defn"]

  class ExtendedCljMode extends ClojureMode
    constructor: ->
      super
      @$behaviour = new cstyle.CstyleBehaviour()

    _tokenise: (line, state) =>
      if line.state?
        state = line.state
        tokens = line.tokens
      else
        tokens = @$tokenizer.getLineTokens(line, state).tokens
      pos = 0
      parens = 0
      indent = if tokens[0]?.type in ["string", "text"] then getIndent(tokens[0].value) else 0
      lastcloseparen = null
      pindent = []
      infunc = null
      sinceparen = null
      indentingsince = null
      for token in tokens
        newpos = pos + token.value.length
        if isfunc(token) and last.value == "("
          infunc = pos
        if token.type == "string" and token.value == "\""
          null
        else if token.type not in unalign
          if sinceparen != null
            if sinceparen < indentingsince
              indent = pos
            sinceparen++
        else if token.type == "keyword"
          if token.value in openparens
            sinceparen = 0
            indentingsince = if token.value == "(" then 2 else 1
            parens++
            pindent.push(pos)
          else if token.value in closeparens
            parens--
            if parens >= 0
              indent = pindent.pop()
            lastcloseparen = newpos
        last = token if token.type != "text"
        pos = newpos
      if parens > 0 and infunc != null
        indent = infunc + 1

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
        pos = doc.findMatchingBracket({row: row, column: lastcloseparen})
        if pos
          doc.replace(new Range(row+1, 0, row+1, oldindent), spaces(pos.column))

    tokeniseDocument: (doc, upto) ->
      if not upto then upto = doc.doc.getLength() - 1
      rows = []
      state = "start"
      for row in [0..upto]
        line = doc.getLine(row)
        tokens = @$tokenizer.getLineTokens(line, state)
        state = tokens.state
        rows[row] = tokens

    indentForRow: (doc, tokenised, row) =>
      prev = row - 1
      while true
        if prev < 0 then return 0
        line = doc.getLine(prev)
        if not empty(line) then break
        prev--
      [tokens, indent, parens, lastcloseparen] = @_tokenise(tokenised[prev])
      if parens < 0 and lastcloseparen != null
        pos = doc.findMatchingBracket({row: prev, column: lastcloseparen})
        if pos then indent = pos.column
      indent

    autoIndentRow: (doc, tokenised, row) =>
      indent = @indentForRow(doc, tokenised, row)
      line = doc.getLine(row)
      current = getIndent(line)
      doc.replace(new Range(row, 0, row, current), spaces(indent))
      [current, indent]

    autoIndentCurrentRow: (doc) =>
      range = doc.selection.getRange()
      pos = doc.selection.getCursor()
      tokenised = @tokeniseDocument(doc, range.end.row)
      for row in [range.start.row..range.end.row]
        i = @autoIndentRow(doc, tokenised, row)
        if row == pos.row then [current, indent] = i
      if doc.selection.isEmpty() and pos.column < current
        doc.selection.moveCursorTo(pos.row, indent)

    autoIndentBuffer: (doc) =>
      tokenised = @tokeniseDocument(doc)
      for row in [0...doc.doc.getLength()]
        @autoIndentRow(doc, tokenised, row)
