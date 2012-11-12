# Catnip

Catnip is a Leiningen plugin providing a fully functional text editor
and REPL environment geared towards web development with Clojure and
ClojureScript.

Catnip's primary goals are to facilitate the teaching of Clojure, and
to provide a simple yet powerful development environment for the
novice Clojure user.

![Screenshot](http://raw.github.com/bodil/catnip/master/catnip-screenshot-light.png)

## Installation

### Windows Installer

There is a (Windows
installer)[https://github.com/bodil/leiningen-for-dummies] available
that will attempt to automatically install a complete Catnip
environment on your system. This method is unsupported, but may work
if you're feeling lazy. We highly recommend you try the manual
installation procedure instead.

### Manual Installation

First, if you haven't already done so, install [Leiningen](https://github.com/technomancy/leiningen), the Clojure build system.

To install Catnip, add the plugin to your
[Leiningen user profile](https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md); if you don't already have one, make one by creating the file `~/.lein/profiles.clj` with the following text:

```clojure
    {:user {:plugins [[lein-catnip "0.4.1"]]}}
```

## Usage

### Quickstart

Once you've installed the plugin as detailed above, this is all you
need to get started with a fresh Clojure project:

```bash
    lein new myproject
    cd myproject
    lein edit
```

This will launch the Catnip web server and open it in your browser.
You'll be able to start writing code right away.

### General Usage

You can launch Catnip from within your own Leiningen projects like
this:

```bash
    lein edit
```

## ClojureScript

Catnip can compile ClojureScript files for you automatically, but you
need to add a
[lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild)
configuration to your `project.clj` file to make it work. Catnip will
automatically recompile needed builds when a file changes using the
following mechanism: for every build whose `:source-path` contains the
file being modified, it will run the CLJS compiler using that build's
`:compiler` flags. It ignores anything else in the build definition,
so the resulting code may differ from what lein-cljsbuild would
produce.

Here's an example `:cljsbuild` section for your `project.clj`:

```clojure
  :cljsbuild {:builds
              [{:source-path "src"
                :compiler
                {:output-to "resources/public/cljs/main.js"
                 :output-dir "resources/public/cljs"
                 :optimizations :simple
                 :pretty-print true}}]}
```

This takes any `.cljs` file under `src` (if you like mixing your
Clojure and ClojureScript code, like me) and compiles them into
`resources/public/cljs/main.js`. Whenever you save a ClojureScript
file in Catnip, it will use this configuration to recompile the
`main.js` file and reload the current page in the browser frame.

Please keep in mind that the level of optimisation will have a
considerable impact on compilation time, so you may wish to limit
optimisation to a bare minimum while developing.

## Browser Notes

Catnip is developed primarily for Google Chrome, and is tested
regularly (though not guaranteed to work perfectly at any given time)
on Mozilla Firefox. No proprietary browsers are supported.

## License

Copyright © 2012 Bodil Stokke

Distributed under the
[Mozilla Public License](http://mozilla.org/MPL/2.0/), version 2.0.
