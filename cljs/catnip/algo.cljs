(ns catnip.algo)

(def levenshtein
  (memoize
   (fn levenshtein
     ([s1 s2] (levenshtein s1 0 (count s1) s2 0 (count s2)))
     ([s1 i1 l1 s2 i2 l2]
        (cond
         (zero? l1) l2
         (zero? l2) l1
         :else
         (let [cost (if (= (get s1 i1) (get s2 i2)) 0 1)]
           (min (inc (levenshtein s1 (inc i1) (dec l1) s2 i2 l2))
                (inc (levenshtein s1 i1 l1 s2 (inc i2) (dec l2)))
                (+ cost (levenshtein s1 (inc i1) (dec l1)
                                     s2 (inc i2) (dec l2))))))))))
