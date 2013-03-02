(ns catnip.debug
  (:require [clojure.browser.repl :as repl]))

(repl/connect "http://localhost:9337/repl")
