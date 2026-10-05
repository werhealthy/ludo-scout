"""Execute the production browser intake SQL against SQLite.

Breaks caught: distinct IDs collapsing, capture reviving hidden listings,
missing metadata overwriting known values, duplicate history/observations,
and automatic Vinted jobs ignoring browser provenance. No network used.
"""
from pathlib import Path
import json, re, sqlite3
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/java/it/vintedaffari/app/BrowserIntakeSql.java'
assert p.exists(), 'Browser capture has no durable per-ID intake'
source=p.read_text()
capture=(root/'app/src/main/assets/browser/vinted-capture.js').read_text(encoding='utf-8')
policy=(root/'app/src/main/java/it/vintedaffari/app/BrowserCapturePolicy.java').read_text(encoding='utf-8')
activity=(root/'app/src/main/java/it/vintedaffari/app/VintedBrowserActivity.java').read_text(encoding='utf-8')
assert "return u&&/^\\/api\\/v\\d+\\//.test(u.pathname);" in capture, 'passive JSON capture is still tied to obsolete endpoint paths'
assert "function cardImage(anchor)" in capture and "image.closest&&image.closest('a[href*=\"/items/\"]')" in capture, 'DOM capture cannot recover sibling card images'
assert "picture.querySelectorAll('source')" in capture, 'picture/srcset sources are ignored'
assert 'h.equals("vinted.com")||h.endsWith(".vinted.com")' in policy, 'trusted vinted.com photo CDN is rejected'
assert 'description="+description+";photos="+photos+' in activity, 'capture completeness is not observable'
def sql(name):
    m=re.search(r'\b'+name+r'\s*=\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+);',source)
    assert m, name
    return ''.join(json.loads(x) for x in re.findall(r'"(?:[^"\\]|\\.)*"',m.group(1)))
db=sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE market_listings(id INTEGER PRIMARY KEY, temp_fingerprint TEXT UNIQUE, legacy_signature TEXT,vinted_item_id TEXT UNIQUE,vinted_title TEXT,brand TEXT,item_condition TEXT,current_price_cents INTEGER NOT NULL,protected_price_cents INTEGER,favorites INTEGER,observed_text TEXT,vinted_url TEXT,image_url TEXT,listing_photos_csv TEXT,seller_id TEXT,seller_name TEXT,published_label TEXT,language_code TEXT,lifecycle TEXT,enrichment_state TEXT,match_state TEXT,first_seen INTEGER,last_seen INTEGER,seen_count INTEGER DEFAULT 1,manual_review_required INTEGER DEFAULT 0);
CREATE TABLE queue_controls(name TEXT PRIMARY KEY,value INTEGER,updated_at INTEGER,text_value TEXT);
CREATE TABLE observations(id INTEGER PRIMARY KEY,signature TEXT,observed_at INTEGER,analysis_status TEXT,verification_state TEXT);
''')
def insert(item_id, price=1000, life='ACTIVE'):
    sig='browser:'+item_id
    db.execute(sql('INSERT'),(sig,sig,item_id,'Catan','Kosmos','Buone',price,None,'Catan', 'https://www.vinted.it/items/'+item_id,100,100,'PENDING_ANALYSIS','PENDING_ANALYSIS'))
    db.execute('UPDATE market_listings SET lifecycle=? WHERE vinted_item_id=?',(life,item_id))
    return db.execute(sql('LOOKUP'),(item_id,)).fetchone()
a=insert('101'); b=insert('102')
assert a[0]!=b[0] and a[1]=='browser:101' and b[1]=='browser:102'
insert('101');assert db.execute('SELECT COUNT(*) FROM market_listings').fetchone()[0]==2
db.execute("UPDATE market_listings SET seller_id='7',published_label='ieri',image_url='https://images1.vinted.net/p.jpg',protected_price_cents=1100 WHERE vinted_item_id='101'")
def update(row, price=1200):
    db.execute(sql('UPDATE'),('Catan','','',price,None,None,price,'Catan','https://www.vinted.it/items/101','','','','','','',200,row[0]))
update(a)
row=db.execute('SELECT current_price_cents,protected_price_cents,seller_id,published_label,image_url,first_seen,last_seen FROM market_listings WHERE id=?',(a[0],)).fetchone()
assert row==(1200,None,'7','ieri','https://images1.vinted.net/p.jpg',100,200),row
for life in ['USER_HIDDEN','SOLD','REMOVED','AUTO_FILTERED']:
    db.execute('UPDATE market_listings SET lifecycle=? WHERE id=?',(life,a[0]));update(a,999)
    assert db.execute('SELECT current_price_cents,lifecycle FROM market_listings WHERE id=?',(a[0],)).fetchone()==(1200,life)
db.execute("UPDATE market_listings SET lifecycle='ACTIVE',manual_review_required=1 WHERE id=?",(a[0],));update(a,998)
assert db.execute('SELECT current_price_cents FROM market_listings WHERE id=?',(a[0],)).fetchone()==(1200,)
db.execute(sql('PROVENANCE'),('browser_listing:'+str(b[0]),1,100,'browser'))
assert db.execute(sql('BROWSER_OWNED'),(str(b[0]),)).fetchone()==(1,)
assert db.execute(sql('BROWSER_OWNED'),(str(a[0]),)).fetchone() is None
print('PASS browser intake SQLite: IDs, repeat, price update, sparse metadata, lifecycle/manual holds, provenance')
