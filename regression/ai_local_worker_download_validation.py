#!/usr/bin/env python3
"""Synthetic image-download validation; no network or real listing URLs."""
import importlib.util
import io
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("ai_local_worker", ROOT / "tools/ai_local_worker.py")
if spec is None or spec.loader is None:
    raise RuntimeError("worker module unavailable")
worker = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = worker
spec.loader.exec_module(worker)


def png(width=10, height=20):
    return bytes([137,80,78,71,13,10,26,10]) + bytes(8) + width.to_bytes(4,"big") + height.to_bytes(4,"big")

assert worker.valid_image(png(), "image/png")
assert not worker.valid_image(png(), "text/html")
assert not worker.valid_image(png(12000,12000), "image/png")
assert worker.image_dimensions(b"not an image") is None
for rejected in (
    "https://user@images1.vinted.net/a.png",
    "https://user:pass@images1.vinted.net/a.png",
    "https://images1.vinted.net:8443/a.png",
):
    assert not worker.allowed_photo(rejected), rejected
assert worker.allowed_photo("https://images1.vinted.net/a.png")

class Response(io.BytesIO):
    def __init__(self, data, content_type="image/png", url="https://images1.vinted.net/a.png"):
        super().__init__(data)
        self.headers = {"content-type": content_type, "content-length": str(len(data))}
        self.url = url
    def geturl(self): return self.url

class Opener:
    def __init__(self, response): self.response=response
    def open(self, request, timeout): return self.response

old=worker.NO_REDIRECT
try:
    setattr(worker,"NO_REDIRECT",Opener(Response(png())))
    assert worker.fetch_image("https://images1.vinted.net/a.png") is not None
    setattr(worker,"NO_REDIRECT",Opener(Response(png(), "text/html")))
    assert worker.fetch_image("https://images1.vinted.net/a.png") is None
    setattr(worker,"NO_REDIRECT",Opener(Response(png(), url="https://evil.test/a.png")))
    assert worker.fetch_image("https://images1.vinted.net/a.png") is None
    assert worker.fetch_image("http://127.0.0.1/private") is None
finally:
    setattr(worker,"NO_REDIRECT",old)

import json
old_http=worker.http_json
captured={}
def fake_http(url,body=None,token=None,timeout=35):
    if body is None: raise AssertionError("expected local Ollama payload")
    captured['prompt']=body['messages'][0]['content']
    return 200, {'message': {'content': json.dumps({'product_title':'Synthetic game','product_type':'BASE_GAME','edition_language':'IT','evidence':['Titolo sintetico']})}}
setattr(worker,"http_json",fake_http)
setattr(worker,"fetch_image",lambda url: 'c3ludGhldGlj' if url.endswith('/ok.png') else None)
try:
    answer=worker.classify({'listing_id':1,'title':'Synthetic title','brand':'','source_text':'Description','photos':['https://images1.vinted.net/ok.png','https://images1.vinted.net/missing.png']})
    assert 'URL foto nell\'annuncio: 2; immagini scaricate e validate: 1.' in captured['prompt']
    assert answer['proposed_type']=='BASE_GAME'
finally:
    setattr(worker,"http_json",old_http)
print("PASS synthetic image validation: MIME/signature, dimensions, host, unavailable-photo accounting, no network")
