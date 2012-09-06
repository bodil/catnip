# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define [], () ->

  unpprint = (node) ->
    ts = for e in node.childNodes
      if e.nodeType == 3 then e.textContent
      else if e.nodeType == 1 then unpprint(e)
      else ""
    ts.join("")
