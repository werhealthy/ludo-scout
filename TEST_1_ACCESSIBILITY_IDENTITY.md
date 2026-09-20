# Test 1 — Accessibility Identity Probe

Obiettivo: verificare se le card Vinted espongono un vero item ID / URL attraverso URLSpan o altre proprietà semantiche Accessibility, senza richieste HTTP aggiuntive.

## Procedura
1. Installa questa build/progetto mantenendo attivo il servizio Accessibilità di Ludo Scout.
2. Apri Vinted.
3. Senza aprire manualmente gli annunci, scorri normalmente almeno 30–50 card diverse. Meglio fare una parte nella Home e una parte in una ricerca di giochi da tavolo.
4. Torna in Ludo Scout.
5. Apri Impostazioni e scegli **Copia diagnostica**.
6. Incolla il testo diagnostico nella chat.

## Cosa guarderemo
- `a11yIdentityProbe.uniqueCards`: deve essere almeno 30; 50 è meglio.
- `a11yIdentityProbe.urlSpans`: quanti URLSpan Android ha esposto.
- `a11yIdentityResult.idsFromUrlSpan`: se > 0, abbiamo trovato un vero item ID direttamente in uno span.
- `a11yIdentityResult.idsFromOtherExplicitField`: se > 0, l'identità è arrivata da un altro campo semantico esplicito.
- `a11yIdentityResult.lastSource`: indica quale campo ha funzionato.

Ludo continua ad accettare un'identità solo se il valore contiene letteralmente `/items/<digits>`: nessun numero generico, recycler id o uniqueId opaco viene interpretato come item ID.
