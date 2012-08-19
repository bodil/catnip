(defproject catnip "0.3.0-SNAPSHOT"
  :plugins [[lein-cljsbuild "0.2.5"]]
  :description "The irresistible Clojure IDE-in-a-plugin"
  :url "https://github.com/bodil/catnip"
  :license {:name "Mozilla Public License"
            :url "http://www.mozilla.org/MPL/2.0/"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/data.json "0.1.3"]
                 [org.webbitserver/webbit "0.4.6"]
                 [org.clojure/clojurescript "0.0-1450"]
                 [clj-info "0.2.3"]]
  :cljsbuild {:builds
              [{:source-path "src"
                :compiler
                {:output-to "resources/catnip/cljs/main.js"
                 :output-dir "resources/catnip/cljs"
                 :optimizations :simple
                 :pretty-print true}}]}
  :main catnip.server)
