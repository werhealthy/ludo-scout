package it.vintedaffari.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

/** One-field, optimistic and reversible write path for an explicitly approved current listing. */
final class AiBetaRegistryApply {
    interface Journal {
        void save(AiBetaRegistryDraft.Draft draft) throws Exception;
    }

    static final class ConflictException extends Exception {
        ConflictException(String message) { super(message); }
    }

    static final class JournalPendingException extends Exception {
        final AiBetaRegistryDraft.Draft recoverableDraft;
        JournalPendingException(AiBetaRegistryDraft.Draft recoverableDraft, Throwable cause) {
            super("La scheda è stata aggiornata; il registro cifrato resta in stato recuperabile.", cause);
            this.recoverableDraft = recoverableDraft;
        }
    }

    static final class Current {
        final AiBetaRegistryView.LocalListing listing;
        final String lifecycle;
        final boolean manualReviewRequired;

        Current(AiBetaRegistryView.LocalListing listing, String lifecycle, boolean manualReviewRequired) {
            this.listing = listing;
            this.lifecycle = lifecycle;
            this.manualReviewRequired = manualReviewRequired;
        }
    }

    private AiBetaRegistryApply() { }

    static SQLiteDatabase openWritableCatalog(Context context) throws Exception {
        File file = context.getDatabasePath("vinted_affari.db");
        if (!file.isFile()) throw new IllegalStateException("Catalogo locale non presente");
        return SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
    }

    static AiBetaRegistryDraft.Draft apply(SQLiteDatabase db, AiBetaRegistryDraft.Draft draft,
                                           Journal journal) throws Exception {
        if (db == null || draft == null || journal == null || !draft.canApply())
            throw new IllegalArgumentException("Bozza non applicabile o incompleta");
        AiBetaRegistryDraft.Draft pending = draft.transition(AiBetaRegistryDraft.APPLY_PENDING, null, null);
        journal.save(pending);
        AiBetaRegistryDraft.Draft pendingAfter = null;
        boolean began = false;
        try {
            db.beginTransaction();
            began = true;
            Current current = readCurrent(db, draft.localId);
            requireCurrentTarget(db, draft, current, true);
            ContentValues values = new ContentValues();
            putField(values, draft.field, draft.afterValue);
            String where = "id=? AND vinted_item_id=? AND category_normalized IS ? AND language_code IS ?"
                    + " AND lifecycle='ACTIVE' AND COALESCE(manual_review_required,0)=0";
            int changed = db.update("market_listings", values, where, new String[]{
                    String.valueOf(draft.localId), draft.itemId, draft.observedCategory, draft.observedLanguage});
            if (changed != 1) throw new ConflictException("La scheda è cambiata o non è più aggiornabile; nessuna correzione applicata.");
            Current after = readCurrent(db, draft.localId);
            if (after == null || after.listing == null || !same(draft.itemId, after.listing.itemId)
                    || !same(draft.afterValue, currentValue(after.listing, draft.field))) {
                throw new ConflictException("Verifica post-scrittura non riuscita; la transazione sarà annullata.");
            }
            pendingAfter = pending.transition(AiBetaRegistryDraft.APPLY_PENDING,
                    after.listing.sourceHash, null);
            journal.save(pendingAfter);
            db.setTransactionSuccessful();
        } catch (Exception failure) {
            if (began && db.inTransaction()) {
                try { db.endTransaction(); } catch (RuntimeException ignored) { }
                began = false;
            }
            try { journal.save(draft.transition(AiBetaRegistryDraft.CONFLICT, null, null)); }
            catch (Exception journalFailure) { failure.addSuppressed(journalFailure); }
            throw failure;
        } finally {
            if (began && db.inTransaction()) db.endTransaction();
        }
        AiBetaRegistryDraft.Draft applied = pendingAfter.transition(AiBetaRegistryDraft.APPLIED,
                pendingAfter.appliedHash, null);
        try {
            journal.save(applied);
        } catch (Exception failure) {
            throw new JournalPendingException(pendingAfter, failure);
        }
        return applied;
    }

