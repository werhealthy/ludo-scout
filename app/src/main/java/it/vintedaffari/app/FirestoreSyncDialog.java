package it.vintedaffari.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;

/** One-time account connection in advanced settings; no password stored by Ludo. */
public final class FirestoreSyncDialog {
    private FirestoreSyncDialog() {}

    /** Testable UI seam: launches and presents only the Firestore settings dialog. */
    public static void showSettings(Activity activity) { show(activity); }

    public static void show(Activity activity) {
        if (!FirestoreSync.configured()) {
            showUnavailable(activity, "La configurazione Firebase non è presente in questa build. Il catalogo resta soltanto sul telefono.");
            return;
        }
        if (!FirestoreSync.authorized(activity)) {
            showUnavailable(activity, "Account Firestore scollegato. Modificare il consenso non collega l'account, non avvia la sync e non invia dati.");
            return;
        }
        if (!FirestoreSync.enabled(activity)) {
            showStateDialog(activity, "Account collegato, sincronizzazione in pausa.", true);
            return;
        }
        showActiveDialog(activity);
    }

    private static void showActiveDialog(Activity activity) {
        String status = "Account collegato. Invio automatico delle sole schede annuncio selezionate; non vengono condivisi preferiti o credenziali.";
        String deviceId = "non disponibile";
        try { deviceId = FirestoreSync.deviceId(activity); } catch (Exception ignored) { }
        int padding = (int) (20 * activity.getResources().getDisplayMetrics().density);
        TextView details = new TextView(activity);
        String activationDiagnostic = FirestoreSync.activationDiagnostic(activity);
        String crashDiagnostic = ProcessCrashJournal.safeLatestCrashSummary(activity);
        if (crashDiagnostic.isEmpty()) crashDiagnostic = ProcessCrashJournal.safeLatestSystemExitSummary(activity);
        StringBuilder text = new StringBuilder(status);
        if (!activationDiagnostic.isEmpty()) text.append("\n\nUltimo errore di attivazione (dati ridotti):\n").append(activationDiagnostic);
        if (!crashDiagnostic.isEmpty()) text.append("\n\nUltimo arresto registrato (senza messaggio né stack):\n").append(crashDiagnostic);
        text.append("\n\nRiepilogo dell’ultimo worker Firestore:\n").append(FirestoreWorkerDiagnostics.summary(activity));
        final String workManagerHeading = "\n\nStato del lavoro Firestore in WorkManager:\n";
        final String workManagerPlaceholder = "lettura locale in corso…";
        text.append(workManagerHeading).append(workManagerPlaceholder);
        text.append("\n\nID dispositivo per i comandi Telegram:\n").append(deviceId);
        details.setText(text.toString());
        details.setTextIsSelectable(true);
        details.setPadding(padding, padding / 2, padding, 0);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(details);
        addEvidenceConsent(activity, content, padding);
        android.app.AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Sincronizzazione Ludo").setView(content)
                .setPositiveButton("Attiva", (d, w) -> FirestoreSync.start(activity, active -> showActivationResult(activity, active)))
                .setNeutralButton("Disconnetti", (d, w) -> {
                    FirestoreSync.stop(activity);
                    Toast.makeText(activity, "Sincronizzazione disattivata", Toast.LENGTH_LONG).show();
                }).setNegativeButton("Chiudi", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        FirestoreSync.readWorkManagerDiagnostic(activity, summary -> {
            if (activity.isFinishing() || activity.isDestroyed() || details.getWindowToken() == null) return;
            String current = details.getText().toString();
            if (current.contains(workManagerPlaceholder)) details.setText(current.replace(workManagerPlaceholder, summary));
        });
    }


    private static void showStateDialog(Activity activity, String status, boolean paused) {
        int padding=(int)(20*activity.getResources().getDisplayMetrics().density);
        LinearLayout content=new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        TextView details=new TextView(activity); details.setText(status); details.setPadding(padding,padding/2,padding,0); content.addView(details);
        addEvidenceConsent(activity,content,padding);
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle("Sincronizzazione Ludo").setView(content);
        if(paused) builder.setPositiveButton("Attiva",(d,w)->FirestoreSync.start(activity,active->showActivationResult(activity,active)));
        builder.setNegativeButton("Chiudi",null).show();
    }

    private static void showUnavailable(Activity activity, String status) {
        int padding = (int) (20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView details = new TextView(activity);
        details.setText(status);
        details.setPadding(padding, padding / 2, padding, 0);
        content.addView(details);
        addEvidenceConsent(activity, content, padding);
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle("Sincronizzazione Ludo").setView(content);
        dialog.setPositiveButton(FirestoreSync.configured() ? "Collega" : "OK", null)
                .setNegativeButton("Chiudi", null);
        AlertDialog shown = dialog.create();
        if (FirestoreSync.configured()) {
            shown.setOnShowListener(d -> shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                shown.dismiss();
                showAccountLink(activity);
            }));
        }
        shown.show();
    }

