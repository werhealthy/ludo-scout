# Ludo Scout — checkpoint 5.12.52

## Stato e base verificata
- Questa modifica nasce da `beta` SHA `45f6bf2d398868179ef4f7386b6c4b7f81b25a15`, versione 5.12.51. Il commit 5.12.50 indicato nel prompt era il genitore.
- La PR #62 punta a `beta`; `main` non è stato modificato.
- Il debug più recente nel prompt era un segnaposto. Non usarlo per dichiarare lo stato attuale del telefono.
- La PR #61 è un bootstrap Vibe Coding ancora aperto che aggiorna `AI_HANDOFF.md` e `CHANGELOG.md`. Per evitare conflitti, questo checkpoint è in un file separato e la PR #62 non modifica `AI_HANDOFF.md`.

## Difetto dimostrato nel percorso di pubblicazione
1. Una card con identità BGG candidata, ma tipo Vinted `UNCERTAIN`, poteva portare `applyBggMetadata()` a impostare il gioco e tutte le inserzioni attive su `TYPE_UNVERIFIED`, nascondere il gioco e completare il job BGG.
2. L’apertura successiva della pagina prodotto Vinted salvava la categoria strutturata, ma non riattivava l’annuncio né riaccodava BGG: la categoria positiva finiva in uno stato terminale.
3. La validazione sceglieva una singola riga deal più recente. Una riga incerta poteva prevalere su una prova `BASE_GAME` indipendente e influenzare tutte le inserzioni dello stesso gioco.
4. La verifica del rating era fondamentale: rating mancante non può valere come superamento della soglia; i rating sotto 6 devono restare invisibili.

## Correzione in PR #62
- Categoria esplicita dalla pagina prodotto riapre soltanto la listing attiva esatta in `PENDING_ANALYSIS`, corregge il tipo legacy e accoda una verifica BGG.
- La categoria riconosce italiano, inglese, spagnolo e francese; breadcrumb di accessori, espansioni e componenti/ricambi vengono respinti.
- La risposta BGG successiva ripristina soltanto listing con categoria positiva: rating assente resta pending, <6 resta `LOCAL_ONLY`, >=6 può superare il blocco. Un URL e ID esatti danno `CORE_COMPLETE`, altrimenti il link resta deferred.
- Una prova `BASE_GAME` ha precedenza sulle righe incerte.
- Nessuna osservazione, gioco o backlog viene cancellato.
- JUnit verifica il classificatore e la policy (categorie localizzate, espansioni/accessori/ricambi, rating mancante, confine 6.0, stato link). Le regressioni statiche verificano il perimetro delle query SQLite e l’accodamento BGG.

## CI e limiti dell’evidenza
- Il run Android PR validation #142 per l’head allora corrente è verde, includendo regressioni, `:app:testDebugUnitTest` e compilazione Java. Ricontrollare il run sull’head finale dopo ogni ulteriore commit.
- I guard esistenti non rappresentano un test Android/SQLite con centinaia di card. La prova realistica richiesta dall’utente resta aperta.
- Nessun test sul Pixel è stato svolto. Non dichiarare risolti volume catalogo, throughput Vinted o ANR.
- Codice da seguire separatamente: `renderEngineHistory()` e `renderEngineDay()` fanno letture DB sincrone sul main thread; timeout busy di SQLite è 8s. Storicamente il resolver pubblico costava ~12,86 richieste per collegamento, ma il debug recente manca e i retry possono amplificare tale costo.
- Restano aperti: attribuzione richiesta/route/retry, stress multi-process reale, query UI sincrone, modello UX Motore, categorie economiche/bundle e proposta di migrazione graduale.

## Prossimo passo sul dispositivo
Installa la beta 5.12.52 senza cancellare i dati. Esegui un singolo test mirato con 10 scroll Vinted, torna in Ludo, apri Motore/Attività e raccogli subito il debug completo. Confrontare versione, timestamp dell’ultimo evento Vinted, batch locale size/age, funnel per fase, richieste pubbliche per route/scopo, errori/attese, tempi UI e ANR. Per verificare il recupero categoria, aprire una listing che il debug mostri `TYPE_UNVERIFIED` e la cui pagina Vinted esponga una categoria board-game; verificare la riapertura BGG e che accessory/expansion restino escluse.