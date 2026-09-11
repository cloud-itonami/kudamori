# kudamori 管守

**Sewer / confined-space in-pipe cleaning robotics — atmosphere gate + in-pipe nav + hydro-jetting.**
Tier-B actor · ADR-2606142030 · 🟡 R0 (design + sim) · Clojure-first.

kudamori ("管守" = pipe-keeper) **removes the human from the confined space** that
ADR-2606073001 §4 named as a high-remote-value GAP (toxic-gas / confined-space death). It is
the **in-pipe cleaning counterpart** that mizuho 水穂 (wastewater *treatment*,
ADR-2605263100) left open: an electric, tazuna-teleoperable in-pipe crawler that gates entry
on the atmosphere, navigates the pipe network, and hydro-jets the blockage — pressure-safe,
with the effluent handed back to mizuho.

It is a sibling of the reference Clojure-first actor kuramori (ADR-2606142000): methods are
pure Clojure (no deps) → run under both `bb` and the kotoba pywasm runtime.

## Run

```bash
bb run_tests.cljk                                                        # 41 tests / 158 assertions
bb --classpath . -m kudamori.methods.analyze                            # → sewer-cleaning R0 report
bb --classpath . -m kudamori.methods.datom-emit                         # → kotoba EAVT Datom log
```

## What it does

| Method | Role |
|---|---|
| `atmosphere.clj` | ★ G5 confined-space entry gate (O2/H2S/CH4/CO thresholds — **raises** on unsafe) · purge-to-entry forced ventilation |
| `pipe_nav.clj`   | diameter-fit check (**raises** on no-fit) · BFS shortest route over the pipe graph · route-around blocked segments |
| `jetting.clj`    | ★ G7 jet-pressure-safe vs pipe-material rating (**raises** on over-pressure) · debris-removal estimate · water-reuse balance (G2, effluent → mizuho) |
| `analyze.clj`    | end-to-end: entry gate (purge if needed) → in-pipe nav → pressure-safe jetting → report (downstream GATED if the air can't be made safe) |
| `datom_emit.clj` | kotoba EAVT projection (`:kuda.*` GROUND + `:bond/*` DERIVED transient) |
| `facts.edn`      | ★ the sources behind G5 — every atmosphere threshold quoted, dated and URL'd (OSHA / NIOSH / 酸欠則), plus the G7 ratings declared **ungrounded** |

## Where the numbers come from

`facts.edn` carries the citation for every ★ G5 threshold: the instrument, the clause,
the verbatim quote, the URL it was read from and the date it was fetched. Nothing there
is asserted from memory — each quote was confirmed present in the fetched body, because
several official sites (eCFR, e-Gov's web UI) answer `200` with the regulation text
absent, so a status code alone is not verification.

Two jurisdictions are recorded because they disagree: OSHA puts oxygen deficiency at
**19.5 %**, 酸素欠乏症等防止規則 第二条 at **18 %**. The code holds 19.5 %, so it is
stricter than the ordinance that actually binds a Japanese sewer crew and satisfies
both — do not "align" it down. A sewer is a 第二種酸素欠乏危険作業 site (令別表第六
第九号 covers the interior of 管・暗きよ・マンホール that have held 汚水), which under
第五条 must be ventilated to O2 ≥ 18 % **and** H2S ≤ 10 ppm; that pairing is what
`purge-to-entry` models.

`test_kudamori.clj` makes those citations load-bearing rather than decorative: it fails
if any constant drifts to the permissive side of an authority cited for it, and it
*errors* — never silently passes — if `facts.edn` is missing or empty.

What is **not** sourced is stated as such: G7's pipe pressure ratings are R0 estimates
recorded under `:kudamori.facts/gaps`, with the condition that would close the gap. The
over-pressure gate still refuses; the limits it refuses against are unverified.

## Gates

R0 design+sim only (G1, no-server-key) · water-reuse/eco + effluent→mizuho (G2) · no worker
surveillance (G3) · Displacement-Dividend-coupled (G4) · ★ confined-space atmosphere gate
raises (G5) · Murakumo-only (G6) · ★ no pipe over-pressure, raises (G7) · tazuna-teleoperable
(G8). See `CLAUDE.md` for the full text.

Apache 2.0 + etzhayyim Charter Compliance Rider v3.1.
