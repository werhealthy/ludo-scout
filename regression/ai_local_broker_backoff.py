from pathlib import Path

worker=Path("tools/ai_local_worker.py").read_text(encoding="utf-8")

checks={
 "bounded HTTP error body": 'raw=error.read(1024)' in worker,
 "surfaces broker status": 'status=data.get("status")' in worker,
 "recognizes Cloudflare 1027": 'if "1027" in compact: return "CLOUDFLARE_1027"' in worker,
 "backs off quota/config HTTP": 'return 60 if code in (429,503) or "1027" in detail else 5' in worker,
 "initial heartbeat uses backoff": 'time.sleep(broker_backoff_seconds(e.code,detail));continue' in worker,
 "processing error uses backoff": 'time.sleep(broker_backoff_seconds(e.code,detail))' in worker,
 "idle polling remains 30s": 'time.sleep(30);continue' in worker,
}
for name,ok in checks.items():
 print(("PASS" if ok else "FAIL"),name)
raise SystemExit(0 if all(checks.values()) else 1)
