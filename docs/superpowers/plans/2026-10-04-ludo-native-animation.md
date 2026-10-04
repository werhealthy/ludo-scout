# Native Ludo Animation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendere idle e tap fluidi e indipendenti dal refresh rate, preparando la mascotte illustrata a parti senza cambiare la UI attuale.

**Architecture:** Una posa numerica pura alimenta LudoPetView. Un renderer Canvas disegna una gerarchia semplice di parti, con l'attuale immagine canonica come parte unica di fallback. L'Activity conserva la gestione di visibilità e focus; nessun nuovo framework.

**Tech Stack:** Android Java17, minSdk28, Canvas, ValueAnimator, JUnit4, instrumentation e CI esistenti.

**Spec:** docs/specs/2026-10-04-ludo-native-animation.md — approvata dall'utente il2026-10-04 con «Si».

## Global Constraints
- Nessuna nuova dipendenza, servizio o costo.
- Non estrarre artificialmente occhi/arti dal WebP; non dichiarare implementati blink o gesti senza asset.
- Nessuna modifica a database, queue, pricing, soglie, acquisizione o backend AI.
- Mantenere UI, navigazione, memoria delle stanze e ricerca persistente attuali.
- Sospendere movimento con pausa, perdita focus, overlay, invisibilità, detach e animazioni disabilitate.
- Nessun decode, bitmap o nuova collezione per frame.
- Questa consegna copre solo il primo incremento della spec. Asset separati e stanze/hotspot sono successivi e dipendono dagli input designer.

## Review Focus
- Tap ripetuti: una sola reazione, senza code di callback.
- Clock ripristinato o primo frame dopo pausa: posa finita e nessun salto temporale.
- Asset assente/riciclato: nessun crash, rendering vuoto sicuro.
- Layer figli: pivot e composizione coerenti, nessuna mutazione della posa del parent.
- Schermo basso/font200% e cambio stanza: stessa mascotte, footer e contenuti accessibili.

---

### Task1: Posa temporale indipendente dal refresh
**Files:** Create app/src/main/java/it/vintedaffari/app/LudoPose.java; create app/src/test/java/it/vintedaffari/app/LudoPoseTest.java; modify app/src/main/java/it/vintedaffari/app/LudoPetView.java.
**Interfaces:** LudoPose.sample(long elapsedMs, long reactionElapsedMs, boolean motionEnabled, float gazeX, float gazeY, LudoPose out):void. out espone bodyScaleY, headRotationDeg, reactionLift, gazeX, gazeY come float. reactionElapsedMs=-1 significa nessuna reazione. LudoPose è privo di dipendenze Android; out è riutilizzato dal renderer.
- [ ] Scrivere test che confrontano gli stessi timestamp dopo120/60/30campionamenti: campi identici; sample(0,-1,false,0,0,out) produce scaleY=1,rotation=0,lift=0; nessun NaN per tempi negativi e gaze fuori range.
- [ ] Eseguire ./gradlew testDebugUnitTest --tests it.vintedaffari.app.LudoPoseTest e registrare il fallimento prima del codice.
- [ ] Implementare ciclo idle4200ms, scala verticale massima±0.004, inclinazione massima1grado, reazione1600ms con inviluppo continuo zero all'inizio e alla fine e picco lift0.015. Limitare gaze a[-1,1]. Usare uptimeMillis per il tempo nella View; eliminare reaction -= .018f. Riutilizzare una posa per frame.
- [ ] Aggiungere test del rientro a1600ms e restart della reazione: sample dello stesso tempo è uguale indipendentemente dal numero di frame precedenti. Tempi negativi vengono limitati a zero; motionEnabled=false azzera il movimento anche durante tap.
- [ ] Eseguire test, aggiornare le regressioni che impongono la vecchia implementazione solo dove contrastano con la spec; non indebolire controlli lifecycle e memoria.
- [ ] Commit feat: make Ludo motion time based.

