package it.vintedaffari.app;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One encrypted, reversible review draft. It never applies values to market_listings. */
final class AiBetaRegistryDraft {
    static final String SETTINGS_KEY = "ai_registry_pending_draft";
    static final String FIELD_CATEGORY = "category";
    static final String FIELD_LANGUAGE = "language";
    static final String SOURCE_ORIGINAL = "original";
    static final String SOURCE_CORRECTED = "corrected";
    static final String STAGED = "STAGED";
    static final String APPLY_PENDING = "APPLY_PENDING";
    static final String APPLIED = "APPLIED";
    static final String RESTORE_PENDING = "RESTORE_PENDING";
    static final String RESTORED = "RESTORED";
    static final String CONFLICT = "CONFLICT";

    static final class Choice {
        final String field;
        final String source;
        final String value;
        final String provenance;
        final boolean reviewRequired;

        Choice(String field, String source, String value, String provenance) {
            this(field, source, value, provenance, false);
        }

        Choice(String field, String source, String value, String provenance, boolean reviewRequired) {
            this.field = field;
            this.source = source;
            this.value = value;
            this.provenance = provenance;
            this.reviewRequired = reviewRequired;
        }

        String label() {
            return (FIELD_CATEGORY.equals(field) ? "Categoria" : "Lingua") + " · "
                    + (SOURCE_ORIGINAL.equals(source) ? "proposta originale" : "proposta A/B corretta")
                    + " (" + provenance + "): " + value
                    + (reviewRequired ? " · revisione richiesta" : "");
        }
    }

    static final class Draft {
        final String registryId;
        final String snapshotId;
        final long listingId;
        /** Current Vinted item ID, captured for this local correction; not a historical identity claim. */
        final String itemId;
        final String sourceItemId;
        final long localId;
        final String title;
        final String field;
        final String source;
        final String provenance;
        final String observedCategory;
        final String observedLanguage;
        final String beforeValue;
        final String afterValue;
        final String currentHash;
        final String appliedHash;
        final String restoredHash;
        final List<String> reviewFlags;
        final boolean reviewRequired;
        final boolean reviewed;
        final String state;

        Draft(String registryId, String snapshotId, long listingId, String itemId, long localId,
              String title, String field, String source, String provenance,
              String observedCategory, String observedLanguage, String beforeValue, String afterValue) {
            this(registryId, snapshotId, listingId, itemId, null, localId, title, field, source,
                    provenance, observedCategory, observedLanguage, beforeValue, afterValue,
                    null, null, null, Collections.emptyList(), false, false, STAGED);
        }

        Draft(String registryId, String snapshotId, long listingId, String itemId, String sourceItemId,
              long localId, String title, String field, String source, String provenance,
              String observedCategory, String observedLanguage, String beforeValue, String afterValue,
              String currentHash, String appliedHash, String restoredHash, List<String> reviewFlags,
              boolean reviewRequired, boolean reviewed, String state) {
            this.registryId = registryId;
            this.snapshotId = snapshotId;
            this.listingId = listingId;
            this.itemId = itemId;
            this.sourceItemId = sourceItemId;
            this.localId = localId;
            this.title = title;
            this.field = field;
            this.source = source;
            this.provenance = provenance;
            this.observedCategory = observedCategory;
            this.observedLanguage = observedLanguage;
            this.beforeValue = beforeValue;
            this.afterValue = afterValue;
            this.currentHash = currentHash;
            this.appliedHash = appliedHash;
            this.restoredHash = restoredHash;
            this.reviewFlags = Collections.unmodifiableList(new ArrayList<>(reviewFlags));
            this.reviewRequired = reviewRequired;
            this.reviewed = reviewed;
            this.state = state;
        }

        boolean stillMatches(AiBetaRegistryView.LocalListing current) {
            return current != null && current.localId == localId
                    && same(itemId, current.itemId) && same(title, current.title)
                    && same(observedCategory, current.category) && same(observedLanguage, current.language)
                    && same(beforeValue, currentValue(current, field))
                    && currentHash != null && same(currentHash, current.sourceHash);
        }

        boolean canApply() {
            return STAGED.equals(state) && currentHash != null && !currentHash.isEmpty()
                    && (!reviewRequired || reviewed) && afterValue != null && !afterValue.isEmpty()
                    && !"UNKNOWN".equals(afterValue);
        }

