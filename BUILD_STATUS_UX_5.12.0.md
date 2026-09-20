# Ludo Scout 5.12.0 — UX Phase 1 build status

## Scope
Information architecture and Motore dashboard only. Engine behavior remains the consolidated 5.11.30 engine.

## UX checks
12/12 static UX checks PASS:
- five primary destinations: Scopri / Catalogo / Motore / Bundle / Libreria
- Database removed from primary nav
- Giochi entry exposed from Catalogo
- Motore overview exists
- work queue is a drill-down, not the landing page
- manual review is a separate drill-down
- session history exists
- observation sessions open a filtered Catalogo view
- sessions are computed locally with no new network calls
- legacy floating activity button hidden
- technical diagnostics moved into Settings
- build version bumped to 5.12.0

## Engine regression
All 28 consolidated engine checks PASS after updating only the expected version number. This includes Vinted batch safety, one-to-one assignment, BGG rating gate, urgent-lane priority, variant guard, price refresh, bounded memory, and production diagnostics.

## Android build
Full Android compilation could not run in this environment because Gradle wrapper requires Gradle 8.9 from services.gradle.org and outbound network is unavailable here. Static Java syntax scan found no parser-level errors before Android dependency resolution.
