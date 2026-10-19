package it.vintedaffari.app;

import android.app.Application;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public final class AiBetaRegistrySampleReportTest {
    @Test public void reportSummarizesFiveTargetsAndExcludesDescriptionsPhotosAndHashes() throws Exception {
        AiBetaRegistry registry = registry();
        List<AiBetaRegistryView.LocalListing> rows = new ArrayList<>();
        for (Long id : AiBetaRegistrySampleReport.REGISTRY_IDS) {
            String rowHash = id == 1034L ? "b".repeat(64) : "a".repeat(64);
            String language = id == 27L ? "?|DEP" : id == 1099L ? "NL|IND" : "UNKNOWN";
            rows.add(new AiBetaRegistryView.LocalListing(id, "current-vinted-" + id,
                    "Current listing " + id, "BASE_GAME", language, rowHash));
        }
        AiBetaRegistryView.CatalogRead catalog = AiBetaRegistryView.CatalogRead.available(rows);
        Map<Long, AiBetaRegistryView.CurrentTargetRead> reads = new LinkedHashMap<>();
        for (Long id : AiBetaRegistrySampleReport.REGISTRY_IDS) {
            boolean active = id != 58L;
            boolean unique = id != 58L;
            reads.put(id, new AiBetaRegistryView.CurrentTargetRead(catalog, unique, active, false));
        }

        String report = AiBetaRegistrySampleReport.build(registry, reads, "2026-10-19T12:00:00Z");
        assertEquals(5, report.split("ID registro=", -1).length - 1);
        for (Long id : AiBetaRegistrySampleReport.REGISTRY_IDS)
            assertTrue(report.contains("ID registro=" + id));
        assertTrue(report.contains("language_code_corrente=?|DEP"));
        assertTrue(report.contains("language_code_corrente=NL|IND"));
        assertTrue(report.contains("hash_riga_v3=UGUALE"));
        assertTrue(report.contains("hash_riga_v3=DIVERSO"));
        assertTrue(report.contains("item_id_univoco=false"));
        assertTrue(report.contains("SCHEDA_NON_ATTIVA"));
        assertTrue(report.contains("Proposta_A_B=categoria=BASE_GAME,lingua=FR"));
        assertTrue(report.contains("scrittura catalogo: no"));
        assertFalse(report.contains("observed_text"));
        assertFalse(report.contains("photo_urls"));
        assertFalse(report.contains("https://"));
        assertFalse(report.contains("BGG_TOKEN"));
        assertFalse(report.contains("a".repeat(64)));
        assertFalse(report.contains("b".repeat(64)));
    }

    @Test public void shareIntentContainsOnlyPlainTextReport() {
        String report = "safe report";
        Intent intent = AiBetaRegistrySampleReport.shareIntent(report);
        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/plain", intent.getType());
        assertEquals(report, intent.getStringExtra(Intent.EXTRA_TEXT));
        assertEquals(Collections.singleton(Intent.EXTRA_TEXT), intent.getExtras().keySet());
    }

    @Test public void sampleContainsExactlyTheFiveApprovedRegistryIds() {
        assertEquals(Arrays.asList(27L, 1099L, 1034L, 58L, 1L), AiBetaRegistrySampleReport.REGISTRY_IDS);
    }

    private static AiBetaRegistry registry() throws Exception {
        Map<String, Object> root = new HashMap<>();
        root.put("schema_version", 1L);
        root.put("registry_id", "ludo-language-suggestions-20261018");
        root.put("source_snapshot_id", "5c02314d-fd73-41b4-b1b3-be5448f374cd");
        root.put("generated_at", "2026-10-18");
        root.put("model_id", null);
        root.put("model_id_status", "not_preserved_in_source_register");
        root.put("historical_prompt_id", null);
        root.put("corrected_ab_prompt_id", "language_and_category_clarity_v1");
        root.put("candidate_prompt_commit", "0123456789abcdef0123456789abcdef01234567");
        root.put("candidate_prompt_executed", false);
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry(27L, "BASE_GAME", "NL", "historical_register_unversioned",
                null, null, null, Collections.emptyList()));
        entries.add(entry(1099L, "BASE_GAME", "NL", "paired_ab_baseline",
                "BASE_GAME", "NL", "language_and_category_clarity_v1", Collections.emptyList()));
        entries.add(entry(1034L, "BASE_GAME", "UNKNOWN", "paired_ab_baseline",
                "BASE_GAME", "FR", "language_and_category_clarity_v1",
                Collections.singletonList("LANGUAGE_REANALYSIS")));
        entries.add(entry(58L, "BUNDLE", "UNKNOWN", "historical_register_unversioned",
                null, null, null, Collections.emptyList()));
        entries.add(entry(1L, "UNKNOWN", "UNKNOWN", "historical_register_unversioned",
                null, null, null, Collections.emptyList()));
        root.put("entries", entries);
        return AiBetaRegistry.parseObject(root);
    }

    private static Map<String, Object> entry(long id, String originalType, String originalLanguage,
                                             String originalSource, String correctedType,
                                             String correctedLanguage, String correctedSource,
                                             List<String> flags) {
        Map<String, Object> row = new HashMap<>();
        row.put("listing_id", id);
        row.put("source_id", id);
        row.put("source_hash", "a".repeat(64));
        row.put("source_hash_version", 1L);
        row.put("original_type", originalType);
        row.put("original_language", originalLanguage);
        row.put("original_source", originalSource);
        row.put("corrected_type", correctedType);
        row.put("corrected_language", correctedLanguage);
        row.put("corrected_source", correctedSource);
        row.put("record_state", "PROPOSED");
        row.put("review_flags", flags);
        return row;
    }
}
