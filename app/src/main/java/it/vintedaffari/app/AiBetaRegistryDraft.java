package it.vintedaffari.app;

import org.json.JSONObject;

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

    static final class Choice {
        final String field;
        final String source;
        final String value;
        final String provenance;

        Choice(String field, String source, String value, String provenance) {
            this.field = field;
            this.source = source;
            this.value = value;
            this.provenance = provenance;
        }

        String label() {
            return (FIELD_CATEGORY.equals(field) ? "Categoria" : "Lingua") + " · "
                    + (SOURCE_ORIGINAL.equals(source) ? "proposta originale" : "proposta A/B corretta")
                    + " (" + provenance + "): " + value;
        }
    }

    static final class Draft {
        final String registryId;
        final String snapshotId;
        final long listingId;
        final String itemId;
        final long localId;
        final String title;
        final String field;
        final String source;
        final String provenance;
        final String observedCategory;
        final String observedLanguage;
        final String beforeValue;
        final String afterValue;

        Draft(String registryId, String snapshotId, long listingId, String itemId, long localId,
              String title, String field, String source, String provenance,
              String observedCategory, String observedLanguage, String beforeValue, String afterValue) {
            this.registryId = registryId;
            this.snapshotId = snapshotId;
            this.listingId = listingId;
            this.itemId = itemId;
            this.localId = localId;
            this.title = title;
            this.field = field;
            this.source = source;
            this.provenance = provenance;
            this.observedCategory = observedCategory;
            this.observedLanguage = observedLanguage;
            this.beforeValue = beforeValue;
            this.afterValue = afterValue;
        }

        boolean stillMatches(AiBetaRegistryView.LocalListing current) {
            return current != null && current.localId == localId
                    && same(itemId, current.itemId) && same(title, current.title)
                    && same(observedCategory, current.category) && same(observedLanguage, current.language)
                    && same(beforeValue, currentValue(current, field));
        }

        String beforeLabel() {
            return AiBetaRegistryView.valueLabel(beforeValue);
        }

        String summary() {
            return (FIELD_CATEGORY.equals(field) ? "categoria" : "lingua") + " · "
                    + beforeLabel() + " → " + afterValue + " · " + title;
        }

        String details(String currentStatus) {
            return "Bozza locale non applicata al catalogo"
                    + "\nScheda: " + AiBetaRegistryView.titleLabel(title)
                    + "\nID item esterno: " + itemId
                    + "\nCampo: " + (FIELD_CATEGORY.equals(field) ? "categoria" : "lingua")
                    + "\nPrima: " + beforeLabel()
                    + "\nDopo: " + afterValue
                    + "\nProvenienza proposta: " + provenance + " · " + source
                    + "\nStato di ID, titolo, categoria e lingua rispetto alla bozza: " + currentStatus
                    + "\nHash dello snapshot AI non presente: nessuna affermazione che il contenuto storico sia invariato."
                    + "\nIl valore precedente è conservato in questa bozza; nessuna scrittura al catalogo è stata eseguita.";
        }

        JSONObject toJson() throws Exception {
            JSONObject value = new JSONObject();
            value.put("registry_id", registryId);
            value.put("snapshot_id", snapshotId);
            value.put("listing_id", listingId);
            value.put("item_id", itemId);
            value.put("local_id", localId);
            value.put("title", title);
            value.put("field", field);
            value.put("source", source);
            value.put("provenance", provenance);
            value.put("observed_category", observedCategory == null ? JSONObject.NULL : observedCategory);
            value.put("observed_language", observedLanguage == null ? JSONObject.NULL : observedLanguage);
            value.put("before_value", beforeValue == null ? JSONObject.NULL : beforeValue);
            value.put("after_value", afterValue);
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
            return new Draft(value.getString("registry_id"), value.getString("snapshot_id"),
                    value.getLong("listing_id"), value.getString("item_id"), value.getLong("local_id"),
                    value.getString("title"), field, source, value.getString("provenance"),
                    category, language, before, after);
        }
    }

    private AiBetaRegistryDraft() { }

    static List<Choice> choices(AiBetaRegistry.Entry entry) {
        if (entry == null || !"PROPOSED".equals(entry.recordState)) return Collections.emptyList();
        List<Choice> choices = new ArrayList<>();
        add(choices, FIELD_CATEGORY, SOURCE_ORIGINAL, entry.originalType, AiBetaRegistryView.sourceLabel(entry.originalSource));
        add(choices, FIELD_LANGUAGE, SOURCE_ORIGINAL, entry.originalLanguage, AiBetaRegistryView.sourceLabel(entry.originalSource));
        if (entry.correctedSource != null) {
            add(choices, FIELD_CATEGORY, SOURCE_CORRECTED, entry.correctedType, entry.correctedSource);
            add(choices, FIELD_LANGUAGE, SOURCE_CORRECTED, entry.correctedLanguage, entry.correctedSource);
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

    private static void add(List<Choice> choices, String field, String source, String value, String provenance) {
        if (value != null && !value.trim().isEmpty() && !"UNKNOWN".equals(value)) {
            choices.add(new Choice(field, source, value, provenance));
        }
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
