"""SQLite behavior: active identities differ from current queue stock."""
import ast, re, runpy, sys
from pathlib import Path
sys.argv.append('--source-eval')
ns=runpy.run_path(str(Path(__file__).with_name('engine_pipeline_sql_v51286.py')))
db=ns['db']
for table in ['observations','games','market_listings','deals','processing_jobs']:
    db.execute('DELETE FROM '+table)
add=ns['add']
a=add('bgg-running',game=1,state='PENDING')
add('same-game',game=1,state='PENDING')
add('bgg-waiting',game=2,state='PENDING')
b=add('vinted-running',game=3,bgg='300',rating=7,gs='MATCHED',state='PENDING',linked=True)
c=add('unknown-running')
db.executemany('INSERT INTO processing_jobs VALUES(?,?,?,?,?)',[(a,'BGG_ENRICHMENT','AUTO','PROCESSING',1),(b,'VINTED_ENRICHMENT','AUTO','PROCESSING',3),(c,'BGG_ENRICHMENT','AUTO','PROCESSING',None)])
source=ns['source'].read_text()
match=re.search(r'static String activeCounts\(\)\{return (.*?);\}',source)
query=ns['literal'](ast.parse('('+match.group(1)+')',mode='eval').body) if match else ns['sql']
actual=dict(db.execute(query,('0','200')))
assert actual=={0:1,1:1,3:1},f'live counts must count only busy identities once, not queue stock: {actual}'
db.execute("UPDATE processing_jobs SET state='COMPLETE'")
assert list(db.execute(query,('0','200')))==[], 'completed work cannot remain live'
assert dict(db.execute(ns['sql'],('0','200')))=={0:1,1:2,3:1}, 'live read must not change queue or ready membership'
print('PASS active identity counts, shared-game deduplication and completion separated from queue stock')
