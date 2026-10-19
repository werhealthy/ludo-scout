package it.vintedaffari.app;

import android.app.Activity;
import android.app.AlertDialog;
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
            AiBetaRegistryView.CatalogRead catalog = AiBetaRegistryView.readCurrentCatalog(activity, registry);
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
                showList(activity, registry, catalog, settings, draftState);
            });
        });
    }

    private static void showList(Activity activity, AiBetaRegistry registry,
                                 AiBetaRegistryView.CatalogRead catalog, AiBetaSettings settings,
                                 DraftState draftState) {
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = Math.round(14 * density);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        TextView heading = new TextView(activity);
        heading.setText(AiBetaRegistryView.header(registry, catalog));
        heading.setTextSize(13);
        heading.setTextColor(Color.rgb(225, 223, 236));
        content.addView(heading);

        Button draftButton = new Button(activity);
        updateDraftButton(draftButton, draftState);
        content.addView(draftButton);
        draftButton.setOnClickListener(v -> showDraft(activity, settings, catalog, draftState, draftButton));

        EditText filter = new EditText(activity);
        filter.setSingleLine(true);
        filter.setHint("Filtra per ID, titolo, metadati o stato");
        content.addView(filter);

        List<RegistryRow> rows = new ArrayList<>();
        String[] displayRows = AiBetaRegistryView.rows(registry, catalog);
        for (int i = 0; i < registry.entries.size(); i++) {
            AiBetaRegistry.Entry entry = registry.entries.get(i);
            rows.add(new RegistryRow(entry, AiBetaRegistryView.match(entry, catalog), displayRows[i]));
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
                .setTitle("Registro AI · confronto locale")
                .setView(content)
                .setNegativeButton("Chiudi", null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            RegistryRow selected = adapter.getItem(position);
            if (selected != null) openEntry(activity, registry, catalog, settings, draftState,
                    draftButton, selected);
        });
        listDialog.show();
    }

    private static void openEntry(Activity activity, AiBetaRegistry registry,
                                  AiBetaRegistryView.CatalogRead catalog, AiBetaSettings settings,
                                  DraftState draftState, Button draftButton, RegistryRow row) {
        TextView body = new TextView(activity);
        body.setText(AiBetaRegistryView.details(registry, row.entry, catalog));
        body.setTextSize(16);
        body.setTextColor(Color.rgb(225, 223, 236));
        body.setTextIsSelectable(true);
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Scheda proposta · ID " + row.entry.listingId)
                .setView(scroll)
                .setNegativeButton("Chiudi", null)
                .setPositiveButton("Scegli proposta", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button choose = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            boolean allowed = row.match.isVerified() && !"UNRESOLVED".equals(row.entry.recordState)
                    && !AiBetaRegistryDraft.choices(row.entry, row.match.listing).isEmpty()
                    && draftState.error == null && draftState.draft == null;
            choose.setEnabled(allowed);
            choose.setOnClickListener(v -> {
                dialog.dismiss();
                showChoices(activity, registry, row.entry, row.match, settings, draftState, draftButton);
            });
        });
        dialog.show();
    }

    private static void showChoices(Activity activity, AiBetaRegistry registry, AiBetaRegistry.Entry entry,
                                    AiBetaRegistryView.Match match, AiBetaSettings settings,
                                    DraftState draftState, Button draftButton) {
        List<AiBetaRegistryDraft.Choice> choices = AiBetaRegistryDraft.choices(entry, match.listing);
        if (choices.isEmpty() || !match.isVerified() || draftState.draft != null || draftState.error != null) {
            Toast.makeText(activity, "Accettazione bloccata: serve una corrispondenza verificata e una proposta valida.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) labels[i] = choices.get(i).label();
        new AlertDialog.Builder(activity)
                .setTitle("Scegli un solo campo e una sola provenienza")
                .setItems(labels, (dialog, which) -> showPreview(activity, registry, entry, match,
                        choices.get(which), settings, draftState, draftButton))
                .setNegativeButton("Annulla", null)
                .show();
    }

    private static void showPreview(Activity activity, AiBetaRegistry registry, AiBetaRegistry.Entry entry,
                                    AiBetaRegistryView.Match match, AiBetaRegistryDraft.Choice choice,
                                    AiBetaSettings settings, DraftState draftState, Button draftButton) {
        final AiBetaRegistryDraft.Draft preview;
        try {
            preview = AiBetaRegistryDraft.create(registry, entry, match, choice);
        } catch (Exception failure) {
            Toast.makeText(activity, "Proposta non preparabile: " + failure.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        String before = AiBetaRegistryView.valueLabel(preview.beforeValue);
        String after = preview.afterValue;
        String message = "Scheda locale: " + AiBetaRegistryView.titleLabel(preview.title)
                + "\nID item esterno verificato: " + preview.itemId
                + "\nCampo: " + (AiBetaRegistryDraft.FIELD_CATEGORY.equals(preview.field) ? "categoria" : "lingua")
                + "\nPrima: " + before
                + "\nDopo: " + after
                + "\nProvenienza: " + preview.provenance + " · " + preview.source
                + "\nHash dell’inserzione non conservato: contenuto attuale rispetto all’analisi storica non verificabile."
                + "\nConfermare salva solo una bozza privata cifrata; il catalogo resta invariato.";
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Anteprima prima/dopo")
                .setMessage(message)
                .setNegativeButton("Indietro", null)
                .setPositiveButton("Salva bozza locale", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> saveDraft(activity, registry, entry, preview, settings,
                        draftState, draftButton, dialog)));
        dialog.show();
    }

    private static void saveDraft(Activity activity, AiBetaRegistry registry, AiBetaRegistry.Entry entry,
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
                AiBetaRegistryView.CatalogRead fresh = AiBetaRegistryView.readByItemId(activity, preview.itemId);
                AiBetaRegistryView.Match current = AiBetaRegistryView.match(entry, fresh);
                if (!current.isVerified()) {
                    throw new IllegalStateException("Corrispondenza non più verificabile; bozza non salvata.");
                }
                if (!preview.stillMatches(current.listing)) {
                    throw new IllegalStateException("I dati locali sono cambiati dopo l’anteprima; riapri la scheda e verifica i valori aggiornati.");
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

    private static void showDraft(Activity activity, AiBetaSettings settings,
                                  AiBetaRegistryView.CatalogRead catalog, DraftState draftState,
                                  Button draftButton) {
        if (draftState.error != null) {
            Toast.makeText(activity, draftState.error, Toast.LENGTH_LONG).show();
            return;
        }
        AiBetaRegistryDraft.Draft draft = draftState.draft;
        if (draft == null) return;
        String currentStatus = "non verificabili";
        if (catalog.state == AiBetaRegistryView.CatalogState.AVAILABLE) {
            List<AiBetaRegistryView.LocalListing> matches = catalog.byItemId.get(draft.itemId);
            if (matches == null || matches.isEmpty()) currentStatus = "ID esterno non trovato";
            else if (matches.size() > 1) currentStatus = "associazione ambigua";
            else currentStatus = draft.stillMatches(matches.get(0)) ? "invariati rispetto alla bozza" : "cambiati; bozza da rivedere";
        } else if (catalog.state == AiBetaRegistryView.CatalogState.QUERY_FAILED) {
            currentStatus = "query non riuscita";
        } else {
            currentStatus = "database locale non presente";
        }
        String message = draft.details(currentStatus);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Bozza locale · valore precedente conservato")
                .setMessage(message)
                .setNegativeButton("Chiudi", null)
                .setPositiveButton("Annulla bozza", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> clearDraft(activity, settings, draftState, draftButton, dialog)));
        dialog.show();
    }

    private static void clearDraft(Activity activity, AiBetaSettings settings, DraftState draftState,
                                   Button draftButton, AlertDialog dialog) {
        if (!AiBetaTestDialog.BUSY.compareAndSet(false, true)) return;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
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
            button.setText("Bozza locale: " + state.draft.summary() + " · tocca per vedere/annullare");
            button.setEnabled(true);
        } else {
            button.setText("Nessuna bozza locale salvata");
            button.setEnabled(false);
        }
    }
}
