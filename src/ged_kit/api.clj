(ns ged-kit.api
  (:require [instaparse.core :as insta]
            [clojure.string :as string]))

;;
;; GEDCOM ver.7.0.16 ABNF grammar
;; See: https://gedcom.io/specifications/FamilySearchGEDCOMv7.html#lines
;;
(def ^:private grammar
  "line    = level <D> [xref <D>] tag [<D> lineVal] [<EOL>]

   level   = Integer                         ; '0' / nonzero *DIGIT
   D       = %x20                            ; space
   xref    = <atsign> 1*tagchar <atsign>     ; but not '@VOID@'
   tag     = stdTag / extTag
   lineVal = pointer / lineStr
   EOL     = %x0D [%x0A] / %x0A              ; CR-LF, CR, or LF

   stdTag  = ucletter *tagchar
   extTag  = underscore 1*tagchar
   <tagchar> = ucletter / DIGIT / underscore

   Integer       = 1*DIGIT
   <DIGIT>       = %x30-39   ; 0 through 9 -- defined in RFC 5234 section B.1
   <nonzero>     = %x31-39   ; 1 through 9
   <ucletter>    = %x41-5A   ; A through Z
   <underscore>  = %x5F      ; _
   <atsign>      = %x40      ; @

   <pointer> = voidPtr / xref
   <voidPtr> = '@VOID@'

   <nonAt>   = %x09 / %x20-3F / %x41-10FFFF    ; non-EOL, non-@
   <nonEOL>  = %x09 / %x20-10FFFF              ; non-EOL
   lineStr = (nonAt / atsign atsign / atsign '#') *nonEOL ; leading @ doubled")

(def ^:private mapping
  {:line (fn [& args] (into {} args))
   :xref (fn [& tagchar] [:id (apply str tagchar)])
   :Integer (comp Integer/parseInt str)
   :lineStr str
   :stdTag str
   :extTag str
   :lineVal #(vec [:data %])})

(def ^:private parser
  (insta/parser grammar
                :input-format :abnf
                :string-ci false))

(defn parse [in]
  (->> in
       (insta/parse parser)
       (insta/transform mapping)))

(def ^:private x-grammar
  "NOTE: it supports GEDCOM v5.x escape strings: `@#DJULIAN@`.
   In all the rest cases it is GEDCOM v7.x grammar."

  "<line> = level <SP> [id <SP>] tag [<SP> data]

   level = #'(\\d+)'
   id = <'@'> #'[A-Z0-9_]+' <'@'>
   tag = #'[A-Z][A-Z0-9_]*|_[A-Z0-9_]+'
   data = id / lineStr

   lineStr = #'([^@]|@[@#]).*'")

(def x-parser
  (insta/parser x-grammar
                :input-format :abnf
                :string-ci false
                :no-slurp true))

(defn x-parse [in]
  (->> in
       (insta/parse x-parser)
       (insta/transform {:level (fn [v] [:level (Integer/parseInt v)])
                         :lineStr (fn [s] (string/replace-first (str s) #"^@@" "@"))})
       (into {})))

(defn concatenate
  "Squashes `CONT` and `CONC` lines"
  [lines]
  (lazy-seq
   (loop [[head & tail] lines]
     (when head
       (let [[{:keys [tag data]}] tail]
         (if (#{"CONT" "CONC"} tag)
           (recur (cons (update-in head [:data] str
                                   (when (= "CONT" tag) "\n") data)
                        (rest tail)))
           (cons head (concatenate tail))))))))

(defn ^:private level< [& args]
  (apply < (map :level args)))

(defn ^:private assoc-lines
  "Assoc lines with levels `0..n` to a single record."
  [parent lines]
  (let [[line] lines]
    (if (and line (level< parent line))
      (let [tail (drop-while (partial level< line) (rest lines))]
        (assoc-lines
         (update-in parent [(get line :tag)] (fnil conj [])
                    (assoc-lines line (rest lines)))
         tail))
      parent)))

(defn record-seq
  "Sequence of GEDCOM records"
  [lines]
  (lazy-seq
   (if (seq (rest lines))
     (let [[head tail] (split-with (comp pos? :level) (rest lines))]
       (cons (assoc-lines (first lines) head)
             (record-seq tail)))
     (take 1 lines))))

(defn ^:private graph-db
  "Turn list of records to a simple hash-map database."
  [records]
  (reduce (fn [h record]
            (assoc h (or (:id record) (:tag record))
                   record)) {}
          records))

(defn parse-str [str]
  (->> (string/split-lines str)
       (map x-parse)
       concatenate
       record-seq
       graph-db))

(defn parse-io [path]
  (parse-str (slurp path)))

(defn wrap-at [s]
  (str "@" s "@"))

(defn render-line
  "Takes a single GEDCOM line and returns its string representation.\n
   The output string could be multi-lined, e.g. in case of multi-lined
   data in input string. In that case the input data split by `\\n` or
   `\\r\\n` and concatenated with `CONT` tag."
  [{:keys [level id tag data]}]
  (let [id (when (some? id)
             (wrap-at id))
        data (if (vector? data)
               (wrap-at (second data))
               (when data
                 (let [[head & cont-lines] (string/split-lines data)]
                   (loop [[line & tail] cont-lines
                          result [head]]
                     (if line
                       (recur tail
                              (conj result
                                    (str (inc level) \space "CONT" (when-not (empty? line) \space) line)))
                       (string/join "\n" result))))))]
    (->> [level id tag data]
         (filter some?)
         (string/join \space))))

(defn render-record
  "Takes a single GEDCOM record and returns its string represenatation."
  [record]
  (->> (dissoc record :level :id :tag :data)
       vals
       flatten
       (map render-record)
       (cons (render-line record))
       (string/join "\n")))

;;
;; Helper functions
;;

(defn indi [g id]
  (get g id))

(defn given-name
  "The character U+002F (/, slash or solidus) has special meaning in a personal
   name, being used to delimit the portion of the name that most closely matches
   the concept of a surname, family name, or the like."
  [person]
  (or (get-in person ["NAME" 0 "GIVN"  0 :data])
      (when-let [name-data (get-in person ["NAME" 0 :data])]
        (->> name-data
             (re-find #"(^.*)(?:\s\/.*\/$)")
             second))))

(defn surname
  "The character U+002F (/, slash or solidus) has special meaning in a personal
   name, being used to delimit the portion of the name that most closely matches
   the concept of a surname, family name, or the like."
  [person]
  (or (get-in person ["NAME" 0 "SURN"  0 :data])
      (when-let [name-data (get-in person ["NAME" 0 :data])]
        (->> name-data
             (re-find #"\/(.*)\/$")
             second))))

(defn birth-date [person]
  (get-in person ["BIRT" 0 "DATE" 0 :data]))

(defn death-date [person]
  (get-in person ["DEAT" 0 "DATE" 0 :data]))

(defn sex [person]
  (get-in person ["SEX" 0 :data]))

(defn occupation [person]
  (get-in person ["OCCU" 0 :data]))

(defn marriage-date [fam]
  (get-in fam ["MARR" 0 "DATE" 0 :data]))

(defn husband [g fam]
  (when-let [husb (get-in fam ["HUSB" 0 :data 1])]
    (get g husb)))

(defn wife [g fam]
  (when-let [wife (get-in fam ["WIFE" 0 :data 1])]
    (get g wife)))

(defn spouse [g fam person]
  (case (sex person)
    "M" (wife g fam)
    "F" (husband g fam)))

(defn family [g id]
  (get g id))

(defn families [g person]
  (map #(family g (get-in % [:data 1])) (get person "FAMS")))

(defn family-children [g fam]
  (->> (get fam "CHIL")
       (map (fn [{[_ id] :data}] (get g id)))))

(defn children [g person]
  (->> (families g person)
       (mapcat (fn [{id :id}]
                 (family-children g (family g id))))))

(defn parentes [g person]
  (when-let [fam (family g (get-in person ["FAMC" 0 :data 1]))]
    (->> [(husband g fam) (wife g fam)]
         (filter some?)
         (into []))))

(defn siblings [g person]
  (when-let [fam (family g (get-in person ["FAMC" 0 :data 1]))]
    (->> (family-children g fam)
         (filter #(not= % person)))))