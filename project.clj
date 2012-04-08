(defproject mizugorou "0.1.0-SNAPSHOT"
  :description "A browser based ClojureScript IDE"
  :url "https://github.com/bodil/mizugorou"
  :license {:name "Mozilla Public License"
            :url "http://www.mozilla.org/MPL/2.0/"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojure/data.json "0.1.2"]
                 [org.webbitserver/webbit "0.4.6"]
                 [clojail "0.5.1"]
                 [clojure-complete "0.2.1"]]
  :jvm-opts ["-Djava.security.policy=security.policy"]
  :main mizugorou.server)
