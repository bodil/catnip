# Catnip

Catnip is a Leiningen plugin providing a fully functional text editor
and REPL environment geared towards web development with Clojure and
ClojureScript.

## Usage

To install using Leiningen, simply add the plugin to your ~/.lein/profiles.clj file:

```clojure
    {:user {:plugins [...
                      [lein-catnip "0.1.0"]]}}
```

You can now launch it from within your own Leiningen projects like this:

```bash
    lein edit
```

This will launch the Catnip web server and open it in your browser.
You'll be able to start writing code right away.

### Quickstart

Once you've installed the plugin as detailed above, this is all you
need to get started with a fresh Clojure project:

```bash
    lein new myproject
    cd myproject
    lein edit
```

## License

Copyright © 2012 Bodil Stokke

Distributed under the [Mozilla Public License](http://mozilla.org/MPL/2.0/), version 2.0.
