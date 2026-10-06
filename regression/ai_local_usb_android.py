from pathlib import Path

root=Path(__file__).resolve().parents[1]
protocol=(root/"app/src/main/java/it/vintedaffari/app/AiBetaProtocol.java").read_text(encoding="utf-8")
client=(root/"app/src/main/java/it/vintedaffari/app/AiBetaClient.java").read_text(encoding="utf-8")
dialog=(root/"app/src/main/java/it/vintedaffari/app/AiBetaTestDialog.java").read_text(encoding="utf-8")
engine=(root/"app/src/main/java/it/vintedaffari/app/AiEngineSession.java").read_text(encoding="utf-8")
main_manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
debug_manifest=(root/"app/src/debug/AndroidManifest.xml").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")

checks={
 "exact USB endpoint": 'LOCAL_USB_ENDPOINT="http://127.0.0.1:8765"' in protocol,
 "debug-only endpoint": 'if(!BuildConfig.DEBUG||value==null)return false' in protocol,
 "reject localhost alias": '"127.0.0.1".equals(u.getHost())&&u.getPort()==8765' in protocol,
 "local token separated from cloud token": 'LOCAL_USB_TOKEN="ludo-local-usb-debug"' in protocol and 'validToken' in protocol,
 "generic HttpURLConnection keeps HTTPS support": 'import java.net.HttpURLConnection;' in client and 'HttpsURLConnection' not in client,
 "local Qwen timeout only": 'AiBetaProtocol.isLocalUsbEndpoint(endpoint)?240000:35000' in client,
 "one-tap USB config": 'Usa Qwen sul PC via USB' in dialog and 'LOCAL_USB_ENDPOINT' in dialog,
 "engine uses endpoint-aware auth": 'AiBetaProtocol.validToken(config.optString("endpoint"),config.optString("token"))' in engine,
 "main manifest stays cleartext-default": 'usesCleartextTraffic' not in main_manifest,
 "debug manifest owns cleartext": 'android:usesCleartextTraffic="true"' in debug_manifest,
 "version": "5.12.202-ai-wait-deadline" in gradle,
}
for name,ok in checks.items():
 print(("PASS" if ok else "FAIL"),name)
raise SystemExit(0 if all(checks.values()) else 1)

