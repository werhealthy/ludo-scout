package it.vintedaffari.app;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    @Test public void legacyNumericEqualityIsOnlyAnUnverifiedCandidate() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistry());
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing candidate = new AiBetaRegistryView.LocalListing(
                entry.listingId, "vinted-elsewhere", "Titolo candidato sintetico", null, "");
        AiBetaRegistryView.CatalogRead read = AiBetaRegistryView.CatalogRead.available(candidate);
        AiBetaRegistryView.Match match = AiBetaRegistryView.resolve(entry, read);
        assertEquals(AiBetaRegistryView.MatchState.UNVERIFIED_NUMERIC_CANDIDATE, match.state);
        assertFalse(match.isVerified());
        try {
            AiBetaRegistryDraft.create(registry, entry, match, AiBetaRegistryDraft.choices(entry).get(0));
            fail("numeric coincidence accepted as identity");
        } catch (IllegalArgumentException expected) { }
        String details = AiBetaRegistryView.details(registry, entry, read);
        assertTrue(details.contains("Candidato locale, non associato: Titolo candidato sintetico"));
        assertTrue(details.contains("categoria mancante · lingua mancante"));
        assertTrue(details.contains("corrispondenza non verificata"));
        assertTrue(details.contains("bloccata: corrispondenza non verificata"));
        assertTrue(details.contains("Proposta A/B corretta (language_and_category_clarity_v1)"));
        assertTrue(details.contains("esito non conservato; flag: rianalisi lingua"));
        assertTrue(details.contains("hash"));
    }

    @Test public void failedQueryDoesNotLookLikeZeroMatches() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistry());
        String header = AiBetaRegistryView.header(registry, AiBetaRegistryView.CatalogRead.queryFailed());
        assertTrue(header.contains("Query catalogo locale: query non riuscita"));
        assertTrue(header.contains("ID esterno nel registro: 0/1"));
        assertTrue(header.contains("corrispondenze non valutabili finché la query non è completata"));
        assertFalse(header.contains("corrispondenze verificate: 0"));
    }

    @Test public void exactExternalIdFindsLocalTitleEvenWhenLocalPrimaryKeyDiffers() throws Exception {
        Map<String, Object> source = validRegistryWithItemId("vinted-900100");
        AiBetaRegistry registry = AiBetaRegistry.parseObject(source);
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing local = new AiBetaRegistryView.LocalListing(
                7001L, "vinted-900100", "Titolo reale sintetico", "BASE_GAME", "EN");
        AiBetaRegistryView.CatalogRead read = AiBetaRegistryView.CatalogRead.available(local);
        AiBetaRegistryView.Match match = AiBetaRegistryView.resolve(entry, read);
        assertTrue(match.isVerified());
        assertEquals(7001L, match.listing.localId);
        assertTrue(AiBetaRegistryView.details(registry, entry, read).contains("Scheda locale: Titolo reale sintetico"));
    }

    @Test public void verifiedListingReportsMissingMetadataSeparatelyFromMissingId() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistryWithItemId("vinted-900100"));
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing local = new AiBetaRegistryView.LocalListing(
                7001L, "vinted-900100", "Titolo reale sintetico", null, "");
        AiBetaRegistryView.CatalogRead read = AiBetaRegistryView.CatalogRead.available(local);
        assertTrue(AiBetaRegistryView.resolve(entry, read).isVerified());
        assertTrue(AiBetaRegistryView.details(registry, entry, read)
                .contains("Metadati attuali: categoria mancante · lingua mancante"));
        AiBetaRegistryView.Match absentLegacy = AiBetaRegistryView.resolve(
                AiBetaRegistry.parseObject(validRegistry()).entries.get(0),
                AiBetaRegistryView.CatalogRead.available(Collections.emptyList()));
        assertEquals(AiBetaRegistryView.MatchState.UNVERIFIED_NO_CANDIDATE, absentLegacy.state);
    }

    @Test public void externalIdNotFoundDoesNotFallBackToEqualNumericIdAndQueryErrorsStayDistinct() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistryWithItemId("vinted-missing"));
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing numericCoincidence = new AiBetaRegistryView.LocalListing(
                entry.listingId, "vinted-other", "Non è la stessa inserzione", "BASE_GAME", "IT");
        AiBetaRegistryView.Match absent = AiBetaRegistryView.resolve(entry,
                AiBetaRegistryView.CatalogRead.available(numericCoincidence));
        assertEquals(AiBetaRegistryView.MatchState.EXTERNAL_ID_NOT_FOUND, absent.state);
        assertNull(absent.listing);
        assertTrue(AiBetaRegistryView.details(registry, entry,
                AiBetaRegistryView.CatalogRead.available(numericCoincidence)).contains("nessun fallback sull’ID numerico"));
        assertEquals(AiBetaRegistryView.MatchState.QUERY_FAILED,
                AiBetaRegistryView.resolve(entry, AiBetaRegistryView.CatalogRead.queryFailed()).state);
        assertEquals(AiBetaRegistryView.MatchState.DATABASE_MISSING,
                AiBetaRegistryView.resolve(entry, AiBetaRegistryView.CatalogRead.databaseMissing()).state);
    }

    @Test public void unknownProposalsAreNeverSelectableAndLocalChangesInvalidatePreview() throws Exception {
        Map<String, Object> unknown = validRegistry();
        unknown.put("entries", Collections.singletonList(entry(1016L, "UNKNOWN", "UNKNOWN",
                "historical_register_unversioned", null, null, null, "PROPOSED")));
        AiBetaRegistry.Entry unknownEntry = AiBetaRegistry.parseObject(unknown).entries.get(0);
        assertTrue(AiBetaRegistryDraft.choices(unknownEntry).isEmpty());
        assertFalse(AiBetaRegistryDraft.hasChoices(unknownEntry));

        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistryWithItemId("vinted-900100"));
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing local = new AiBetaRegistryView.LocalListing(
                7001L, "vinted-900100", "Titolo reale sintetico", "BASE_GAME", "EN");
        AiBetaRegistryView.Match match = AiBetaRegistryView.resolve(entry,
                AiBetaRegistryView.CatalogRead.available(local));
        List<AiBetaRegistryDraft.Choice> choices = AiBetaRegistryDraft.choices(entry, local);
        assertEquals(1, choices.size());
        assertEquals(AiBetaRegistryDraft.FIELD_LANGUAGE, choices.get(0).field);
        assertEquals(AiBetaRegistryDraft.SOURCE_CORRECTED, choices.get(0).source);
        AiBetaRegistryDraft.Draft draft = AiBetaRegistryDraft.create(registry, entry, match, choices.get(0));
        assertEquals("EN", draft.beforeValue);
        assertEquals("IT", draft.afterValue);
        assertTrue(draft.stillMatches(local));
        assertFalse(draft.stillMatches(new AiBetaRegistryView.LocalListing(
                7001L, "vinted-900100", "Titolo reale sintetico", "BASE_GAME", "FR")));
        assertFalse(draft.stillMatches(new AiBetaRegistryView.LocalListing(
                7001L, "vinted-900100", "Titolo modificato", "BASE_GAME", "EN")));
        assertFalse(draft.stillMatches(new AiBetaRegistryView.LocalListing(
                7002L, "vinted-900100", "Titolo reale sintetico", "BASE_GAME", "EN")));
        assertFalse(draft.stillMatches(new AiBetaRegistryView.LocalListing(
                7001L, "vinted-900101", "Titolo reale sintetico", "BASE_GAME", "EN")));
        assertFalse(draft.stillMatches(new AiBetaRegistryView.LocalListing(
                7001L, "vinted-900100", "Titolo reale sintetico", "EXPANSION", "EN")));
        assertTrue(draft.details("invariati rispetto alla bozza").contains("Prima: EN"));
        assertTrue(draft.details("invariati rispetto alla bozza").contains("Dopo: IT"));
        assertTrue(draft.details("invariati rispetto alla bozza").contains("nessuna scrittura al catalogo"));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> validRegistryWithItemId(String itemId) {
        Map<String, Object> root = validRegistry();
        ((Map<String, Object>) ((java.util.List<?>) root.get("entries")).get(0)).put("item_id", itemId);
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
