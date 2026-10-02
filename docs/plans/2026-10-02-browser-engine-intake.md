# Browser → Motore implementation plan

Goal: turn explicitly captured public Vinted items into durable observations and existing local BGG analysis, then deliver a verified signed beta in Tester. Scope authorized by user21:27; execution in this session. No schema/dependency/paid service, filter or threshold changes.

Use the current single radar analysis owner. Browser capture saves by exact ID in existing SQLite tables, wakes its local analysis lane independently of scan opt-in, and retains missing-price snapshots for later explicit captures. Missing seller/publication/language never create automatic Vinted work. Existing manual requests remain explicit. Preserve hidden/sold/manual-review records and legacy signatures for already known exact IDs. Browser controls become compact search and secondary sort/minimum-price menus; remove arbitrary Catan/Azul shortcuts.

- [x] Reproduce absent intake using a failing SQLite regression.
- [ ] Add ID-preserving adapter and durable snapshot/intake queries; test repeated IDs, distinct same-title IDs, sparse metadata, price changes and protected lifecycle states.
- [ ] Wake existing runtime and preserve captured identity through pending selection, analysis and Catalog metadata; guard automatic remote work by durable provenance.
- [ ] Connect browser capture outside main thread and retain accepted writes at close; simplify native controls using current design assets.
- [ ] Run existing regressions and Android validation on dedicated backend branch; review shared files, refresh beta base, merge verified PR.
- [ ] Verify signed beta, expected certificate, Firebase upload and tester distribution; update STATE and workstream backlog with actual evidence and phone test limits.

Review focus: capture with missing price/currency; two sellers with identical title/price; repeated DOM plus richer JSON; page changes/close while writes are queued; scan opt-in OFF/runtime startup. Device performance and visual approval remain phone checks, not CI claims.
