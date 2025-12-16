(ns ged-kit.api-test
  (:require [clojure.test :as t :refer [deftest is are testing]]
            [ged-kit.api :as ged]))

(deftest parse-test
  (testing "GEDCOM lines"
    (are [input expected] (= expected
                             (ged/parse input)
                             (ged/x-parse input))
      "1 @I1@ INDI" {:level 1 :id "I1" :tag "INDI"}
      "1 NAME John /Doe/" {:level 1 :tag "NAME" :data "John /Doe/"}
      "2 DATE 1 JAN 1900" {:level 2 :tag "DATE" :data "1 JAN 1900"}
      "2 PLAC Someplace, Somecity" {:level 2 :tag "PLAC" :data "Someplace, Somecity"}
      "0 @F1@ FAM" {:level 0 :id "F1" :tag "FAM"}
      "10 HUSB @I1@" {:level 10 :tag "HUSB" :data [:id "I1"]}
      "2 DATE @#DJULIAN@ 2 AUG 1790" {:level 2, :tag "DATE", :data "@#DJULIAN@ 2 AUG 1790"}))
  (testing "escaping"
    (are [input expected] (= expected (ged/x-parse input))
      "3 NOTE @@I42@" {:level 3 :tag "NOTE" :data "@I42@"}
      "3 NOTE example @" {:level 3 :tag "NOTE" :data "example @"}
      "3 NOTE @@example.com" {:level 3 :tag "NOTE" :data "@example.com"})))


(deftest concatenate-test
  (testing "concatenate"
    (is (= '({:level 0, :id "I1", :tag "INDI"}
             {:level 1, :tag "BIRT"}
             {:level 2, :tag "NOTE", :data "The first line of note\n\nThird line of note"}
             {:level 1, :tag "FAMC", :data [:id "F248"]})
           (ged/concatenate
            [{:level 0, :id "I1", :tag "INDI"}
             {:level 1, :tag "BIRT"}
             {:level 2, :tag "NOTE", :data "The first line of note"}
             {:level 3, :tag "CONT"}
             {:level 3, :tag "CONT", :data "Third line o"}
             {:level 3, :tag "CONC", :data "f note"}
             {:level 1, :tag "FAMC", :data [:id "F248"]}])))))

(deftest parse-str-test
  (testing "parse-str"
    (are [input expected] (= expected (ged/parse-str input))
      (str "0 @I1@ INDI\n"
           "1 BIRT\n"
           "2 NOTE The first line of note\n"
           "3 CONT\n"
           "3 CONT Third line o\n"
           "3 CONC f note\n")
      {"I1" {:id "I1",
             :level 0,
             :tag "INDI",
             :BIRT
             [{:tag "BIRT",
               :level 1,
               :NOTE [{:level 2,
                       :tag "NOTE",
                       :data "The first line of note\n\nThird line of note"}]}]}})))