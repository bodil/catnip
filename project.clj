(defproject catnip "0.5.0-SNAPSHOT"
  :plugins [[lein-cljsbuild "0.2.5"]
            [lein-exec "0.2.1"]]
  :description "The irresistible Clojure IDE-in-a-plugin"
  :url "https://github.com/bodil/catnip"
  :license {:name "Mozilla Public License"
            :url "http://www.mozilla.org/MPL/2.0/"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.webbitserver/webbit "0.4.6"]
                 [org.clojure/clojurescript "0.0-1535"]
                 [clj-info "0.3.1"]
                 [enlive "1.0.1"]
                 [clj-stacktrace "0.2.5"]
                 [cheshire "4.0.4"]]
  :cljsbuild {:builds
              [{:source-path "src"
                :compiler
                {:output-to "resources/catnip/cljs/main.js"
                 :output-dir "resources/catnip/cljs"
                 :optimizations :simple
                 :pretty-print true}}]}
  :main catnip.server)
