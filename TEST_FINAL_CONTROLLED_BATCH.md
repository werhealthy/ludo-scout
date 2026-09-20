# Test finale — attivazione controllata batch Vinted

Questa build applica realmente al massimo 3 link Vinted, ma solo quando il match locale soddisfa criteri volutamente più severi del matcher shadow:

- nessuna nuova richiesta HTTP per scegliere il link;
- solo backlog, mai Caccia/manuale/live;
- BGG rating >= 6 e gioco visibile;
- snapshot già esistente, recente e non antecedente chiaramente all'osservazione;
- prezzo esatto (tolleranza 1 centesimo);
- safety gate Test 12;
- score >= 160;
- margine >= 20 sul secondo candidato;
- item ID one-to-one e non già usato;
- massimo 3 scritture reali.

Il vecchio resolver resta invariato per tutti gli altri casi.

Dopo il test, verificare manualmente i link indicati in `vintedBatchControlled.matches` e poi copiare la diagnostica.
