package it.vintedaffari.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only registry overlay. Numeric local IDs are candidates, never proof of identity. */
final class AiBetaRegistryView {
    private static final int QUERY_CHUNK = 400;
    private static final String COLUMNS = "id,vinted_item_id,vinted_title,category_normalized,language_code";

    enum CatalogState { AVAILABLE, DATABASE_MISSING, QUERY_FAILED }
    enum MatchState {
        VERIFIED,
        UNVERIFIED_NUMERIC_CANDIDATE,
        UNVERIFIED_NO_CANDIDATE,
        EXTERNAL_ID_NOT_FOUND,
        AMBIGUOUS,
        DATABASE_MISSING,
        QUERY_FAILED
    }

    static final class LocalListing {
        final long localId;
        final String itemId;
        final String title;
        final String category;
        final String language;

        LocalListing(long localId, String itemId, String title, String category, String language) {
            this.localId = localId;
            this.itemId = itemId;
            this.title = title;
            this.category = category;
            this.language = language;
        }
    }

    static final class CatalogRead {
        final CatalogState state;
        final Map<Long, LocalListing> byLocalId;
        final Map<String, List<LocalListing>> byItemId;

        private CatalogRead(CatalogState state, List<LocalListing> rows) {
            this.state = state;
            Map<Long, LocalListing> local = new LinkedHashMap<>();
            Map<String, List<LocalListing>> external = new HashMap<>();
            for (LocalListing row : rows) {
                local.put(row.localId, row);
                if (row.itemId != null && !row.itemId.isEmpty()) {
                    List<LocalListing> matches = external.get(row.itemId);
                    if (matches == null) {
                        matches = new ArrayList<>();
                        external.put(row.itemId, matches);
                    }
                    boolean alreadyAdded = false;
                    for (LocalListing match : matches) {
                        if (match.localId == row.localId) {
                            alreadyAdded = true;
                            break;
                        }
                    }
                    if (!alreadyAdded) matches.add(row);
                }
            }
            this.byLocalId = Collections.unmodifiableMap(local);
            Map<String, List<LocalListing>> frozen = new HashMap<>();
            for (Map.Entry<String, List<LocalListing>> entry : external.entrySet()) {
                frozen.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            this.byItemId = Collections.unmodifiableMap(frozen);
        }

        static CatalogRead available(List<LocalListing> rows) {
            return new CatalogRead(CatalogState.AVAILABLE, rows == null ? Collections.emptyList() : rows);
        }

        static CatalogRead available(LocalListing... rows) {
            return available(Arrays.asList(rows));
        }

        static CatalogRead databaseMissing() {
            return new CatalogRead(CatalogState.DATABASE_MISSING, Collections.emptyList());
        }

        static CatalogRead queryFailed() {
            return new CatalogRead(CatalogState.QUERY_FAILED, Collections.emptyList());
        }
    }

    static final class Match {
        final MatchState state;
        final LocalListing listing;

        Match(MatchState state, LocalListing listing) {
            this.state = state;
            this.listing = listing;
        }

        boolean isVerified() {
            return state == MatchState.VERIFIED && listing != null;
        }
    }

    private AiBetaRegistryView() { }

    static CatalogRead readCurrentCatalog(Context context, AiBetaRegistry registry) {
        File file = context.getDatabasePath("vinted_affari.db");
        if (!file.isFile()) return CatalogRead.databaseMissing();
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            Set<Long> localIds = new LinkedHashSet<>();
            Set<String> itemIds = new LinkedHashSet<>();
            for (AiBetaRegistry.Entry entry : registry.entries) {
                localIds.add(entry.listingId);
                if (entry.itemId != null) itemIds.add(entry.itemId);
            }
            List<LocalListing> rows = new ArrayList<>();
            queryChunks(db, "id", localIds, rows);
            queryChunks(db, "vinted_item_id", itemIds, rows);
            return CatalogRead.available(rows);
        } catch (Exception failure) {
            return CatalogRead.queryFailed();
        } finally {
            if (db != null) db.close();
        }
    }

