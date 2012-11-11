# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["ace/mode/text_highlight_rules"],
(highlight) ->

  nonalphanum = "></_$*+!?#.-"
  symbolstarter = "\\w#{nonalphanum}"
  symbolchar = "\\d" + symbolstarter
  symbolre = "[#{symbolstarter}][#{symbolchar}]*\\b"

  class ClojureHighlightRules extends highlight.TextHighlightRules
    constructor: ->

    $rules:
      "start": [
        { token: "comment", regex: ";.*$" }
        { token: "comment", regex: "^=begin$", next: "comment" }
        { token: "keyword", regex: "\\(", next: "function" }
        { token: "keyword", regex: "\\)" }
        { token: "keyword", regex: "[\\[\\]]" }
        { token: "keyword", regex: '(?:\\{|\\}|#\\{)' }
        { token: "keyword", regex: "&" }
        { token: "keyword", regex: '#\\^\\{' }
        { token: "keyword", regex: "%" }
        { token: "keyword", regex: "@" }
        { token: "constant.numeric", regex: "0[xX][0-9a-fA-F]+\\b" }
        {
          token: "constant.numeric"
          regex: "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
        }
        { token: "variable", regex: "['][:]?#{symbolre}" }
        { token: "keyword", regex: "\\'" }
        { token: "variable.parameter", regex: "[:]#{symbolre}" }
        { token: "support.function", regex: "[.]#{symbolre}" }
        { token: "identifier", regex: symbolre }
        { token: "identifier", regex: "[#{nonalphanum}]+" }
        { token: "string", regex: "\"", next: "string" }
        { token: "string.regexp", regex: '/#"(?:\\.|(?:\\\")|[^\""\n])*"/g' }
      ]
      "comment": [
        { token: "comment", regex: "^=end$", next: "start"}
        { token: "comment", regex: ".+", merge: true }
      ]
      "string": [
        { token: "constant.language.escape", regex: "\\\\.|\\\\$", merge: true }
        { token: "string", regex: '[^"\\\\]+', merge: true }
        { token: "string", regex: '"', merge: true, next: "start" }
      ]
      "function": [
        { token: "support.function", regex: symbolre, next: "start" }
        { token: "support.function", regex: "[#{nonalphanum}]+", next: "start" }
        { token: "keyword", regex: "", merge: true, next: "start" }
      ]
