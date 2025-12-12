(ns ged-kit.date-test
  (:require [clojure.test :as t :refer [deftest are testing]]
            [ged-kit.date :as ged-date]))

(deftest parse-line-test
  (testing "GEDCOM dates"
    (are [input expected] (= expected (ged-date/parse input))
      "3 DEC 2025" [:date [:day 3] [:month "Dec"] [:year 2025]]
      "1 JAN 163 BCE" [:date [:day 1] [:month "Jan"] [:year 163] [:epoch "BCE"]]
      "APR 1782" [:date [:month "Apr"] [:year 1782]]
      "ABT 1695" [:dateApprox "ABT" [:date [:year 1695]]]
      "CAL 1857" [:dateApprox "CAL" [:date [:year 1857]]]
      "EST 1893" [:dateApprox "EST" [:date [:year 1893]]]
      "AFT 1780" [:dateRange "AFT" [:date [:year 1780]]]
      "BEF 1777" [:dateRange "BEF" [:date [:year 1777]]]
      "BET 1777 AND 1781" [:dateRange "BET" [:date [:year 1777]] "AND" [:date [:year 1781]]]
      "@#DJULIAN@15 JUN 1907" [:date [:calendar "JULIAN"] [:day 15] [:month "Jun"] [:year 1907]]
      "JULIAN 15 JUN 1907" [:date [:calendar "JULIAN"] [:day 15] [:month "Jun"] [:year 1907]]
      "BET @#DJULIAN@9 NOV 1772 AND @#DJULIAN@21 DEC 1774"
      [:dateRange
       "BET" [:date [:calendar "JULIAN"] [:day 9] [:month "Nov"] [:year 1772]]
       "AND" [:date [:calendar "JULIAN"] [:day 21] [:month "Dec"] [:year 1774]]])))