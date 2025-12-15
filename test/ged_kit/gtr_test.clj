(ns ged-kit.gtr-test
  (:require [clojure.test :as t :refer [deftest are is testing use-fixtures]]
            [ged-kit.gtr :as gtr]
            [ged-kit.api :as ged]))

(deftest gtr-date-test
  (testing "GTR dates"
    (are [input expected] (= expected (gtr/gtr-date input))
      [:date [:year 2025]] "2025"
      [:date [:year 225]] "225"
      [:date [:month "Dec"] [:year 2025]] "2025-12"
      [:date [:day 9] [:month "Dec"] [:year 2025]] "2025-12-09"
      [:date [:day 10] [:month "Dec"] [:year 2025]] "2025-12-10"
      [:date [:day 18] [:month "Nov"] [:year 163] [:epoch "BCE"]] "(BC)0163-11-18"
      [:date [:month "Nov"] [:year 163] [:epoch "BCE"]] "(BC)0163-11"
      [:date [:calendar "JULIAN"] [:day 25] [:month "Aug"] [:year 1789]] "(JU)1789-08-25"
      [:dateApprox "ABT" [:date [:day 9] [:month "Dec"] [:year 2025]]] "(ca)2025-12-09"
      [:dateApprox "CAL" [:date [:day 9] [:month "Dec"] [:year 2025]]] "(ca)2025-12-09"
      [:dateApprox "EST" [:date [:day 9] [:month "Dec"] [:year 2025]]] "(ca)2025-12-09"
      [:dateApprox "EST" [:date [:day 9] [:month "Dec"] [:year 2025] [:epoch "BCE"]]] "(ca)(BC)2025-12-09"
      [:dateRange "AFT" [:date [:day 9] [:month "Dec"] [:year 2025]]] "2025-12-09/"
      [:dateRange "BEF" [:date [:day 9] [:month "Dec"] [:year 2025]]] "/2025-12-09"
      [:dateRange
       "BET" [:date [:day 10] [:month "Dec"] [:year 2025]]
       "AND" [:date [:day 20] [:month "Dec"] [:year 2025]]] "2025-12-10/2025-12-20")))

(def graph
  {"I42" {:id "I42"
          "SEX"  [{:data "M"}]
          "OCCU" [{:data "Major"}]}})

(use-fixtures :once graph)

(deftest sandclock-test
  (testing "sandclock"
    (is (= {:node :sandclock,
            :options nil,
            :content
            [{:node :child,
              :options [[:id nil]],
              :content
              [{:node :g,
                :options [[:id "I42"]],
                :content {:sex "male",
                          :name "\\pref{} \\surn{}",
                          :birth "{}{}",
                          :death "{}{}",
                          :profession "Major"}}]}]}
           (gtr/sandclock graph "I42")))))

(deftest gtr-string-test
  (testing "GTR string"
    (is (= "sandclock[]{%
  child[id=]{%
    g[id=I42]{%
      sex=male,
      name=\\pref{} \\surn{},
      birth={}{},
      death={}{},
      profession=Major,
    }%
  }%
}%"
           (gtr/gtr-string (gtr/sandclock graph "I42"))))))