        boolean sameProposal(Draft other) {
            return other != null && listingId == other.listingId && localId == other.localId
                    && same(registryId, other.registryId) && same(snapshotId, other.snapshotId)
                    && same(itemId, other.itemId) && same(field, other.field) && same(source, other.source)
                    && same(provenance, other.provenance) && same(currentHash, other.currentHash)
                    && same(beforeValue, other.beforeValue) && same(afterValue, other.afterValue);
        }

        Draft transition(String nextState, String nextAppliedHash, String nextRestoredHash) {
            return new Draft(registryId, snapshotId, listingId, itemId, sourceItemId, localId, title,
                    field, source, provenance, observedCategory, observedLanguage, beforeValue, afterValue,
                    currentHash, nextAppliedHash, nextRestoredHash, reviewFlags,
                    reviewRequired, reviewed, nextState);
        }

        String beforeLabel() {
            return AiBetaRegistryView.valueLabel(beforeValue);
        }

        String summary() {
            return (FIELD_CATEGORY.equals(field) ? "categoria" : "lingua") + " · "
                    + beforeLabel() + " → " + afterValue + " · " + title;
        }

        String details(String currentStatus) {
            String stateLabel = state.equals(APPLIED) ? "applicata; ripristino disponibile"
                    : state.equals(RESTORED) ? "ripristinata; prima/dopo conservati"
                    : state.equals(CONFLICT) ? "conflitto; applicazione bloccata"
                    : state.equals(APPLY_PENDING) || state.equals(RESTORE_PENDING)
                    ? "operazione da riconciliare; nessuna nuova azione consentita"
                    : "bozza locale non applicata al catalogo";
            return "Stato: " + stateLabel
                    + "\nScheda: " + AiBetaRegistryView.titleLabel(title)
                    + "\nID Vinted attuale: " + itemId + " (non prova identità storica)"
                    + "\nCampo: " + (FIELD_CATEGORY.equals(field) ? "categoria" : "lingua")
                    + "\nPrima: " + beforeLabel()
                    + "\nDopo: " + afterValue
                    + "\nProvenienza proposta: " + provenance + " · " + source
                    + "\nFlag di revisione: " + (reviewFlags.isEmpty() ? "nessuno" : reviewFlags)
                    + " · revisione umana: " + (reviewed ? "registrata nella bozza" : "non registrata")
                    + "\nHash v3 corrente alla bozza: " + AiBetaRegistryView.valueLabel(currentHash)
                    + "\nHash v3 dopo applicazione: " + AiBetaRegistryView.valueLabel(appliedHash)
                    + "\nStato corrente rispetto alla bozza: " + currentStatus
                    + "\nLa verifica dell’ID e dell’hash correnti non dimostra retroattivamente l’identità storica."
                    + "\nPrima/dopo e provenienza restano conservati nella bozza cifrata.";
        }

        JSONObject toJson() throws Exception {
            JSONObject value = new JSONObject();
            value.put("registry_id", registryId);
            value.put("snapshot_id", snapshotId);
            value.put("listing_id", listingId);
            value.put("item_id", itemId);
            value.put("source_item_id", sourceItemId == null ? JSONObject.NULL : sourceItemId);
            value.put("local_id", localId);
            value.put("title", title);
            value.put("field", field);
            value.put("source", source);
            value.put("provenance", provenance);
            value.put("observed_category", observedCategory == null ? JSONObject.NULL : observedCategory);
            value.put("observed_language", observedLanguage == null ? JSONObject.NULL : observedLanguage);
            value.put("before_value", beforeValue == null ? JSONObject.NULL : beforeValue);
            value.put("after_value", afterValue);
            value.put("current_hash", currentHash == null ? JSONObject.NULL : currentHash);
            value.put("applied_hash", appliedHash == null ? JSONObject.NULL : appliedHash);
            value.put("restored_hash", restoredHash == null ? JSONObject.NULL : restoredHash);
            value.put("review_flags", new JSONArray(reviewFlags));
            value.put("review_required", reviewRequired);
            value.put("reviewed", reviewed);
            value.put("state", state);
            return value;
        }

