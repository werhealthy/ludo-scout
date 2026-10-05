#!/usr/bin/env python3
"""Ludo Scout local AI worker.

Environment:
  LUDO_AI_ENDPOINT       e.g. https://<worker>.workers.dev
  LUDO_AI_WORKER_TOKEN   private worker token
Optional:
  OLLAMA_URL             default http://127.0.0.1:11434
  OLLAMA_MODEL           default qwen3-vl:8b-instruct-q4_K_M
"""
import base64
import json
import os
import socket
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

ENDPOINT=os.environ.get("LUDO_AI_ENDPOINT","").rstrip("/")
TOKEN=os.environ.get("LUDO_AI_WORKER_TOKEN","")
OLLAMA=os.environ.get("OLLAMA_URL","http://127.0.0.1:11434").rstrip("/")
MODEL=os.environ.get("OLLAMA_MODEL","qwen3-vl:8b-instruct-q4_K_M")
healthy=False
stop=False
worked_since_idle=False
idle_announced=False

SCHEMA={
 "type":"object",
 "properties":{
  "product_title":{"type":"string"},
  "product_type":{"type":"string","enum":["BASE_GAME","EXPANSION","BUNDLE","ACCESSORY","COMPONENT","EMPTY_BOX","NON_GAME","UNKNOWN"]},
  "edition_language":{"type":"string","enum":["IT","EN","DE","FR","ES","PT","NL","MULTI","OTHER","UNKNOWN"]},
  "evidence":{"type":"array","maxItems":4,"items":{"type":"string"}}
 },
 "required":["product_title","product_type","edition_language","evidence"]
}
TYPE_MAP={"ACCESSORY":"ACCESSORY_COMPONENT","COMPONENT":"ACCESSORY_COMPONENT","EMPTY_BOX":"ACCESSORY_COMPONENT"}

class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,req,fp,code,msg,headers,newurl):
  return None

NO_REDIRECT=urllib.request.build_opener(NoRedirect)

def valid_endpoint(value):
 try:
  u=urllib.parse.urlparse(value)
  host=(u.hostname or "").lower()
  return u.scheme=="https" and host.endswith(".workers.dev") and u.username is None and u.password is None and u.port is None and (u.path in ("","/")) and not u.query and not u.fragment
 except Exception:
  return False

def http_json(url,body=None,token=None,timeout=35):
 data=None if body is None else json.dumps(body,separators=(",",":")).encode()
 headers={"content-type":"application/json","user-agent":"LudoScoutLocalAI/1.0","accept":"application/json"}
 if token: headers["authorization"]="Bearer "+token
 req=urllib.request.Request(url,data=data,headers=headers,method="POST" if body is not None else "GET")
 with NO_REDIRECT.open(req,timeout=timeout) as r:
  raw=r.read(1024*1024)
  return r.status, (json.loads(raw) if raw else {})

def post_local(path,body=None,timeout=35):
 return http_json(ENDPOINT+"/v1/local/"+path,{} if body is None else body,TOKEN,timeout)

def ollama_ready():
 try:
  with urllib.request.urlopen(OLLAMA+"/api/tags",timeout=3) as r:
   data=json.loads(r.read(2*1024*1024))
  return any(m.get("name")==MODEL or m.get("model")==MODEL for m in data.get("models",[]))
 except Exception:
  return False

def allowed_photo(url):
 try:
  u=urllib.parse.urlparse(url)
  host=(u.hostname or "").lower()
  return u.scheme=="https" and (host=="vinted.net" or host.endswith(".vinted.net") or host=="vinted.com" or host.endswith(".vinted.com"))
 except Exception:
  return False

def fetch_image(url):
 if not allowed_photo(url): return None
 req=urllib.request.Request(url,headers={"User-Agent":"LudoScoutLocalAI/1.0"})
 try:
  with NO_REDIRECT.open(req,timeout=10) as r:
   if not allowed_photo(r.geturl()): return None
   length=r.headers.get("content-length")
   if length and int(length)>8*1024*1024: return None
   data=r.read(8*1024*1024+1)
   if len(data)>8*1024*1024: return None
   return base64.b64encode(data).decode()
 except Exception:
  return None

