(ns iwaswhere-web.backup
  (:require [clojure.tools.logging :as log]
            [clojure.java.shell :refer [sh]]
            [iwaswhere-web.file-utils :as fu]))

(defn backup [{:keys []}]
  (when (System/getenv "GIT_COMMITS")
    (log/info "creating git commit")
    (prn (sh "/usr/bin/git" "add" fu/data-path :dir fu/data-path))
    (prn (sh "/usr/bin/git" "commit" "-m" "hourly commit" :dir fu/data-path)))
  {})

(defn state-fn [put-fn]
  (let [state (atom {:last-backup 0})]
    #_
    (put-fn [:cmd/send {:to  :server/scheduler-cmp
                        :msg [:cmd/schedule-new {:timeout (* 60 60 1000)
                                                 :message [:backup/git]
                                                 :repeat  true
                                                 :initial true}]}])
    {:state state}))

(defn cmp-map
  [cmp-id]
  {:cmp-id      cmp-id
   :state-fn    state-fn
   :handler-map {:backup/git backup}})