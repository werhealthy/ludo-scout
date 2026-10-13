package it.vintedaffari.app;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public final class FirestoreSnapshotStoreBoundsTest {
    @Test public void totalSidecarAccountingIncludesSqliteAuxiliaryFiles() throws Exception {
        File dir = Files.createTempDirectory("snapshot-bounds").toFile();
        File database = new File(dir, "snapshot.db");
        try {
            Files.write(database.toPath(), new byte[3]);
            Files.write(new File(database.getPath() + "-journal").toPath(), new byte[5]);
            Files.write(new File(database.getPath() + "-wal").toPath(), new byte[7]);
            Files.write(new File(database.getPath() + "-shm").toPath(), new byte[11]);
            assertEquals(26L, FirestoreSnapshotStore.sidecarBytes(database));
            assertEquals(FirestoreSnapshotStore.MAX_BYTES,
                    FirestoreSnapshotStore.MAX_DATABASE_BYTES + FirestoreSnapshotStore.AUXILIARY_RESERVE_BYTES);
        } finally {
            File[] files = dir.listFiles();
            if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(dir.toPath());
        }
    }
}
