package it.vintedaffari.app;

/** Pure policy for preserving a previously explicit Firestore opt-in across schema upgrades. */
final class FirestoreOptInPolicy {
    static final String CURRENT_VERSION = "minimal-v3";

    private FirestoreOptInPolicy() {}

    static boolean accepts(boolean enabled, String version) {
        // Before minimal-v3, explicit opt-in was stored only as enabled=true.
        // Preserve that existing state; unknown non-empty version markers fail closed.
        return enabled && (version == null || version.isEmpty() || CURRENT_VERSION.equals(version));
    }
}
