from pathlib import Path

dialog=Path("app/src/main/java/it/vintedaffari/app/AiBetaRealDialog.java").read_text(encoding="utf-8")
worker=Path("tools/ai_local_worker.py").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")

checks={
 "copy button": 'copy.setText("Copia testo")' in dialog,
 "clipboard": "ClipboardManager" in dialog and "setPrimaryClip" in dialog,
 "selectable text": "status.setTextIsSelectable(true)" in dialog,
 "copy feedback": 'Toast.makeText(activity,"Testo copiato"' in dialog,
 "worker photo count": '"| con foto",with_photos,"| foto",total_photos' in worker,
 "worker queue idle": "Coda Qwen vuota · tutti i job ricevuti finora sono completati." in worker,
 "idle only after work": "worked_since_idle and not idle_announced" in worker,
 "version": "5.12.179-ai-copy-status" in gradle,
}
for name,ok in checks.items():
 print(("PASS" if ok else "FAIL"),name)
raise SystemExit(0 if all(checks.values()) else 1)
