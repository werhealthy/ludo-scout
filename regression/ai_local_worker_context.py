import json
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
import ai_local_worker as worker

original_fetch=worker.fetch_image
original_http=worker.http_json
seen={}

def fake_http(url,payload=None,token=None,timeout=35):
 seen["url"]=url
 seen["payload"]=payload
 return 200,{"message":{"content":json.dumps({
  "product_title":"Diagnostic board game",
  "product_type":"BASE_GAME",
  "edition_language":"UNKNOWN",
  "evidence":["Synthetic diagnostic title"]
 })}}

worker.fetch_image=lambda _url:"c3ludGhldGljLWltYWdl"
worker.http_json=fake_http
try:
 answer=worker.classify({
  "listing_id":990051,
  "title":"Synthetic photo context fixture",
  "brand":"",
  "source_text":"",
  "photos":["https://photo.vinted.net/synthetic.jpg"],
 })
finally:
 worker.fetch_image=original_fetch
 worker.http_json=original_http

assert seen["url"].endswith("/api/chat")
payload=seen["payload"]
assert payload["messages"][0]["images"]==["c3ludGhldGljLWltYWdl"]
assert payload["options"].get("num_ctx",0)>=8192, "photo prompts need a context larger than Ollama's 4096-token default"
assert answer["proposed_type"]=="BASE_GAME"
assert answer["language"]=="UNKNOWN", "the worker must preserve the model abstention"
prompt=payload["messages"][0]["content"]
assert "Una parola isolata non basta" in prompt
assert "Vinted puo tradurli" in prompt
assert "una espansione o contenuto extra incluso nella confezione non basta" in prompt
assert "non richiede di verificare a vista tutti i componenti" in prompt
print("PASS 8192-token photo context and language/category evidence prompt guards")
