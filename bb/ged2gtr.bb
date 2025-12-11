#!/usr/bin/env bb

(ns ged2gtr
  (:require [babashka.cli :as cli]
            [ged-kit.api :as ged]
            [ged-kit.gtr :as gtr])
  (:gen-class))

(def cli-spec {:spec {}
               :args->opts [:input :xref]})

(defn show-help [spec]
  (cli/format-opts spec))

(defn -main [args]
  (let [opts (cli/parse-opts args cli-spec)]
    (if (or (:help opts) (:h opts))
      (println (show-help cli-spec))
      (let [g (ged/parse-io (:input opts))
            result (gtr/gtr-string (gtr/sandclock g (:xref opts)))]
        (println result)))))

(-main *command-line-args*)