package it.vintedaffari.app;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class AiBetaRegistryTest {
    @Test public void preservesPairedOriginalAndCorrectedProposalsAndUnexecutedCandidate() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistry());
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        assertEquals("UNKNOWN", entry.originalLanguage);
        assertEquals("IT", entry.correctedLanguage);
        assertEquals("language_and_category_clarity_v1", entry.correctedSource);
        assertFalse(registry.candidatePromptExecuted);
        assertEquals("7fabd03a256e0bab12a093c227d569d0acdb9b4a", registry.candidatePromptCommit);
    }

    @Test public void rejectsDuplicateIdsUnsupportedCodesAndUnexpectedFields() throws Exception {
        Map<String, Object> duplicate = validRegistry();
        duplicate.put("entries", Arrays.asList(
                entry(1016L, "BASE_GAME", "UNKNOWN", "paired_ab_baseline", "BASE_GAME", "IT",
                        "language_and_category_clarity_v1", "PROPOSED", "LANGUAGE_REANALYSIS"),
                entry(1016L, "BASE_GAME", "UNKNOWN", "paired_ab_baseline", "BASE_GAME", "IT",
                        "language_and_category_clarity_v1", "PROPOSED", "LANGUAGE_REANALYSIS")));
        assertRejected(duplicate);

        Map<String, Object> invalidCode = validRegistry();
        invalidCode.put("entries", Collections.singletonList(entry(1016L, "BASE_GAME", "UNKNOWN",
                "paired_ab_baseline", "BASE_GAME", "ZZ", "language_and_category_clarity_v1",
                "PROPOSED", "LANGUAGE_REANALYSIS")));
        assertRejected(invalidCode);

        Map<String, Object> unexpected = validRegistry();
        unexpected.put("catalog_write", true);
        assertRejected(unexpected);
    }

    @Test public void distinguishesAbstentionFromUnresolvedAndEnforcesPayloadBound() throws Exception {
        Map<String, Object> unresolved = validRegistry();
        unresolved.put("entries", Collections.singletonList(entry(1291L, null, null,
                "historical_register_unversioned", null, null, null, "UNRESOLVED", "RECORD_UNRESOLVED")));
        AiBetaRegistry registry = AiBetaRegistry.parseObject(unresolved);
        assertNull(registry.entries.get(0).originalLanguage);
        assertEquals("UNRESOLVED", registry.entries.get(0).recordState);

        try {
            AiBetaRegistry.parse(new byte[AiBetaRegistry.MAX_BYTES + 1]);
            fail("oversized payload accepted");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void rendersCurrentMetadataAlongsideSeparateSuggestionsAndReviewFlags() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistry());
        Map<Long, AiBetaRegistryView.Metadata> current = new HashMap<>();
        current.put(1016L, new AiBetaRegistryView.Metadata("BASE_GAME", "UNKNOWN"));
        String display = AiBetaRegistryView.format(registry, current);
        assertTrue(display.contains("Metadati attuali: categoria BASE_GAME · lingua UNKNOWN"));
        assertTrue(display.contains("Originale (baseline A/B): BASE_GAME / UNKNOWN"));
        assertTrue(display.contains("Prompt corretto (language_and_category_clarity_v1): BASE_GAME / IT"));
        assertTrue(display.contains("AI — non verificato"));
        assertTrue(display.contains("rianalisi lingua"));
        String row = AiBetaRegistryView.rows(registry, current)[0];
        assertTrue(row.contains("attuale: BASE_GAME / UNKNOWN"));
        assertTrue(row.contains("Originale (baseline A/B): BASE_GAME / UNKNOWN"));
        assertTrue(row.contains("A/B corretto (language_and_category_clarity_v1): BASE_GAME / IT"));
        assertTrue(row.contains("AI — non verificato"));
        assertFalse(display.contains("catalogo aggiornato"));
    }

    private static Map<String, Object> validRegistry() {
        Map<String, Object> root = new HashMap<>();
        root.put("schema_version", 1L);
        root.put("registry_id", "ludo-language-suggestions-20261018");
        root.put("source_snapshot_id", "5c02314d-fd73-41b4-b1b3-be5448f374cd");
        root.put("generated_at", "2026-10-18");
        root.put("model_id", null);
        root.put("model_id_status", "not_preserved_in_source_register");
        root.put("historical_prompt_id", null);
        root.put("corrected_ab_prompt_id", "language_and_category_clarity_v1");
        root.put("candidate_prompt_commit", "7fabd03a256e0bab12a093c227d569d0acdb9b4a");
        root.put("candidate_prompt_executed", false);
        root.put("entries", Collections.singletonList(entry(1016L, "BASE_GAME", "UNKNOWN",
                "paired_ab_baseline", "BASE_GAME", "IT", "language_and_category_clarity_v1",
                "PROPOSED", "LANGUAGE_REANALYSIS")));
        return root;
    }

    private static Map<String, Object> entry(long id, String originalType, String originalLanguage,
                                             String originalSource, String correctedType, String correctedLanguage,
                                             String correctedSource, String recordState, String... flags) {
        Map<String, Object> row = new HashMap<>();
        row.put("listing_id", id);
        row.put("original_type", originalType);
        row.put("original_language", originalLanguage);
        row.put("original_source", originalSource);
        row.put("corrected_type", correctedType);
        row.put("corrected_language", correctedLanguage);
        row.put("corrected_source", correctedSource);
        row.put("record_state", recordState);
        row.put("review_flags", Arrays.asList(flags));
        return row;
    }

    private static void assertRejected(Map<String, Object> value) throws Exception {
        try {
            AiBetaRegistry.parseObject(value);
            fail("invalid registry accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
