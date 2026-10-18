package it.vintedaffari.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** SAF import flow: read the user-selected document once, then keep a private validated copy. */
public final class AiBetaRegistryImportUi {
    public static final int REQUEST_CODE = 5811;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private AiBetaRegistryImportUi() { }

    public static void start(Activity activity) {
        if (!ACTIVE.compareAndSet(false, true)) {
            Toast.makeText(activity, "Importazione già in corso.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_CODE);
        } catch (Exception failure) {
            ACTIVE.set(false);
            Toast.makeText(activity, "Selettore file non disponibile.", Toast.LENGTH_LONG).show();
        }
    }

    public static boolean handle(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) return false;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            ACTIVE.set(false);
            return true;
        }
        final Uri selected = data.getData();
        IO.execute(() -> {
            AiBetaRegistry registry = null;
            String error = null;
            try (InputStream input = activity.getContentResolver().openInputStream(selected)) {
                if (input == null) throw new IllegalArgumentException("File non leggibile");
                byte[] bytes = readBounded(input);
                registry = AiBetaRegistry.parse(bytes);
                AiBetaRegistryStore.replace(activity, bytes);
            } catch (Exception failure) {
                error = "File non valido o non leggibile; il registro precedente è rimasto invariato.";
            } finally {
                ACTIVE.set(false);
            }
            final AiBetaRegistry imported = registry;
            final String message = error;
            activity.runOnUiThread(() -> {
                if (imported != null) {
                    AiBetaRealDialog.onRegistryImported(activity, imported);
                    Toast.makeText(activity, "Registro importato (" + imported.entries.size() + " record). Catalogo invariato.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(activity, message == null ? "Importazione non riuscita." : message, Toast.LENGTH_LONG).show();
                }
            });
        });
        return true;
    }

    private static byte[] readBounded(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > AiBetaRegistry.MAX_BYTES) throw new IllegalArgumentException("File troppo grande");
            output.write(buffer, 0, count);
        }
        if (output.size() == 0) throw new IllegalArgumentException("File vuoto");
        return output.toByteArray();
    }
}
