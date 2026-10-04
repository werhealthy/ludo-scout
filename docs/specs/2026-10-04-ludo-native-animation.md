# Ludo — animazione nativa e scena interattiva

## Stato e obiettivo
Direzione proposta nella chat e approvata dall'utente con «Vai cominciamo a implementare», 2026-10-04. Spec e piano approvati con «Si» nella stessa chat; primo incremento implementato nella PR238, beta5.12.167-ludo-native-motion. Base implementazione riallineata a beta166; nessuna modifica al suo servizio AI. Nessuna nuova dipendenza, servizio o costo.

Ludo rimane al centro/basso della scena mentre cambiano le stanze. Stile canonico: folletto verde illustrato con cappello prugna, mantello teal e occhi a spirale. Non creare nuove identità stilistiche. Renderer Android Java/Canvas con animazioni native; nessun motore 3D o runtime Rive/Lottie.

## Primo incremento implementabile con gli asset disponibili
Separare il calcolo della posa temporale dal disegno in LudoPetView. Idle e reazione al tap dipendono dal tempo trascorso, non dal numero di frame: l'attuale reaction -= .018f è dipendente dal refresh rate. Movimento minimo con appoggio inferiore stabile; transizione di reazione morbida con rientro senza scatti. Ripetere un tap riavvia una sola reazione, senza accumulare callback o animator.

Predisporre un contratto per parti illustrate: identificatore, parent, ordine di disegno, pivot, posizione nella tavola sorgente, immagine ed eventuali varianti espressive. Non aggiungere parser/editor generici o animazioni scheletriche con mesh. Renderer accetta una singola immagine come fallback, preservando l'asset canonico disponibile. Le parti non sono ancora disponibili: non estrarre artificialmente occhi/arti dal WebP, non dichiarare implementati battito, saluto della mano o movimento autonomo del volto.

Idle e reazioni si fermano quando Activity è in pausa, finestra senza focus, scena nascosta, view detached o un overlay copre la stanza. Con animazioni di sistema disabilitate: posa statica, azioni e annunci accessibili funzionanti. Il tap non apre nuove funzioni: resta il saluto accessibile esistente.

## Incremento successivo: parti reali
Input designer: sorgente a livelli con corpo/piedi, mantello, testa senza volto, cappello, orecchie, occhi con fondo e spirali separati, bocche alternative, mani/braccia e dado, scintilla, ombra. Completare le superfici nascoste e annotare pivot/ordine. Esportazioni trasparenti e coordinate rispetto alla stessa tavola; ottimizzazione WebP all'integrazione. Varianti chiuso/socchiuso/aperto e bocca neutra/sorriso/sorpresa/sonno. Respiro, sguardo limitato, blink, tap e greeting condividono parti; vera rotazione testa richiede pose disegnate. Validare una mascotte prima di estendere il catalogo di animazioni.

## Stanze interattive — contratto futuro
Layer distinti: background, effetti ambientali, ombra/mascotte, hotspot Android, overlay UI. La mascotte e il suo stato non vengono ricreati al cambio stanza. Background forniti dall'utente senza personaggio/testi/pulsanti incorporati. Oggetti animati separati. Nessuna vecchia stanza viene assunta come nuovo asset approvato.

Ogni stanza definisce ID, background e coordinate normalizzate degli hotspot nella tavola illustrata. Un'unica trasformazione di scala/ritaglio mappa immagine e hotspot; mascot anchor nello spazio della scena. Safe insets e footer non coprono gli hotspot. Area di tap almeno 48dp, etichetta TalkBack, ordine di focus comprensibile. Riutilizzare destinazioni esistenti per Motore, Libreria, Preferiti ed Esplora; verificare il routing reale di Bintel prima di abilitarne un hotspot. Niente azioni finte. Swipe e tap non interferiscono con scroll/overlay.

Mantenere la UI attuale e la sua memoria nel primo incremento: stanze verticali e hotspot vengono integrati dopo l'arrivo dei background, in una consegna distinta. I pannelli brevi usano dialog/sheet esistenti; contenuti lunghi possono usare fullscreen con Back alla stanza e stato conservato.

## Prestazioni e confini
Decode e cache fuori dal main thread secondo il pattern esistente; nessun decode, bitmap o nuova collezione per frame. Calcolo geometrico aggiornato con dimensioni, disegno con oggetti riutilizzati. Caricare solo asset necessari; budget RAM misurato sulle dimensioni decodificate, non sul peso WebP. Nessuna modifica a database, queue, pricing, soglie, acquisizione, backend AI, navigation destinations o preferenze.

## Verifica e accettazione
Test significativi della posa a tempi uguali con refresh differenti, rientro tap e coordinate/pivot del fallback. Verifica lifecycle/overlay/reduced motion sul rendering Activity reale, regressioni frontend pertinenti e pipeline Android esistente. Ispezione visuale a font100/200%, viewport stretta e bassa, ritorno Ludo→Home→Ludo. Test sul telefono per appoggio, assenza scatti, tap ripetuti e fluidità; CI non prova prestazioni reali. Registrare risultati effettivi e limiti prima di distribuire la beta firmata secondo workflow.

## File e consegna
LudoPetView.java e nuovi helper focalizzati per posa/parti. MainActivity.java condiviso solo se necessario per lifecycle o binding; dichiararlo nella PR. Non riscrivere MainActivity né LudoRoomFrame per preparare stanze non ancora fornite. Branch frontend/ludo-native-animation. Nessuna APK per la sola specifica. Frontend7/backend6 gruppi aperti; lavoro consolidato nel gruppo Ludo esistente.

