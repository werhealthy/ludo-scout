# Prompt di ricerca — Ludo Scout / Vinted

Ti fornirò uno ZIP di un'app Android chiamata **Ludo Scout**. Analizza il progetto prima di proporre modifiche. Il problema prioritario è **ridurre drasticamente le richieste automatiche a Vinted**, perché il backlog può arrivare a migliaia di annunci, impiega giorni e può contribuire a rate limit/blocchi temporanei.

## Contesto prodotto
L'UX ideale è già definita: apro Vinted normalmente e scrollo senza fare operazioni extra. Un AccessibilityService cattura card di annunci e Ludo prova a riconoscere il gioco, collegarlo a BGG, valutare prezzo/lingua e infine identificare l'annuncio Vinted preciso. L'utente non vuole sostituire lo scroll con import manuali o workflow più macchinosi.

## Dati che NON possiamo sacrificare
Per un annuncio valido vogliamo mantenere, quando tecnicamente ottenibili: prezzo osservato, thumbnail/foto, identità BGG corretta, lingua/edizione quando rilevante, Vinted item ID/URL preciso e vera data di pubblicazione dell'annuncio. Non proporre scorciatoie che semplicemente eliminano questi dati o li sostituiscono con timestamp locali.

## Vincoli
- Non proporre bypass di rate limit, cookie/session hijacking, API private abusive, scraping aggressivo, rotazione IP/account o tecniche che aumentino il rischio dell'account.
- Preferisci dati già presenti nell'Accessibility tree, intent/deep link/share, cache, deduplicazione, batching, candidate reuse, local inference e architetture che riducono il numero di chiamate.
- Distingui chiaramente **osservazione locale**, **identità del gioco BGG** e **identità dell'annuncio Vinted**.
- Una super-offerta o una Caccia deve poter emergere subito, indipendentemente dal backlog storico.

## Cosa voglio che studi a fondo
1. Tutti i punti in cui il codice crea o esegue richieste Vinted e quante richieste può costare un singolo annuncio.
2. Se l'item ID/URL Vinted può essere ottenuto direttamente dallo scroll Android: AccessibilityNodeInfo (text/contentDescription/viewId/uniqueId/extras/actions), clickable spans, intent/deep-link semantics, share targets o altri segnali esposti legittimamente dalla UI.
3. Modi per risolvere **più annunci con una sola risposta/search page**, soprattutto per annunci dello stesso gioco: batching, assignment bipartito, cache per canonical game, price+image matching, candidate pools persistenti.
4. Modi per evitare richieste inutili prima della rete: BGG rating <6, non-giochi, prezzi palesemente non interessanti, duplicati, annunci già identificati, lingua incompatibile.
5. Come mantenere comunque URL/ID/data pubblicazione per gli annunci validi senza trasformare ogni osservazione in 2–3 chiamate.
6. Strategie offline/on-device per elaborare migliaia di osservazioni in minuti, con benchmark misurabili.
7. Forum, issue tracker, documentazione Android e discussioni tecniche recenti che mostrino approcci pratici a problemi simili.

## Output richiesto
Non modificare subito il codice. Prima produci un audit con: (a) mappa completa delle chiamate Vinted, (b) costo richieste per percorso, (c) colli di bottiglia, (d) almeno 3 architetture alternative ordinate per rischio/beneficio, (e) esperimenti diagnostici concreti per capire se l'ID Vinted è già disponibile durante lo scroll, (f) piano benchmark su un backlog reale, (g) raccomandazione finale.

Ogni proposta deve spiegare **quante richieste Vinted elimina**, quali dati conserva/perde e quali rischi introduce. Cerca fonti online aggiornate e separa chiaramente: codice osservato / documentazione ufficiale / community / inferenza.
