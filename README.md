# Catnip

Catnip is a Leiningen plugin providing a fully functional text editor
and REPL environment geared towards web development with Clojure and
ClojureScript.

![Screenshot](https://raw.github.com/bodil/catnip/master/catnip-screenshot.png)

## Usage

To install using Leiningen, simply add the plugin to your
~/.lein/profiles.clj file:

```clojure
    {:user {:plugins [...
                      [lein-catnip "0.1.0"]]}}
```

You can now launch it from within your own Leiningen projects like
this:

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

## ClojureScript

Catnip can compile ClojureScript files for you automatically, but you
need to add a
[lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild)
configuration to your `project.clj` file to make it work. Catnip will
figure out how to compile the ClojureScript by looking at the first
build in your `:cljsbuild` section. It takes the `:source-path` and
compiles every `.cljs` file in that path using the `:compiler` flags
specified. It ignores anything else in the build definition, so the
resulting code may differ from what lein-cljsbuild would produce.

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

Note that Catnip will only look at the first build configuration; if
you have more than one, the rest will be ignored.

## License

Copyright © 2012 Bodil Stokke

Distributed under the
[Mozilla Public License](http://mozilla.org/MPL/2.0/), version 2.0.