    static CatalogRead readByItemId(Context context, String itemId) {
        if (itemId == null || itemId.isEmpty()) return CatalogRead.queryFailed();
        File file = context.getDatabasePath("vinted_affari.db");
        if (!file.isFile()) return CatalogRead.databaseMissing();
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            List<LocalListing> rows = new ArrayList<>();
            queryChunks(db, "vinted_item_id", Collections.singleton(itemId), rows);
            return CatalogRead.available(rows);
        } catch (Exception failure) {
            return CatalogRead.queryFailed();
        } finally {
            if (db != null) db.close();
        }
    }

    private static void queryChunks(SQLiteDatabase db, String column, Set<?> values, List<LocalListing> output) {
        if (values.isEmpty()) return;
        List<?> all = new ArrayList<>(values);
        for (int start = 0; start < all.size(); start += QUERY_CHUNK) {
            int end = Math.min(all.size(), start + QUERY_CHUNK);
            StringBuilder placeholders = new StringBuilder();
            String[] args = new String[end - start];
            for (int i = start; i < end; i++) {
                if (i > start) placeholders.append(',');
                placeholders.append('?');
                args[i - start] = String.valueOf(all.get(i));
            }
            String sql = "SELECT " + COLUMNS + " FROM market_listings WHERE " + column
                    + " IN (" + placeholders + ")";
            try (Cursor cursor = db.rawQuery(sql, args)) {
                while (cursor.moveToNext()) {
                    output.add(new LocalListing(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                            cursor.getString(3), cursor.getString(4)));
                }
            }
        }
    }

    static Match resolve(AiBetaRegistry.Entry entry, CatalogRead read) {
        if (read == null || read.state == CatalogState.QUERY_FAILED) return new Match(MatchState.QUERY_FAILED, null);
        if (read.state == CatalogState.DATABASE_MISSING) return new Match(MatchState.DATABASE_MISSING, null);
        if (entry.itemId != null) {
            List<LocalListing> matches = read.byItemId.get(entry.itemId);
            if (matches == null || matches.isEmpty()) return new Match(MatchState.EXTERNAL_ID_NOT_FOUND, null);
            if (matches.size() != 1) return new Match(MatchState.AMBIGUOUS, null);
            return new Match(MatchState.VERIFIED, matches.get(0));
        }
        LocalListing candidate = read.byLocalId.get(entry.listingId);
        return candidate == null
                ? new Match(MatchState.UNVERIFIED_NO_CANDIDATE, null)
                : new Match(MatchState.UNVERIFIED_NUMERIC_CANDIDATE, candidate);
    }

    static String header(AiBetaRegistry registry) {
        return header(registry, null);
    }

    static String header(AiBetaRegistry registry, CatalogRead read) {
        String query = read == null ? "non ancora eseguita" : catalogStateLabel(read.state);
        int itemIdRecords = 0;
        int verified = 0;
        int unverified = 0;
        int noCandidate = 0;
        int externalNotFound = 0;
        int ambiguous = 0;
        for (AiBetaRegistry.Entry entry : registry.entries) {
            if (entry.itemId != null) itemIdRecords++;
        }
        boolean matchesEvaluated = read != null && read.state == CatalogState.AVAILABLE;
        if (matchesEvaluated) {
            for (AiBetaRegistry.Entry entry : registry.entries) {
                Match match = resolve(entry, read);
                if (match.isVerified()) verified++;
                else if (match.state == MatchState.UNVERIFIED_NUMERIC_CANDIDATE) unverified++;
                else if (match.state == MatchState.UNVERIFIED_NO_CANDIDATE) noCandidate++;
                else if (match.state == MatchState.EXTERNAL_ID_NOT_FOUND) externalNotFound++;
                else if (match.state == MatchState.AMBIGUOUS) ambiguous++;
            }
        }
        return "Registro AI · " + registry.registryId
                + "\nSnapshot: " + registry.sourceSnapshotId + " · esportato il " + registry.generatedAt
                + "\nModello: " + (registry.modelId == null ? "non registrato nella fonte" : registry.modelId)
                + "\nPrompt storico: " + (registry.historicalPromptId == null ? "non conservato" : registry.historicalPromptId)
                + "\nA/B corretto: " + registry.correctedAbPromptId
                + "\nCandidato prompt " + registry.candidatePromptCommit.substring(0, 8) + ": non eseguito su questi record"
                + "\nQuery catalogo locale: " + query
                + "\nIdentità: solo item_id esterno esatto verifica la corrispondenza; ID numerico locale non basta."
                + "\nHash inserzione assente dal registro: contenuto rispetto allo snapshot non verificabile."
                + "\nID esterno nel registro: " + itemIdRecords + "/" + registry.entries.size()
                + " · " + (matchesEvaluated
                    ? "corrispondenze verificate: " + verified
                        + " · candidati numerici non verificati: " + unverified
                        + " · ID locale assente: " + noCandidate
                        + " · ID esterno non trovato: " + externalNotFound
                        + " · associazioni ambigue: " + ambiguous
                    : "corrispondenze non valutabili finché la query non è completata")
                + "\nAI — non verificato. Nessuna proposta è stata scritta nel catalogo.\nRecord: " + registry.entries.size();
    }

    static String[] rows(Context context, AiBetaRegistry registry) {
        return rows(registry, readCurrentCatalog(context, registry));
    }

    static String[] rows(AiBetaRegistry registry, CatalogRead read) {
        String[] rows = new String[registry.entries.size()];
        for (int i = 0; i < registry.entries.size(); i++) {
            AiBetaRegistry.Entry entry = registry.entries.get(i);
            Match match = resolve(entry, read);
            StringBuilder row = new StringBuilder("ID registro ").append(entry.listingId)
                    .append(" · ").append(matchLabel(match.state));
            if (match.listing != null) {
                row.append("\n").append(match.isVerified() ? "Scheda locale: " : "Candidato locale (non verificato): ")
                        .append(titleLabel(match.listing.title));
                row.append("\n").append(match.isVerified() ? "Attuale" : "Dati del candidato")
                        .append(": categoria ").append(valueLabel(match.listing.category))
                        .append(" · lingua ").append(valueLabel(match.listing.language));
            }
            row.append("\nOriginale (").append(sourceLabel(entry.originalSource)).append("): ")
                    .append(proposal(entry.originalType, entry.originalLanguage));
            if (entry.correctedSource == null) {
                row.append("\nA/B corretto: non disponibile per questo record");
            } else {
                row.append("\nA/B corretto (").append(entry.correctedSource).append("): ")
                        .append(proposal(entry.correctedType, entry.correctedLanguage));
            }
            row.append("\nRevisione: ").append(reviewLabel(entry));
            rows[i] = row.toString();
        }
        return rows;
    }

    static Match match(AiBetaRegistry.Entry entry, CatalogRead read) {
        return resolve(entry, read);
    }

    static String details(AiBetaRegistry registry, AiBetaRegistry.Entry entry, CatalogRead read) {
        Match match = resolve(entry, read);
        StringBuilder text = new StringBuilder();
        text.append("ID registro: ").append(entry.listingId)
                .append("\nEsito lettura catalogo: ").append(catalogStateLabel(read == null ? CatalogState.QUERY_FAILED : read.state))
                .append("\nCorrispondenza: ").append(matchDescription(match));
        if (match.listing != null) {
            text.append("\n").append(match.isVerified() ? "Scheda locale" : "Candidato locale, non associato")
                    .append(": ").append(titleLabel(match.listing.title))
                    .append("\nID locale: ").append(match.listing.localId);
            if (match.isVerified()) text.append(" · item_id: ").append(match.listing.itemId);
            text.append("\n").append(match.isVerified() ? "Metadati attuali" : "Metadati del candidato (non verificati)")
                    .append(": categoria ").append(valueLabel(match.listing.category))
                    .append(" · lingua ").append(valueLabel(match.listing.language));
        }
        text.append("\nProposta originale (").append(sourceLabel(entry.originalSource)).append("): ")
                .append(proposal(entry.originalType, entry.originalLanguage));
        if (entry.correctedSource == null) {
            text.append("\nProposta A/B corretta: non disponibile per questo record (nessun risultato abbinato).");
        } else {
            text.append("\nProposta A/B corretta (").append(entry.correctedSource).append("): ")
                    .append(proposal(entry.correctedType, entry.correctedLanguage));
        }
        text.append("\nStato record: ").append(entry.recordState)
                .append("\nStato revisione: ").append(reviewLabel(entry))
                .append("\nProvenienza snapshot: ").append(registry.sourceSnapshotId)
                .append(" · registro generato il ").append(registry.generatedAt)
                .append("\nContenuto dell’inserzione rispetto allo snapshot: non verificabile; il registro non conserva l’hash.")
                .append("\nAccettazione: ").append(acceptanceLabel(entry, match));
        return text.toString();
    }

    static String acceptanceLabel(AiBetaRegistry.Entry entry, Match match) {
        if (match == null || !match.isVerified()) return "bloccata: corrispondenza non verificata";
        if (!"PROPOSED".equals(entry.recordState)) return "bloccata: record irrisolto";
        if (AiBetaRegistryDraft.choices(entry, match.listing).isEmpty()) return "bloccata: nessuna proposta diversa e accettabile (mancante, UNKNOWN o già attuale)";
        return "disponibile solo come bozza locale; nessuna modifica al catalogo";
    }

    private static String matchLabel(MatchState state) {
        switch (state) {
            case VERIFIED: return "corrispondenza verificata";
            case UNVERIFIED_NUMERIC_CANDIDATE: return "corrispondenza non verificata";
            case UNVERIFIED_NO_CANDIDATE: return "corrispondenza non verificata · ID locale non trovato";
            case EXTERNAL_ID_NOT_FOUND: return "ID esterno non trovato · nessun fallback numerico";
            case AMBIGUOUS: return "ID esterno ambiguo · accettazione bloccata";
            case DATABASE_MISSING: return "database locale non presente";
            default: return "query locale non riuscita";
        }
    }

    private static String matchDescription(Match match) {
        switch (match.state) {
            case VERIFIED: return "verificata tramite item_id esterno esatto";
            case UNVERIFIED_NUMERIC_CANDIDATE: return "non verificata: solo coincidenza con ID numerico locale";
            case UNVERIFIED_NO_CANDIDATE: return "non verificata: registro senza item_id e nessun ID numerico locale trovato";
            case EXTERNAL_ID_NOT_FOUND: return "item_id esterno non trovato nel catalogo locale; nessun fallback sull’ID numerico";
            case AMBIGUOUS: return "item_id presente su più righe locali; associazione ambigua";
            case DATABASE_MISSING: return "catalogo locale non presente";
            default: return "query catalogo non riuscita; assenza e metadati non valutabili";
        }
    }

    private static String catalogStateLabel(CatalogState state) {
        switch (state) {
            case AVAILABLE: return "completata";
            case DATABASE_MISSING: return "database locale non presente";
            default: return "query non riuscita";
        }
    }

    private static String proposal(String type, String language) {
        return "categoria " + (type == null ? "non proposta" : type)
                + " · lingua " + (language == null ? "non proposta" : language);
    }

    static String valueLabel(String value) {
        return value == null || value.trim().isEmpty() ? "mancante" : value.trim();
    }

    static String titleLabel(String value) {
        return value == null || value.trim().isEmpty() ? "titolo mancante" : value.trim();
    }

    static String sourceLabel(String source) {
        return "paired_ab_baseline".equals(source)
                ? "baseline A/B storica"
                : "registro storico, prompt non conservato";
    }

    private static String reviewLabel(AiBetaRegistry.Entry entry) {
        if (entry.reviewFlags.isEmpty()) return "esito non conservato; nessun flag nel registro";
        StringBuilder label = new StringBuilder("esito non conservato; flag: ");
        for (int i = 0; i < entry.reviewFlags.size(); i++) {
            if (i > 0) label.append(", ");
            label.append(flagLabel(entry.reviewFlags.get(i)));
        }
        return label.toString();
    }

    private static String flagLabel(String flag) {
        if ("LANGUAGE_REANALYSIS".equals(flag)) return "rianalisi lingua";
        if ("CATEGORY_REVIEW".equals(flag)) return "revisione categoria";
        return "record irrisolto";
    }
}