package it.vintedaffari.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict, read-only import model for externally transferred AI suggestions. */
public final class AiBetaRegistry {
    public static final int MAX_ENTRIES = 2000;
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final Set<String> TYPES = immutableSet("BASE_GAME", "EXPANSION", "BUNDLE", "ACCESSORY_COMPONENT", "NON_GAME", "UNKNOWN");
    private static final Set<String> LANGUAGES = immutableSet("IT", "EN", "DE", "FR", "ES", "PT", "NL", "MULTI", "OTHER", "UNKNOWN");
    private static final Set<String> ORIGINAL_SOURCES = immutableSet("historical_register_unversioned", "paired_ab_baseline");
    private static final Set<String> REVIEW_FLAGS = immutableSet("LANGUAGE_REANALYSIS", "CATEGORY_REVIEW", "RECORD_UNRESOLVED");
    private static final Pattern REGISTRY_ID = Pattern.compile("[a-z0-9-]{1,80}");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern SOURCE_HASH = Pattern.compile("[0-9a-f]{64}");

    public final String registryId;
    public final String sourceSnapshotId;
    public final String generatedAt;
    public final String modelId;
    public final String modelIdStatus;
    public final String historicalPromptId;
    public final String correctedAbPromptId;
    public final String candidatePromptCommit;
    public final boolean candidatePromptExecuted;
    public final List<Entry> entries;

