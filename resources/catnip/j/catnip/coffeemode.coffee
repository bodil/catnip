# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["ace/mode/coffee", "ace/mode/behaviour/cstyle"], (coffee_mode, cstyle) ->
  CoffeeMode = coffee_mode.Mode

  class ExtendedCoffeeMode extends CoffeeMode
    constructor: ->
      super
      @$behaviour = new cstyle.CstyleBehaviour()
