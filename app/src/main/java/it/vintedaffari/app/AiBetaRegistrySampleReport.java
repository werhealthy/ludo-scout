package it.vintedaffari.app;

import android.content.Intent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal, local-only, read-only report for the five agreed registry candidates. */
final class AiBetaRegistrySampleReport {
    static final List<Long> REGISTRY_IDS = Collections.unmodifiableList(
            Arrays.asList(27L, 1099L, 1034L, 58L, 1L));

    private AiBetaRegistrySampleReport() { }

    static List<Long> localIdsToRead(AiBetaRegistry registry) {
        if (registry == null) return Collections.emptyList();
        Map<Long, AiBetaRegistry.Entry> byId = entriesById(registry);
        List<Long> localIds = new ArrayList<>();
        for (Long registryId : REGISTRY_IDS) {
            AiBetaRegistry.Entry entry = byId.get(registryId);
            if (entry != null) localIds.add(entry.sourceId == null ? entry.listingId : entry.sourceId);
        }
        return Collections.unmodifiableList(localIds);
    }

    static String build(AiBetaRegistry registry, Map<Long, AiBetaRegistryView.CurrentTargetRead> reads) {
        return build(registry, reads, Instant.now().toString());
    }

    static Intent shareIntent(String report) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, report == null ? "" : report);
        return send;
    }

    static String build(AiBetaRegistry registry, Map<Long, AiBetaRegistryView.CurrentTargetRead> reads,
                        String generatedAtUtc) {
        StringBuilder report = new StringBuilder("Ludo Scout · verifica campione AI · sola lettura\n")
                .append("Generato UTC: ").append(singleLine(generatedAtUtc, 40))
                .append("\nAI rieseguita: no · scrittura catalogo: no")
                .append("\nIdentità storica Vinted: non dimostrata; titolo e item_id sono valori correnti.");
        if (registry == null) return report.append("\nRegistro: non disponibile.").toString();
        report.append("\nRegistro: ").append(singleLine(registry.registryId, 80));
        Map<Long, AiBetaRegistry.Entry> byId = entriesById(registry);
        for (Long registryId : REGISTRY_IDS) {
            report.append("\n\nID registro=").append(registryId);
            AiBetaRegistry.Entry entry = byId.get(registryId);
            if (entry == null) {
                report.append(" · esito=RECORD_ASSENTE_NEL_REGISTRO");
                continue;
            }
            long localId = entry.sourceId == null ? entry.listingId : entry.sourceId;
            AiBetaRegistryView.CurrentTargetRead target = reads == null ? null : reads.get(localId);
            if (target == null || target.catalog == null) {
                report.append(" · lettura=NON_DISPONIBILE · esito=QUERY_NON_ESEGUITA");
                continue;
            }
            AiBetaRegistryView.Match match = AiBetaRegistryView.match(entry, target.catalog);
            AiBetaRegistryView.LocalListing listing = match == null ? null : match.listing;
            report.append(" · record=").append(entry.recordState)
                    .append(" · match=").append(match == null ? "NON_DISPONIBILE" : match.state.name());
            if (listing == null) {
                report.append("\nTitolo_corrente=NON_DISPONIBILE · local_id=NON_DISPONIBILE")
                        .append(" · item_id_Vinted_corrente=NON_DISPONIBILE")
                        .append("\nCategoria_corrente=NON_DISPONIBILE · language_code_corrente=NON_DISPONIBILE");
            } else {
                report.append("\nTitolo_corrente=").append(singleLine(listing.title, 120))
                        .append(" · local_id=").append(listing.localId)
                        .append(" · item_id_Vinted_corrente=").append(rawValue(listing.itemId))
                        .append("\nCategoria_corrente=").append(rawValue(listing.category))
                        .append(" · language_code_corrente=").append(rawValue(listing.language));
            }
            report.append("\nitem_id_univoco=").append(listing == null ? "NON_VERIFICABILE" : target.uniqueCurrentItemId)
                    .append(" · active=").append(listing == null ? "NON_VERIFICABILE" : target.active)
                    .append(" · manual_review_required=").append(listing == null ? "NON_VERIFICABILE" : target.manualReviewRequired)
                    .append("\nhash_riga_v3=").append(hashStatus(match, entry))
                    .append(" · source_hash_version=").append(entry.sourceHashVersion == null ? "NON_DISPONIBILE" : entry.sourceHashVersion)
                    .append(" · continuità_root_proiezione=").append(match != null && match.projectionContinuityProven ? "PROVATA" : "NON_PROVATA")
                    .append("\nProposta_originale=").append(proposal(entry.originalType, entry.originalLanguage))
                    .append(" · fonte=").append(singleLine(entry.originalSource, 80))
                    .append("\nProposta_A_B=").append(entry.correctedSource == null ? "NON_DISPONIBILE"
                            : proposal(entry.correctedType, entry.correctedLanguage))
                    .append(" · flag_revisione=").append(entry.reviewFlags.isEmpty() ? "NESSUNO" : entry.reviewFlags)
                    .append("\nesito_tecnico=").append(decision(entry, target, match));
        }
        return report.toString();
    }

    private static Map<Long, AiBetaRegistry.Entry> entriesById(AiBetaRegistry registry) {
        Map<Long, AiBetaRegistry.Entry> result = new LinkedHashMap<>();
        if (registry != null) for (AiBetaRegistry.Entry entry : registry.entries)
            result.put(entry.listingId, entry);
        return result;
    }

    private static String decision(AiBetaRegistry.Entry entry,
                                   AiBetaRegistryView.CurrentTargetRead target,
                                   AiBetaRegistryView.Match match) {
        List<String> blockers = new ArrayList<>();
        if (target.catalog.state != AiBetaRegistryView.CatalogState.AVAILABLE) {
            blockers.add(target.catalog.state.name());
        } else if (match == null || match.listing == null) {
            blockers.add(match == null ? "MATCH_NON_DISPONIBILE" : match.state.name());
        } else {
            if (!match.contentMatched) blockers.add(match.dataChanged ? "HASH_DIVERSO" : "HASH_NON_VERIFICABILE");
            if (match.listing.itemId == null || match.listing.itemId.trim().isEmpty() || !target.uniqueCurrentItemId)
                blockers.add("ITEM_ID_MANCANTE_O_NON_UNIVOCO");
            if (!target.active) blockers.add("SCHEDA_NON_ATTIVA");
            if (target.manualReviewRequired) blockers.add("REVISIONE_MANUALE_GIA_RICHIESTA");
            if (!"PROPOSED".equals(entry.recordState)) blockers.add("RECORD_NON_PROPOSTO");
            if (AiBetaRegistryDraft.choices(entry, match.listing).isEmpty()) blockers.add("NESSUNA_PROPOSTA_NON_UNKNOWN_DIVERSA");
        }
        if (!blockers.isEmpty()) return "BLOCCATO:" + String.join(",", blockers);
        if (!entry.reviewFlags.isEmpty()) return "REVISIONE_RICHIESTA:" + String.join(",", entry.reviewFlags);
        return "CONTROLLI_TECNICI_SUPERATI_SOLO_REVISIONE; NESSUNA_AUTORIZZAZIONE_DI_SCRITTURA";
    }

    private static String hashStatus(AiBetaRegistryView.Match match, AiBetaRegistry.Entry entry) {
        if (match == null || entry.sourceHash == null || entry.sourceHashVersion == null)
            return "NON_DISPONIBILE";
        if (match.contentMatched) return "UGUALE";
        if (match.dataChanged) return "DIVERSO";
        return "NON_CONFRONTABILE";
    }

    private static String proposal(String category, String language) {
        return "categoria=" + rawValue(category) + ",lingua=" + rawValue(language);
    }

    private static String rawValue(String value) {
        if (value == null) return "<NULL>";
        if (value.isEmpty()) return "<VUOTO>";
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length() && escaped.length() < 120; i++) {
            char c = value.charAt(i);
            if (c == '\r') escaped.append("\\r");
            else if (c == '\n') escaped.append("\\n");
            else if (c == '\t') escaped.append("\\t");
            else if (Character.isISOControl(c)) escaped.append('?');
            else escaped.append(c);
        }
        if (value.length() > 80) escaped.append('…');
        return escaped.toString();
    }

    private static String singleLine(String value, int maxChars) {
        if (value == null) return "NON_DISPONIBILE";
        StringBuilder clean = new StringBuilder();
        boolean pendingSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                pendingSpace = clean.length() > 0;
                continue;
            }
            if (pendingSpace) clean.append(' ');
            clean.append(c);
            pendingSpace = false;
            if (clean.length() >= maxChars) {
                clean.append('…');
                break;
            }
        }
        return clean.length() == 0 ? "<VUOTO>" : clean.toString();
    }
}
