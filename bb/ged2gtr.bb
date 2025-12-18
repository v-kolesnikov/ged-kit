#!/usr/bin/env bb

(ns ged2gtr
  (:require [babashka.cli :as cli]
            [ged-kit.api :as ged]
            [ged-kit.gtr :as gtr]
            [clojure.string])
  (:gen-class))

(def cli-spec
  {:spec
   {:input {:desc "Input GEDCOM file (first arg by default)"}
    :xref {:desc "Target INDI Xref (second arg by default)"}
    :siblings {:desc "Whether to show the siblings of the target person"
               :coerce :boolean}
    :ancestor-siblings {:desc "Whether to show the siblings of the target person's ancestors"
                        :coerce :boolean}
    :ancestors {:desc "Number of ancestor generations to graph"
               :alias :a
               :coerce :long}
    :descendants {:desc "Number of descendant generations to graph"
                  :alias :d
                  :coerce :long}
    :banner {:desc "Whether to show the banner above the graph"
             :coerce :boolean}
    :verbose {:desc "Detailed logging for debug"
              :alias :v}
    :help {:alias :h
           :desc "Show this message and exit"}}
   :order [:input :xref
           :siblings :ancestor-siblings
           :ancestors :descendants
           :banner :verbose :help]
   :args->opts [:input :xref]
   :exec-args {:siblings true
               :ancestor-siblings true
               :banner true}})

(defn show-help [spec]
  (println "Usage: ged2gtr.bb input.ged XREF [OPTIONS]

  Create databases for genealogytree from GEDCOM files.

  The LaTeX genealogytree package (GTR) provides tools for including
  genealogy trees in LaTeX documents. One way of doing that is by storing
  the genealogical information in a GTR-specific database file. This tool
  allows you to create such databases from GEDCOM files .

  The input file is read, and a GTR database is written to to STDOUT.
  The GTR database contains a `sandclock` node for the person with the
  given XREF.

  Options:")
  (cli/format-opts spec))

(defn show-banner [opts]
  (println (str "% Produced by ged-kit.clj\n"
                "%\n"
                "% Arguments:\n"
                (->> (dissoc opts :banner :help :verbose)
                     (map (fn [[k v]] (str "% \t--" (name k) " = " v)))
                     (clojure.string/join "\n"))
                "\n%")))

(def sandclock-opts
  [:siblings
   :ancestor-siblings
   :ancestors
   :descendants])

(defn -main [args]
  (let [opts (cli/parse-opts args cli-spec)]
    (when (:verbose opts)
      (.println *err* (str "[debug] " opts)))
    (when (:help opts)
      (println (show-help cli-spec))
      (System/exit 0))
    (let [g (ged/parse-io (:input opts))
          result (gtr/gtr-string (gtr/sandclock g (:xref opts) []
                                                (select-keys opts sandclock-opts)))]
      (when (:banner opts)
        (show-banner opts))
      (println result))))

(-main *command-line-args*)