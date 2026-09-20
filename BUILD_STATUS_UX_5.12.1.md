# Ludo Scout 5.12.1 — UX Phase 2 build status

## Goal
Replace the card-heavy Activity dashboard with one human-readable current scroll/run, daily history, and a safe one-time clean start for the new engine.

## Implemented
- One dominant `Motore` hero for the latest Vinted scroll.
- Hero emphasizes valid games found; raw Vinted card count is secondary.
- BGG, Vinted identity, and ready-card progress are simple rows, not equal-weight cards.
- Green completion is reserved for fully ready runs. Runs with human review are orange.
- Ready cards are directly tappable into a filtered Catalog view.
- History is grouped by day (`Oggi`, `Ieri`, `L'altro ieri`, date). Individual scrolls appear only inside a day's detail.
- Observation bursts are separated after 3 minutes of inactivity, so a new scroll after the completion notification becomes a new run.
- Completion notification: once automatic processing of a run has settled, Ludo can notify `puoi fare un nuovo scroll`; tapping opens Motore.
- Catalog's secondary BGG collection is labelled `Archivio` rather than `Giochi`/`Database`.

## Safe clean start
On the first launch of 5.12.1 only:
- durable processing jobs from the old backlog are removed;
- old raw observations are removed from the active timeline;
- incomplete legacy market listings/deals are archived as `RESET_LEGACY`, not hard-deleted;
- only listings already complete enough to be useful (matched BGG >= 6, exact Vinted id+URL, current price and publication label) remain active;
- games with no remaining active listing are hidden from the active scouting archive;
- completed/owned Library data is not touched;
- Vinted pacing/circuit-breaker safety state is deliberately preserved.

Archived sightings can be revived if they are observed again in a future Vinted scroll.

## Validation
- `regression/ux_phase2_v5121.py`: 17/17 PASS.
- engine regression (`_engine_check_v5120.py`, updated for 5.12.1): 27/27 PASS.
- all application `R.drawable.*` references resolve to project resources.
- `javac` parser pass found no Java syntax/parser errors before the expected missing Android SDK dependency errors.
- Full Gradle/Android compilation cannot run in this environment because the Gradle 8.9 wrapper distribution is not cached and outbound download from services.gradle.org is unavailable.
