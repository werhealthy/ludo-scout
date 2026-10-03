package it.vintedaffari.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** System file picker; export and copying run off the UI thread. No automatic upload. */
public final class ClassificationAuditExportUi {
    private static final int REQUEST = 5810;
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private ClassificationAuditExportUi() {}

    public static void start(Activity activity) {
        if (!BUSY.compareAndSet(false, true)) {
            Toast.makeText(activity, "Esportazione già in corso", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "ludo-audit-" + System.currentTimeMillis() + ".zip");
        try { activity.startActivityForResult(intent, REQUEST); }
        catch (RuntimeException unavailable) {
            BUSY.set(false);
            Toast.makeText(activity, "Selettore file non disponibile", Toast.LENGTH_LONG).show();
        }
    }

    public static boolean handle(Activity activity, int request, int result, Intent data, String databaseName) {
        if (request != REQUEST) return false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) {
            BUSY.set(false);
            return true;
        }
        Context context = activity.getApplicationContext();
        Uri destination = data.getData();
        Toast.makeText(activity, "Preparazione archivio per audit…", Toast.LENGTH_LONG).show();
        IO.execute(() -> {
            File archive = null;
            String message;
            try {
                archive = ClassificationAuditExport.create(context.getDatabasePath(databaseName),
                        new File(context.getCacheDir(), "classification-audit"), BuildConfig.VERSION_NAME);
                try (FileInputStream input = new FileInputStream(archive);
                     OutputStream output = context.getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) throw new java.io.IOException("Destinazione non disponibile");
                    byte[] buffer = new byte[32768];
                    int size;
                    while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
                }
                message = "Archivio audit salvato. Nessun dato modificato.";
            } catch (Exception failure) {
                try { DocumentsContract.deleteDocument(context.getContentResolver(), destination); }
                catch (Exception ignored) { /* Provider may not support deletion. Report the failure. */ }
                message = "Esportazione non riuscita (" + failure.getClass().getSimpleName()
                        + "). Elimina l’eventuale file incompleto e riprova.";
            } finally {
                if (archive != null) archive.delete();
                BUSY.set(false);
            }
            final String outcome = message;
            activity.runOnUiThread(() -> {
                if (!activity.isDestroyed() && !activity.isFinishing())
                    Toast.makeText(activity, outcome, Toast.LENGTH_LONG).show();
            });
        });
        return true;
    }
}
