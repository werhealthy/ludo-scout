package it.vintedaffari.app;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AiBetaRegistryTest {
    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

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
        assertTrue(details.contains("Riga locale osservata (associazione non dimostrata): Titolo candidato sintetico"));
        assertTrue(details.contains("categoria mancante · lingua mancante"));
        assertTrue(details.contains("non verificata: solo coincidenza con ID numerico locale"));
        assertTrue(details.contains("bloccata: hash dei contenuti correnti diverso o non disponibile"));
        assertTrue(details.contains("Proposta A/B corretta (language_and_category_clarity_v1)"));
        assertTrue(details.contains("esito non conservato; flag: rianalisi lingua"));
        assertTrue(details.contains("hash"));
    }

    @Test public void sourceHashAndSnapshotWithoutHistoricalItemIdRemainUnverified() throws Exception {
        Map<String, Object> source = validRegistry();
        @SuppressWarnings("unchecked") Map<String, Object> row =
                (Map<String, Object>) ((java.util.List<?>) source.get("entries")).get(0);
        row.put("source_id", 1016L);
        row.put("source_hash", HASH_A);
        row.put("source_hash_version", 1L);
        AiBetaRegistry registry = AiBetaRegistry.parseObject(source);
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing local = new AiBetaRegistryView.LocalListing(
                1016L, "vinted-900100", "Titolo sintetico", "BASE_GAME", "EN", HASH_A);
        AiBetaRegistryView.Match match = AiBetaRegistryView.resolve(entry, snapshotProofRead(local));
        assertEquals(AiBetaRegistryView.MatchState.CONTENT_MATCH_PROJECTION_PROVEN, match.state);
        assertFalse(match.isVerified());
        assertTrue(match.contentMatched);
        assertTrue(match.projectionContinuityProven);
        assertTrue(AiBetaRegistryView.acceptanceLabel(entry, match)
                        .contains("identità storica non dimostrata"));
    }


    @Test public void hashMismatchSnapshotMismatchAndDuplicateExternalIdsFailClosed() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistryWithItemId("vinted-900100"));
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing wrongHash = new AiBetaRegistryView.LocalListing(
                1016L, "vinted-900100", "Titolo sintetico", "BASE_GAME", "EN", HASH_B);
        assertEquals(AiBetaRegistryView.MatchState.DATA_CHANGED,
                AiBetaRegistryView.resolve(entry, snapshotProofRead(wrongHash)).state);

        AiBetaRegistryView.LocalListing exact = new AiBetaRegistryView.LocalListing(
                1016L, "vinted-900100", "Titolo sintetico", "BASE_GAME", "EN", HASH_A);
        AiBetaRegistryView.SnapshotProof wrongSnapshot = new AiBetaRegistryView.SnapshotProof(
                "another-snapshot", "PUBLISHED", HASH_B, HASH_B, 6, true, false);
        AiBetaRegistryView.Match noRootProof = AiBetaRegistryView.resolve(entry,
                AiBetaRegistryView.CatalogRead.available(Arrays.asList(exact), wrongSnapshot));
        assertEquals(AiBetaRegistryView.MatchState.CONTENT_MATCH_PROJECTION_UNPROVEN, noRootProof.state);
        assertTrue(noRootProof.contentMatched);
        assertFalse(noRootProof.projectionContinuityProven);

        AiBetaRegistryView.LocalListing duplicate = new AiBetaRegistryView.LocalListing(
                7002L, "vinted-900100", "Seconda riga", "BASE_GAME", "EN", HASH_A);
        AiBetaRegistryView.Match externalDuplicate = AiBetaRegistryView.resolve(entry,
                snapshotProofRead(exact, duplicate));
        assertEquals(AiBetaRegistryView.MatchState.CONTENT_MATCH_PROJECTION_PROVEN, externalDuplicate.state);
        assertFalse(externalDuplicate.isVerified());
    }

    @Test public void sourceIdConflictBlocksEvenWhenExternalIdAndHashMatch() throws Exception {
        Map<String, Object> source = validRegistryWithItemId("vinted-900100");
        @SuppressWarnings("unchecked") Map<String, Object> row =
                (Map<String, Object>) ((java.util.List<?>) source.get("entries")).get(0);
        row.put("source_id", 7001L);
        AiBetaRegistry.Entry entry = AiBetaRegistry.parseObject(source).entries.get(0);
        AiBetaRegistryView.LocalListing local = new AiBetaRegistryView.LocalListing(
                7001L, "vinted-900100", "Titolo sintetico", "BASE_GAME", "EN", HASH_A);
        assertEquals(AiBetaRegistryView.MatchState.SOURCE_ID_CONFLICT,
                AiBetaRegistryView.resolve(entry, snapshotProofRead(local)).state);
    }

    @Test public void failedQueryDoesNotLookLikeZeroMatches() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistry());
        String header = AiBetaRegistryView.header(registry, AiBetaRegistryView.CatalogRead.queryFailed());
        assertTrue(header.contains("Query catalogo locale: query non riuscita"));
        assertTrue(header.contains("item_id storico nel registro: 0/1"));
        assertTrue(header.contains("corrispondenze non valutabili finché la query non è completata"));
        assertFalse(header.contains("corrispondenze verificate: 0"));
    }

    @Test public void currentExternalIdAndProjectionEvidenceStillDoNotProveHistoricalIdentity() throws Exception {
        Map<String, Object> source = validRegistryWithItemId("vinted-900100");
        AiBetaRegistry registry = AiBetaRegistry.parseObject(source);
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing local = new AiBetaRegistryView.LocalListing(
                1016L, "vinted-900100", "Titolo reale sintetico", "BASE_GAME", "EN", HASH_A);
        AiBetaRegistryView.CatalogRead read = snapshotProofRead(local);
        AiBetaRegistryView.Match match = AiBetaRegistryView.resolve(entry, read);
        assertFalse(match.isVerified());
        assertTrue(match.contentMatched);
        assertTrue(match.projectionContinuityProven);
        assertEquals(1016L, match.listing.localId);
        assertTrue(AiBetaRegistryView.details(registry, entry, read)
                .contains("item_id Vinted attuale: vinted-900100 (dato attuale, non prova d’identità storica)"));
    }

    @Test public void currentExternalIdAloneIsOnlyAnUnverifiedCandidate() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistryWithItemIdOnly("vinted-900100"));
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing local = new AiBetaRegistryView.LocalListing(
                7001L, "vinted-900100", "Titolo attuale", "BASE_GAME", "EN");
        AiBetaRegistryView.Match match = AiBetaRegistryView.resolve(entry,
                AiBetaRegistryView.CatalogRead.available(local));
        assertEquals(AiBetaRegistryView.MatchState.CURRENT_ID_CANDIDATE_ONLY, match.state);
        assertFalse(match.isVerified());
        assertTrue(AiBetaRegistryView.details(registry, entry,
                AiBetaRegistryView.CatalogRead.available(local))
                .contains("valori uguali, ma la provenienza dell’item_id nel registro non è dimostrata"));
    }

    @Test public void projectionMatchReportsMissingMetadataAndCurrentExternalIdSeparately() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistryWithItemId("vinted-900100"));
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing local = new AiBetaRegistryView.LocalListing(
                1016L, "vinted-900100", "Titolo reale sintetico", null, "", HASH_A);
        AiBetaRegistryView.CatalogRead read = snapshotProofRead(local);
        assertFalse(AiBetaRegistryView.resolve(entry, read).isVerified());
        assertTrue(AiBetaRegistryView.details(registry, entry, read)
                .contains("Metadati attuali: categoria mancante · lingua mancante"));
        AiBetaRegistryView.Match absentLegacy = AiBetaRegistryView.resolve(
                AiBetaRegistry.parseObject(validRegistry()).entries.get(0),
                AiBetaRegistryView.CatalogRead.available(Collections.emptyList()));
        assertEquals(AiBetaRegistryView.MatchState.UNVERIFIED_NO_CANDIDATE, absentLegacy.state);
    }

    @Test public void externalIdNotFoundDoesNotFallBackToEqualNumericIdAndQueryErrorsStayDistinct() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistryWithItemIdOnly("vinted-missing"));
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

    @Test public void reusedLocalIdWithChangedProjectionIsReportedWithoutClaimingReuseOrIdentity() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistryWithSourceEvidence());
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing reused = new AiBetaRegistryView.LocalListing(
                entry.listingId, "vinted-new-current-id", "Titolo diverso", "EXPANSION", "FR", HASH_B);
        AiBetaRegistryView.Match match = AiBetaRegistryView.resolve(entry, snapshotProofRead(reused));
        assertEquals(AiBetaRegistryView.MatchState.DATA_CHANGED, match.state);
        assertTrue(match.dataChanged);
        assertFalse(match.isVerified());
        String details = AiBetaRegistryView.details(registry, entry, snapshotProofRead(reused));
        assertTrue(details.contains("riuso dell’ID locale non è dimostrabile"));
        assertTrue(details.contains("item_id Vinted attuale: vinted-new-current-id"));
    }

    @Test public void missingHistoricalLocalRowIsNotConfusedWithCurrentAnnouncementIdentity() throws Exception {
        Map<String, Object> source = validRegistry();
        @SuppressWarnings("unchecked") Map<String, Object> row =
                (Map<String, Object>) ((java.util.List<?>) source.get("entries")).get(0);
        row.put("source_id", 1016L);
        row.put("source_hash", HASH_A);
        row.put("source_hash_version", 1L);
        AiBetaRegistry registry = AiBetaRegistry.parseObject(source);
        AiBetaRegistryView.LocalListing unrelated = new AiBetaRegistryView.LocalListing(
                7001L, "vinted-current", "Titolo uguale non probante", "BASE_GAME", "EN", HASH_B);
        AiBetaRegistryView.Match match = AiBetaRegistryView.resolve(registry.entries.get(0),
                snapshotProofRead(unrelated));
        assertEquals(AiBetaRegistryView.MatchState.LOCAL_ROW_ABSENT, match.state);
        assertFalse(match.isVerified());
    }

    @Test public void matchingProjectionWithoutCurrentVintedIdStillMatchesContentOnly() throws Exception {
        Map<String, Object> source = validRegistry();
        @SuppressWarnings("unchecked") Map<String, Object> row =
                (Map<String, Object>) ((java.util.List<?>) source.get("entries")).get(0);
        row.put("source_id", 1016L);
        row.put("source_hash", HASH_A);
        row.put("source_hash_version", 1L);
        AiBetaRegistry registry = AiBetaRegistry.parseObject(source);
        AiBetaRegistryView.LocalListing missingExternalId = new AiBetaRegistryView.LocalListing(
                1016L, null, "Titolo sintetico", "BASE_GAME", "EN", HASH_A);
        AiBetaRegistryView.CatalogRead read = snapshotProofRead(missingExternalId);
        AiBetaRegistryView.Match match = AiBetaRegistryView.resolve(registry.entries.get(0), read);
        assertEquals(AiBetaRegistryView.MatchState.CONTENT_MATCH_PROJECTION_PROVEN, match.state);
        assertTrue(match.contentMatched);
        assertTrue(match.externalIdentityMissing);
        assertFalse(match.isVerified());
        String header = AiBetaRegistryView.header(registry, read);
        assertTrue(header.contains("Identità Vinted storica dimostrata: 0"));
        assertTrue(header.contains("item_id corrente mancante: 1"));
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
                1016L, "vinted-900100", "Titolo reale sintetico", "BASE_GAME", "EN", HASH_A);
        AiBetaRegistryView.Match match = AiBetaRegistryView.resolve(entry, snapshotProofRead(local));
        assertTrue(match.contentMatched);
        assertFalse(match.isVerified());
        assertTrue(AiBetaRegistryView.acceptanceLabel(entry, match)
                        .contains("identità storica non dimostrata"));
        try {
            AiBetaRegistryDraft.create(registry, entry, match,
                    AiBetaRegistryDraft.choices(entry, local).get(0));
            fail("draft accepted without historical identity proof");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void currentTargetDraftKeepsHistoricalIdentityUnverifiedAndRequiresFlaggedReview() throws Exception {
        AiBetaRegistry registry = AiBetaRegistry.parseObject(validRegistryWithItemId("vinted-historic-id"));
        AiBetaRegistry.Entry entry = registry.entries.get(0);
        AiBetaRegistryView.LocalListing local = new AiBetaRegistryView.LocalListing(
                1016L, "vinted-current-id", "Titolo attuale", "BASE_GAME", "EN", HASH_A);
        AiBetaRegistryView.CatalogRead read = AiBetaRegistryView.CatalogRead.available(local);
        AiBetaRegistryView.Match match = AiBetaRegistryView.match(entry, read);
        assertEquals(AiBetaRegistryView.MatchState.CONTENT_MATCH_PROJECTION_UNPROVEN, match.state);
        assertTrue(match.contentMatched);
        assertFalse(match.isVerified());

        AiBetaRegistryDraft.Choice language = null;
        for (AiBetaRegistryDraft.Choice choice : AiBetaRegistryDraft.choices(entry, local))
            if (AiBetaRegistryDraft.FIELD_LANGUAGE.equals(choice.field)) language = choice;
        assertNotNull(language);
        assertTrue(language.reviewRequired);
        try {
            AiBetaRegistryDraft.createCurrentTarget(registry, entry, match, language,
                    true, true, false, false);
            fail("flagged proposal staged without human review");
        } catch (IllegalArgumentException expected) { }

        AiBetaRegistryDraft.Draft draft = AiBetaRegistryDraft.createCurrentTarget(
                registry, entry, match, language, true, true, false, true);
        assertTrue(draft.canApply());
        assertEquals("vinted-current-id", draft.itemId);
        assertEquals("vinted-historic-id", draft.sourceItemId);
        assertEquals(HASH_A, draft.currentHash);
        assertTrue(draft.reviewed);
        assertTrue(draft.details("invariato").contains("non dimostra retroattivamente l’identità storica"));


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

    private static Map<String, Object> validRegistryWithItemId(String itemId) {
        Map<String, Object> root = validRegistry();
        @SuppressWarnings("unchecked") Map<String, Object> row =
                (Map<String, Object>) ((java.util.List<?>) root.get("entries")).get(0);
        row.put("item_id", itemId);
        row.put("source_id", 1016L);
        row.put("source_hash", HASH_A);
        row.put("source_hash_version", 1L);
        return root;
    }

    private static Map<String, Object> validRegistryWithItemIdOnly(String itemId) {
        Map<String, Object> root = validRegistry();
        @SuppressWarnings("unchecked") Map<String, Object> row =
                (Map<String, Object>) ((java.util.List<?>) root.get("entries")).get(0);
        row.put("item_id", itemId);
        return root;
    }

    private static Map<String, Object> validRegistryWithSourceEvidence() {
        Map<String, Object> root = validRegistry();
        @SuppressWarnings("unchecked") Map<String, Object> row =
                (Map<String, Object>) ((java.util.List<?>) root.get("entries")).get(0);
        row.put("source_id", 1016L);
        row.put("source_hash", HASH_A);
        row.put("source_hash_version", 1L);
        return root;
    }

    private static AiBetaRegistryView.CatalogRead snapshotProofRead(AiBetaRegistryView.LocalListing... rows) {
        AiBetaRegistryView.SnapshotProof proof = new AiBetaRegistryView.SnapshotProof(
                "5c02314d-fd73-41b4-b1b3-be5448f374cd", "PUBLISHED", HASH_B, HASH_B,
                6, true, false);
        return AiBetaRegistryView.CatalogRead.available(Arrays.asList(rows), proof);
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
