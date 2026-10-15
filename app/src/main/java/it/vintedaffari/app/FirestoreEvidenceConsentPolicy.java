package it.vintedaffari.app;

/** Separate, fail-closed consent for transmitting listing descriptions and photo URLs. */
final class FirestoreEvidenceConsentPolicy {
    static final String CURRENT_VERSION = "description-photo-v1";

    private FirestoreEvidenceConsentPolicy() {}

    static boolean accepts(boolean enabled, String version) {
        return enabled && CURRENT_VERSION.equals(version);
    }
}