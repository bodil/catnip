# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define "mizurogou/keybindings", (require, exports, module) ->
  specialKeys =
    8: "backspace", 9: "tab", 13: "return", 19: "pause",
    20: "capslock", 27: "esc", 32: "space", 33: "pageup",
    34: "pagedown", 35: "end", 36: "home", 37: "left",
    38: "up", 39: "right", 40: "down", 45: "insert",
    46: "del", 96: "0", 97: "1", 98: "2", 99: "3", 100: "4",
    101: "5", 102: "6", 103: "7", 104: "8", 105: "9",
    106: "*", 107: "+", 109: "-", 110: ".", 111 : "/",
    112: "f1", 113: "f2", 114: "f3", 115: "f4", 116: "f5",
    117: "f6", 118: "f7", 119: "f8", 120: "f9", 121: "f10",
    122: "f11", 123: "f12", 144: "numlock", 145: "scroll",
    188: ",", 190: ".", 191: "/"

  modifierKeys =
    16: "shift", 17: "ctrl", 18: "alt", 224: "meta"

  eventToString = (e) ->
    if modifierKeys[e.which]
      return ""
    l = [ specialKeys[e.which] or String.fromCharCode(e.which).toLowerCase() ]
    if e.shiftKey then l.unshift("S-")
    if e.ctrlKey then l.unshift("C-")
    if e.altKey then l.unshift("M-")
    if e.metaKey then l.unshift("⌘-")
    l.join("")

  matchBinding = (e, binding) ->
    eventToString(e).toLowerCase() == binding.toLowerCase()

  delegate = (e) ->
    for i in [1...arguments.length]
      keymap = arguments[i]
      for binding, func of keymap
        if matchBinding(e, binding)
          return func(e)
    for i in [1...arguments.length]
      keymap = arguments[i]
      if keymap["all"]
        return keymap["all"](e)

  eventToString: eventToString
  matchBinding: matchBinding
  delegate: delegate
