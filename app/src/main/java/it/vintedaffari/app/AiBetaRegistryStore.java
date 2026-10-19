package it.vintedaffari.app;

import android.content.Context;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** App-private storage for the imported overlay; it never writes the catalog database. */
final class AiBetaRegistryStore {
    private AiBetaRegistryStore() { }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), "ai-suggestions-registry.json"));
    }

    static AiBetaRegistry load(Context context) throws Exception {
        AtomicFile target = file(context);
        if (!target.getBaseFile().isFile()) return null;
        try (FileInputStream input = target.openRead(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > AiBetaRegistry.MAX_BYTES) throw new IOException("Registro locale troppo grande");
                output.write(buffer, 0, count);
            }
            return AiBetaRegistry.parse(output.toByteArray());
        }
    }

    static void replace(Context context, byte[] bytes) throws Exception {
        AiBetaRegistry.parse(bytes);
        AtomicFile target = file(context);
        FileOutputStream output = target.startWrite();
        try {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
            target.finishWrite(output);
        } catch (Exception failure) {
            target.failWrite(output);
            throw failure;
        }
    }
}