### Task2: Renderer illustrato a parti con fallback reale
**Files:** Create app/src/main/java/it/vintedaffari/app/LudoPart.java; create app/src/main/java/it/vintedaffari/app/LudoCharacterRenderer.java; modify LudoPetView.java; create app/src/test/java/it/vintedaffari/app/LudoPartTest.java.
**Interfaces:** LudoPart(String id,String parentId,int drawOrder,float x,float y,float width,float height,float pivotX,float pivotY) usa coordinate nella tavola sorgente e pivot locali. LudoCharacterRenderer.setParts(java.util.List<LudoPart> parts,java.util.Map<String,android.graphics.Bitmap> images,float sourceWidth,float sourceHeight):void; draw(android.graphics.Canvas canvas,android.graphics.RectF bounds,LudoPose pose,boolean portrait):void; setFallback(android.graphics.Bitmap bitmap):void. Nessun parser o file asset fittizio.
- [ ] Scrivere test per dimensioni non valide, IDduplicati,parent mancante/ciclico e ordinamento stabile; input invalidi rifiutati alla configurazione, non durante onDraw.
- [ ] Eseguire ./gradlew testDebugUnitTest --tests it.vintedaffari.app.LudoPartTest e verificare RED.
- [ ] Implementare metadati immutabili e gerarchia precomputata; bitmap/Matrix/Paint/RectF e trasformazioni riutilizzati. Root ancorata al centro/basso; figli seguono parent, drawOrder determina la sovrapposizione. Nessuna mesh o IK. Non associare parti non fornite ad asset inventati.
- [ ] Collegare setIllustration/setIllustrations esistenti al fallback senza cambiare firme dei chiamanti. Conservare portrait/crop attuale nelle facce inline; il fallback singolo conserva proporzioni, piedi inferiori stabili durante respiro e traslazione minima durante tap. Bitmap assente o riciclata non disegnata.
- [ ] Estendere instrumentation con piccole bitmap di fixture per verificare pixel di sovrapposizione, pivot parent/child e assenza asset; solo test, nessuna fixture visuale nella produzione.
- [ ] Eseguire unit test e instrumentation nella pipeline Android; commit feat: support illustrated Ludo parts with single image fallback.

### Task3: Lifecycle, verifica visiva e consegna
**Files:** Modify app/src/main/java/it/vintedaffari/app/LudoPetView.java; app/src/main/java/it/vintedaffari/app/LudoRoomBackdropView.java solo per allineare pausa effetti se necessario; app/src/main/java/it/vintedaffari/app/MainActivity.java solo per binding lifecycle necessario; app/src/androidTest/java/it/vintedaffari/app/LudoVisualInstrumentation.java; .github/workflows/android-pr.yml solo se runner richiede nuovi test; app/build.gradle per versione distribuita; STATE.md e spec UI al checkpoint.
**Interfaces:** Conservare setResumed(boolean), react(), setMood(LudoPetMood), lookTowards(float,float), setPortrait(boolean). La scena riceve un gate unico derivato da Activity/focus/overlay/visibilità e animazioni di sistema. Riutilizzare syncPetVisibility esistente.
- [ ] Aggiungere fixture instrumentation: tap ripetuti non creano animator aggiuntivi; pausa/focus/overlay/detach fermano animator e callback; ripresa parte da posa neutra; cambio stanza conserva petView. A scala animazioni0 il tap annuncia ancora il saluto e nessun movimento gira.
- [ ] Eseguire fixture sulla versione precedente dell'integrazione e registrare i casi RED effettivi; implementare il minimo gating e cancellazione necessari, usando tempo relativo al ciclo attivo. Saluto accessibile esistente conservato.
- [ ] Eseguire ./gradlew testDebugUnitTest assembleDebug e il workflow android-pr esistente, inclusi rendering Ludo font100/200% e regressioni JVM/SQLite/frontend. Usare configurazione instrumentation e invocazioni reali già definite nei workflow, non inventare runner.
- [ ] Ispezionare screenshot stretti/bassi e normali; verificare appoggio, scala, footer, contenuti, portrait e ritorno Home. Separare prove CI da fluidità reale; se toolchain locale manca, usare CI e dichiararlo.
- [ ] Revisionare diff contro spec, risolvere findings e riallineare branch all'ultimo beta prima del merge; ripetere controlli pertinenti dopo modifiche.
- [ ] Aggiornare versione e note, seguire distribuzione beta già autorizzata solo dopo CI verde e certificato atteso verificato; confermare separatamente uploadFirebase e distribuzione tester. Checkpoint STATE/spec conserva frontend7/backend6 aperti e richiede prova sul telefono per accettazione/fluidità.
- [ ] Commit per integrazione e PR unica; nessuna nuova stanza o funzione AI in questa PR.

## Self-review
Copertura: primo incremento, contratto parti, fallback, lifecycle, accessibilità, prestazioni e verifica coperti dai tre task. Fasi con nuovi asset e hotspot sono esplicitamente escluse da questa consegna. Interfacce di posa/renderer sono consistenti; review focus coperto da test nei task1–3. Nessuna nuova dipendenza o scelta di prodotto.

## Execution handoff
Raccomandata esecuzione nativa in questa sessione: tre task strettamente collegati, una PR, nessuna ragione di moltiplicare implementatori. Il piano scritto deve essere revisionato dall'utente prima del codice, secondo writing-plans.
