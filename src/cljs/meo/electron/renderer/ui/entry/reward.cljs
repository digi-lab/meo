(ns meo.electron.renderer.ui.entry.reward
  (:require [matthiasn.systems-toolbox.component :as st]
            [clojure.string :as s]
            [moment]
            [taoensso.timbre :refer-macros [info error debug]]
            [meo.electron.renderer.helpers :as h]))

(defn reward-details [entry put-fn]
  (let [claimed (fn [entry]
                  (fn [ev]
                    (let [completion-ts (.format (moment))
                          updated (-> entry
                                      (assoc-in [:reward :claimed-ts] completion-ts)
                                      (update-in [:reward :claimed] not))]
                      (put-fn [:entry/update updated]))))
        set-points (fn [entry]
                     (fn [ev]
                       (let [v (.. ev -target -value)
                             parsed (when (seq v) (js/parseFloat v))
                             updated (assoc-in entry [:reward :points] parsed)]
                         (when parsed
                           (put-fn [:entry/update-local updated])))))]
    (when (contains? (set (:tags entry)) "#reward")
      (info "reward-details" entry)
      [:form.task-details
       [:fieldset
        [:legend "Reward details"]
        [:div
         [:label "Reward points: "]
         [:input {:type      :number
                  :on-change (set-points entry)
                  :value     (get-in entry [:reward :points] 0)}]]
        [:div
         [:label "Claimed? "]
         [:input {:type      :checkbox
                  :checked   (get-in entry [:reward :claimed])
                  :on-change (claimed entry)}]]]])))
