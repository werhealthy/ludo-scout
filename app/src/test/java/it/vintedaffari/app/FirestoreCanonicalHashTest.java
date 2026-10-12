package it.vintedaffari.app;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public final class FirestoreCanonicalHashTest {
    @Test public void canonicalRowHashMatchesHermesGoldenVector() throws Exception {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("e", null);
        row.put("d", false);
        row.put("c", 42L);
        row.put("b", java.util.Arrays.asList("é", "x\n"));
        row.put("a", "é😀\"\\\n");
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(FirestoreSnapshotWorker.canonicalJson(row).getBytes(StandardCharsets.UTF_8));
        assertEquals("4c9480a3cde4dd8d9c552fbdd3eb1c39299a7efffb69cacacc53206e76c41787", hex(bytes));
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }
}