    static AiBetaRegistryDraft.Draft restore(SQLiteDatabase db, AiBetaRegistryDraft.Draft draft,
                                             Journal journal) throws Exception {
        if (db == null || draft == null || journal == null
                || !AiBetaRegistryDraft.APPLIED.equals(draft.state) || draft.appliedHash == null)
            throw new IllegalArgumentException("Nessuna correzione ripristinabile");
        AiBetaRegistryDraft.Draft pending = draft.transition(AiBetaRegistryDraft.RESTORE_PENDING,
                draft.appliedHash, null);
        journal.save(pending);
        AiBetaRegistryDraft.Draft pendingRestored = null;
        boolean began = false;
        try {
            db.beginTransaction();
            began = true;
            Current current = readCurrent(db, draft.localId);
            if (!isApplied(draft, current) || !uniqueItemId(db, draft.itemId, draft.localId))
                throw new ConflictException("La scheda è cambiata dopo la correzione; ripristino bloccato.");
            ContentValues values = new ContentValues();
            putField(values, draft.field, draft.beforeValue);
            int changed = db.update("market_listings", values,
                    "id=? AND vinted_item_id=? AND category_normalized IS ? AND language_code IS ?",
                    new String[]{String.valueOf(draft.localId), draft.itemId,
                            current.listing.category, current.listing.language});
            if (changed != 1) throw new ConflictException("La scheda è cambiata durante il ripristino; nessuna sovrascrittura.");
            Current restored = readCurrent(db, draft.localId);
            if (restored == null || restored.listing == null
                    || !same(draft.beforeValue, currentValue(restored.listing, draft.field)))
                throw new ConflictException("Verifica del ripristino non riuscita; la transazione sarà annullata.");
            pendingRestored = pending.transition(AiBetaRegistryDraft.RESTORE_PENDING,
                    draft.appliedHash, restored.listing.sourceHash);
            journal.save(pendingRestored);
            db.setTransactionSuccessful();
        } catch (Exception failure) {
            if (began && db.inTransaction()) {
                try { db.endTransaction(); } catch (RuntimeException ignored) { }
                began = false;
            }
            try { journal.save(draft.transition(AiBetaRegistryDraft.CONFLICT,
                    draft.appliedHash, null)); }
            catch (Exception journalFailure) { failure.addSuppressed(journalFailure); }
            throw failure;
        } finally {
            if (began && db.inTransaction()) db.endTransaction();
        }
        AiBetaRegistryDraft.Draft restored = pendingRestored.transition(AiBetaRegistryDraft.RESTORED,
                draft.appliedHash, pendingRestored.restoredHash);
        try {
            journal.save(restored);
        } catch (Exception failure) {
            throw new JournalPendingException(pendingRestored, failure);
        }
        return restored;
    }

    static AiBetaRegistryDraft.Draft reconcile(AiBetaRegistryDraft.Draft draft, Current current) {
        if (draft == null || current == null || current.listing == null) return draft;
        if (AiBetaRegistryDraft.APPLY_PENDING.equals(draft.state)) {
            if (draft.appliedHash != null && isApplied(draft, current))
                return draft.transition(AiBetaRegistryDraft.APPLIED, draft.appliedHash, null);
            if (draft.stillMatches(current.listing) && same(draft.beforeValue, currentValue(current.listing, draft.field)))
                return draft.transition(AiBetaRegistryDraft.STAGED, null, null);
            return draft.transition(AiBetaRegistryDraft.CONFLICT, draft.appliedHash, null);
        }
        if (AiBetaRegistryDraft.RESTORE_PENDING.equals(draft.state)) {
            if (draft.restoredHash != null && same(draft.itemId, current.listing.itemId)
                    && same(draft.restoredHash, current.listing.sourceHash)
                    && same(draft.beforeValue, currentValue(current.listing, draft.field)))
                return draft.transition(AiBetaRegistryDraft.RESTORED, draft.appliedHash, draft.restoredHash);
            if (isApplied(draft, current))
                return draft.transition(AiBetaRegistryDraft.APPLIED, draft.appliedHash, null);
            return draft.transition(AiBetaRegistryDraft.CONFLICT, draft.appliedHash, draft.restoredHash);
        }
        return draft;
    }

