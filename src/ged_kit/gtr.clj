(ns ged-kit.gtr
  "This module provides functions to build GTR database.
   GTR is data format for LaTeX `genealogytree` package.

   Most of the functions in this module takes GEDCOM graph
   as the first argument named `g`. GEDCOM graph is a
   hash-map like `{id gedcom-record, ...}`.

   Another common argument is `options` array. These options
   applies to database nodes as following `node[<key=value>]`."
  (:require [ged-kit.api :as ged]
            [ged-kit.date :as ged-date]
            [clojure.string :as string]
            [clojure.core.match :refer [match]])
  (:import java.time.LocalDate
           java.time.YearMonth
           [java.time.format DateTimeFormatter]))


(defn ^:private indent [n]
  (apply str (repeat n "  ")))

(defn ^:private indent+ [n]
  (indent (inc n)))

(defn ^:private wrap-curly [s]
  (str "{" s "}"))

(defn ^:private wrap-square [s]
  (str "[" s "]"))

(defn ^:private escape [s]
  (if (string/includes? s ",")
    (wrap-curly s)
    s))

(defn gtr-string
  ([gtr] (gtr-string gtr 0))
  ([gtr lvl]
   (let [{:keys [node options content]} gtr
         opts-pairs (->> options
                         (map (fn [[k v]] (str (name k) "=" v)))
                         (string/join ", "))
         cont-pairs (if (#{:g :c :p} node)
                      (->> content
                           (filter (comp some? second))
                           (map (fn [[k v]] (str (indent+ lvl) (name k) "=" (escape v) ",")))
                           (string/join "\n"))
                      (->> content
                           (map #(gtr-string % (inc lvl)))
                           (string/join "\n")))]
     (str (indent lvl) (name node) (wrap-square opts-pairs) "{%\n"
          cont-pairs "\n"
          (indent lvl) "}%"))))

(defn ^:private parse-date [s]
  (LocalDate/parse s (DateTimeFormatter/ofPattern "yyyyMMMdd")))

(def ^:private gtr-calendar
  {"JULIAN" "JU"
   "GREGORIAN" "GR"})

(defn gtr-date [date]
  (match date
    [:date [:year y]] (str y)
    [:date [:month m] [:year y]] (str (YearMonth/from (parse-date (str y m "01"))))
    [:date [:day d] [:month m] [:year y]] (str (parse-date (str y m (format "%02d" d))))
    [:date [:calendar c] & d] (str "(" (get gtr-calendar c) ")" (gtr-date (into [:date] d)))
    [:dateApprox "ABT" d] (str "(ca)" (gtr-date d))
    [:dateApprox "CAL" d] (str "(ca)" (gtr-date d))
    [:dateApprox "EST" d] (str "(ca)" (gtr-date d))
    [:dateRange "AFT" d] (str (gtr-date d) "/")
    [:dateRange "BEF" d] (str "/" (gtr-date d))
    [:dateRange "BET" d1 "AND" d2] (str (gtr-date d1) "/" (gtr-date d2))
    :else ""))

(defn node
  ([person] (node person :g []))
  ([person t] (node person t []))
  ([{id :id :as person} t options]
   {:node t
    :options (into [[:id id]] options)
    :content {:sex (case (ged/sex person)
                     "M" "male"
                     "F" "female"
                     "neuter")
              :name (str "\\pref" (wrap-curly (ged/given-name person)) \space
                         "\\surn" (wrap-curly (ged/surname person)))
              :birth (str (-> person ged/birth-date ged-date/parse gtr-date wrap-curly) "{}")
              :death (str (-> person ged/death-date ged-date/parse gtr-date wrap-curly) "{}")
              :profession (ged/occupation person)}}))

(defn wrap-node
  ([node content]
   (wrap-node node content []))
  ([node content options]
   {:node node
    :options options
    :content (into [] content)}))

(defn wrap-union
  ([content]
   (wrap-union content []))
  ([content options]
   (wrap-node :union content options)))

(defn add-marriage
  [person fam]
  (if-let [date (ged/marriage-date fam)]
    (assoc-in person [:content :marriage-]
              (-> date ged-date/parse gtr-date wrap-curly))
    person))

(defn g-node
  ([person] (g-node person []))
  ([person options] (node person :g options)))

(defn c-node
  ([person] (c-node person []))
  ([person options] (node person :c options)))

(defn p-node
  ([person] (p-node person []))
  ([person options] (node person :p options)))

(defn parent-node
  ([g person] (parent-node g person []))
  ([g person options]
   (when-let [parentes (ged/parentes g person)]
     (map (fn [spouse]
            (if (empty? (ged/parentes g spouse))
              (p-node spouse)
              {:node :parent
               :options options
               :content (into [(g-node spouse)]
                              (parent-node g spouse))}))
          parentes))))

(defn child-node
  ([g person] (child-node g person []))
  ([g person options]
   (let [[head-fam & rest-fams]
         (map (fn [{id :id :as fam}]
                (let [children (map (fn [child]
                                      (if (empty? (ged/children g child))
                                        (c-node child)
                                        (child-node g child [[:id id]])))
                                    (ged/family-children g fam))]
                  (if-let [spouse (ged/spouse g fam person)]
                    (into [(-> (p-node spouse)
                               (add-marriage fam))]
                          children)
                    children)))
              (ged/families g person))]
     {:node :child
      :options options
      :content (into [(g-node person)]
                     (into head-fam (map wrap-union rest-fams)))})))

(defn sandclock
  "Create GTR structure of sand clock diagram.

   The structure is a hash-map like:
   `{:node :sandclok :options [] :content [<nodes>]}`"
  ([g id] (sandclock g id []))
  ([g id options]
   (let [person (ged/indi g id)
         [{fam-id :id}] (ged/families g person)]
     {:node :sandclock
      :options options
      :content (into [(child-node g person [[:id fam-id]])]
                     (parent-node g person))})))