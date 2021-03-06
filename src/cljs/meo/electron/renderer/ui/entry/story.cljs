(ns meo.electron.renderer.ui.entry.story
  (:require [re-frame.core :refer [subscribe]]
            [reagent.ratom :refer-macros [reaction]]
            [reagent.core :as r]
            [taoensso.timbre :refer [info error debug]]
            [meo.electron.renderer.helpers :as h]
            [clojure.set :as set]))

(defn editable-field [on-input-fn on-keydown-fn text]
  (fn [_ _ _]
    [:div.story-edit-field
     {:content-editable true
      :on-input         on-input-fn
      :on-key-down      on-keydown-fn}
     text]))

(defn keydown-fn [entry k put-fn]
  (fn [ev]
    (let [text (aget ev "target" "innerText")
          updated (assoc-in entry [k] text)
          key-code (.. ev -keyCode)
          meta-key (.. ev -metaKey)]
      (when (and meta-key (= key-code 83))                  ; CMD-s pressed
        (put-fn [:entry/update updated])
        (.preventDefault ev)))))

(defn input-fn [entry k put-fn]
  (fn [ev]
    (let [text (aget ev "target" "innerText")
          updated (assoc-in entry [k] text)]
      (put-fn [:entry/update-local updated]))))

(defn story-name-field
  "Renders editable field for story name when the entry is of type :story.
   Updates local entry on input, and saves the entry when CMD-S is pressed."
  [entry edit-mode? put-fn]
  (when (= (:entry_type entry) :story)
    (let [on-input-fn (input-fn entry :story_name put-fn)
          on-keydown-fn (keydown-fn entry :story_name put-fn)]
      (if edit-mode?
        [:div.story
         [:label "Story:"]
         [editable-field on-input-fn on-keydown-fn (:story_name entry)]]
        [:h2 "Story: " (:story_name entry)]))))

(defn saga-name-field
  "Renders editable field for saga name when the entry is of type :saga.
   Updates local entry on input, and saves the entry when CMD-S is pressed."
  [entry edit-mode? put-fn]
  (when (= (:entry_type entry) :saga)
    (let [on-input-fn (input-fn entry :saga_name put-fn)
          on-keydown-fn (keydown-fn entry :saga_name put-fn)]
      (if edit-mode?
        [:div.story
         [:label "Saga:"]
         [editable-field on-input-fn on-keydown-fn (:saga_name entry)]]
        [:h2 "Saga: " (:saga_name entry)]))))

(defn saga-select
  "In edit mode, allow editing of story, otherwise show story name."
  [entry put-fn edit-mode?]
  (let [sagas (subscribe [:sagas])
        ts (:timestamp entry)]
    (fn story-select-render [entry put-fn edit-mode?]
      (let [linked-saga (:linked_saga entry)
            entry-type (:entry_type entry)
            select-handler
            (fn [ev]
              (let [selected (js/parseInt (-> ev .-nativeEvent .-target .-value))
                    updated (assoc-in entry [:linked_saga] selected)]
                (info "saga-select" selected)
                (put-fn [:entry/update-local updated])))]
        (when (= entry-type :story)
          (if edit-mode?
            (when-not (:comment_for entry)
              [:div.story
               [:label "Saga:"]
               [:select {:value     (or linked-saga "")
                         :on-change select-handler}
                [:option {:value ""} "no saga selected"]
                (for [[id saga] (sort-by #(:saga_name (second %)) @sagas)]
                  (let [saga-name (:saga_name saga)]
                    ^{:key (str ts saga-name)}
                    [:option {:value id} saga-name]))]])
            (when linked-saga
              [:div.story "Saga: " (:saga_name (get @sagas linked-saga))])))))))

(defn merged-stories [predictions stories]
  (let [ranked (:ranked predictions)
        predictions-set (set ranked)
        stories-set (set stories)
        without-predictions (set/difference stories-set predictions-set)]
    (if (seq ranked)
      (concat ranked without-predictions)
      stories)))

(defn story-select [entry put-fn]
  (let [stories (subscribe [:stories])
        ts (:timestamp entry)
        local (r/atom {:search "" :show false :idx 0})
        story-predict (subscribe [:story-predict])
        predictions (reaction (get-in @story-predict [ts]))
        indexed (reaction
                  (let [story-tss (merged-stories @predictions (keys @stories))
                        stories (map #(get @stories %) story-tss)
                        s (:search @local)
                        filter-fn #(h/str-contains-lc? (:story_name %) s)
                        stories (vec (filter filter-fn stories))]
                    (map-indexed (fn [i v] [i v]) (take 10 stories))))
        assign-story (fn [story]
                       (let [ts (:timestamp story)
                             updated (assoc-in entry [:primary_story] ts)]
                         (swap! local assoc-in [:show] false)
                         (put-fn [:entry/update updated])))

        keydown (fn [ev]
                  (let [key-code (.. ev -keyCode)
                        idx-inc #(if (< % (dec (count @indexed))) (inc %) %)
                        idx-dec #(if (pos? %) (dec %) %)]
                    (when (:show @local)
                      (when (= key-code 27)
                        (swap! local assoc-in [:show] false))
                      (when (= key-code 40)
                        (swap! local update-in [:idx] idx-inc))
                      (when (= key-code 38)
                        (swap! local update-in [:idx] idx-dec))
                      (when (= key-code 13)
                        (assign-story (second (nth @indexed (:idx @local))))))
                    (.stopPropagation ev)))
        start-watch #(.addEventListener js/document "keydown" keydown)
        stop-watch #(.removeEventListener js/document "keydown" keydown)]
    (fn story-select-filter-render [entry put-fn]
      (let [linked-story (get-in entry [:story :timestamp])
            story-name (get-in entry [:story :story_name])
            input-fn (fn [ev]
                       (let [s (-> ev .-nativeEvent .-target .-value)]
                         (swap! local assoc-in [:idx] 0)
                         (swap! local assoc-in [:search] s)))
            mouse-leave (fn [_]
                          (let [t (js/setTimeout
                                    #(swap! local assoc-in [:show] false)
                                    1500)]
                            (swap! local assoc-in [:timeout] t)
                            (stop-watch)))
            mouse-enter #(do (info :mouse-enter)
                             (when-let [t (:timeout @local)] (js/clearTimeout t)))
            toggle-visible (fn [_]
                             (swap! local update-in [:show] not)
                             (if (:show @local) (start-watch) (stop-watch)))
            icon-cls (str (when (and (not (:primary_story entry))
                                     @predictions)
                            "predicted ")
                          (when (:show @local) "show"))]
        (when-not (or (:comment_for entry)
                      (contains? #{:story :saga} (:entry_type entry)))
          [:div.story-select
           (if (:show @local)
             (let [curr-idx (:idx @local)]
               (when-let [p (:p-1 @predictions)] (info p))
               [:div.story-search {:on-mouse-leave mouse-leave
                                   :on-mouse-enter mouse-enter}
                [:div
                 [:i.fal.fa-search]
                 [:input {:type       :text
                          :on-change  input-fn
                          :auto-focus true
                          :value      (:search @local)}]]
                [:table
                 [:tbody
                  (for [[idx story] @indexed]
                    (let [active (= linked-story (:timestamp story))
                          cls (cond active "current"
                                    (= idx curr-idx) "idx"
                                    :else "")
                          click #(assign-story story)]
                      ^{:key (:timestamp story)}
                      [:tr {:on-click click}
                       [:td {:class cls}
                        (:story_name story)]]))]]])
             [:div.story
              [:i.fal.fa-book {:on-click toggle-visible :class icon-cls}]
              story-name])])))))