        static Draft fromJson(JSONObject value) throws Exception {
            String field = value.getString("field");
            String source = value.getString("source");
            String category = value.isNull("observed_category") ? null : value.getString("observed_category");
            String language = value.isNull("observed_language") ? null : value.getString("observed_language");
            String before = value.isNull("before_value") ? null : value.getString("before_value");
            String after = value.getString("after_value");
            if (!FIELD_CATEGORY.equals(field) && !FIELD_LANGUAGE.equals(field)) throw new IllegalArgumentException("Campo bozza non valido");
            if (!SOURCE_ORIGINAL.equals(source) && !SOURCE_CORRECTED.equals(source)) throw new IllegalArgumentException("Provenienza bozza non valida");
            if (after.isEmpty() || "UNKNOWN".equals(after)) throw new IllegalArgumentException("Valore bozza non accettabile");
            String state = value.optString("state", STAGED);
            if (!STAGED.equals(state) && !APPLY_PENDING.equals(state) && !APPLIED.equals(state)
                    && !RESTORE_PENDING.equals(state) && !RESTORED.equals(state) && !CONFLICT.equals(state))
                throw new IllegalArgumentException("Stato bozza non valido");
            JSONArray flags = value.optJSONArray("review_flags");
            List<String> reviewFlags = new ArrayList<>();
            if (flags != null) for (int i = 0; i < flags.length(); i++) reviewFlags.add(flags.getString(i));
            String sourceItemId = value.isNull("source_item_id") ? null : value.optString("source_item_id", null);
            String currentHash = value.isNull("current_hash") ? null : value.optString("current_hash", null);
            String appliedHash = value.isNull("applied_hash") ? null : value.optString("applied_hash", null);
            String restoredHash = value.isNull("restored_hash") ? null : value.optString("restored_hash", null);
            return new Draft(value.getString("registry_id"), value.getString("snapshot_id"),
                    value.getLong("listing_id"), value.getString("item_id"), sourceItemId,
                    value.getLong("local_id"), value.getString("title"), field, source,
                    value.getString("provenance"), category, language, before, after,
                    currentHash, appliedHash, restoredHash, reviewFlags,
                    value.optBoolean("review_required", false), value.optBoolean("reviewed", false), state);
        }
    }

    private AiBetaRegistryDraft() { }

    static List<Choice> choices(AiBetaRegistry.Entry entry) {
        if (entry == null || !"PROPOSED".equals(entry.recordState)) return Collections.emptyList();
        List<Choice> choices = new ArrayList<>();
        add(choices, FIELD_CATEGORY, SOURCE_ORIGINAL, entry.originalType,
                AiBetaRegistryView.sourceLabel(entry.originalSource), requiresReview(entry, FIELD_CATEGORY));
        add(choices, FIELD_LANGUAGE, SOURCE_ORIGINAL, entry.originalLanguage,
                AiBetaRegistryView.sourceLabel(entry.originalSource), requiresReview(entry, FIELD_LANGUAGE));
        if (entry.correctedSource != null) {
            add(choices, FIELD_CATEGORY, SOURCE_CORRECTED, entry.correctedType,
                    entry.correctedSource, requiresReview(entry, FIELD_CATEGORY));
            add(choices, FIELD_LANGUAGE, SOURCE_CORRECTED, entry.correctedLanguage,
                    entry.correctedSource, requiresReview(entry, FIELD_LANGUAGE));
        }
        return Collections.unmodifiableList(choices);
    }

    static boolean hasChoices(AiBetaRegistry.Entry entry) {
        return !choices(entry).isEmpty();
    }

    static List<Choice> choices(AiBetaRegistry.Entry entry, AiBetaRegistryView.LocalListing listing) {
        if (listing == null) return Collections.emptyList();
        List<Choice> result = new ArrayList<>();
        for (Choice choice : choices(entry)) {
            String current = FIELD_CATEGORY.equals(choice.field) ? listing.category : listing.language;
            if (!same(current, choice.value)) result.add(choice);
        }
        return Collections.unmodifiableList(result);
    }

    private static void add(List<Choice> choices, String field, String source, String value,
                            String provenance, boolean reviewRequired) {
        if (value != null && !value.trim().isEmpty() && !"UNKNOWN".equals(value)) {
            choices.add(new Choice(field, source, value, provenance, reviewRequired));
        }
    }

    static boolean requiresReview(AiBetaRegistry.Entry entry, String field) {
        if (entry == null) return true;
        if (FIELD_CATEGORY.equals(field)) return entry.reviewFlags.contains("CATEGORY_REVIEW");
        if (FIELD_LANGUAGE.equals(field)) return entry.reviewFlags.contains("LANGUAGE_REANALYSIS");
        return true;
    }

