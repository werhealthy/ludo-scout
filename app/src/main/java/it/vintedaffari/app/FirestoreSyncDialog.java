package it.vintedaffari.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;

/** One-time account connection in advanced settings; no password stored by Ludo. */
public final class FirestoreSyncDialog {
    private FirestoreSyncDialog() {}

    public static void show(Activity activity) {
        if (!FirestoreSync.configured()) {
            new AlertDialog.Builder(activity).setTitle("Sincronizzazione Ludo")
                    .setMessage("La configurazione Firebase non è presente in questa build. "
                            + "Il catalogo resta soltanto sul telefono.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (FirestoreSync.authorized(activity)) {
            String status = FirestoreSync.enabled(activity)
                            ? "Account collegato. Invio automatico delle sole schede annuncio selezionate; "
                                    + "non vengono condivisi preferiti o credenziali."
                            : "Account collegato, sincronizzazione in pausa.";
            String deviceId = "non disponibile";
            try { deviceId = FirestoreSync.deviceId(activity); }
            catch (Exception ignored) { }
            int padding = (int) (20 * activity.getResources().getDisplayMetrics().density);
            TextView details = new TextView(activity);
            String activationDiagnostic = FirestoreSync.activationDiagnostic(activity);
            String crashDiagnostic = ProcessCrashJournal.safeLatestCrashSummary(activity);
            if (crashDiagnostic.isEmpty()) {
                crashDiagnostic = ProcessCrashJournal.safeLatestSystemExitSummary(activity);
            }
            StringBuilder text = new StringBuilder(status);
            if (!activationDiagnostic.isEmpty()) {
                text.append("\n\nUltimo errore di attivazione (dati ridotti):\n")
                        .append(activationDiagnostic);
            }
            if (!crashDiagnostic.isEmpty()) {
                text.append("\n\nUltimo arresto registrato (senza messaggio né stack):\n")
                        .append(crashDiagnostic);
            }
            text.append("\n\nRiepilogo dell’ultimo worker Firestore:\n")
                    .append(FirestoreWorkerDiagnostics.summary(activity));
            text.append("\n\nID dispositivo per i comandi Telegram:\n").append(deviceId);
            details.setText(text.toString());
            details.setTextIsSelectable(true);
            details.setPadding(padding, padding / 2, padding, 0);
            AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                    .setTitle("Sincronizzazione Ludo")
                    .setView(details);
            if (!FirestoreSync.enabled(activity)) {
                dialog.setPositiveButton("Attiva", (d, w) ->
                        FirestoreSync.start(activity, active -> showActivationResult(activity, active)));
            }
            dialog.setNeutralButton("Disconnetti", (d, w) -> {
                FirestoreSync.stop(activity);
                Toast.makeText(activity, "Sincronizzazione disattivata", Toast.LENGTH_LONG).show();
            }).setNegativeButton("Chiudi", null).show();
            return;
        }
        int space = (int) (20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(space, space / 2, space, 0);
        TextView explanation = new TextView(activity);
        explanation.setText("Collega una sola volta l'account dedicato a Ludo. "
                + "Le credenziali non vengono salvate dall'app; Firebase gestisce la sessione.");
        fields.addView(explanation);
        EditText email = new EditText(activity);
        email.setHint("Email Firebase");
        email.setSingleLine(true);
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        fields.addView(email);
        EditText password = new EditText(activity);
        password.setHint("Password Firebase");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fields.addView(password);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Collega Ludo a Firestore")
                .setView(fields)
                .setPositiveButton("Collega", null)
                .setNegativeButton("Annulla", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String username = email.getText().toString().trim();
                    String secret = password.getText().toString();
                    password.setText("");
                    if (username.isEmpty() || secret.isEmpty()) {
                        Toast.makeText(activity, "Inserisci email e password", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FirebaseAuth auth = FirestoreSync.auth(activity);
                    if (auth == null) return;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    auth.signInWithEmailAndPassword(username, secret).addOnCompleteListener(activity, result -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        if (!result.isSuccessful() || !FirestoreSync.authorized(activity)) {
                            auth.signOut();
                            Toast.makeText(activity, "Account non autorizzato o accesso non riuscito",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        dialog.dismiss();
                        FirestoreSync.start(activity, active -> showActivationResult(activity, active));
                    });
                }));
        dialog.show();
    }

    private static void showActivationResult(Activity activity, boolean active) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String message = active ? "Account collegato. Sincronizzazione pianificata."
                : "Account collegato, sincronizzazione in pausa: pianificazione non riuscita.";
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
