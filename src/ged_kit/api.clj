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
         (update-in parent [(:tag line)] (fnil conj [])
                    (assoc-lines line (rest lines)))
         tail))
      (dissoc parent :level))))

(defn record-seq
  "Sequence of GEDCOM records"
  [lines]
  (lazy-seq
   (if (seq (rest lines))
     (let [[head tail] (split-with (comp pos? :level) (rest lines))]
       (cons (assoc-lines (first lines) head)
             (record-seq tail)))
     (take 1 lines))))

(defn turn-db [records]
  (reduce (fn [h record]
            (assoc h (or (:id record) (:tag record))
                   record)) {}
          records))

(defn parse-io [path]
  (->> (slurp path)
       (string/split-lines)
       (map parse)
       concatenate
       record-seq
       turn-db))

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