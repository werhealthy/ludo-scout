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

    @Test public void photoProjectionKeepsOnlyFourUniqueHttpsVintedUrls() {
        List<String> urls = FirestoreSnapshotWorker.photos(
                "https://images1.vinted.net/a.jpg, https://vinted.com/b.jpg, https://evil.test/c.jpg, http://vinted.net/d.jpg, https://vinted.net:8443/port.jpg, https://vinted.net/e.jpg",
                "https://images1.vinted.net/a.jpg");
        assertEquals(Arrays.asList("https://images1.vinted.net/a.jpg", "https://vinted.com/b.jpg", "https://vinted.net/e.jpg"), urls);
        assertEquals(4, FirestoreSnapshotWorker.photos(
                "https://vinted.net/1,https://vinted.net/2,https://vinted.net/3,https://vinted.net/4,https://vinted.net/5", "").size());
        assertTrue(FirestoreSnapshotWorker.photos(null, null).isEmpty());
    }
}
