package it.vintedaffari.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only overlay joining imported proposals to current local category/language metadata by ID. */
final class AiBetaRegistryView {
    private static final int QUERY_CHUNK = 400;

    static final class Metadata {
        final String category;
        final String language;
        Metadata(String category, String language) {
            this.category = category == null ? "" : category;
            this.language = language == null ? "" : language;
        }
    }

    private AiBetaRegistryView() { }

    static String render(Context context, AiBetaRegistry registry) throws Exception {
        Map<Long, Metadata> current = readCurrentMetadata(context, registry);
        return format(registry, current);
    }

    static String header(AiBetaRegistry registry) {
        return "Registro AI · " + registry.registryId + "\nSnapshot: " + registry.sourceSnapshotId
                + " · esportato il " + registry.generatedAt
                + "\nModello: " + (registry.modelId == null ? "non registrato nella fonte" : registry.modelId)
                + "\nPrompt storico: " + (registry.historicalPromptId == null ? "non conservato" : registry.historicalPromptId)
                + "\nA/B corretto: " + registry.correctedAbPromptId
                + "\nCandidato prompt " + registry.candidatePromptCommit.substring(0, 8) + ": non eseguito su questi record"
                + "\nAI — non verificato. Nessuna proposta è stata scritta nel catalogo.\nRecord: " + registry.entries.size();
    }

    static String[] rows(Context context, AiBetaRegistry registry) throws Exception {
        return rows(registry, readCurrentMetadata(context, registry));
    }

    static String[] rows(AiBetaRegistry registry, Map<Long, Metadata> current) {
        String[] rows = new String[registry.entries.size()];
        for (int i = 0; i < registry.entries.size(); i++) {
            AiBetaRegistry.Entry entry = registry.entries.get(i);
            Metadata metadata = current.get(entry.listingId);
            String currentValue = metadata == null ? "assente dal catalogo locale"
                    : valueOrDash(metadata.category) + " / " + valueOrDash(metadata.language);
            StringBuilder row = new StringBuilder("ID ").append(entry.listingId)
                    .append(" · attuale: ").append(currentValue)
                    .append("\nOriginale (").append(sourceLabel(entry.originalSource)).append("): ")
                    .append(proposal(entry.originalType, entry.originalLanguage));
            if (entry.correctedSource != null) row.append("\nA/B corretto (").append(entry.correctedSource).append("): ")
                    .append(proposal(entry.correctedType, entry.correctedLanguage));
            if (!entry.reviewFlags.isEmpty()) {
                row.append("\nDa revisionare: ");
                for (int flag = 0; flag < entry.reviewFlags.size(); flag++) {
                    if (flag > 0) row.append(", ");
                    row.append(flagLabel(entry.reviewFlags.get(flag)));
                }
            }
            if ("UNRESOLVED".equals(entry.recordState)) row.append("\nNessuna proposta disponibile: record irrisolto.");
            row.append("\nAI — non verificato");
            rows[i] = row.toString();
        }
        return rows;
    }