    static Draft create(AiBetaRegistry registry, AiBetaRegistry.Entry entry,
                        AiBetaRegistryView.Match match, Choice choice) {
        if (registry == null || entry == null || match == null || !match.isVerified()) {
            throw new IllegalArgumentException("Corrispondenza non verificata");
        }
        if (entry.itemId == null || !entry.itemId.equals(match.listing.itemId)) {
            throw new IllegalArgumentException("ID esterno non corrispondente");
        }
        if (!"PROPOSED".equals(entry.recordState)) throw new IllegalArgumentException("Record irrisolto");
        if (choice == null || !containsChoice(entry, choice)) throw new IllegalArgumentException("Proposta non disponibile");
        if ("UNKNOWN".equals(choice.value)) throw new IllegalArgumentException("UNKNOWN non è accettabile");
        String before = currentValue(match.listing, choice.field);
        if (same(before, choice.value)) throw new IllegalArgumentException("Il valore locale è già uguale alla proposta");
        return new Draft(registry.registryId, registry.sourceSnapshotId, entry.listingId, entry.itemId,
                match.listing.localId, match.listing.title, choice.field, choice.source,
                choice.provenance, match.listing.category, match.listing.language, before, choice.value);
    }

    static Draft createCurrentTarget(AiBetaRegistry registry, AiBetaRegistry.Entry entry,
                                     AiBetaRegistryView.Match match, Choice choice,
                                     boolean uniqueCurrentItemId, boolean active,
                                     boolean manualReviewRequired, boolean reviewed) {
        if (registry == null || entry == null || match == null || match.listing == null
                || !match.contentMatched || entry.sourceId == null || entry.sourceHash == null
                || entry.sourceHashVersion == null || entry.sourceId != entry.listingId
                || entry.sourceId != match.listing.localId) {
            throw new IllegalArgumentException("Contenuto corrente non corrisponde alla proposta; identità storica non verificata");
        }
        if (match.listing.itemId == null || match.listing.itemId.trim().isEmpty() || !uniqueCurrentItemId)
            throw new IllegalArgumentException("ID Vinted corrente mancante o non univoco");
        if (!active || manualReviewRequired)
            throw new IllegalArgumentException("Scheda non attiva o già in revisione manuale");
        if (!"PROPOSED".equals(entry.recordState)) throw new IllegalArgumentException("Record irrisolto");
        if (choice == null || !containsChoice(entry, choice)) throw new IllegalArgumentException("Proposta non disponibile");
        boolean reviewRequired = requiresReview(entry, choice.field);
        if (reviewRequired && !reviewed) throw new IllegalArgumentException("La proposta richiede revisione umana esplicita");
        String before = currentValue(match.listing, choice.field);
        if (same(before, choice.value)) throw new IllegalArgumentException("Il valore locale è già uguale alla proposta");
        return new Draft(registry.registryId, registry.sourceSnapshotId, entry.listingId,
                match.listing.itemId, entry.itemId, match.listing.localId, match.listing.title,
                choice.field, choice.source, choice.provenance, match.listing.category,
                match.listing.language, before, choice.value, match.listing.sourceHash,
                null, null, entry.reviewFlags, reviewRequired, reviewed, STAGED);
    }

    static Draft load(JSONObject settings) throws Exception {
        if (!settings.has(SETTINGS_KEY)) return null;
        Object value = settings.get(SETTINGS_KEY);
        if (!(value instanceof JSONObject)) throw new IllegalArgumentException("Bozza locale non leggibile");
        return Draft.fromJson((JSONObject) value);
    }

    static void store(JSONObject settings, Draft draft) throws Exception {
        if (settings.has(SETTINGS_KEY)) throw new IllegalStateException("Esiste già una bozza locale");
        settings.put(SETTINGS_KEY, draft.toJson());
    }

    static void replace(JSONObject settings, Draft draft) throws Exception {
        settings.put(SETTINGS_KEY, draft.toJson());
    }

    static void clear(JSONObject settings) {
        settings.remove(SETTINGS_KEY);
    }

    private static boolean containsChoice(AiBetaRegistry.Entry entry, Choice wanted) {
        for (Choice choice : choices(entry)) {
            if (choice.field.equals(wanted.field) && choice.source.equals(wanted.source)
                    && choice.value.equals(wanted.value) && choice.provenance.equals(wanted.provenance)) return true;
        }
        return false;
    }

    private static String currentValue(AiBetaRegistryView.LocalListing listing, String field) {
        if (FIELD_CATEGORY.equals(field)) return listing.category;
        if (FIELD_LANGUAGE.equals(field)) return listing.language;
        throw new IllegalArgumentException("Campo bozza non valido");
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
