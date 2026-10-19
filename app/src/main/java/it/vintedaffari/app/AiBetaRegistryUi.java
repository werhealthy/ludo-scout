package it.vintedaffari.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local review UI; accepted values are staged in encrypted settings and never written to the catalog. */
final class AiBetaRegistryUi {
    private AiBetaRegistryUi() { }

    private static final class RegistryRow {
        final AiBetaRegistry.Entry entry;
        final AiBetaRegistryView.Match match;
        final String display;

        RegistryRow(AiBetaRegistry.Entry entry, AiBetaRegistryView.Match match, String display) {
            this.entry = entry;
            this.match = match;
            this.display = display;
        }

        @Override public String toString() { return display; }
    }

    private static final class DraftState {
        AiBetaRegistryDraft.Draft draft;
        String error;
    }

    static void open(Activity activity, AiBetaRegistry registry, AiBetaSettings settings) {
        if (registry == null || !AiBetaTestDialog.BUSY.compareAndSet(false, true)) return;
        AiBetaTestDialog.IO.execute(() -> {
            DraftState draftState = new DraftState();
            try {
                JSONObject saved = settings.load();
                draftState.draft = AiBetaRegistryDraft.load(saved);
            } catch (Exception failure) {
                draftState.error = "Bozza privata non leggibile; non verrà sovrascritta.";
            } finally {
                AiBetaTestDialog.BUSY.set(false);
            }
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                showList(activity, registry, settings, draftState);
            });
        });
    }

    private static void showList(Activity activity, AiBetaRegistry registry, AiBetaSettings settings,
                                 DraftState draftState) {
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = Math.round(14 * density);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        TextView heading = new TextView(activity);
        heading.setText(AiBetaRegistryView.reviewHeader(registry));
        heading.setTextSize(13);
        heading.setTextColor(Color.rgb(225, 223, 236));
        content.addView(heading);

        Button draftButton = new Button(activity);
        updateDraftButton(draftButton, draftState);
        content.addView(draftButton);
        draftButton.setOnClickListener(v -> showDraft(activity, settings, draftState, draftButton));

        Button sampleReportButton = new Button(activity);
        sampleReportButton.setText("Leggi e condividi report campione (5) · sola lettura");
        sampleReportButton.setEnabled(!AiBetaTestDialog.BUSY.get());
        content.addView(sampleReportButton);
        sampleReportButton.setOnClickListener(v -> shareSampleReport(activity, registry, sampleReportButton));

        EditText filter = new EditText(activity);
        filter.setSingleLine(true);
        filter.setHint("Filtra per ID, titolo, metadati o stato");
        content.addView(filter);

        List<RegistryRow> rows = new ArrayList<>();
        String[] displayRows = AiBetaRegistryView.reviewRows(registry);
        for (int i = 0; i < registry.entries.size(); i++) {
            AiBetaRegistry.Entry entry = registry.entries.get(i);
            rows.add(new RegistryRow(entry, null, displayRows[i]));
        }
        ArrayAdapter<RegistryRow> adapter = new ArrayAdapter<RegistryRow>(activity,
                android.R.layout.simple_list_item_1, rows) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                TextView label = row.findViewById(android.R.id.text1);
                label.setSingleLine(false);
                label.setMaxLines(6);
                return row;
            }
        };
        ListView list = new ListView(activity);
        list.setAdapter(adapter);
        int screenDp = (int) (activity.getResources().getDisplayMetrics().heightPixels / density);
        int listHeight = Math.round(Math.max(140, Math.min(400, screenDp - 280)) * density);
        content.addView(list, new LinearLayout.LayoutParams(-1, listHeight));
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable editable) { }
        });

        AlertDialog listDialog = new AlertDialog.Builder(activity)
                .setTitle("Registro AI · proposta controllata")
                .setView(content)
                .setNegativeButton("Chiudi", null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            RegistryRow selected = adapter.getItem(position);
            if (selected != null) openEntry(activity, registry, settings, draftState,
                    draftButton, selected);
        });
        listDialog.show();
    }

    private static void shareSampleReport(Activity activity, AiBetaRegistry registry, Button button) {
        if (registry == null || !AiBetaTestDialog.BUSY.compareAndSet(false, true)) return;
        button.setEnabled(false);
        AiBetaTestDialog.IO.execute(() -> {
            String report;
            boolean available = true;
            try {
                List<Long> localIds = AiBetaRegistrySampleReport.localIdsToRead(registry);
                java.util.Map<Long, AiBetaRegistryView.CurrentTargetRead> reads =
                        AiBetaRegistryView.readCurrentTargets(activity, localIds);
                report = AiBetaRegistrySampleReport.build(registry, reads);
            } catch (Exception failure) {
                report = "Ludo Scout · verifica campione AI\nReport non generato: lettura locale non riuscita. "
                        + "Nessuna modifica al catalogo.";
                available = false;
            } finally {
                AiBetaTestDialog.BUSY.set(false);
            }
            final String sharedReport = report;
            final boolean canShare = available;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                button.setEnabled(true);
                if (!canShare) {
                    new AlertDialog.Builder(activity).setTitle("Report non disponibile")
                            .setMessage(sharedReport).setPositiveButton("Chiudi", null).show();
                    return;
                }
                try {
                    activity.startActivity(Intent.createChooser(
                            AiBetaRegistrySampleReport.shareIntent(sharedReport), "Condividi report campione"));
                } catch (RuntimeException failure) {
                    new AlertDialog.Builder(activity).setTitle("Condivisione non disponibile")
                            .setMessage("Android non ha restituito un’app di condivisione. "
                                    + "Nessun report è stato copiato o inviato.")
                            .setPositiveButton("Chiudi", null).show();
                }
            });
        });
    }

    private static void openEntry(Activity activity, AiBetaRegistry registry, AiBetaSettings settings,
                                  DraftState draftState, Button draftButton, RegistryRow row) {
        if (!AiBetaTestDialog.BUSY.compareAndSet(false, true)) return;
        AiBetaTestDialog.IO.execute(() -> {
            AiBetaRegistryView.CurrentTargetRead target = null;
            AiBetaRegistryView.Match match = null;
            String details;
            try {
                if ("PROPOSED".equals(row.entry.recordState) && row.entry.sourceId != null) {
                    target = AiBetaRegistryView.readCurrentTarget(activity, row.entry.sourceId);
                    match = AiBetaRegistryView.match(row.entry, target.catalog);
                    details = AiBetaRegistryView.details(registry, row.entry, target.catalog)
                            + "\n\nVerifica mirata della scheda attuale: "
                            + (AiBetaRegistryView.canPrepareCurrentTarget(row.entry, match, target)
                            ? "ID corrente univoco, scheda attiva e hash contenuti uguale; identità storica comunque non dimostrata."
                            : "non sufficiente per preparare una correzione; consultare i motivi riportati sopra.");
                    if (sameJournalEntry(draftState.draft, registry, row.entry)) {
                        if (AiBetaRegistryDraft.APPLIED.equals(draftState.draft.state)
                                && draftStillApplied(draftState.draft, match.listing, target.uniqueCurrentItemId)) {
                            details += "\n\nJournal: APPLIED; l’hash post-correzione e il valore dopo coincidono. "
                                    + "La proposta storica resta nel registro, ma non va riproposta né trattata come nuova anomalia.";
                        } else if (AiBetaRegistryDraft.APPLIED.equals(draftState.draft.state)) {
                            details += "\n\nJournal: APPLIED registrato, ma i controlli correnti non coincidono. "
                                    + "Non riproporre; aprire la bozza per riconciliare il conflitto.";
                        } else if (AiBetaRegistryDraft.CONFLICT.equals(draftState.draft.state)) {
                            details += "\n\nJournal: CONFLICT; dati o identità sono cambiati dopo la bozza. "
                                    + "Nessuna nuova proposta o ripristino automatico.";
                        }
                    }
                } else {
                    details = AiBetaRegistryView.registryOnlyDetails(registry, row.entry);
                }
            } catch (Exception failure) {
                details = AiBetaRegistryView.registryOnlyDetails(registry, row.entry)
                        + "\n\nLettura mirata della scheda non riuscita; nessuna modifica effettuata.";
            } finally {
                AiBetaTestDialog.BUSY.set(false);
            }
            final AiBetaRegistryView.CurrentTargetRead selectedTarget = target;
            final AiBetaRegistryView.Match selectedMatch = match;
            final String selectedDetails = details;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                showEntryDetails(activity, registry, settings, draftState, draftButton,
                        row.entry, selectedTarget, selectedMatch, selectedDetails);
            });
        });
    }

    private static void showEntryDetails(Activity activity, AiBetaRegistry registry, AiBetaSettings settings,
                                         DraftState draftState, Button draftButton, AiBetaRegistry.Entry entry,
                                         AiBetaRegistryView.CurrentTargetRead target,
                                         AiBetaRegistryView.Match match, String details) {
        TextView body = new TextView(activity);
        body.setText(details);
        body.setTextSize(16);
        body.setTextColor(Color.rgb(225, 223, 236));
        body.setTextIsSelectable(true);
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        boolean allowed = match != null && AiBetaRegistryView.canPrepareCurrentTarget(entry, match, target)
                && draftState.error == null && draftState.draft == null;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Scheda proposta · ID " + entry.listingId)
                .setView(scroll)
                .setNegativeButton("Chiudi", null)
                .setPositiveButton("Scegli proposta", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button choose = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            choose.setEnabled(allowed);
            choose.setOnClickListener(v -> {
                dialog.dismiss();
                showChoices(activity, registry, entry, match, target, settings, draftState, draftButton);
            });
        });
        dialog.show();
    }

    private static void showChoices(Activity activity, AiBetaRegistry registry, AiBetaRegistry.Entry entry,
                                    AiBetaRegistryView.Match match,
                                    AiBetaRegistryView.CurrentTargetRead target, AiBetaSettings settings,
                                    DraftState draftState, Button draftButton) {
        List<AiBetaRegistryDraft.Choice> choices = AiBetaRegistryDraft.choices(entry, match.listing);
        if (choices.isEmpty() || !AiBetaRegistryView.canPrepareCurrentTarget(entry, match, target)
                || draftState.draft != null || draftState.error != null) {
            Toast.makeText(activity, "Bozza bloccata: controlla ID corrente, hash, stato della scheda e revisione.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) labels[i] = choices.get(i).label();
        new AlertDialog.Builder(activity)
                .setTitle("Scegli un solo campo e una sola provenienza")
                .setItems(labels, (dialog, which) -> showPreview(activity, registry, entry, match,
                        target, choices.get(which), settings, draftState, draftButton))
                .setNegativeButton("Annulla", null)
                .show();
    }

    private static void showPreview(Activity activity, AiBetaRegistry registry, AiBetaRegistry.Entry entry,
                                    AiBetaRegistryView.Match match,
                                    AiBetaRegistryView.CurrentTargetRead target,
                                    AiBetaRegistryDraft.Choice choice,
                                    AiBetaSettings settings, DraftState draftState, Button draftButton) {
        final AiBetaRegistryDraft.Draft preview;
        try {
            preview = AiBetaRegistryDraft.createCurrentTarget(registry, entry, match, choice,
                    target.uniqueCurrentItemId, target.active, target.manualReviewRequired,
                    choice.reviewRequired);
        } catch (Exception failure) {
            Toast.makeText(activity, "Proposta non preparabile: " + failure.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        String before = AiBetaRegistryView.valueLabel(preview.beforeValue);
        String after = preview.afterValue;
        String message = "Scheda attuale: " + AiBetaRegistryView.titleLabel(preview.title)
                + "\nID Vinted attuale: " + preview.itemId + " (non prova identità storica)"
                + "\nCampo: " + (AiBetaRegistryDraft.FIELD_CATEGORY.equals(preview.field) ? "categoria" : "lingua")
                + "\nPrima: " + before
                + "\nDopo: " + after
                + "\nProvenienza: " + preview.provenance + " · " + preview.source
                + "\nHash v3 corrente: " + preview.currentHash
                + (preview.reviewRequired ? "\nFlag dubbio: revisione umana richiesta; confermando dichiari di aver esaminato la proposta." : "")
                + "\nID e hash saranno ricontrollati nella transazione al momento dell’applicazione."
                + "\nQuesto controllo non dimostra retroattivamente l’identità storica."
                + "\nConfermare salva solo una bozza privata cifrata; nessuna scrittura al catalogo.";
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Anteprima prima/dopo")
                .setMessage(message)
                .setNegativeButton("Indietro", null)
                .setPositiveButton(preview.reviewRequired ? "Confermo la revisione · salva bozza" : "Salva bozza locale", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> saveDraft(activity, entry, preview, settings,
                        draftState, draftButton, dialog)));
        dialog.show();
    }

    private static void saveDraft(Activity activity, AiBetaRegistry.Entry entry,
                                  AiBetaRegistryDraft.Draft preview, AiBetaSettings settings,
                                  DraftState draftState, Button draftButton, AlertDialog previewDialog) {
        if (!AiBetaTestDialog.BUSY.compareAndSet(false, true)) return;
        previewDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        AiBetaTestDialog.IO.execute(() -> {
            String error = null;
            boolean savedDraft = false;
            try {
                JSONObject saved = settings.load();
                if (saved.has(AiBetaRegistryDraft.SETTINGS_KEY)) {
                    throw new IllegalStateException("Esiste già una bozza locale; annullala prima di crearne un’altra.");
                }
                AiBetaRegistryView.CurrentTargetRead fresh = AiBetaRegistryView.readCurrentTarget(activity, preview.localId);
                AiBetaRegistryView.Match current = AiBetaRegistryView.match(entry, fresh.catalog);
                if (!AiBetaRegistryView.canPrepareCurrentTarget(entry, current, fresh)
                        || !preview.stillMatches(current.listing)) {
                    throw new IllegalStateException("ID corrente o hash cambiati dopo l’anteprima; bozza non salvata.");
                }
                AiBetaRegistryDraft.store(saved, preview);
                settings.save(saved);
                savedDraft = true;
            } catch (Exception failure) {
                error = failure.getMessage() == null ? "Bozza non salvata." : failure.getMessage();
            } finally {
                AiBetaTestDialog.BUSY.set(false);
            }
            final boolean success = savedDraft;
            final String message = error;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (success) {
                    draftState.draft = preview;
                    draftState.error = null;
                    updateDraftButton(draftButton, draftState);
                    previewDialog.dismiss();
                    Toast.makeText(activity, "Bozza privata salvata. Il catalogo è invariato.", Toast.LENGTH_LONG).show();
                } else {
                    previewDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    new AlertDialog.Builder(activity).setTitle("Bozza non salvata")
                            .setMessage(message).setPositiveButton("Chiudi", null).show();
                }
            });
        });
    }

    private static void showDraft(Activity activity, AiBetaSettings settings, DraftState draftState,
                                  Button draftButton) {
        if (draftState.error != null) {
            Toast.makeText(activity, draftState.error, Toast.LENGTH_LONG).show();
            return;
        }
        AiBetaRegistryDraft.Draft draft = draftState.draft;
        if (draft == null || !AiBetaTestDialog.BUSY.compareAndSet(false, true)) return;
        AiBetaTestDialog.IO.execute(() -> {
            AiBetaRegistryView.CurrentTargetRead target = AiBetaRegistryView.readCurrentTarget(activity, draft.localId);
            AiBetaRegistryView.LocalListing listing = target.catalog.byLocalId.get(draft.localId);
            AiBetaRegistryDraft.Draft reconciled = draft;
            if (listing != null) reconciled = AiBetaRegistryApply.reconcile(draft,
                    new AiBetaRegistryApply.Current(listing, target.active ? "ACTIVE" : "INACTIVE",
                            target.manualReviewRequired));
            String error = null;
            if (!sameState(draft, reconciled)) {
                try {
                    JSONObject saved = settings.load();
                    AiBetaRegistryDraft.Draft persisted = AiBetaRegistryDraft.load(saved);
                    if (persisted == null || !persisted.sameProposal(draft))
                        throw new IllegalStateException("Bozza cambiata durante la verifica");
                    AiBetaRegistryDraft.replace(saved, reconciled);
                    settings.save(saved);
                } catch (Exception failure) {
                    error = "Stato recuperato in memoria ma non salvato; nessuna nuova azione consentita.";
                }
            }
            final AiBetaRegistryDraft.Draft currentDraft = reconciled;
            final String recoveryError = error;
            AiBetaTestDialog.BUSY.set(false);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                draftState.draft = currentDraft;
                if (recoveryError != null) draftState.error = recoveryError;
                String currentStatus;
                if (target.catalog.state != AiBetaRegistryView.CatalogState.AVAILABLE)
                    currentStatus = target.catalog.state == AiBetaRegistryView.CatalogState.DATABASE_MISSING
                            ? "database locale non presente" : "lettura mirata non riuscita";
                else if (listing == null) currentStatus = "scheda locale non trovata";
                else if (draftStillApplied(currentDraft, listing, target.uniqueCurrentItemId)) currentStatus = "applicata e hash post-correzione invariato";
                else if (draftStillRestored(currentDraft, listing)) currentStatus = "ripristinata e hash invariato";
                else if (currentDraft.stillMatches(listing) && target.uniqueCurrentItemId
                        && target.active && !target.manualReviewRequired) currentStatus = "ID e hash invariati rispetto alla bozza";
                else currentStatus = "dati o identità corrente cambiati; applicazione/ripristino bloccati";
                showDraftDialog(activity, settings, draftState, draftButton, target, listing, currentStatus);
            });
        });
    }

    private static void showDraftDialog(Activity activity, AiBetaSettings settings, DraftState draftState,
                                        Button draftButton, AiBetaRegistryView.CurrentTargetRead target,
                                        AiBetaRegistryView.LocalListing listing, String currentStatus) {
        AiBetaRegistryDraft.Draft draft = draftState.draft;
        if (draft == null) return;
        boolean canApply = draft.canApply() && listing != null && draft.stillMatches(listing)
                && target.uniqueCurrentItemId && target.active && !target.manualReviewRequired
                && draftState.error == null;
        boolean canRestore = draftStillApplied(draft, listing, target.uniqueCurrentItemId)
                && draftState.error == null;
        boolean canDelete = AiBetaRegistryDraft.STAGED.equals(draft.state)
                || AiBetaRegistryDraft.RESTORED.equals(draft.state);
        boolean canDeleteNeutral = AiBetaRegistryDraft.STAGED.equals(draft.state);
        String action = canApply ? "Applica correzione" : canRestore ? "Ripristina valore precedente"
                : canDelete ? "Elimina bozza" : "Chiudi";
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("Bozza · prima/dopo conservati")
                .setMessage(draft.details(currentStatus))
                .setNegativeButton("Chiudi", null)
                .setPositiveButton(action, null);
        if (canDeleteNeutral) builder.setNeutralButton("Elimina bozza", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (canApply) positive.setOnClickListener(v -> showApplyConfirmation(activity, settings,
                    draftState, draftButton, draft, dialog));
            else if (canRestore) positive.setOnClickListener(v -> showRestoreConfirmation(activity, settings,
                    draftState, draftButton, draft, dialog));
            else if (canDelete && AiBetaRegistryDraft.RESTORED.equals(draft.state))
                positive.setOnClickListener(v -> clearDraft(activity, settings, draftState, draftButton, dialog));
            else if (!canDelete) positive.setEnabled(false);
            if (canDeleteNeutral) dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> clearDraft(activity, settings, draftState, draftButton, dialog));
        });
        dialog.show();
    }

    private static void showApplyConfirmation(Activity activity, AiBetaSettings settings,
                                             DraftState draftState, Button draftButton,
                                             AiBetaRegistryDraft.Draft draft, AlertDialog parent) {
        String message = "Scheda: " + AiBetaRegistryView.titleLabel(draft.title)
                + "\nID Vinted attuale: " + draft.itemId
                + "\nCampo: " + draft.field + " · " + draft.beforeLabel() + " → " + draft.afterValue
                + "\nProvenienza: " + draft.provenance + " · " + draft.source
                + "\nHash v3 atteso: " + draft.currentHash
                + "\nLa transazione rileggerà ID e hash e bloccherà la scrittura se differiscono."
                + "\nIdentità storica: non dimostrata. Verrà modificato un solo campo di una sola scheda.";
        new AlertDialog.Builder(activity).setTitle("Conferma questa correzione")
                .setMessage(message).setNegativeButton("Non applicare", null)
                .setPositiveButton("Applica questa sola correzione", (d, which) -> runApply(activity,
                        settings, draftState, draftButton, draft, parent)).show();
    }

    private static void showRestoreConfirmation(Activity activity, AiBetaSettings settings,
                                               DraftState draftState, Button draftButton,
                                               AiBetaRegistryDraft.Draft draft, AlertDialog parent) {
        new AlertDialog.Builder(activity).setTitle("Ripristina il valore precedente?")
                .setMessage("La scheda verrà riletta nella transazione. Il ripristino procede solo se ID e hash post-correzione sono invariati.\n\n"
                        + AiBetaRegistryView.titleLabel(draft.title) + " · " + draft.itemId
                        + "\n" + draft.afterValue + " → " + draft.beforeLabel()
                        + "\nHash post-correzione: " + draft.appliedHash)
                .setNegativeButton("Mantieni correzione", null)
                .setPositiveButton("Ripristina", (d, which) -> runRestore(activity,
                        settings, draftState, draftButton, draft, parent)).show();
    }

    private static void runApply(Activity activity, AiBetaSettings settings, DraftState draftState,
                                Button draftButton, AiBetaRegistryDraft.Draft draft, AlertDialog parent) {
        runDatabaseChange(activity, settings, draftState, draftButton, draft, parent, true);
    }

    private static void runRestore(Activity activity, AiBetaSettings settings, DraftState draftState,
                                  Button draftButton, AiBetaRegistryDraft.Draft draft, AlertDialog parent) {
        runDatabaseChange(activity, settings, draftState, draftButton, draft, parent, false);
    }

    private static void runDatabaseChange(Activity activity, AiBetaSettings settings, DraftState draftState,
                                          Button draftButton, AiBetaRegistryDraft.Draft draft,
                                          AlertDialog parent, boolean apply) {
        if (!AiBetaTestDialog.BUSY.compareAndSet(false, true)) return;
        AiBetaTestDialog.IO.execute(() -> {
            AiBetaRegistryDraft.Draft result = null;
            String message = null;
            boolean changed = false;
            try {
                JSONObject saved = settings.load();
                AiBetaRegistryDraft.Draft persisted = AiBetaRegistryDraft.load(saved);
                if (persisted == null || !persisted.sameProposal(draft) || !persisted.state.equals(draft.state))
                    throw new IllegalStateException("La bozza è cambiata; nessuna scrittura eseguita.");
                AiBetaRegistryApply.Journal journal = update -> {
                    JSONObject current = settings.load();
                    AiBetaRegistryDraft.Draft stored = AiBetaRegistryDraft.load(current);
                    if (stored == null || !stored.sameProposal(draft))
                        throw new IllegalStateException("La bozza cifrata è cambiata; transazione annullata.");
                    AiBetaRegistryDraft.replace(current, update);
                    settings.save(current);
                };
                try (android.database.sqlite.SQLiteDatabase db = AiBetaRegistryApply.openWritableCatalog(activity)) {
                    result = apply ? AiBetaRegistryApply.apply(db, draft, journal)
                            : AiBetaRegistryApply.restore(db, draft, journal);
                }
                changed = true;
                message = apply ? "Correzione applicata; prima/dopo conservati per il ripristino."
                        : "Valore precedente ripristinato; cronologia prima/dopo conservata.";
            } catch (AiBetaRegistryApply.JournalPendingException pending) {
                result = pending.recoverableDraft;
                message = pending.getMessage();
                changed = true;
            } catch (Exception failure) {
                message = failure.getMessage() == null ? "Operazione bloccata; controlla lo stato della scheda." : failure.getMessage();
                try { result = AiBetaRegistryDraft.load(settings.load()); } catch (Exception ignored) { }
            } finally {
                AiBetaTestDialog.BUSY.set(false);
            }
            final AiBetaRegistryDraft.Draft finalDraft = result;
            final String finalMessage = message;
            final boolean finalChanged = changed;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (finalDraft != null) draftState.draft = finalDraft;
                updateDraftButton(draftButton, draftState);
                if (finalChanged) parent.dismiss();
                Toast.makeText(activity, finalMessage, Toast.LENGTH_LONG).show();
            });
        });
    }

    private static boolean sameState(AiBetaRegistryDraft.Draft left, AiBetaRegistryDraft.Draft right) {
        return left != null && right != null && left.state.equals(right.state)
                && same(left.appliedHash, right.appliedHash) && same(left.restoredHash, right.restoredHash);
    }

    private static boolean sameJournalEntry(AiBetaRegistryDraft.Draft draft,
                                            AiBetaRegistry registry, AiBetaRegistry.Entry entry) {
        return draft != null && registry != null && entry != null
                && same(draft.registryId, registry.registryId)
                && same(draft.snapshotId, registry.sourceSnapshotId)
                && draft.listingId == entry.listingId;
    }

    private static boolean draftStillApplied(AiBetaRegistryDraft.Draft draft,
                                             AiBetaRegistryView.LocalListing listing, boolean uniqueItemId) {
        return draft != null && listing != null && uniqueItemId
                && AiBetaRegistryDraft.APPLIED.equals(draft.state) && draft.appliedHash != null
                && same(draft.itemId, listing.itemId) && same(draft.appliedHash, listing.sourceHash)
                && same(draft.afterValue, AiBetaRegistryDraft.FIELD_CATEGORY.equals(draft.field)
                ? listing.category : listing.language);
    }

    private static boolean draftStillRestored(AiBetaRegistryDraft.Draft draft,
                                             AiBetaRegistryView.LocalListing listing) {
        return draft != null && listing != null && AiBetaRegistryDraft.RESTORED.equals(draft.state)
                && draft.restoredHash != null && same(draft.itemId, listing.itemId)
                && same(draft.restoredHash, listing.sourceHash)
                && same(draft.beforeValue, AiBetaRegistryDraft.FIELD_CATEGORY.equals(draft.field)
                ? listing.category : listing.language);
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void clearDraft(Activity activity, AiBetaSettings settings, DraftState draftState,
                                   Button draftButton, AlertDialog dialog) {
        if (draftState.draft == null || (!AiBetaRegistryDraft.STAGED.equals(draftState.draft.state)
                && !AiBetaRegistryDraft.RESTORED.equals(draftState.draft.state))) {
            Toast.makeText(activity, "Bozza conservata: prima/dopo non verranno eliminati mentre la correzione è applicata o in conflitto.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!AiBetaTestDialog.BUSY.compareAndSet(false, true)) return;
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
        AiBetaTestDialog.IO.execute(() -> {
            String error = null;
            boolean cleared = false;
            try {
                JSONObject saved = settings.load();
                AiBetaRegistryDraft.clear(saved);
                settings.save(saved);
                cleared = true;
            } catch (Exception failure) {
                error = "Bozza privata non rimossa; il catalogo è invariato.";
            } finally {
                AiBetaTestDialog.BUSY.set(false);
            }
            final boolean success = cleared;
            final String message = error;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (success) {
                    draftState.draft = null;
                    draftState.error = null;
                    updateDraftButton(draftButton, draftState);
                    dialog.dismiss();
                    Toast.makeText(activity, "Bozza annullata. Il catalogo non è stato modificato.", Toast.LENGTH_LONG).show();
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    new AlertDialog.Builder(activity).setTitle("Bozza non rimossa")
                            .setMessage(message).setPositiveButton("Chiudi", null).show();
                }
            });
        });
    }

    private static void updateDraftButton(Button button, DraftState state) {
        if (state.error != null) {
            button.setText("Bozza privata non leggibile · non sovrascritta");
            button.setEnabled(false);
        } else if (state.draft != null) {
            button.setText("Bozza " + state.draft.state + ": " + state.draft.summary() + " · tocca per verificare");
            button.setEnabled(true);
        } else {
            button.setText("Nessuna bozza locale salvata");
            button.setEnabled(false);
        }
    }
}