    private AiBetaRegistry(String registryId, String sourceSnapshotId, String generatedAt,
                           String modelId, String modelIdStatus, String historicalPromptId,
                           String correctedAbPromptId, String candidatePromptCommit,
                           boolean candidatePromptExecuted, List<Entry> entries) {
        this.registryId = registryId;
        this.sourceSnapshotId = sourceSnapshotId;
        this.generatedAt = generatedAt;
        this.modelId = modelId;
        this.modelIdStatus = modelIdStatus;
        this.historicalPromptId = historicalPromptId;
        this.correctedAbPromptId = correctedAbPromptId;
        this.candidatePromptCommit = candidatePromptCommit;
        this.candidatePromptExecuted = candidatePromptExecuted;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public static final class Entry {
        public final String sourceSnapshotId;
        public final long listingId;
        /** Optional stable Vinted ID from the source snapshot; never the local SQLite primary key. */
        public final String itemId;
        /** Optional exact source row key and v3 projection hash retained beside a proposal. */
        public final Long sourceId;
        public final String sourceHash;
        public final Integer sourceHashVersion;
        public final String originalType;
        public final String originalLanguage;
        public final String originalSource;
        public final String correctedType;
        public final String correctedLanguage;
        public final String correctedSource;
        public final String recordState;
        public final List<String> reviewFlags;

        private Entry(String sourceSnapshotId, long listingId, String itemId, Long sourceId, String sourceHash,
                      Integer sourceHashVersion, String originalType, String originalLanguage,
                      String originalSource, String correctedType, String correctedLanguage,
                      String correctedSource, String recordState, List<String> reviewFlags) {
            this.sourceSnapshotId = sourceSnapshotId;
            this.listingId = listingId;
            this.itemId = itemId;
            this.sourceId = sourceId;
            this.sourceHash = sourceHash;
            this.sourceHashVersion = sourceHashVersion;
            this.originalType = originalType;
            this.originalLanguage = originalLanguage;
            this.originalSource = originalSource;
            this.correctedType = correctedType;
            this.correctedLanguage = correctedLanguage;
            this.correctedSource = correctedSource;
            this.recordState = recordState;
            this.reviewFlags = Collections.unmodifiableList(new ArrayList<>(reviewFlags));
        }
    }

    public static AiBetaRegistry parse(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Dimensione del registro non valida");
        }
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    public static AiBetaRegistry parse(String json) throws Exception {
        if (json == null || json.isEmpty() || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Dimensione del registro non valida");
        }
        return parseObject(toPlain(new JSONObject(json)));
    }

    /** Package-visible plain-object entry point keeps validation independently unit-testable. */
    static AiBetaRegistry parseObject(Object value) throws Exception {
        if (!(value instanceof Map)) throw new IllegalArgumentException("Il registro deve essere un oggetto JSON");
        Map<String, Object> root = stringMap(value);
        requireKeys(root, "schema_version", "registry_id", "source_snapshot_id", "generated_at",
                "model_id", "model_id_status", "historical_prompt_id", "corrected_ab_prompt_id",
                "candidate_prompt_commit", "candidate_prompt_executed", "entries");
        if (requiredLong(root, "schema_version") != 1L) throw new IllegalArgumentException("Versione schema non supportata");

        String registryId = requiredString(root, "registry_id", 80);
        if (!REGISTRY_ID.matcher(registryId).matches()) throw new IllegalArgumentException("Identificatore registro non valido");
        String snapshotId = requiredString(root, "source_snapshot_id", 36);
        try {
            UUID parsed = UUID.fromString(snapshotId);
            if (!parsed.toString().equalsIgnoreCase(snapshotId)) throw new IllegalArgumentException("Snapshot non valido");
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Snapshot non valido");
        }
        String generatedAt = requiredString(root, "generated_at", 10);
        try { LocalDate.parse(generatedAt); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Data registro non valida"); }

        String modelId = nullableString(root, "model_id", 120);
        String modelIdStatus = requiredString(root, "model_id_status", 80);
        String historicalPromptId = nullableString(root, "historical_prompt_id", 120);
        String correctedPromptId = requiredString(root, "corrected_ab_prompt_id", 120);
        String candidateCommit = requiredString(root, "candidate_prompt_commit", 40);
        if (!COMMIT.matcher(candidateCommit).matches()) throw new IllegalArgumentException("Commit candidato non valido");
        boolean candidateExecuted = requiredBoolean(root, "candidate_prompt_executed");
        if (candidateExecuted) throw new IllegalArgumentException("Il candidato indicato non è verificato come eseguito");

        Object rawRows = root.get("entries");
        if (!(rawRows instanceof List)) throw new IllegalArgumentException("Elenco record non valido");
        List<?> rows = (List<?>) rawRows;
        if (rows.size() < 1 || rows.size() > MAX_ENTRIES) throw new IllegalArgumentException("Numero di record non valido");
        List<Entry> entries = new ArrayList<>(rows.size());
        Set<Long> seen = new HashSet<>();
        for (Object rawRow : rows) {
            Map<String, Object> row = stringMap(rawRow);
            requireEntryKeys(row);
            long id = requiredLong(row, "listing_id");
            if (id <= 0 || !seen.add(id)) throw new IllegalArgumentException("ID mancante o duplicato");
            String itemId = row.containsKey("item_id") ? requiredString(row, "item_id", 80) : null;
            boolean hasSourceId = row.containsKey("source_id");
            boolean hasSourceHash = row.containsKey("source_hash");
            boolean hasSourceHashVersion = row.containsKey("source_hash_version");
            if ((hasSourceId || hasSourceHash || hasSourceHashVersion)
                    && !(hasSourceId && hasSourceHash && hasSourceHashVersion)) {
                throw new IllegalArgumentException("Provenienza hash sorgente incompleta");
            }
            Long sourceId = null;
            String sourceHash = null;
            Integer sourceHashVersion = null;
            if (hasSourceId) {
                long parsedSourceId = requiredLong(row, "source_id");
                if (parsedSourceId <= 0) throw new IllegalArgumentException("Source ID non valido");
                sourceId = parsedSourceId;
                sourceHash = requiredString(row, "source_hash", 64);
                if (!SOURCE_HASH.matcher(sourceHash).matches()) throw new IllegalArgumentException("Hash sorgente non valido");
                long parsedHashVersion = requiredLong(row, "source_hash_version");
                if (parsedHashVersion != 1L) throw new IllegalArgumentException("Versione hash sorgente non supportata");
                sourceHashVersion = (int) parsedHashVersion;
            }
            String originalType = nullableCode(row, "original_type", TYPES);
            String originalLanguage = nullableCode(row, "original_language", LANGUAGES);
            String originalSource = requiredString(row, "original_source", 80);
            if (!ORIGINAL_SOURCES.contains(originalSource)) throw new IllegalArgumentException("Provenienza originale non supportata");
            String correctedType = nullableCode(row, "corrected_type", TYPES);
            String correctedLanguage = nullableCode(row, "corrected_language", LANGUAGES);
            String correctedSource = nullableString(row, "corrected_source", 120);
            String recordState = requiredString(row, "record_state", 20);
            if (!"PROPOSED".equals(recordState) && !"UNRESOLVED".equals(recordState)) throw new IllegalArgumentException("Stato record non valido");

            if (correctedSource == null) {
                if (correctedType != null || correctedLanguage != null) throw new IllegalArgumentException("Proposta corretta senza provenienza");
            } else if (!correctedPromptId.equals(correctedSource) || correctedType == null || correctedLanguage == null) {
                throw new IllegalArgumentException("Proposta A/B corretta incoerente");
            }
            if ("paired_ab_baseline".equals(originalSource) != (correctedSource != null)) {
                throw new IllegalArgumentException("Armi A/B non abbinate");
            }
            if ("PROPOSED".equals(recordState) && originalType == null && originalLanguage == null) {
                throw new IllegalArgumentException("Proposta senza valori");
            }
            if ("UNRESOLVED".equals(recordState) && (originalType != null || originalLanguage != null)) {
                throw new IllegalArgumentException("Record irrisolto con proposta valorizzata");
            }
            List<String> flags = parseFlags(row.get("review_flags"));
            if ("UNRESOLVED".equals(recordState) != flags.contains("RECORD_UNRESOLVED")) {
                throw new IllegalArgumentException("Flag unresolved incoerente");
            }
            entries.add(new Entry(snapshotId, id, itemId, sourceId, sourceHash, sourceHashVersion,
                    originalType, originalLanguage, originalSource,
                    correctedType, correctedLanguage, correctedSource, recordState, flags));
        }
        return new AiBetaRegistry(registryId, snapshotId, generatedAt, modelId, modelIdStatus,
                historicalPromptId, correctedPromptId, candidateCommit, candidateExecuted, entries);
    }

    private static Object toPlain(Object value) throws Exception {
        if (value == JSONObject.NULL) return null;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Map<String, Object> result = new LinkedHashMap<>();
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                result.put(key, toPlain(object.get(key)));
            }
            return result;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> result = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) result.add(toPlain(array.get(i)));
            return result;
        }
        return value;
    }

    private static Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map)) throw new IllegalArgumentException("Record JSON non valido");
        Map<?, ?> raw = (Map<?, ?>) value;
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String)) throw new IllegalArgumentException("Chiave JSON non valida");
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static List<String> parseFlags(Object value) {
        if (!(value instanceof List)) throw new IllegalArgumentException("Flag di revisione non validi");
        List<?> array = (List<?>) value;
        if (array.size() > REVIEW_FLAGS.size()) throw new IllegalArgumentException("Troppi flag di revisione");
        List<String> flags = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object item : array) {
            if (!(item instanceof String)) throw new IllegalArgumentException("Flag di revisione non valido");
            String flag = (String) item;
            if (!REVIEW_FLAGS.contains(flag) || !seen.add(flag)) throw new IllegalArgumentException("Flag di revisione non supportato o duplicato");
            flags.add(flag);
        }
        return flags;
    }

    private static String nullableCode(Map<String, Object> object, String key, Set<String> allowed) {
        String value = nullableString(object, key, 40);
        if (value != null && !allowed.contains(value)) throw new IllegalArgumentException("Codice proposta non supportato");
        return value;
    }

    private static String requiredString(Map<String, Object> object, String key, int maxLength) {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException("Campo testuale non valido: " + key);
        String text = (String) value;
        if (text.isEmpty() || text.length() > maxLength) throw new IllegalArgumentException("Lunghezza campo non valida: " + key);
        return text;
    }

    private static String nullableString(Map<String, Object> object, String key, int maxLength) {
        Object value = object.get(key);
        if (value == null) return null;
        if (!(value instanceof String)) throw new IllegalArgumentException("Campo testuale non valido: " + key);
        String text = (String) value;
        if (text.isEmpty() || text.length() > maxLength) throw new IllegalArgumentException("Lunghezza campo non valida: " + key);
        return text;
    }

    private static long requiredLong(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Number)) throw new IllegalArgumentException("Campo numerico non valido: " + key);
        Number number = (Number) value;
        long result = number.longValue();
        if (!Double.isFinite(number.doubleValue()) || number.doubleValue() != (double) result) {
            throw new IllegalArgumentException("Numero non intero: " + key);
        }
        return result;
    }

    private static boolean requiredBoolean(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Boolean)) throw new IllegalArgumentException("Campo booleano non valido: " + key);
        return (Boolean) value;
    }

    private static void requireKeys(Map<String, Object> object, String... expected) {
        Set<String> actual = new HashSet<>(object.keySet());
        Set<String> wanted = new HashSet<>(Arrays.asList(expected));
        if (!wanted.equals(actual)) throw new IllegalArgumentException("Campi JSON mancanti o inattesi");
    }

    private static void requireEntryKeys(Map<String, Object> object) {
        Set<String> required = new HashSet<>(Arrays.asList("listing_id", "original_type", "original_language",
                "original_source", "corrected_type", "corrected_language", "corrected_source",
                "record_state", "review_flags"));
        Set<String> allowed = new HashSet<>(required);
        allowed.add("item_id");
        allowed.add("source_id");
        allowed.add("source_hash");
        allowed.add("source_hash_version");
        if (!object.keySet().containsAll(required) || !allowed.containsAll(object.keySet())) {
            throw new IllegalArgumentException("Campi JSON mancanti o inattesi");
        }
    }

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
