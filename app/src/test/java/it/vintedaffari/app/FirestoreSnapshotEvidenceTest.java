package it.vintedaffari.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class FirestoreSnapshotEvidenceTest {
    @Test public void descriptionIsBoundedByUnicodeCodePointAndNullIsEmpty() {
        String source = "  A😀BC  ";
        assertEquals("A😀B", FirestoreSnapshotWorker.boundedCodePoints(source, 3));
        assertEquals("", FirestoreSnapshotWorker.boundedCodePoints(null, 2000));
        assertEquals("", FirestoreSnapshotWorker.boundedCodePoints("  ", 2000));
    }

    @Test public void evidenceProjectionIsBoundedAndFailClosedByConsentMode() throws Exception {
        String source = readProjectSource("app/src/main/java/it/vintedaffari/app/FirestoreSnapshotWorker.java");
        assertTrue(source.contains("private static Row project(Cursor c,boolean includeEvidence)"));
        int consentBranch = source.indexOf("if(includeEvidence){");
        assertTrue(consentBranch >= 0);
        assertTrue(source.indexOf("v.put(\"observed_text\"", consentBranch) > consentBranch);
        assertTrue(source.indexOf("v.put(\"photo_urls\"", consentBranch) > consentBranch);
        assertTrue(source.contains("MAX_EVIDENCE_TEXT = 2_000"));
        assertTrue(source.contains("MAX_PHOTO_URLS = 4"));
    }

    private static String readProjectSource(String relativePath) throws Exception {
        java.io.File dir = new java.io.File(System.getProperty("user.dir"));
        while (dir != null && !new java.io.File(dir, relativePath).isFile()) dir = dir.getParentFile();
        if (dir == null) throw new java.io.IOException("project_source_not_found:" + relativePath);
        return new String(java.nio.file.Files.readAllBytes(new java.io.File(dir, relativePath).toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test public void evidenceProjectionChangesContentHash() throws Exception {
        String source = readProjectSource("app/src/main/java/it/vintedaffari/app/FirestoreSnapshotWorker.java");
        int evidenceBranch = source.indexOf("if(includeEvidence){");
        int evidenceFields = source.indexOf("v.put(\"observed_text\"", evidenceBranch);
        int hash = source.indexOf("String hash=", evidenceFields);
        assertTrue(evidenceBranch >= 0 && evidenceFields > evidenceBranch && hash > evidenceFields);
        assertTrue(source.contains("sourceHash(File file,boolean includeEvidence)"));
    }

    @Test public void consentCheckboxIsSharedByAllFirestoreDialogStates() throws Exception {
        String source = readProjectSource("app/src/main/java/it/vintedaffari/app/FirestoreSyncDialog.java");
        assertTrue(source.contains("showUnavailable(activity, \"Account Firestore scollegato."));
        assertTrue(source.contains("showStateDialog(activity, \"Account collegato, sincronizzazione in pausa.\", true)"));
        assertTrue(source.contains("addEvidenceConsent(activity, content, padding)"));
        assertTrue(source.contains("FirestoreSync.persistEvidenceConsentOnly(activity, checked)"));
        assertTrue(source.contains("FirestoreSync.existingAuth(activity)"));
        assertTrue(source.contains("Se la sync è in pausa o l'account è scollegato, i dati già caricati restano online."));
    }

    @Test public void evidenceOptOutScrubsDurableLocalRowsBeforeMetadataOnlyGeneration() {
        assertTrue(FirestoreSyncSourceContract.sidecarRevocationIsFailClosed());
    }

    @Test public void revocationDuringUploadIsCheckedBeforeBatchCommit() throws Exception {
        String worker = readProjectSource("app/src/main/java/it/vintedaffari/app/FirestoreSnapshotWorker.java");
        int uploadStart = worker.indexOf("store.markUploadAttempted(done)");
        int gate = worker.indexOf("commitIfEvidenceModeMatches(", uploadStart);
        int submit = worker.indexOf("batch::commit", gate);
        int await = worker.indexOf("Tasks.await(uploadTask", submit);
        assertTrue(uploadStart >= 0);
        assertTrue(gate > uploadStart);
        assertTrue(submit > gate);
        assertTrue(await > submit);
        assertTrue(FirestoreSyncSourceContract.evidenceRevocationScrubsBeforeConsentCommit());
        assertTrue(FirestoreSyncSourceContract.sidecarRevocationIsFailClosed());
    }

    @Test public void explicitEvidenceConsentRequiresItsOwnVersion() {
        assertTrue(FirestoreEvidenceConsentPolicy.accepts(true, "description-photo-v1"));
        assertFalse(FirestoreEvidenceConsentPolicy.accepts(true, ""));
        assertFalse(FirestoreEvidenceConsentPolicy.accepts(false, "description-photo-v1"));
        assertTrue(FirestoreSyncSourceContract.evidenceConsentDoesNotMigrateFromMetadata());
    }

    @Test public void fullEvidenceContract() throws Exception {
        descriptionIsBoundedByUnicodeCodePointAndNullIsEmpty();
        evidenceProjectionIsBoundedAndFailClosedByConsentMode();
        evidenceProjectionChangesContentHash();
        evidenceOptOutScrubsDurableLocalRowsBeforeMetadataOnlyGeneration();
        revocationDuringUploadIsCheckedBeforeBatchCommit();
        explicitEvidenceConsentRequiresItsOwnVersion();
        photoProjectionKeepsOnlyFourUniqueHttpsVintedUrls();
    }
    @Test public void disconnectRevokesMetadataAndEvidenceConsents() {
        assertTrue(FirestoreSyncSourceContract.sourceDisconnectClearsBothConsents());
    }

    private static void photoProjectionKeepsOnlyFourUniqueHttpsVintedUrls() {
        List<String> urls = FirestoreSnapshotWorker.photos(
                "https://images1.vinted.net/a.jpg, https://vinted.com/b.jpg, https://evil.test/c.jpg, http://vinted.net/d.jpg, https://vinted.net:8443/port.jpg, https://vinted.net/e.jpg",
                "https://images1.vinted.net/a.jpg");
        assertEquals(Arrays.asList("https://images1.vinted.net/a.jpg", "https://vinted.com/b.jpg", "https://vinted.net/e.jpg"), urls);
        assertEquals(4, FirestoreSnapshotWorker.photos(
                "https://vinted.net/1,https://vinted.net/2,https://vinted.net/3,https://vinted.net/4,https://vinted.net/5", "").size());
        assertTrue(FirestoreSnapshotWorker.photos(null, null).isEmpty());
    }
}
