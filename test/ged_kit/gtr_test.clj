(ns ged-kit.gtr-test
  (:require [clojure.test :as t :refer [deftest are testing]]
            [ged-kit.gtr :as gtr]))

(deftest gtr-datr-test
  (testing "GTR dates"
    (are [input expected] (= expected (gtr/gtr-date input))
      [:date [:year 2025]] "2025"
      [:date [:month "Dec"] [:year 2025]] "2025-12"
      [:date [:day  9] [:month "Dec"] [:year 2025]] "2025-12-09"
      [:date [:day 10] [:month "Dec"] [:year 2025]] "2025-12-10"
      [:date [:calendar "JULIAN"] [:day 25] [:month "Aug"] [:year 1789]] "(JU)1789-08-25"
      [:dateApprox "ABT" [:date [:day 9] [:month "Dec"] [:year 2025]]] "(ca)2025-12-09"
      [:dateApprox "CAL" [:date [:day 9] [:month "Dec"] [:year 2025]]] "(ca)2025-12-09"
      [:dateApprox "EST" [:date [:day 9] [:month "Dec"] [:year 2025]]] "(ca)2025-12-09"
      [:dateRange "AFT" [:date [:day 9] [:month "Dec"] [:year 2025]]] "2025-12-09/"
      [:dateRange "BEF" [:date [:day 9] [:month "Dec"] [:year 2025]]] "/2025-12-09"
      [:dateRange
       "BET" [:date [:day 10] [:month "Dec"] [:year 2025]]
       "AND" [:date [:day 20] [:month "Dec"] [:year 2025]]] "2025-12-10/2025-12-20")))