    private static void showAccountLink(Activity activity) {
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
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Collega Ludo a Firestore")
                .setView(fields).setPositiveButton("Collega", null).setNegativeButton("Annulla", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
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
                    Toast.makeText(activity, "Account non autorizzato o accesso non riuscito", Toast.LENGTH_LONG).show();
                    return;
                }
                dialog.dismiss();
                FirestoreSync.start(activity, active -> showActivationResult(activity, active));
            });
        }));
        dialog.show();
    }

    private static void addEvidenceConsent(Activity activity, LinearLayout content, int padding) {
        TextView notice = new TextView(activity);
        notice.setText("Consenso separato dai metadati e disattivato per impostazione predefinita. Cambiarlo non collega l'account, non avvia la sync e non invia dati. Se attivo, descrizioni e URL foto saranno inclusi solo in una sincronizzazione autorizzata. Revocandolo, il worker si sospende e i dati evidence locali vengono rimossi preparando uno snapshot senza evidence. La copia già caricata su Firestore non viene cancellata automaticamente: per sostituirla occorre che la sync dei metadati resti attiva e completi un nuovo snapshot. Se la sync è in pausa o l'account è scollegato, i dati già caricati restano online.");
        notice.setPadding(padding, padding / 2, padding, 0);
        content.addView(notice);
        CheckBox checkbox = new CheckBox(activity);
        checkbox.setText("Consento il trasferimento di descrizioni e URL foto a Firestore");
        checkbox.setChecked(FirestoreSync.evidenceConsented(activity));
        FirebaseAuth existing = FirestoreSync.existingAuth(activity);
        boolean authorized = existing != null && existing.getCurrentUser() != null
                && BuildConfig.LUDO_SYNC_OWNER_UID.equals(existing.getCurrentUser().getUid());
        checkbox.setEnabled(FirestoreSync.configured() && authorized);
        boolean[] persisting = {false};
        checkbox.setOnCheckedChangeListener((button, checked) -> {
            if (persisting[0]) return;
            persisting[0] = true;
            button.setEnabled(false);
            new Thread(() -> {
                boolean wasEnabled = FirestoreSync.enabled(activity);
                boolean saved = FirestoreSync.persistEvidenceConsentOnly(activity, checked);
                activity.runOnUiThread(() -> {
                    button.setEnabled(true);
                    persisting[0] = false;
                    if (FirestoreSync.enabled(activity) != wasEnabled) {
                        Toast.makeText(activity, "Il consenso non modifica la sincronizzazione dei metadati", Toast.LENGTH_LONG).show();
                    }
                    if (!saved) {
                        persisting[0] = true;
                        button.setChecked(!checked);
                        persisting[0] = false;
                        Toast.makeText(activity, "Scelta evidence non salvata", Toast.LENGTH_LONG).show();
                    } else if (!checked && FirestoreSync.evidenceCleanupPending(activity)) {
                        Toast.makeText(activity, "Consenso revocato; pulizia locale in attesa di un nuovo tentativo", Toast.LENGTH_LONG).show();
                    }
                });
            }, "ludo-evidence-consent").start();
        });
        content.addView(checkbox);
    }

    private static void showActivationResult(Activity activity, boolean active) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String message = active ? "Account collegato. Sincronizzazione pianificata."
                : "Account collegato, sincronizzazione in pausa: pianificazione non riuscita.";
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
