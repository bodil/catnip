# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define [
  "ace/mode/clojure"
  "ace/mode/coffee"
  "ace/mode/javascript"
  "ace/mode/json"
  "ace/mode/html"
  "ace/mode/css"
  "ace/mode/scss"
  "ace/mode/java"
  "ace/mode/markdown"
  "ace/mode/xml"
  "ace/mode/sql"
], (clj, coffee, js, json, html, css, scss, java, md, xml, sql) ->
  "clj": clj.Mode
  "cljs": clj.Mode
  "coffee": coffee.Mode
  "js": js.Mode
  "json": json.Mode
  "html": html.Mode
  "css": css.Mode
  "less": css.Mode
  "scss": scss.Mode
  "java": java.Mode
  "md": md.Mode
  "markdown": md.Mode
  "xml": xml.Mode
  "sql": sql.Mode
