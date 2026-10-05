#!/usr/bin/env python3
"""Direct owner-only Android -> PC -> Ollama bridge.

Run from the repository root after:
  adb reverse tcp:8765 tcp:8765
  python tools/ai_local_bridge.py

The server binds only 127.0.0.1. Android debug reaches it through adb reverse.
No Cloudflare, Gemini, public listener or paid service is involved.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import hashlib
import json
import os
import re
import threading
import time

from ai_local_worker import classify, ollama_ready, MODEL as WORKER_MODEL

HOST="127.0.0.1"
PORT=int(os.environ.get("LUDO_AI_LOCAL_PORT","8765"))
API_MODEL="ludo-hybrid-v1"
CONTRACT="listing-evidence-v2"
LOCAL_TOKEN="ludo-local-usb-debug"
MAX_BODY=32768
CACHE_TTL_MS=7*24*60*60*1000
MAX_CACHE=256
LOCK=threading.Lock()
REQUEST_ID=re.compile(r"^[-a-zA-Z0-9]{8,80}$")

def cache_path():
 override=os.environ.get("LUDO_AI_LOCAL_CACHE")
 if override:return Path(override)
 base=os.environ.get("LOCALAPPDATA")
 root=Path(base) if base else Path.home()
 return root/"LudoScout"/"ai-local-usb-cache.json"

def compact_json(value):
 return json.dumps(value,separators=(",",":"),ensure_ascii=False)

def load_cache():
 path=cache_path()
 try:
  data=json.loads(path.read_text(encoding="utf-8"))
  return data if isinstance(data,dict) else {}
 except Exception:
  return {}

def save_cache(data):
 path=cache_path();path.parent.mkdir(parents=True,exist_ok=True)
 tmp=path.with_suffix(".tmp")
 tmp.write_text(compact_json(data),encoding="utf-8")
 os.replace(tmp,path)

def prune_cache(data,now_ms):
 fresh={k:v for k,v in data.items() if isinstance(v,dict) and now_ms-int(v.get("at",0))<=CACHE_TTL_MS}
 if len(fresh)>MAX_CACHE:
  ordered=sorted(fresh.items(),key=lambda x:int(x[1].get("at",0)),reverse=True)[:MAX_CACHE]
  fresh=dict(ordered)
 return fresh

def allowed_photo(value):
 if not isinstance(value,str) or len(value)>2048:return False
 try:
  from urllib.parse import urlparse
  u=urlparse(value);host=(u.hostname or "").lower()
  return u.scheme=="https" and (host=="vinted.net" or host.endswith(".vinted.net") or host=="vinted.com" or host.endswith(".vinted.com"))
 except Exception:
  return False

def validate(payload):
 if not isinstance(payload,dict) or set(payload)!={"request_id","records"}:raise ValueError("input")
 request_id=payload.get("request_id")
 rows=payload.get("records")
 if not isinstance(request_id,str) or not REQUEST_ID.fullmatch(request_id):raise ValueError("input")
 if not isinstance(rows,list) or not 1<=len(rows)<=8:raise ValueError("input")
 seen=set()
 for row in rows:
  if not isinstance(row,dict) or set(row)!={"listing_id","title","brand","source_text","photos"}:raise ValueError("input")
  listing_id=row.get("listing_id")
  if not isinstance(listing_id,int) or isinstance(listing_id,bool) or listing_id in seen:raise ValueError("input")
  seen.add(listing_id)
  title=row.get("title");brand=row.get("brand");source=row.get("source_text");photos=row.get("photos")
  if not isinstance(title,str) or not title.strip() or len(title)>180:raise ValueError("input")
  if not isinstance(brand,str) or len(brand)>160:raise ValueError("input")
  if not isinstance(source,str) or len(source)>2000:raise ValueError("input")
  if not isinstance(photos,list) or len(photos)>4 or any(not allowed_photo(p) for p in photos):raise ValueError("input")
 return request_id,rows

def normalized_answer(answer):
 types={"BASE_GAME","EXPANSION","BUNDLE","ACCESSORY_COMPONENT","NON_GAME","UNKNOWN"}
 languages={"IT","EN","DE","FR","ES","PT","NL","MULTI","OTHER","UNKNOWN"}
 ptype=answer.get("proposed_type");language=answer.get("language","UNKNOWN");evidence=str(answer.get("evidence","")).strip()
 if ptype not in types or language not in languages or not evidence:raise ValueError("local output")
 return {
  "listing_id":answer["listing_id"],
  "proposed_type":ptype,
  "confidence":None,
  "evidence":evidence[:500],
  "needs_review":True,
  "apply_authorized":False,
  "language":language,
  "bgg_verified":False,
  "product_title":str(answer.get("product_title",""))[:180],
 }

def proposal(payload,classifier=classify,ready=ollama_ready):
 request_id,rows=validate(payload)
 content=hashlib.sha256(compact_json(rows).encode("utf-8")).hexdigest()
 now=int(time.time()*1000)
 with LOCK:
  data=prune_cache(load_cache(),now)
  prior=data.get(request_id)
  if prior:
   if prior.get("content")!=content:return 409,{"status":"CONFLICT"}
   response=prior.get("response")
   if isinstance(response,dict):return 200,response
 if not ready():return 503,{"status":"LOCAL_UNAVAILABLE"}
 started=time.time()
 answers=[normalized_answer(classifier(row)) for row in rows]
 response={
  "status":"PROPOSAL",
  "records":answers,
  "model":API_MODEL,
  "contract":CONTRACT,
  "provider":"LOCAL",
  "worker_model":WORKER_MODEL,
  "request_id":request_id,
 }
 with LOCK:
  data=prune_cache(load_cache(),now)
  data[request_id]={"content":content,"at":now,"response":response}
  save_cache(data)
 print(f"Done request={request_id} records={len(rows)} elapsed={time.time()-started:.1f}s")
 return 200,response

def failed_response(request_id,stage,error):
 safe_id=request_id if isinstance(request_id,str) and REQUEST_ID.fullmatch(request_id) else "unknown"
 safe_stage=stage if stage in ("request","proposal") else "request"
 code=getattr(error,"code",None)
 suffix=f" http={code}" if type(code) is int and 100<=code<=599 else ""
 print(f"Failed request={safe_id} stage={safe_stage} error={type(error).__name__}{suffix}",flush=True)
 return 502,{"status":"FAILED"}

class Handler(BaseHTTPRequestHandler):
 server_version="LudoScoutLocalUSB/1.0"
 def log_message(self,format,*args):
  return
 def send_json(self,status,body):
  raw=compact_json(body).encode("utf-8")
  self.send_response(status)
  self.send_header("Content-Type","application/json; charset=utf-8")
  self.send_header("Content-Length",str(len(raw)))
  self.send_header("Cache-Control","no-store")
  self.end_headers();self.wfile.write(raw)
 def authorized(self):
  return self.headers.get("Authorization","")==("Bearer "+LOCAL_TOKEN)
 def do_GET(self):
  if self.path!="/v1/status":return self.send_json(404,{"status":"NOT_FOUND"})
  if not self.authorized():return self.send_json(401,{"status":"UNAUTHORIZED"})
  ready=ollama_ready()
  return self.send_json(200,{
   "enabled":ready,
   "local_online":ready,
   "gemini_available":False,
   "transport":"USB_LOCAL",
   "budget":{"calls_reserved":0,"reserved_micro":0},
  })
 def do_POST(self):
  if self.path!="/v1/classify":return self.send_json(404,{"status":"NOT_FOUND"})
  if not self.authorized():return self.send_json(401,{"status":"UNAUTHORIZED"})
  request_id="unknown";stage="request"
  try:
   size=int(self.headers.get("Content-Length","0"))
   if size<1 or size>MAX_BODY:return self.send_json(400,{"status":"INVALID_INPUT"})
   raw=self.rfile.read(size)
   if len(raw)!=size:return self.send_json(400,{"status":"INVALID_INPUT"})
   payload=json.loads(raw.decode("utf-8"))
   candidate=payload.get("request_id") if isinstance(payload,dict) else None
   if isinstance(candidate,str) and REQUEST_ID.fullmatch(candidate):request_id=candidate
   stage="proposal"
   status,body=proposal(payload)
   return self.send_json(status,body)
  except (ValueError,UnicodeDecodeError,json.JSONDecodeError):
   return self.send_json(400,{"status":"INVALID_INPUT"})
  except Exception as error:
   status,body=failed_response(request_id,stage,error)
   return self.send_json(status,body)
 
def main():
 if PORT!=8765:raise SystemExit("LUDO_AI_LOCAL_PORT must remain 8765 for the Android debug contract")
 if not ollama_ready():raise SystemExit(f"Ollama model unavailable: {WORKER_MODEL}")
 server=ThreadingHTTPServer((HOST,PORT),Handler)
 print(f"Ludo USB AI bridge | http://{HOST}:{PORT} | model={WORKER_MODEL}")
 print("Only PC loopback is listening. Keep adb reverse active on the connected phone.")
 try:server.serve_forever(poll_interval=0.5)
 except KeyboardInterrupt:pass
 finally:server.server_close()

if __name__=="__main__":
 main()
