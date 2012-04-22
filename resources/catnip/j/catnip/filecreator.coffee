# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at http://mozilla.org/MPL/2.0/.

define ["jquery", "cs!./fileselector", "cs!./filenameentry"
], ($, FileSelector, FilenameEntry) ->

  class FileCreator extends FileSelector
    constructor: (paths) ->
      super(paths, [])

    close: (e) =>
      e?.preventDefault()
      @delayedClose()

    completeSelection: =>
      if @selected
        new FilenameEntry(@selected).on "selected", (e) =>
          @_emit "selected",
            selected: e.selected
            cancelled: e.cancelled
      else
        super()
