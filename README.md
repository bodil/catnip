# Catnip

A browser based Clojure IDE.

## Usage

The installation procedure, for now:

```bash
    lein install
    cd plugin
    lein install
```

Then, add the plugin to your ~/.lein/profiles.clj file:

```clojure
    {:user {:plugins [...
                      [lein-catnip "0.1.0-SNAPSHOT"]]}}
```

You can now launch it from within your own Leiningen projects like this:

```bash
    lein edit
```

## License

Copyright © 2012 Bodil Stokke

Distributed under the [Mozilla Public License](http://mozilla.org/MPL/2.0/), version 2.0.