def classify(record):
 images=[]
 for url in record.get("photos",[])[:4]:
  data=fetch_image(url)
  if data: images.append(data)
 prompt=f"""Sei il riconoscitore multimodale di Ludo Scout.
Titolo annuncio: {record.get('title','')}
Brand: {record.get('brand','')}
Descrizione acquisita: {record.get('source_text','')}

Vinted puo tradurre automaticamente titolo e descrizione: usali per capire COSA viene venduto, non per dedurre la lingua fisica.
Se ci sono immagini, usale insieme: fronte, retro e componenti.
BASE_GAME = gioco autonomo completo, incluse varianti standalone.
EXPANSION = richiede o amplia un altro gioco.
BUNDLE = piu prodotti distinti venduti insieme.
ACCESSORY = organizer, playmat, insert, sleeves, storage.
COMPONENT = pezzi, carte, dadi, miniature, token o ricambi separati.
EMPTY_BOX = scatola vuota.
NON_GAME = prodotto non tabletop.
UNKNOWN = prove insufficienti.
Per edition_language usa soprattutto testo realmente visibile sulla confezione; se non dimostrabile usa UNKNOWN.
Non inventare. Evidence massimo 4 frasi brevi."""
 payload={"model":MODEL,"stream":False,"format":SCHEMA,"options":{"temperature":0,"num_predict":240},
          "messages":[{"role":"user","content":prompt,**({"images":images} if images else {})}]}
 status,response=http_json(OLLAMA+"/api/chat",payload,timeout=180)
 if status!=200: raise RuntimeError("ollama status")
 result=json.loads(response["message"]["content"])
 ptype=TYPE_MAP.get(result["product_type"],result["product_type"])
 evidence="; ".join(str(x).strip() for x in result.get("evidence",[]) if str(x).strip())[:500]
 if not evidence: evidence="Analisi locale senza evidenza sufficiente"
 return {
  "listing_id":record["listing_id"],
  "proposed_type":ptype,
  "confidence":None,
  "evidence":evidence,
  "language":result.get("edition_language","UNKNOWN"),
  "product_title":str(result.get("product_title",""))[:180]
 }

def heartbeat_loop():
 global healthy
 while not stop:
  if healthy:
   try: post_local("heartbeat",{"model":MODEL},timeout=8)
   except Exception: pass
  time.sleep(5)

def main():
 global healthy,stop,worked_since_idle,idle_announced
 if not valid_endpoint(ENDPOINT) or len(TOKEN)<16:
  raise SystemExit("Set LUDO_AI_ENDPOINT to the Ludo .workers.dev root and LUDO_AI_WORKER_TOKEN")
 socket.setdefaulttimeout(35)
 threading.Thread(target=heartbeat_loop,daemon=True).start()
 print(f"Ludo local AI worker | model={MODEL}")
 try:
  while True:
   if not healthy:
    healthy=ollama_ready()
    if not healthy:
     print("Ollama/model unavailable; Gemini fallback will remain available.")
     time.sleep(5);continue
    try: post_local("heartbeat",{"model":MODEL},timeout=8)
    except urllib.error.HTTPError as e:
     print("Broker unavailable: HTTP",e.code);time.sleep(5);continue
    except Exception as e:
     print("Broker unavailable:",type(e).__name__);time.sleep(5);continue
    print("Local AI online.")
   try:
    status,job=post_local("claim",{"model":MODEL},timeout=10)
    if status==204 or job.get("status")=="NO_JOB":
     if worked_since_idle and not idle_announced:
      print("Coda Qwen vuota · tutti i job ricevuti finora sono completati.")
      idle_announced=True
      worked_since_idle=False
     time.sleep(1);continue
    if job.get("status")!="JOB":
     time.sleep(2);continue
    started=time.time()
    records=job.get("records",[])
    with_photos=sum(1 for r in records if r.get("photos"))
    total_photos=sum(min(4,len(r.get("photos",[]))) for r in records)
    idle_announced=False
    print("Job",job.get("request_id"),"| records",len(records),"| con foto",with_photos,"| foto",total_photos)
    answers=[classify(r) for r in records]
    post_local("result",{"job_id":job["job_id"],"worker_model":MODEL,"records":answers},timeout=20)
    worked_since_idle=True
    print("Done",job.get("request_id"),f"| {time.time()-started:.1f}s")
   except KeyboardInterrupt:
    break
   except urllib.error.HTTPError as e:
    healthy=False
    print("Local AI error: HTTP",e.code,"- fallback will resume after heartbeat expires.")
    time.sleep(3)
   except Exception as e:
    healthy=False
    print("Local AI error:",type(e).__name__,str(e)[:160],"- fallback will resume after heartbeat expires.")
    time.sleep(3)
 finally:
  stop=True

if __name__=="__main__":
 main()
