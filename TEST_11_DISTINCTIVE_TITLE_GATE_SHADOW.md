# Test 11 — Distinctive title gate (shadow)

Obiettivo: impedire che un candidato non-game o un prodotto diverso venga auto-collegato solo perché condivide una parola del nome BGG e lo stesso prezzo.

Regole shadow aggiunte:
- per giochi con 2–3 parole distintive, il candidato deve contenerle tutte;
- per nomi più lunghi, deve contenere almeno l'80% delle parole distintive;
- se il candidato aggiunge almeno 2 parole distintive non spiegate e non ha un vero secondo segnale (cue boardgame, brand coerente, titolo osservato molto coerente), viene lasciato irrisolto;
- zero HTTP e zero scritture di link.

Check diagnostico: `build=batch-resolver-distinctive-shadow-v3`, `distinctiveCoverageRejected`, `extraContextRejected`, `blockedSamples`, `samples`, `safeAssignments`.