    static String format(AiBetaRegistry registry, Map<Long, Metadata> current) {
        StringBuilder text = new StringBuilder(Math.max(2048, registry.entries.size() * 170));
        text.append("Registro AI · ").append(registry.registryId)
                .append("\nSnapshot: ").append(registry.sourceSnapshotId)
                .append(" · esportato il ").append(registry.generatedAt)
                .append("\nModello: ").append(registry.modelId == null ? "non registrato nella fonte" : registry.modelId)
                .append("\nPrompt storico: ").append(registry.historicalPromptId == null ? "non conservato" : registry.historicalPromptId)
                .append("\nA/B corretto: ").append(registry.correctedAbPromptId)
                .append("\nCandidato prompt ").append(registry.candidatePromptCommit, 0, 8).append(": non eseguito su questi record")
                .append("\nAI — non verificato. Nessuna proposta è stata scritta nel catalogo.\n");
        int present = 0;
        int unresolved = 0;
        for (AiBetaRegistry.Entry entry : registry.entries) {
            if (current.containsKey(entry.listingId)) present++;
            if ("UNRESOLVED".equals(entry.recordState)) unresolved++;
        }
        text.append("Record: ").append(registry.entries.size())
                .append(" · presenti nel catalogo locale: ").append(present)
                .append(" · senza proposta risolta: ").append(unresolved).append("\n\n");
        for (AiBetaRegistry.Entry entry : registry.entries) {
            Metadata metadata = current.get(entry.listingId);
            text.append("ID ").append(entry.listingId).append("\nMetadati attuali: ");
            if (metadata == null) {
                text.append("annuncio non presente nel catalogo locale");
            } else {
                text.append("categoria ").append(valueOrDash(metadata.category))
                        .append(" · lingua ").append(valueOrDash(metadata.language));
            }
            text.append("\nOriginale (").append(sourceLabel(entry.originalSource)).append("): ")
                    .append(proposal(entry.originalType, entry.originalLanguage));
            if (entry.correctedSource == null) {
                text.append("\nPrompt corretto: non disponibile (non eseguito in A/B)");
            } else {
                text.append("\nPrompt corretto (").append(entry.correctedSource).append("): ")
                        .append(proposal(entry.correctedType, entry.correctedLanguage));
            }
            if (!entry.reviewFlags.isEmpty()) {
                text.append("\nDa revisionare: ");
                for (int i = 0; i < entry.reviewFlags.size(); i++) {
                    if (i > 0) text.append(", ");
                    text.append(flagLabel(entry.reviewFlags.get(i)));
                }
            }
            if ("UNRESOLVED".equals(entry.recordState)) text.append("\nNessuna proposta disponibile: record irrisolto.");
            text.append("\nAI — non verificato.\n\n");
        }
        return text.toString();
    }

    private static Map<Long, Metadata> readCurrentMetadata(Context context, AiBetaRegistry registry) throws Exception {
        Map<Long, Metadata> result = new HashMap<>();
        File database = context.getDatabasePath("vinted_affari.db");
        if (!database.isFile()) return result;
        SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            List<Long> ids = new ArrayList<>(registry.entries.size());
            for (AiBetaRegistry.Entry entry : registry.entries) ids.add(entry.listingId);
            for (int start = 0; start < ids.size(); start += QUERY_CHUNK) {
                int end = Math.min(ids.size(), start + QUERY_CHUNK);
                StringBuilder placeholders = new StringBuilder();
                String[] args = new String[end - start];
                for (int i = start; i < end; i++) {
                    if (i > start) placeholders.append(',');
                    placeholders.append('?');
                    args[i - start] = Long.toString(ids.get(i));
                }
                String sql = "SELECT id,COALESCE(category_normalized,''),COALESCE(language_code,'') FROM market_listings WHERE id IN (" + placeholders + ")";
                try (Cursor cursor = db.rawQuery(sql, args)) {
                    while (cursor.moveToNext()) {
                        result.put(cursor.getLong(0), new Metadata(cursor.getString(1), cursor.getString(2)));
                    }
                }
            }
        } finally {
            db.close();
        }
        return result;
    }

    private static String proposal(String type, String language) {
        if (type == null && language == null) return "nessuna proposta";
        return (type == null ? "—" : type) + " / " + (language == null ? "—" : language);
    }

    private static String valueOrDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    private static String sourceLabel(String source) {
        return "paired_ab_baseline".equals(source) ? "baseline A/B" : "registro storico, prompt non conservato";
    }

    private static String flagLabel(String flag) {
        if ("LANGUAGE_REANALYSIS".equals(flag)) return "rianalisi lingua";
        if ("CATEGORY_REVIEW".equals(flag)) return "revisione categoria";
        return "record irrisolto";
    }
}
