from pathlib import Path
import os
import sys
import tempfile
import io
import json
import threading
import urllib.error
import urllib.request
from contextlib import redirect_stdout
from http.server import ThreadingHTTPServer

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
import ai_local_bridge as bridge

with tempfile.TemporaryDirectory() as td:
 os.environ["LUDO_AI_LOCAL_CACHE"]=str(Path(td)/"cache.json")
 calls={"n":0}
 def fake(row):
  calls["n"]+=1
  return {"listing_id":row["listing_id"],"proposed_type":"BASE_GAME","confidence":None,"evidence":"Titolo coerente","language":"UNKNOWN","product_title":row["title"]}
 row={"listing_id":1,"title":"Catan","brand":"Kosmos","source_text":"","photos":[]}
 payload={"request_id":"request-0001","records":[row]}
 code,body=bridge.proposal(payload,classifier=fake,ready=lambda:True)
 assert code==200 and body["status"]=="PROPOSAL"
 assert body["model"]=="ludo-hybrid-v1" and body["contract"]=="listing-evidence-v2"
 assert body["provider"]=="LOCAL" and calls["n"]==1
 answer=body["records"][0]
 assert answer["apply_authorized"] is False and answer["bgg_verified"] is False and answer["needs_review"] is True
 code,body2=bridge.proposal(payload,classifier=fake,ready=lambda:True)
 assert code==200 and body2==body and calls["n"]==1, "same request must reuse local cache"
 changed={"request_id":"request-0001","records":[{**row,"title":"Catan Junior"}]}
 assert bridge.proposal(changed,classifier=fake,ready=lambda:True)[0]==409
 assert bridge.proposal({"request_id":"request-0002","records":[row]},classifier=fake,ready=lambda:False)==(503,{"status":"LOCAL_UNAVAILABLE"})
 bad={"request_id":"request-0003","records":[{**row,"photos":["http://127.0.0.1/private"]}]}
 try:
  bridge.validate(bad)
  raise AssertionError("untrusted photo URL accepted")
 except ValueError:
  pass

assert bridge.HOST=="127.0.0.1" and bridge.PORT==8765
assert bridge.LOCAL_TOKEN=="ludo-local-usb-debug"
print("PASS direct USB bridge: loopback-only, idempotent, no provider budget, Vinted-photo allowlist")

# A failed classifier must preserve the generic HTTP contract while logging only
# request metadata; exception text can contain listing content or credentials.
original_proposal=bridge.proposal
sensitive="title=private listing photo=https://vinted.example/token=secret"
failure=RuntimeError(sensitive)
failure.code=503
def fail_proposal(payload):
 raise failure
bridge.proposal=fail_proposal
server=ThreadingHTTPServer((bridge.HOST,0),bridge.Handler)
server_thread=threading.Thread(target=server.serve_forever,daemon=True)
log=io.StringIO()
server_thread.start()
try:
 with redirect_stdout(log):
  request=urllib.request.Request(
   "http://127.0.0.1:%d/v1/classify"%server.server_address[1],
   data=json.dumps({"request_id":"request-0004","records":[]}).encode(),
   headers={"Authorization":"Bearer "+bridge.LOCAL_TOKEN,"Content-Type":"application/json"},
   method="POST")
  try:
   urllib.request.urlopen(request,timeout=3)
   raise AssertionError("failed proposal must return HTTP 502")
  except urllib.error.HTTPError as response:
   assert response.code==502
   assert json.loads(response.read())=={"status":"FAILED"}
finally:
 server.shutdown()
 server.server_close()
 server_thread.join(timeout=3)
 bridge.proposal=original_proposal
diagnostic=log.getvalue()
assert "Failed request=request-0004 stage=proposal error=RuntimeError http=503" in diagnostic
assert sensitive not in diagnostic and "private listing" not in diagnostic and "vinted.example" not in diagnostic and "secret" not in diagnostic
print("PASS failed USB proposal logs redacted diagnostics and preserves FAILED response")
