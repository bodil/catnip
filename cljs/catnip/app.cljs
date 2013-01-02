(ns catnip.app
  (:use-macros [catnip.requirejs :only [require]]
               [jayq.macros :only [ready]])
  (:use [catnip.editor :only [create-editor]]
        [catnip.session :only [create-session]])
  (:require [jayq.core :as j :refer [$]]
            [catnip.socket :as socket]
            [redlobster.promise :as p]))

(defn main []
  (j/add-class ($ "body") (str "theme-" (or window/CatnipProfile.theme "light")))

  (socket/connect)
  (.setSession (create-editor (.getElementById js/document "editor"))
               (create-session "test.clj"
                               "(ns test)\n\n(println \"Hello sailor!\")\n"))

  (p/on-realised
   (socket/send {:eval "(+ 1300 37)" :path "*repl*"})
   #(.debug js/console "Result:" (str (:result (first (:eval %)))))
   #(.error js/console "Message failed"))

  (j/remove-class ($ "body") "loading"))

(ready (main))