    static Current readCurrent(SQLiteDatabase db, long localId) throws Exception {
        if (db == null || localId <= 0) return null;
        String projection = FirestoreSnapshotWorker.QUERY;
        int from = projection.indexOf(" FROM ");
        int order = projection.lastIndexOf(" ORDER BY l.id");
        if (from < 0 || order < from) throw new IllegalStateException("writer_projection_invalid");
        String sql = projection.substring(0, from) + ",l.vinted_item_id"
                + projection.substring(from, order) + " WHERE l.id=?" + projection.substring(order);
        AiBetaRegistryView.LocalListing listing = null;
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(localId)})) {
            if (cursor.moveToNext()) {
                int itemIdIndex = cursor.getColumnCount() - 1;
                listing = new AiBetaRegistryView.LocalListing(cursor.getLong(0), cursor.getString(itemIdIndex),
                        cursor.getString(2), cursor.getString(4), cursor.getString(1),
                        FirestoreSnapshotWorker.sourceHashForRow(cursor, true));
            }
        }
        if (listing == null) return new Current(null, null, true);
        String lifecycle;
        boolean manualReview;
        try (Cursor state = db.rawQuery("SELECT lifecycle,COALESCE(manual_review_required,0) FROM market_listings WHERE id=?",
                new String[]{String.valueOf(localId)})) {
            if (!state.moveToFirst()) return new Current(null, null, true);
            lifecycle = state.getString(0);
            manualReview = state.getInt(1) != 0;
        }
        return new Current(listing, lifecycle, manualReview);
    }

    private static void requireCurrentTarget(SQLiteDatabase db, AiBetaRegistryDraft.Draft draft, Current current,
                                             boolean requireActive) throws ConflictException {
        if (current == null || current.listing == null || !draft.stillMatches(current.listing)
                || !same(draft.itemId, current.listing.itemId)
                || (requireActive && (!"ACTIVE".equals(current.lifecycle) || current.manualReviewRequired))
                || !uniqueItemId(db, current.listing.itemId, draft.localId)) {
            throw new ConflictException("Identità Vinted corrente o hash dei contenuti cambiati; applicazione bloccata. L’identità storica resta non verificata.");
        }
    }

    private static boolean uniqueItemId(SQLiteDatabase db, String itemId, long localId) {
        if (db == null || itemId == null || itemId.trim().isEmpty() || localId <= 0) return false;
        try (Cursor ids = db.rawQuery("SELECT id FROM market_listings WHERE vinted_item_id=? LIMIT 2",
                new String[]{itemId})) {
            return ids.moveToFirst() && ids.getLong(0) == localId && !ids.moveToNext();
        }
    }

    private static boolean isApplied(AiBetaRegistryDraft.Draft draft, Current current) {
        return current != null && current.listing != null && draft.appliedHash != null
                && same(draft.itemId, current.listing.itemId)
                && same(draft.appliedHash, current.listing.sourceHash)
                && same(draft.afterValue, currentValue(current.listing, draft.field));
    }

    private static void putField(ContentValues values, String field, String value) {
        if (AiBetaRegistryDraft.FIELD_CATEGORY.equals(field)) {
            if (value == null) values.putNull("category_normalized");
            else values.put("category_normalized", value);
        } else if (AiBetaRegistryDraft.FIELD_LANGUAGE.equals(field)) {
            if (value == null) values.putNull("language_code");
            else values.put("language_code", value);
        } else throw new IllegalArgumentException("Campo bozza non valido");
    }

    private static String currentValue(AiBetaRegistryView.LocalListing listing, String field) {
        if (AiBetaRegistryDraft.FIELD_CATEGORY.equals(field)) return listing.category;
        if (AiBetaRegistryDraft.FIELD_LANGUAGE.equals(field)) return listing.language;
        throw new IllegalArgumentException("Campo bozza non valido");
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
