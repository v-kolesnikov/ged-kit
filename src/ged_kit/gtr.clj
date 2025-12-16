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
         body-pairs (if (#{:g :c :p} node)
                      (->> content
                           (filter (comp some? second))
                           (map (fn [[k v]] (str (indent+ lvl) (name k) "=" (escape v) ",")))
                           (string/join "\n"))
                      (->> content
                           (map #(gtr-string % (inc lvl)))
                           (string/join "\n")))]
     (str (indent lvl) (name node) (wrap-square opts-pairs) "{%\n"
          body-pairs "\n"
          (indent lvl) "}%"))))

(defn ^:private parse-date [s]
  (LocalDate/parse s (DateTimeFormatter/ofPattern "yMMMd")))

(defn ^:private gtr-calendar [c]
  (or (get {"JULIAN" "JU"
            "GREGORIAN" "GR"} c)
      c))

(defn gtr-date
  "NOTE: `(caBC)` dates not supported yet."
  [date]
  (match date
    [:date [:year y]] (str y)
    [:date [:month m] [:year y]] (str (YearMonth/from (parse-date (str y m 1))))
    [:date [:day d] [:month m] [:year y]] (str (parse-date (str y m d)))
    [:date [:calendar c] & d] (str "(" (gtr-calendar c) ")" (gtr-date (into [:date] d)))
    [:dateApprox (:or "ABT" "CAL" "EST") d] (str "(ca)" (gtr-date d))
    [:dateRange "AFT" d] (str (gtr-date d) "/")
    [:dateRange "BEF" d] (str "/" (gtr-date d))
    [:dateRange "BET" d1 "AND" d2] (str (gtr-date d1) "/" (gtr-date d2))
    [:date y [:epoch "BCE"]] (str "(BC)" (gtr-date [:date y]))
    [:date m y [:epoch "BCE"]] (str "(BC)" (gtr-date [:date m y]))
    [:date d m y [:epoch "BCE"]] (str "(BC)" (gtr-date [:date d m y]))
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
  "Subgraph `union`. A union subgraph is a family without a `g` node. The `g`
   node (`parent`) is inherited from an embedding child family. A union family
   may have arbitrary child and parent leaves. Also, this family may have
   arbitrary child subgraphs."
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
  "Subgraph `parent`. A parent subgraph is a family where the g node acts as
   a child. This family may have arbitrary child and parent leaves. Also, this
   family may have arbitrary parent subgraphs.

   Arguments: `g` — graph, `person` — proband, `options` - node options:
   `parent[<options>]{}`, `opts` — GTR graph options."
  ([g person options]
   (parent-node g person options {}))
  ([g person options opts]
   (let [{:keys [ancestor-siblings
                 ancestors]} opts]
     (when (or (nil? ancestors)
               (pos? ancestors))
       (map (fn [spouse]
              (if (empty? (ged/parentes g spouse))
                (p-node spouse)
                {:node :parent
                 :options options
                 :content (into [] (concat [(g-node spouse)]
                                           (when ancestor-siblings
                                             (map c-node (ged/siblings g spouse)))
                                           (parent-node g spouse []
                                                        (update-in opts [:ancestors]
                                                                   #(when (some? %) (dec %))))))}))
            (ged/parentes g person))))))

(defn child-node
  "Subgraph `child`. A child subgraph is a family where the g node acts as
   a parent. This family may have arbitrary child and parent leaves. Also, this
   family may have arbitrary child and union subgraphs.

   Arguments: `g` — graph, `person` — proband, `options` - node options:
   `child[<options>]{}`, `opts` — GTR graph options."
  ([g person options]
   (child-node g person options {}))
  ([g person options opts]
   (let [{:keys [descendants]} opts]
     (when (or (nil? descendants)
               (pos? descendants))
       (let [[head-fam & rest-fams]
             (map (fn [{id :id :as fam}]
                    (let [children (->> (ged/family-children g fam)
                                        (map (fn [child]
                                               (if (empty? (ged/children g child))
                                                 (c-node child)
                                                 (child-node g child [[:id id]]
                                                             (update-in opts [:descendants]
                                                                        #(when (some? %) (dec %)))))))
                                        (filter some?))]
                      (if-let [spouse (ged/spouse g fam person)]
                        (into [(-> (p-node spouse)
                                   (add-marriage fam))]
                              children)
                        children)))
                  (ged/families g person))]
         {:node :child
          :options options
          :content (into [(g-node person)]
                         (into head-fam (map wrap-union rest-fams)))})))))

(defn sandclock
  "Create GTR structure of sand clock diagram.

   The structure is a hash-map like:
   `{:node :sandclok :options [] :content [<nodes>]}`

   Params are: `g` — GED graph, `id` — proband ID.
   Options are `:siblings`, `:ancestor-siblings`."
  ([g id options]
   (sandclock g id options {}))
  ([g id options opts]
   (let [{:keys [siblings]} opts
         proband (ged/indi g id)
         [{fam-id :id}] (ged/families g proband)]
     {:node :sandclock
      :options options
      :content (into [] (concat [(child-node g proband [[:id fam-id]] opts)]
                                (when siblings
                                  (map c-node (ged/siblings g proband)))
                                (parent-node g proband [] opts)))})))