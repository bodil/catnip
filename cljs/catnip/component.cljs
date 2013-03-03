(ns catnip.component)

(defprotocol IComponent
  (-init [this])
  (-destroy [this]))
