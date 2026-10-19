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
        CONTENT_MATCH_PROJECTION_PROVEN,
        CONTENT_MATCH_PROJECTION_UNPROVEN,
        DATA_CHANGED,
        LOCAL_ROW_ABSENT,
        CURRENT_ID_CANDIDATE_ONLY,
        SOURCE_ID_CONFLICT,
        EXTERNAL_ID_HASH_MISSING,
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
        final String sourceHash;

        LocalListing(long localId, String itemId, String title, String category, String language) {
            this(localId, itemId, title, category, language, null);
        }

        LocalListing(long localId, String itemId, String title, String category, String language,
                     String sourceHash) {
            this.localId = localId;
            this.itemId = itemId;
            this.title = title;
            this.category = category;
            this.language = language;
            this.sourceHash = sourceHash;
        }
    }

    static final class SnapshotProof {
        final String snapshotId;
        final String snapshotState;
        final String publishedContentHash;
        final String currentContentHash;
        final int protocolVersion;
        final boolean includesEvidence;
        final boolean metadataQueryFailed;

        SnapshotProof(FirestoreSnapshotStore.MetadataRead read, String currentContentHash) {
            FirestoreSnapshotStore.Snapshot snapshot = read == null ? null : read.snapshot;
            this.snapshotId = snapshot == null ? null : snapshot.id;
            this.snapshotState = snapshot == null ? null : snapshot.state;
            this.publishedContentHash = snapshot == null ? null : snapshot.contentHash;
            this.currentContentHash = currentContentHash;
            this.protocolVersion = snapshot == null ? 0 : snapshot.protocolVersion;
            this.includesEvidence = snapshot != null && snapshot.includesEvidence;
            this.metadataQueryFailed = read != null && read.queryFailed;
        }

        SnapshotProof(String snapshotId, String snapshotState, String publishedContentHash,
                      String currentContentHash, int protocolVersion, boolean includesEvidence,
                      boolean metadataQueryFailed) {
            this.snapshotId = snapshotId;
            this.snapshotState = snapshotState;
            this.publishedContentHash = publishedContentHash;
            this.currentContentHash = currentContentHash;
            this.protocolVersion = protocolVersion;
            this.includesEvidence = includesEvidence;
            this.metadataQueryFailed = metadataQueryFailed;
        }

        boolean provesCurrentProjection(String expectedSnapshotId) {
            return !metadataQueryFailed && snapshotId != null && snapshotId.equals(expectedSnapshotId)
                    && "PUBLISHED".equals(snapshotState) && protocolVersion == 6 && includesEvidence
                    && publishedContentHash != null && publishedContentHash.equals(currentContentHash);
        }
    }

    static final class CatalogRead {
        final CatalogState state;
        final Map<Long, LocalListing> byLocalId;
        final Map<String, List<LocalListing>> byItemId;
        final SnapshotProof snapshotProof;

        private CatalogRead(CatalogState state, List<LocalListing> rows, SnapshotProof snapshotProof) {
            this.state = state;
            this.snapshotProof = snapshotProof;
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
            return available(rows, null);
        }

        static CatalogRead available(List<LocalListing> rows, SnapshotProof snapshotProof) {
            return new CatalogRead(CatalogState.AVAILABLE,
                    rows == null ? Collections.emptyList() : rows, snapshotProof);
        }

        static CatalogRead available(LocalListing... rows) {
            return available(Arrays.asList(rows));
        }

        static CatalogRead databaseMissing() {
            return new CatalogRead(CatalogState.DATABASE_MISSING, Collections.emptyList(), null);
        }

        static CatalogRead queryFailed() {
            return new CatalogRead(CatalogState.QUERY_FAILED, Collections.emptyList(), null);
        }
    }

    static final class Match {
        final MatchState state;
        final LocalListing listing;
        final boolean contentMatched;
        final boolean dataChanged;
        final boolean projectionContinuityProven;
        final boolean externalIdentityMissing;

        Match(MatchState state, LocalListing listing) {
            this(state, listing, false, false, false,
                    listing != null && (listing.itemId == null || listing.itemId.trim().isEmpty()));
        }

        Match(MatchState state, LocalListing listing, boolean contentMatched, boolean dataChanged,
              boolean projectionContinuityProven, boolean externalIdentityMissing) {
            this.state = state;
            this.listing = listing;
            this.contentMatched = contentMatched;
            this.dataChanged = dataChanged;
            this.projectionContinuityProven = projectionContinuityProven;
            this.externalIdentityMissing = externalIdentityMissing;
        }

        boolean isVerified() {
            // No imported field in this schema proves that a current item_id was present historically.
            return false;
        }
    }

    static final class CurrentTargetRead {
        final CatalogRead catalog;
        final boolean uniqueCurrentItemId;
        final boolean active;
        final boolean manualReviewRequired;

        CurrentTargetRead(CatalogRead catalog, boolean uniqueCurrentItemId,
                          boolean active, boolean manualReviewRequired) {
            this.catalog = catalog;
            this.uniqueCurrentItemId = uniqueCurrentItemId;
            this.active = active;
            this.manualReviewRequired = manualReviewRequired;
        }
    }

    private AiBetaRegistryView() { }

    static CatalogRead readCurrentCatalog(Context context, AiBetaRegistry registry) {
        File file = context.getDatabasePath("vinted_affari.db");
        if (!file.isFile()) return CatalogRead.databaseMissing();
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            db.execSQL("BEGIN");
            try {
                Set<Long> localIds = new LinkedHashSet<>();
                Set<String> itemIds = new LinkedHashSet<>();
                for (AiBetaRegistry.Entry entry : registry.entries) {
                    localIds.add(entry.sourceId == null ? entry.listingId : entry.sourceId);
                    if (entry.itemId != null) itemIds.add(entry.itemId);
                }
                List<LocalListing> rows = new ArrayList<>();
                queryChunks(db, "id", localIds, rows);
                queryChunks(db, "vinted_item_id", itemIds, rows);
                String currentContentHash = FirestoreSnapshotWorker.sourceHash(db, true);
                FirestoreSnapshotStore.MetadataRead metadata = FirestoreSnapshotStore.readMetadataReadOnly(context);
                return CatalogRead.available(rows, new SnapshotProof(metadata, currentContentHash));
            } finally {
                db.execSQL("ROLLBACK");
            }
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

    /** Reads one row only; no full catalog/root hash is recalculated for the controlled apply flow. */
    static CurrentTargetRead readCurrentTarget(Context context, long localId) {
        if (localId <= 0) return new CurrentTargetRead(CatalogRead.queryFailed(), false, false, true);
        File file = context.getDatabasePath("vinted_affari.db");
        if (!file.isFile()) return new CurrentTargetRead(CatalogRead.databaseMissing(), false, false, true);
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            db.execSQL("BEGIN");
            try {
                List<LocalListing> rows = new ArrayList<>();
                queryChunks(db, "id", Collections.singleton(localId), rows);
                CatalogRead read = CatalogRead.available(rows);
                if (rows.size() != 1) return new CurrentTargetRead(read, false, false, true);
                LocalListing listing = rows.get(0);
                boolean unique = false;
                if (listing.itemId != null && !listing.itemId.trim().isEmpty()) {
                    try (Cursor ids = db.rawQuery("SELECT id FROM market_listings WHERE vinted_item_id=? LIMIT 2",
                            new String[]{listing.itemId})) {
                        unique = ids.moveToFirst() && ids.getLong(0) == localId && !ids.moveToNext();
                    }
                }
                boolean active;
                boolean manualReview;
                try (Cursor state = db.rawQuery("SELECT lifecycle,COALESCE(manual_review_required,0) FROM market_listings WHERE id=?",
                        new String[]{String.valueOf(localId)})) {
                    if (!state.moveToFirst()) return new CurrentTargetRead(read, false, false, true);
                    active = "ACTIVE".equals(state.getString(0));
                    manualReview = state.getInt(1) != 0;
                }
                return new CurrentTargetRead(read, unique, active, manualReview);
            } finally {
                db.execSQL("ROLLBACK");
            }
        } catch (Exception failure) {
            return new CurrentTargetRead(CatalogRead.queryFailed(), false, false, true);
        } finally {
            if (db != null) db.close();
        }
    }

    /** Reads a bounded set of current local rows in one read-only SQLite snapshot. */
    static Map<Long, CurrentTargetRead> readCurrentTargets(Context context, List<Long> localIds) {
        Set<Long> requested = new LinkedHashSet<>();
        if (localIds != null) {
            for (Long localId : localIds) {
                if (localId != null && localId > 0) requested.add(localId);
            }
        }
        if (requested.isEmpty()) return Collections.emptyMap();
        if (context == null) return failedTargets(requested, CatalogRead.queryFailed());
        File file = context.getDatabasePath("vinted_affari.db");
        if (!file.isFile()) return failedTargets(requested, CatalogRead.databaseMissing());
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            db.execSQL("BEGIN");
            try {
                List<LocalListing> rows = new ArrayList<>();
                queryChunks(db, "id", requested, rows);
                CatalogRead read = CatalogRead.available(rows);
                Map<Long, CurrentTargetRead> targets = new LinkedHashMap<>();
                for (Long localId : requested) {
                    LocalListing listing = read.byLocalId.get(localId);
                    if (listing == null) {
                        targets.put(localId, new CurrentTargetRead(read, false, false, true));
                        continue;
                    }
                    boolean unique = false;
                    if (listing.itemId != null && !listing.itemId.trim().isEmpty()) {
                        try (Cursor ids = db.rawQuery("SELECT id FROM market_listings WHERE vinted_item_id=? LIMIT 2",
                                new String[]{listing.itemId})) {
                            unique = ids.moveToFirst() && ids.getLong(0) == localId && !ids.moveToNext();
                        }
                    }
                    boolean active = false;
                    boolean manualReview = true;
                    try (Cursor state = db.rawQuery(
                            "SELECT lifecycle,COALESCE(manual_review_required,0) FROM market_listings WHERE id=?",
                            new String[]{String.valueOf(localId)})) {
                        if (state.moveToFirst()) {
                            active = "ACTIVE".equals(state.getString(0));
                            manualReview = state.getInt(1) != 0;
                        }
                    }
                    targets.put(localId, new CurrentTargetRead(read, unique, active, manualReview));
                }
                return Collections.unmodifiableMap(targets);
            } finally {
                db.execSQL("ROLLBACK");
            }
        } catch (Exception failure) {
            return failedTargets(requested, CatalogRead.queryFailed());
        } finally {
            if (db != null) db.close();
        }
    }

    private static Map<Long, CurrentTargetRead> failedTargets(Set<Long> localIds, CatalogRead read) {
        Map<Long, CurrentTargetRead> targets = new LinkedHashMap<>();
        for (Long localId : localIds)
            targets.put(localId, new CurrentTargetRead(read, false, false, true));
        return Collections.unmodifiableMap(targets);
    }

    static boolean canPrepareCurrentTarget(AiBetaRegistry.Entry entry, Match match,
                                           CurrentTargetRead target) {
        return entry != null && target != null && target.catalog.state == CatalogState.AVAILABLE
                && match != null && match.listing != null && match.contentMatched
                && entry.sourceId != null && entry.sourceHash != null && entry.sourceHashVersion != null
                && entry.sourceId == entry.listingId && entry.sourceId == match.listing.localId
                && target.uniqueCurrentItemId && target.active && !target.manualReviewRequired
                && match.listing.itemId != null && !match.listing.itemId.trim().isEmpty()
                && "PROPOSED".equals(entry.recordState)
                && !AiBetaRegistryDraft.choices(entry, match.listing).isEmpty();
    }

    static String reviewHeader(AiBetaRegistry registry) {
        int proposed = 0;
        int unresolved = 0;
        for (AiBetaRegistry.Entry entry : registry.entries) {
            if ("PROPOSED".equals(entry.recordState)) proposed++;
            else unresolved++;
        }
        return "Registro AI · " + registry.registryId
                + "\nRecord: " + registry.entries.size() + " · proposte: " + proposed
                + " · irrisolti: " + unresolved
                + "\nNessun confronto completo o AI viene eseguito in questa vista."
                + "\nLa scheda corrente viene letta solo aprendo un singolo record; l’identità storica resta non verificata.";
    }

    static String[] reviewRows(AiBetaRegistry registry) {
        String[] rows = new String[registry.entries.size()];
        for (int i = 0; i < registry.entries.size(); i++) {
            AiBetaRegistry.Entry entry = registry.entries.get(i);
            StringBuilder row = new StringBuilder("ID registro ").append(entry.listingId)
                    .append(" · ").append(entry.recordState);
            row.append("\nOriginale (").append(sourceLabel(entry.originalSource)).append("): ")
                    .append(proposal(entry.originalType, entry.originalLanguage));
            if (entry.correctedSource == null) row.append("\nA/B corretto: non disponibile");
            else row.append("\nA/B corretto (").append(entry.correctedSource).append("): ")
                    .append(proposal(entry.correctedType, entry.correctedLanguage));
            row.append("\nRevisione: ").append(reviewLabel(entry));
            rows[i] = row.toString();
        }
        return rows;
    }

    static String registryOnlyDetails(AiBetaRegistry registry, AiBetaRegistry.Entry entry) {
        StringBuilder text = new StringBuilder("ID registro: ").append(entry.listingId)
                .append("\nSource ID storico: ").append(entry.sourceId == null ? "non conservato" : entry.sourceId)
                .append("\nitem_id nel registro: ").append(valueLabel(entry.itemId))
                .append(" (provenienza storica non verificata)")
                .append("\nHash proiezione v3 nel registro: ").append(valueLabel(entry.sourceHash))
                .append("\nTitolo/ID/metadati correnti: non letti; questa vista non esegue un confronto completo")
                .append("\nProposta originale (").append(sourceLabel(entry.originalSource)).append("): ")
                .append(proposal(entry.originalType, entry.originalLanguage));
        if (entry.correctedSource == null) text.append("\nProposta A/B corretta: non disponibile");
        else text.append("\nProposta A/B corretta (").append(entry.correctedSource).append("): ")
                .append(proposal(entry.correctedType, entry.correctedLanguage));
        return text.append("\nStato record: ").append(entry.recordState)
                .append("\nRevisione: ").append(reviewLabel(entry))
                .append("\nIdentità Vinted storica: non dimostrata").toString();
    }

    private static void queryChunks(SQLiteDatabase db, String column, Set<?> values, List<LocalListing> output) throws Exception {
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
            String projection = FirestoreSnapshotWorker.QUERY;
            int from = projection.indexOf(" FROM ");
            int order = projection.lastIndexOf(" ORDER BY l.id");
            if (from < 0 || order < from) throw new IllegalStateException("writer_projection_invalid");
            String listingIdColumn = "id".equals(column) ? "l.id" : "l.vinted_item_id";
            String sql = projection.substring(0, from) + ",l.vinted_item_id"
                    + projection.substring(from, order) + " WHERE " + listingIdColumn
                    + " IN (" + placeholders + ")" + projection.substring(order);
            try (Cursor cursor = db.rawQuery(sql, args)) {
                while (cursor.moveToNext()) {
                    int itemIdIndex = cursor.getColumnCount() - 1;
                    output.add(new LocalListing(cursor.getLong(0), cursor.getString(itemIdIndex),
                            cursor.getString(2), cursor.getString(4), cursor.getString(1),
                            FirestoreSnapshotWorker.sourceHashForRow(cursor, true)));
                }
            }
        }
    }

    static Match resolve(AiBetaRegistry.Entry entry, CatalogRead read) {
        if (read == null || read.state == CatalogState.QUERY_FAILED) return new Match(MatchState.QUERY_FAILED, null);
        if (read.state == CatalogState.DATABASE_MISSING) return new Match(MatchState.DATABASE_MISSING, null);
        if (entry.sourceId != null) {
            LocalListing candidate = read.byLocalId.get(entry.sourceId);
            if (candidate != null) return compareSourceProjection(entry, read, candidate);
            if (entry.itemId != null) {
                List<LocalListing> externalCandidates = read.byItemId.get(entry.itemId);
                if (externalCandidates != null && externalCandidates.size() > 1)
                    return new Match(MatchState.AMBIGUOUS, null);
                if (externalCandidates != null && externalCandidates.size() == 1)
                    return compareSourceProjection(entry, read, externalCandidates.get(0));
            }
            return new Match(MatchState.LOCAL_ROW_ABSENT, null);
        }
        if (entry.itemId != null) {
            List<LocalListing> matches = read.byItemId.get(entry.itemId);
            if (matches == null || matches.isEmpty()) return new Match(MatchState.EXTERNAL_ID_NOT_FOUND, null);
            if (matches.size() != 1) return new Match(MatchState.AMBIGUOUS, null);
            return new Match(MatchState.CURRENT_ID_CANDIDATE_ONLY, matches.get(0));
        }
        LocalListing candidate = read.byLocalId.get(entry.listingId);
        return candidate == null
                ? new Match(MatchState.UNVERIFIED_NO_CANDIDATE, null)
                : new Match(MatchState.UNVERIFIED_NUMERIC_CANDIDATE, candidate);
    }

    private static Match compareSourceProjection(AiBetaRegistry.Entry entry, CatalogRead read,
                                                LocalListing candidate) {
        if (entry.sourceId == null || entry.sourceHash == null || entry.sourceHashVersion == null) {
            return new Match(MatchState.EXTERNAL_ID_HASH_MISSING, candidate);
        }
        if (entry.sourceId != entry.listingId || entry.sourceId != candidate.localId) {
            return new Match(MatchState.SOURCE_ID_CONFLICT, candidate);
        }
        if (candidate.sourceHash == null || !entry.sourceHash.equals(candidate.sourceHash)) {
            return new Match(MatchState.DATA_CHANGED, candidate, false, true, false,
                    candidate.itemId == null || candidate.itemId.trim().isEmpty());
        }
        boolean continuity = read.snapshotProof != null
                && read.snapshotProof.provesCurrentProjection(entry.sourceSnapshotId);
        return new Match(continuity ? MatchState.CONTENT_MATCH_PROJECTION_PROVEN
                : MatchState.CONTENT_MATCH_PROJECTION_UNPROVEN, candidate, true, false, continuity,
                candidate.itemId == null || candidate.itemId.trim().isEmpty());
    }

    static String header(AiBetaRegistry registry) {
        return header(registry, null);
    }

    static String header(AiBetaRegistry registry, CatalogRead read) {
        String query = read == null ? "non ancora eseguita" : catalogStateLabel(read.state);
        int itemIdRecords = 0;
        int contentMatches = 0;
        int projectionContinuityMatches = 0;
        int projectionContinuityUnproven = 0;
        int dataChanged = 0;
        int identityProven = 0;
        int identityUnverifiable = 0;
        int currentExternalIdPresent = 0;
        int currentExternalIdMissing = 0;
        int externalIdConflicts = 0;
        int unverified = 0;
        int noCandidate = 0;
        int externalNotFound = 0;
        int ambiguous = 0;
        int sourceHashRecords = 0;
        int sourceIdConflict = 0;
        int externalIdHashMissing = 0;
        for (AiBetaRegistry.Entry entry : registry.entries) {
            if (entry.itemId != null) itemIdRecords++;
            if (entry.sourceHash != null) sourceHashRecords++;
        }
        boolean matchesEvaluated = read != null && read.state == CatalogState.AVAILABLE;
        if (matchesEvaluated) {
            for (AiBetaRegistry.Entry entry : registry.entries) {
                Match match = resolve(entry, read);
                if (match.contentMatched) contentMatches++;
                if (match.projectionContinuityProven) projectionContinuityMatches++;
                else if (match.contentMatched) projectionContinuityUnproven++;
                if (match.dataChanged) dataChanged++;
                if (match.isVerified()) identityProven++;
                else identityUnverifiable++;
                if (match.listing != null && match.listing.itemId != null
                        && !match.listing.itemId.trim().isEmpty()) currentExternalIdPresent++;
                else if (match.listing != null) currentExternalIdMissing++;
                if (entry.itemId != null && match.listing != null && match.listing.itemId != null
                        && !entry.itemId.equals(match.listing.itemId)) externalIdConflicts++;
                if (match.state == MatchState.UNVERIFIED_NUMERIC_CANDIDATE
                        || match.state == MatchState.CURRENT_ID_CANDIDATE_ONLY) unverified++;
                else if (match.state == MatchState.UNVERIFIED_NO_CANDIDATE) noCandidate++;
                else if (match.state == MatchState.LOCAL_ROW_ABSENT) noCandidate++;
                else if (match.state == MatchState.EXTERNAL_ID_NOT_FOUND) externalNotFound++;
                else if (match.state == MatchState.AMBIGUOUS) ambiguous++;
                else if (match.state == MatchState.SOURCE_ID_CONFLICT) sourceIdConflict++;
                else if (match.state == MatchState.EXTERNAL_ID_HASH_MISSING) externalIdHashMissing++;
            }
        }
        return "Registro AI · " + registry.registryId
                + "\nSnapshot: " + registry.sourceSnapshotId + " · esportato il " + registry.generatedAt
                + "\nModello: " + (registry.modelId == null ? "non registrato nella fonte" : registry.modelId)
                + "\nPrompt storico: " + (registry.historicalPromptId == null ? "non conservato" : registry.historicalPromptId)
                + "\nA/B corretto: " + registry.correctedAbPromptId
                + "\nCandidato prompt " + registry.candidatePromptCommit.substring(0, 8) + ": non eseguito su questi record"
                + "\nQuery catalogo locale: " + query
                + "\nHash riga: SHA-256 della proiezione canonica del writer v3. Continuità database = sola proiezione."
                + "\nIdentità Vinted storica dimostrata: " + identityProven
                + " · identità non dimostrata: " + (matchesEvaluated ? identityUnverifiable : "non valutabile")
                + "\nHash sorgente nel registro: " + sourceHashRecords + "/" + registry.entries.size()
                + " · item_id storico nel registro: " + itemIdRecords + "/" + registry.entries.size()
                + " · " + (matchesEvaluated
                    ? "contenuto corrispondente: " + contentMatches
                        + " (continuità proiezione provata: " + projectionContinuityMatches
                        + ", non provata: " + projectionContinuityUnproven + ")"
                        + " · dati cambiati: " + dataChanged
                        + " · riga locale assente: " + noCandidate
                        + " · candidati solo numerici/ID attuale: " + unverified
                        + " · item_id corrente presente: " + currentExternalIdPresent
                        + " · item_id corrente mancante: " + currentExternalIdMissing
                        + " · item_id registro/attuale in conflitto: " + externalIdConflicts
                        + " · ID esterno non trovato: " + externalNotFound
                        + " · associazioni ambigue: " + ambiguous
                        + " · source_id in conflitto: " + sourceIdConflict
                        + " · hash storico mancante: " + externalIdHashMissing
                    : "corrispondenze non valutabili finché la query non è completata")
                + "\nIl solo item_id corrente, ID locale o titolo non dimostra identità storica."
                + "\nAI — non verificato. Solo lettura: nessuna proposta scritta nel catalogo.\nRecord: " + registry.entries.size();
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
                row.append("\nID locale attuale: ").append(match.listing.localId)
                        .append(" · item_id Vinted attuale: ").append(valueLabel(match.listing.itemId));
                if (entry.itemId != null) row.append("\nitem_id nel registro: ").append(entry.itemId)
                        .append(" (provenienza non verificata)");
                row.append("\n").append(match.isVerified() ? "Scheda locale: " : "Riga locale osservata (identità non provata): ")
                        .append(titleLabel(match.listing.title));
                row.append("\nDati attuali")
                        .append(": categoria ").append(valueLabel(match.listing.category))
                        .append(" · lingua ").append(valueLabel(match.listing.language));
                row.append("\nHash proiezione attuale: ").append(valueLabel(match.listing.sourceHash));
            }
            row.append("\nHash proiezione storica: ").append(valueLabel(entry.sourceHash));
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
                .append("\nSource ID storico: ").append(entry.sourceId == null ? "non conservato" : entry.sourceId)
                .append("\nEsito lettura catalogo: ").append(catalogStateLabel(read == null ? CatalogState.QUERY_FAILED : read.state))
                .append("\nCorrispondenza: ").append(matchDescription(match));
        if (match.listing != null) {
            text.append("\nRiga locale osservata (associazione non dimostrata)")
                    .append(": ").append(titleLabel(match.listing.title))
                    .append("\nID locale attuale: ").append(match.listing.localId)
                    .append("\nitem_id Vinted attuale: ").append(valueLabel(match.listing.itemId))
                    .append(" (dato attuale, non prova d’identità storica)");
            text.append("\nConfronto identità Vinted: ").append(externalIdentityComparison(entry, match.listing));
            if (entry.itemId != null) text.append("\nitem_id nel registro: ").append(entry.itemId)
                    .append(" (provenienza non verificata; un’eventuale uguaglianza non prova identità storica)");
            text.append("\nMetadati attuali")
                    .append(": categoria ").append(valueLabel(match.listing.category))
                    .append(" · lingua ").append(valueLabel(match.listing.language));
        }
        text.append("\nHash storico proiezione v3: ").append(valueLabel(entry.sourceHash))
                .append("\nHash attuale proiezione v3: ")
                .append(match.listing == null ? "non disponibile" : valueLabel(match.listing.sourceHash))
                .append("\nContinuità della proiezione completa: ")
                .append(match.projectionContinuityProven ? "provata dal root hash dello snapshot" : "non dimostrata");
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
                .append("\nProva di continuità: ").append(sourceProofLabel(entry, match))
                .append("\nAccettazione: ").append(acceptanceLabel(entry, match));
        return text.toString();
    }

    static String acceptanceLabel(AiBetaRegistry.Entry entry, Match match) {
        if (match == null || match.listing == null) return "bloccata: nessuna scheda corrente disponibile";
        if (!match.contentMatched) return "bloccata: hash dei contenuti correnti diverso o non disponibile";
        if (!"PROPOSED".equals(entry.recordState)) return "bloccata: record irrisolto";
        if (AiBetaRegistryDraft.choices(entry, match.listing).isEmpty()) return "bloccata: nessuna proposta diversa e accettabile (mancante, UNKNOWN o già attuale)";
        if (match.listing.itemId == null || match.listing.itemId.trim().isEmpty()) return "bloccata: ID Vinted corrente mancante";
        return "possibile solo per la scheda corrente dopo controllo di unicità; identità storica non dimostrata, nessuna scrittura finché non si conferma una correzione concreta";
    }

    private static String matchLabel(MatchState state) {
        switch (state) {
            case CONTENT_MATCH_PROJECTION_PROVEN: return "hash contenuti uguale · continuità proiezione provata · identità Vinted non verificata";
            case CONTENT_MATCH_PROJECTION_UNPROVEN: return "hash contenuti uguale · continuità proiezione non provata · identità non verificata";
            case DATA_CHANGED: return "dati della proiezione cambiati · possibile ID riutilizzato, non dimostrato";
            case LOCAL_ROW_ABSENT: return "riga locale storica assente · annuncio/associazione non verificabile";
            case CURRENT_ID_CANDIDATE_ONLY: return "item_id corrente coincide ma non prova il legame storico";
            case SOURCE_ID_CONFLICT: return "source_id in conflitto; associazione non verificata";
            case EXTERNAL_ID_HASH_MISSING: return "ID trovato ma prova hash/continuità mancante";
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
            case CONTENT_MATCH_PROJECTION_PROVEN: return "hash della proiezione v3 uguale e root hash dello snapshot coerente; questo dimostra solo continuità dei campi proiettati, non identità Vinted";
            case CONTENT_MATCH_PROJECTION_UNPROVEN: return "hash della proiezione v3 uguale, ma manca una prova di continuità dell’intera proiezione del database; identità Vinted non dimostrata";
            case DATA_CHANGED: return "hash della proiezione locale diverso; dati cambiati. L’eventuale riuso dell’ID locale non è dimostrabile dai campi disponibili";
            case LOCAL_ROW_ABSENT: return "source_id storico non presente nel catalogo locale; non è possibile distinguere rimozione, sostituzione o mancata associazione";
            case CURRENT_ID_CANDIDATE_ONLY: return "item_id presente nel catalogo attuale, ma manca un legame storico verificabile; nessuna identità attribuita";
            case SOURCE_ID_CONFLICT: return "source_id non coincide con listing_id atteso o con la riga locale";
            case EXTERNAL_ID_HASH_MISSING: return "item_id trovato ma manca l’hash necessario a verificare la continuità delle evidenze";
            case UNVERIFIED_NUMERIC_CANDIDATE: return "non verificata: solo coincidenza con ID numerico locale";
            case UNVERIFIED_NO_CANDIDATE: return "non verificata: registro senza item_id e nessun ID numerico locale trovato";
            case EXTERNAL_ID_NOT_FOUND: return "item_id esterno non trovato nel catalogo locale; nessun fallback sull’ID numerico";
            case AMBIGUOUS: return "item_id presente su più righe locali; associazione ambigua";
            case DATABASE_MISSING: return "catalogo locale non presente";
            default: return "query catalogo non riuscita; assenza e metadati non valutabili";
        }
    }

    private static String sourceProofLabel(AiBetaRegistry.Entry entry, Match match) {
        if (match.projectionContinuityProven) return "root hash SHA-256 v3 dello snapshot coincide; prova limitata ai campi proiettati, item_id storico non conservato";
        if (match.contentMatched) return "hash SHA-256 v3 della riga coincide; root hash di continuità assente o diverso, identità esterna non provata";
        if (match.dataChanged) return "hash SHA-256 v3 diverso; dati modificati, riuso dell’ID non dimostrato";
        if (entry.sourceHash == null) return "registro privo di source_hash per riga; contenuto storico non verificabile";
        return "non sufficiente a verificare l’identità esterna dell’annuncio";
    }

    private static String externalIdentityComparison(AiBetaRegistry.Entry entry, LocalListing listing) {
        boolean currentMissing = listing.itemId == null || listing.itemId.trim().isEmpty();
        if (entry.itemId == null) {
            return currentMissing
                    ? "item_id storico e attuale mancanti; identità non verificabile"
                    : "item_id storico non conservato; ID attuale presente ma non prova identità storica";
        }
        if (currentMissing) return "item_id storico presente nel registro, ma quello attuale manca; provenienza storica non verificata";
        if (entry.itemId.equals(listing.itemId)) {
            return "valori uguali, ma la provenienza dell’item_id nel registro non è dimostrata; identità non verificata";
        }
        return "item_id del registro e attuale diversi; conflitto osservato, associazione non dimostrata";
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