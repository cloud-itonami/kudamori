;; kudamori 管守 — confined-space atmosphere entry gate (THE headline safety gate, ★ G5).
;;
;; A foul-sewer headspace is the documented killer: O2 displacement + H2S + CH4 + CO.
;; This is the gate that removes a human from the confined space — entry on an unsafe
;; reading is UNREPRESENTABLE: `entry-permitted?` returns false and `assert-entry!`
;; RAISES. A purge-to-entry model (forced ventilation) drives concentrations toward the
;; threshold over time, mirroring niyaku/kamado purge-to-entry discipline.
;;
;; Thresholds — SOURCED, not a house norm. Each constant below is quoted, dated and
;; URL'd in `facts.edn` (:kudamori.facts/atmosphere), and a drift test in
;; kudamori/methods/test_kudamori.clj fails if one is loosened past its authority:
;;   O2   safe 19.5 % .. 23.5 %      OSHA 29 CFR 1910.146(b) definitions
;;   H2S  < 10 ppm   (硫化水素)       NIOSH REL ceiling (10-min); 酸欠則 第二条第二号 agrees
;;   CH4  < 10 %LEL                  OSHA 29 CFR 1910.146(b) hazardous atmosphere (1)
;;   CO   < 35 ppm   (一酸化炭素)     NIOSH REL TWA
;;
;; The refusals are `>=`, so this gate refuses AT a limit its authority still permits.
;; Deliberate: see facts.edn. Note the O2 floor is the US 19.5 %, while 酸欠則 defines
;; deficiency at 18 % — this code is STRICTER than the ordinance binding a JP sewer
;; crew, and satisfies both. Do not "align" 19.5 down to 18.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable. Pure compute;
;; it gates no real entry (G1 no-server-key / R0 design+sim).
;; Per ADR-2606142030 (kudamori R0). Clojure-first (the GAP-actor wave).
(ns kudamori.methods.atmosphere)

;; ── safe-atmosphere thresholds (cited in facts.edn; drift-tested) ────────────
(def ^:const o2-min-pct 19.5)
(def ^:const o2-max-pct 23.5)
(def ^:const h2s-max-ppm 10.0)
(def ^:const ch4-max-lel 10.0)
(def ^:const co-max-ppm 35.0)

(defn- field [reading k default]
  (let [v (get reading k default)] (if (number? v) (double v) default)))

(defn hazards
  "Return the seq of {:gas :value :limit :kind} for every reading that BREACHES a
   confined-space threshold. Empty seq = a passing atmosphere. Pure inspection."
  [reading]
  (let [o2  (field reading :o2-pct 0.0)
        h2s (field reading :h2s-ppm 999.0)
        ch4 (field reading :ch4-lel 999.0)
        co  (field reading :co-ppm 999.0)]
    (cond-> []
      (< o2 o2-min-pct)  (conj {:gas :o2  :value o2  :limit o2-min-pct :kind :oxygen-deficient})
      (> o2 o2-max-pct)  (conj {:gas :o2  :value o2  :limit o2-max-pct :kind :oxygen-enriched})
      (>= h2s h2s-max-ppm) (conj {:gas :h2s :value h2s :limit h2s-max-ppm :kind :toxic})
      (>= ch4 ch4-max-lel) (conj {:gas :ch4 :value ch4 :limit ch4-max-lel :kind :flammable})
      (>= co  co-max-ppm)  (conj {:gas :co  :value co  :limit co-max-ppm  :kind :toxic}))))

(defn entry-permitted?
  "True iff EVERY gas is within its safe band. NEVER true on an unsafe reading."
  [reading]
  (empty? (hazards reading)))

(defn assert-entry!
  "Return the reading if entry is permitted; RAISE otherwise (★ G5 — an unsafe
   atmosphere refuses entry; entry without a passing atmosphere is unrepresentable)."
  [reading]
  (let [hz (hazards reading)]
    (when (seq hz)
      (throw (ex-info "confined-space entry refused: unsafe atmosphere (G5)"
                      {:reading reading :hazards hz})))
    reading))

;; ── purge-to-entry (forced ventilation) ──────────────────────────────────────
;; Ventilating to a threshold is a legal duty here, not an optimisation: a sewer is a
;; 第二種酸素欠乏危険作業 site (令別表第六第九号 lists the interior of 管/暗きよ/マンホール
;; that have held 汚水), and 酸欠則 第五条 requires ventilating to O2 >= 18 % AND
;; H2S <= 10 ppm. Quoted in facts.edn (:kudamori.facts/ventilation).
;;
;; Well-mixed dilution: each air change scales every CONTAMINANT toward 0 by a fixed
;; fraction; O2 is restored toward fresh-air 20.9 % from whichever side it sits on.
(def ^:const fresh-o2-pct 20.9)

(defn- purge-step [reading air-changes]
  (let [decay (Math/exp (- (max 0.0 (double air-changes))))   ; e^{-N} → 0 over changes
        o2 (field reading :o2-pct 0.0)]
    {:o2-pct  (+ fresh-o2-pct (* (- o2 fresh-o2-pct) decay))   ; relax toward fresh air
     :h2s-ppm (* (field reading :h2s-ppm 0.0) decay)
     :ch4-lel (* (field reading :ch4-lel 0.0) decay)
     :co-ppm  (* (field reading :co-ppm 0.0) decay)}))

(defn purge-to-entry
  "Forced-ventilation model. Given an initial `reading`, the blower's
   `air-changes-per-min`, and a `max-min` ventilation budget, step minute-by-minute
   until the atmosphere passes (`entry-permitted?`) or the budget is exhausted.
   Returns {:entry-permitted? bool :minutes n :reading <post-purge> :hazards […]}.
   NEVER reports :entry-permitted? true unless the post-purge reading actually passes."
  [reading air-changes-per-min max-min]
  (loop [m 0]
    (let [post (purge-step reading (* air-changes-per-min m))]
      (cond
        (entry-permitted? post) {:entry-permitted? true  :minutes m :reading post :hazards []}
        (>= m max-min)          {:entry-permitted? false :minutes m :reading post
                                 :hazards (hazards post)}
        :else                   (recur (inc m))))))
