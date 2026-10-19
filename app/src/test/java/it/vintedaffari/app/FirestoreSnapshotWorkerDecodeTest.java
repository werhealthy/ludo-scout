package it.vintedaffari.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import com.google.firebase.firestore.util.CustomClassMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public final class FirestoreSnapshotWorkerDecodeTest {
    @Test public void decoderConvertsJsonArraysAndObjectsToFirestoreJavaCollections() throws Exception {
        Map<String, Object> rawArray = new LinkedHashMap<>();
        rawArray.put("photo_urls", new JSONArray().put("https://vinted.net/a"));
        boolean sdkRejectsJsonArray = false;
        try {
            CustomClassMapper.convertToPlainJavaTypes(rawArray);
        } catch (RuntimeException expected) {
            sdkRejectsJsonArray = true;
        }
        assertTrue("Firestore serializer rejects org.json.JSONArray", sdkRejectsJsonArray);

        String payload = "{\"photo_urls\":[\"https://vinted.net/a\",\"https://vinted.com/b\"],"
                + "\"nested\":{\"values\":[1,null]},\"empty\":null}";

        Map<String, Object> decoded = FirestoreSnapshotWorker.decode(payload, "snapshot-test");

        assertTrue(decoded.get("photo_urls") instanceof List);
        assertEquals(Arrays.asList("https://vinted.net/a", "https://vinted.com/b"), decoded.get("photo_urls"));
        assertTrue(decoded.get("nested") instanceof Map);
        Map<?, ?> nested = (Map<?, ?>) decoded.get("nested");
        assertTrue(nested.get("values") instanceof List);
        assertEquals(Arrays.asList(1, null), nested.get("values"));
        assertEquals(null, decoded.get("empty"));
        assertEquals("snapshot-test", decoded.get("snapshot_id"));

        Map<String, Object> firestoreValues = CustomClassMapper.convertToPlainJavaTypes(decoded);
        assertEquals(decoded, firestoreValues);
    }
}
