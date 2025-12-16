# ged-kit.clj

The project serves for genealogical data (ged) manipulation. It consists of:

  - GEDCOM ABNF parser
  - GTR graph comprehension

The whole project is pure Clojure implementation with minimal dependencies and Babashka support. It is mostly in development and any changes are quite possible.

**Table of content**

- [ged-kit.api](#ged-kitapi)
  - [Reading GEDCOM](#reading-gedcom)
  - [Writing GEDCOM](#writing-gedcom)
- [ged-kit.date](#ged-kitdate)
- [ged-kit.gtr](#ged-kitgtr)
- [Babashka CLI](#babashka-cli)


## ged-kit.api

### Reading GEDCOM

The module `ged-kit.api` turns UTF-8 [GEDCOM](https://gedcom.io/specifications/FamilySearchGEDCOMv7.html) data to abstract syntax tree (AST):

```clj
(require '[ged-kit.api :as ged])

(ged/parse "1 @I1@ INDI")
;; => {:level 1 :id "I1" :tag "INDI"}

(ged/parse "1 NAME John /Doe/")
;; => {:level 1 :tag "NAME" :data "John /Doe/"}

(ged/parse "2 DATE 1 JAN 1900")
;; => {:level 2 :tag "DATE" :data "1 JAN 1900"}
```

The result of parsing a single GEDCOM line is a simple hash-map with intuitive keys.

However separate lines are not so comfortable to work with. It is much more convenient to manipulate the whole record as a single object. The first step to «squash» lines into the records is concatenating multi-lined data in GEDCOM that marked with `CONT` and `CONC` tags:

```clj
(require '[ged-kit.api :as ged])

(->> (str "0 @I1@ INDI\n"
          "1 BIRT\n"
          "2 NOTE The first line of note\n"
          "3 CONT\n"
          "3 CONT Third line o\n"
          "3 CONC f note\n")
     (clojure.string/split-lines)
     (map ged/parse)
     (ged/concatenate))

;; =>
;; ({:level 0, :id "I1", :tag "INDI"}
;;  {:level 1, :tag "BIRT"}
;;  {:level 2, :tag "NOTE", :data "The first line of note\n\nThird line of note"})
```

Thus these six lines turned to much more compacted three. But it is still not a record yet.

The last step is turning a list of lines to sequence of records:

```clj
(require '[ged-kit.api :as ged])

(->> (str "0 @I1@ INDI\n"
          "1 BIRT\n"
          "2 NOTE The first line of note\n"
          "3 CONT\n"
          "3 CONT Third line o\n"
          "3 CONC f note\n")
     (clojure.string/split-lines)
     (map ged/parse)
     (ged/concatenate)
     (ged/record-seq))

;; ({:id "I1",
;;   :tag "INDI",
;;   "BIRT"
;;   [{:tag "BIRT",
;;     "NOTE" [{:tag "NOTE", :data "The first line of note\n\nThird line of note"}]}]})
```

Finally it is a sequence that has a single record contais the entire data of the individual.
The next steps left to your imagination.


### Writing GEDCOM

Another feature that `ged-kit.api` provided is writing data back to GEDCOM text representation.

For example to turn a single parsed line back to string:

```clj
(require '[ged-kit.api :as ged])

(ged/render-line {:level 1, :tag "NOTE", :data "The first line of note\n\nThird line of note"})
;; => "1 NOTE The first line of note\n2 CONT\n2 CONT Third line of note"
```

or the entire record:

```clj
(require '[ged-kit.api :as ged])

(def person {:level 0
             :id "I42"
             :tag "INDI"
             "NOTE" [{:level 1
                      :tag "NOTE"
                      :data "The first line of note\n\nThird line of note"}]})

(ged/render-record person)
;; => "0 @I42@ INDI\n1 NOTE The first line of note\n2 CONT\n2 CONT Third line of note"
```

Note that value of `:id` is automatically escaped with `@` symbols. The same escaping applies to `:data` key in case it has a pointer, so `{:level 1 tag: "HUSB" :data [:id "I42"]}` will render as `1 HUSB @I42@`.

Parsing rendered data will get back the same data:

```clj
(= (-> person
       ged/render-record
       ged/parse-str
       (get "I42"))
   person)
;; => true
```


## ged-kit.date

To parse GEDCOM dates there is `ged-kit.date` module. It does not automatically included into the basic parser, but could be easilly used as separated step if needed.

```clj
(require '[ged-kit.date :refer [parse]])

(parse "3 DEC 2025")
;; => [:date [:day 3] [:month "Dec"] [:year 2025]]

(parse "APR 1782")
;; => [:date [:month "Apr"] [:year 1782]]

(parse "AFT 1780")
;; => [:dateRange "AFT" [:date [:year 1780]]]

(parse "BEF 1777")
;; => [:dateRange "BEF" [:date [:year 1777]]]

(parse "BET @#DJULIAN@9 NOV 1772 AND @#DJULIAN@21 DEC 1774")
;; =>
;; [:dateRange
;;  "BET" [:date [:calendar "JULIAN"] [:day 9] [:month "Nov"] [:year 1772]]
;;  "AND" [:date [:calendar "JULIAN"] [:day 21] [:month "Dec"] [:year 1774]]]
```


## ged-kit.gtr

This module produces GTR database from GED data to use with [`genealogytree`](https://ctan.org/pkg/genealogytree) **LaTeX** package.

```clj
(require '[ged-kit.api :as ged]
         '[ged-kit.gtr :as gtr])

(def url "https://raw.githubusercontent.com/findmypast/gedcom-samples/refs/heads/main/Harry%20Potter.ged")
(let [g (ged/parse-io url)]
    (spit "db.graph" (gtr/gtr-string (gtr/sandclock g "I00001"))))
```

It produces a database at `db.graph`:

```latex
% db.graph
sandclock[]{%
  child[id=F00026]{%
    g[id=I00071]{%
      sex=male,
      name=\pref{/Black/} \surn{},
      birth={}{},
      death={}{},
    }%
    child[id=F00026]{%
      g[id=I00060]{%
        sex=male,
        name=\pref{Phineas Nigellus /Black/} \surn{},
        birth={1847}{},
        death={1925}{},
      }%
...
```

that could be used as `genealogypicture` input.

```latex
% main.tex
\genealogytreeinput[
  template=database traditional,
]{db.graph}
```

See the complete result output at [`example/release/main.pdf`](./example/release/main.pdf).


**Feautures**

- `date` — EST, CAL, ABT, BEF, AFT, BET, Julian and Gregorian calendars
- `union` — when there is more than one family of one person (e.g. second wife)

Any unreleased yet functions of `genealogytree` package could be eassily added. Feel free to request.


## Babashka CLI

Turning GED to GTR is also available as Babashka CLI:

```
Usage: ged2gtr.bb input.ged XREF [OPTIONS]

  Create databases for genealogytree from GEDCOM files.

  The LaTeX genealogytree package (GTR) provides tools for including
  genealogy trees in LaTeX documents. One way of doing that is by storing
  the genealogical information in a GTR-specific database file. This tool
  allows you to create such databases from GEDCOM files .

  The input file is read, and a GTR database is written to to STDOUT.
  The GTR database contains a `sandclock` node for the person with the
  given XREF.

  Options:
      --siblings          Whether to show the siblings of the target person
      --xref              Target INDI Xref (second arg by default)
  -v, --verbose           Detailed logging for debug
  -d, --descendants       Number of descendant generations to graph
      --ancestor-siblings Whether to show the siblings of the target person's ancestors
  -a, --ancestos          Number of ancestor generations to graph
      --banner            Whether to show the banner above the graph
      --input             Input GEDCOM file (first arg by default)
  -h, --help              Show this message and exit
```

```shell
$ ./bb/ged2gtr.bb "https://raw.githubusercontent.com/findmypast/gedcom-samples/refs/heads/main/Harry%20Potter.ged" I00071
```