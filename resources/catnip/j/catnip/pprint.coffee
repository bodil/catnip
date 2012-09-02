define ["jquery"], ($) ->

  span = (node, cl, text) ->
    text = "" if not text
    s = $("<span></span>").addClass("clojure").addClass(cl).text(text)
    node.append(s)
    s

  a = (node, cl, text, href) ->
    text = "" if not text
    s = $("<a></a>").addClass("clojure").addClass(cl).attr("href", href).text(text)
    node.append(s)
    s

  seq = (node, cl, parens, forms) ->
    s = span(node, cl)
    span(s, "lparen", parens[0])
    for value in forms
      pprint(s, value)
      space = document.createTextNode(" ")
      s.append(space)
    $(space).remove()
    span(s, "rparen", parens[1])
    s

  readerchar = (form) ->
    if form.value.length != 2 then return null
    car = form.value[0]
    if car.type == "special-form" and car.value.name in ["quote", "var"] then return car.value.name
    if car.type == "symbol" and car.namespace == "clojure.core" and car.value in ["unquote", "unquote-splicing"] then return car.value
    if car.type == "function" and car.value.ns == "clojure.core" and car.value.name in ["deref"] then return car.value.name
    null

  argsub = null

  shortfn = (node, form) ->
    if form.value.length > 2 and form.value[0].type == "special-form" and form.value[0].value.name == "fn*" and form.value[1].type == "vector"
      argsub = (x.value for x in form.value[1].value)
      seq(node, "list", ["#(", ")"], form.value[2].value)
      argsub = null
      true
    else false

  pprint = (node, form) ->
    switch form.type
      when "list"
        if rc = readerchar(form)
          span node, "reader-char", switch rc
            when "quote" then "'"
            when "unquote" then "~"
            when "unquote-splicing" then "~@"
            when "deref" then "@"
            when "var" then "#'"
            else "*READER-CHAR-ERROR*"
          pprint(node, form.value[1])
        else if shortfn(node, form)
          null
        else
          seq(node, "list", "()", form.value)
      when "vector"
        seq(node, "vector", "[]", form.value)
      when "set"
        seq(node, "set", ["\#{", "}"], form.value)
      when "map"
        s = span(node, "map")
        span(s, "lparen", "{")
        for pair in form.value
          pprint(s, pair.key)
          s.append(document.createTextNode(" "))
          pprint(s, pair.value)
          space = document.createTextNode(", ")
          s.append(space)
        $(space).remove()
        span(s, "rparen", "}")
      when "function"
        span(node, "function", form.name)
      when "macro"
        span(node, "macro", form.name)
      when "special-form"
        span(node, "special-form", form.value.name)
      when "symbol"
        name = form.value
        name = "#{form.namespace}/#{name}" if form.namespace
        if argsub and form.namespace == null and (sub = argsub.indexOf(form.value)) >= 0
          name = "%#{sub + 1}"
        span(node, "symbol", name)
      when "keyword"
        name = form.value
        name = "#{form.namespace}/#{name}" if form.namespace
        span(node, "keyword", ":#{name}")
      when "number"
        span(node, "number", form.name)
      when "string"
        span(node, "string", "\"#{form.value}\"")
      when "re"
        span(node, "re", "#\"#{form.value}\"")
      when "var"
        span(node, "var", form.value)
      when "object"
        span(node, "object", "#<#{form.value.name}>")
      else span(node, "error", "*ERROR*")
