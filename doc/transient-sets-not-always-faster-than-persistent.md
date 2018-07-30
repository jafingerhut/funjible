## Running criterium on a few selected function calls

```clojure
(require '[clojure.pprint :as pp])
(require '[criterium.core :as crit])
(def s-to-ns (* 1000 1000 1000))
(def crit-opts {:samples 30
                :warmup-jit-period (long (* 10 s-to-ns))
                :target-execution-time (long (* 1.0 s-to-ns))})

(require '[clojure.set :as cset])
(require '[funjible.set-clj190-precondition-mods-only :as fset-pre-only])
(require '[funjible.set-precondition-always-transient-mods :as fset-trans])

(defn ctrim [crit-results]
  (dissoc crit-results :results :os-details :runtime-details))

(def r0-2 (set (range 0 2)))
(def r0-1000 (set (range 0 1000)))
(def r1000-2000 (set (range 1000 2000)))
(def r0-10000 (set (range 0 10000)))

(def cset-union (ctrim (crit/benchmark (cset/union r0-1000 r0-1000) crit-opts)))
(def fset-pre-union (ctrim (crit/benchmark (fset-pre-only/union r0-1000 r0-1000) crit-opts)))
(def fset-trans-union (ctrim (crit/benchmark (fset-trans/union r0-1000 r0-1000) crit-opts)))

(pprint (map :mean [cset-union fset-pre-union fset-trans-union]))

;; try 1
([9.532943756803636E-5 (9.447158353071356E-5 9.591857917638614E-5)]
 [9.405323683406648E-5 (9.322849515518085E-5 9.462213331805964E-5)]
 [1.0436373431721443E-4 (1.0345910363547555E-4 1.0496905824087643E-4)])
;; try 2
([9.51598479239683E-5 (9.467035174449813E-5 9.559670856303874E-5)]
 [9.519091456702934E-5 (9.460904934465865E-5 9.559847392632183E-5)]
 [1.0490601835794E-4 (1.0397128912802386E-4 1.0569044268385667E-4)])
;; try 3
([9.47796016121922E-5 (9.379288166446249E-5 9.542066398702689E-5)]
 [9.500152413998186E-5 (9.397188572009892E-5 9.577239931448963E-5)]
 [1.0368026585599454E-4 (1.0312529392166923E-4 1.0424735816658116E-4)])

(def cset-union (ctrim (crit/benchmark (cset/union r0-1000 r0-2) crit-opts)))
(def fset-pre-union (ctrim (crit/benchmark (fset-pre-only/union r0-1000 r0-2) crit-opts)))
(def fset-trans-union (ctrim (crit/benchmark (fset-trans/union r0-1000 r0-2) crit-opts)))

(pprint (map :mean [cset-union fset-pre-union fset-trans-union]))

;; try 1
([2.986994703740193E-7 (2.9659292061496867E-7 3.0023293996745695E-7)]
 [3.2165212895977993E-7 (3.176127424616401E-7 3.241394447091216E-7)]
 [4.734618509880783E-7 (4.4185669973255004E-7 5.220225217982498E-7)])
;; try 2 (on battery power at 4% at this time.  Maybe CPU is slowed down?)
([4.999160405185786E-7 (4.993547066405226E-7 5.007443167209559E-7)]
 [5.492855868377612E-7 (5.480151573868871E-7 5.515516845695733E-7)]
 [7.119008742188799E-7 (7.099623676273137E-7 7.148851267995677E-7)])
;; try 3 (on battery power at 4% at this time.  Maybe CPU is slowed down?)
([4.989623611147036E-7 (4.980780111240881E-7 5.010652536561329E-7)]
 [5.486921267901465E-7 (5.4752572419087E-7 5.50494232877962E-7)]
 [7.138611673631087E-7 (7.109508950929927E-7 7.204074057689997E-7)])
;; try 4 - battery about 7% an dincreasing on wall charger
([3.003305326828557E-7 (2.958787513034965E-7 3.056225409109112E-7)]
 [3.1924529675348767E-7 (3.170428190386595E-7 3.2127149348372504E-7)]
 [4.2156870373113284E-7 (4.188292342060894E-7 4.2380951743327215E-7)])

(def non-transient-union (ctrim (crit/benchmark (reduce conj r0-1000 r0-1000) crit-opts)))
(def transient-union (ctrim (crit/benchmark (-> (reduce conj! (transient r0-1000) r0-1000) persistent!) crit-opts)))
(pprint (map :mean [non-transient-union transient-union]))

;; try 1
([9.493850794221608E-5 (9.418259026894116E-5 9.551760348855081E-5)]
 [1.0998065700402765E-4 (1.093832002254E-4 1.1069497760410895E-4)])

;; try 2
([9.584867748751784E-5 (9.505190741797432E-5 9.648453914051356E-5)]
 [1.0952192537980341E-4 (1.0879613109919571E-4 1.1010180944180932E-4)])

(def non-transient-union (ctrim (crit/benchmark (reduce conj r0-10000 r0-10000) crit-opts)))
(def transient-union (ctrim (crit/benchmark (-> (reduce conj! (transient r0-10000) r0-10000) persistent!) crit-opts)))
(pprint (map :mean [non-transient-union transient-union]))

;; try 1
([8.748019358162101E-4 (8.662055655251143E-4 8.808437181792239E-4)]
 [0.0010319925416750085 (0.001025881524591258 0.0010369192194194196)])
;; try 2
([8.813547364833906E-4 (8.740023546105384E-4 8.86789202978236E-4)]
 [0.0010320965014462811 (0.001019705980130854 0.001042300647073003)])

;; The _ratio_ by which the transient version is slower _increases_
;; with the larger set size.  It is _not_ a constant overhead whose
;; effect percentage-wise is decreasing with larger sets.


;; All of the above were doing conj/conj! into sets where the element
;; was already in the set, which should be relatively quick, compared
;; to adding an element that is not yet in the set, which we do now
;; below.

(def non-transient-union (ctrim (crit/benchmark (reduce conj r0-1000 r1000-2000) crit-opts)))
(def transient-union (ctrim (crit/benchmark (-> (reduce conj! (transient r0-1000) r1000-2000) persistent!) crit-opts)))
(pprint (map :mean [non-transient-union transient-union]))

;; try 1
([2.538165474081207E-4 (2.5202406596854443E-4 2.557850552640026E-4)]
 [1.8380616919808817E-4 (1.827859932259397E-4 1.8481674392517849E-4)])
;; try 2
([2.565243062904324E-4 (2.5542898152877084E-4 2.578791815883555E-4)]
 [1.8430700016157988E-4 (1.8333565457809696E-4 1.8548210829443448E-4)])


;; As of Clojure 1.9.0, contains? should work on transient data
;; structures.  See if that can be used to help make a different
;; version of union that can take advantage of transients, but as fast
;; as the persistent one when the second set has elements already in
;; the first set.

(def non-transient-union (ctrim (crit/benchmark (reduce conj r0-1000 r1000-2000) crit-opts)))
(def transient2-union (ctrim (crit/benchmark (-> (reduce (fn [s e] (if (contains? s e) s (conj! s e))) (transient r0-1000) r1000-2000) persistent!) crit-opts)))
(def transient3-union (ctrim (crit/benchmark (-> (reduce (fn [s e] (if (. clojure.lang.RT (contains s e)) s (conj! s e))) (transient r0-1000) r1000-2000) persistent!) crit-opts)))
(def transient-union (ctrim (crit/benchmark (-> (reduce conj! (transient r0-1000) r1000-2000) persistent!) crit-opts)))
(pprint (map :mean [non-transient-union transient2-union transient3-union transient-union]))

;; try 1
([2.5411463409254377E-4 (2.521578245824343E-4 2.558558739151283E-4)]
 [3.291362638188762E-4 (3.257548343371522E-4 3.314287854773595E-4)]
 ;; no transient3-union in these measurements
 [1.8202335751255988E-4 (1.8051123364879075E-4 1.8323768231685945E-4)])
;; try 2
([2.6334088268786874E-4 (2.6196740459092076E-4 2.649376243224759E-4)]
 [3.3532542072316386E-4 (3.339136253220339E-4 3.3697925821468927E-4)]
 [3.3443002611422914E-4 (3.3232258057664803E-4 3.3641873126444375E-4)]
 [1.8977687190880043E-4 (1.8837110164085548E-4 1.9078059551376603E-4)])

;; Now try the second variant of transient with two sets the same, to
;; compare the speed there.

(def non-transient-union (ctrim (crit/benchmark (reduce conj r0-1000 r0-1000) crit-opts)))
(def transient2-union (ctrim (crit/benchmark (-> (reduce (fn [s e] (if (contains? s e) s (conj! s e))) (transient r0-1000) r0-1000) persistent!) crit-opts)))
(def transient3-union (ctrim (crit/benchmark (-> (reduce (fn [s e] (if (. clojure.lang.RT (contains s e)) s (conj! s e))) (transient r0-1000) r0-1000) persistent!) crit-opts)))
(def transient-union (ctrim (crit/benchmark (-> (reduce conj! (transient r0-1000) r0-1000) persistent!) crit-opts)))
(pprint (map :mean [non-transient-union transient2-union transient3-union transient-union]))

;; try 1
([1.0417003051879211E-4 (1.0306564076125988E-4 1.052342616634178E-4)]
 [1.893458731867859E-4 (1.877940594572777E-4 1.9213177885618482E-4)]
 [1.8428005795334041E-4 (1.8189483448804053E-4 1.86525284511606E-4)]
 [1.1287459781058092E-4 (1.1139284559636877E-4 1.1412332610519893E-4)])
;; try 2
([1.0463883761391014E-4 (1.0383843039349186E-4 1.0527485544848295E-4)]
 [1.9384600650566137E-4 (1.928503720007854E-4 1.9483744397539107E-4)]
 [1.9167363224513174E-4 (1.9013468011963857E-4 1.9282842851597304E-4)]
 [1.1805644468499428E-4 (1.1739114478808707E-4 1.1883957378770523E-4)])

;; Wow, I am surprised how much slower the versions are that check the
;; transient with contains? or .contains first.  Weird.  Maybe
;; .contains on transient sets is significantly slower than on
;; persistent sets?

```
