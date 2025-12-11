(ns ged-kit.date
  (:require [instaparse.core :as insta]
            [clojure.string :as string]))

(def grammar
  "DateValue   = [ date / DatePeriod / dateRange / dateApprox ]
   DateExact   = day <D> month <D> year  ; in Gregorian calendar
   DatePeriod  = [ 'TO' <D> date ]
               / 'FROM' <D> date [ <D> 'TO' <D> date ]
               ; note both DateValue and DatePeriod can be the empty string

   date        = [calendar [<D>]] [[day <D>] month <D>] year [<D> epoch]
   dateRange   = 'BET' <D> date <D> 'AND' <D> date
               / 'AFT' <D> date
               / 'BEF' <D> date
   dateApprox  = ('ABT' / 'CAL' / 'EST') <D> date

   dateRestrict = 'FROM' / 'TO' / 'BET' / 'AND' / 'BEF'
               / 'AFT' / 'ABT' / 'CAL' / 'EST'

   calendar = [<'@#D'>] ('GREGORIAN' / 'JULIAN' / 'FRENCH_R' / 'HEBREW') [<'@'>]
            / extTag

   day     = Integer
   year    = Integer
   month   = monthUP
   monthUP = stdTag / extTag  ; constrained by calendar
   epoch   = 'BCE' / extTag ; constrained by calendar

   Integer      = 1*DIGIT
   stdTag       = ucletter *tagchar
   extTag       = underscore 1*tagchar
   <tagchar>    = ucletter / DIGIT / underscore
   <DIGIT>      = %x30-39   ; 0 through 9 -- defined in RFC 5234 section B.1
   <nonzero>    = %x31-39   ; 1 through 9
   <ucletter>   = %x41-5A   ; A through Z
   <underscore> = %x5F      ; _
   atsign       = %x40      ; @
   <D>          = ' '")

(def ts
  {:Integer (comp Integer/parseInt str)
   :stdTag str
   :monthUP string/capitalize})

(def parser
  (insta/parser grammar
                :input-format :abnf
                :string-ci false))

(defn parse [in]
  (->> in
       (insta/parse parser)
       (insta/transform ts)
       second))