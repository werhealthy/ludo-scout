#!/usr/bin/env python3
"""Capture recovery evidence without modifying app data or calling any service.

The app is briefly stopped to copy a consistent DB+WAL, then reopened, matching
the existing seller audit. A recorded AI category proof is not BGG verification.
"""
import argparse
import json
import sqlite3
import subprocess
import tempfile
import time
from contextlib import closing
from pathlib import Path
from catalog_phase1_audit import adb_path, dump, PKG, DB

def report(db, ids=None):
    db.row_factory=sqlite3.Row
    where='l.id IN ('+','.join('?' for _ in ids)+')' if ids else "q.name IS NOT NULL"
    rows=db.execute("SELECT l.id,l.vinted_title,l.lifecycle,l.enrichment_state,l.match_state,l.last_error,"
        "l.current_price_cents,l.manual_review_required,l.category_normalized,l.listing_photos_csv,l.image_url,"
        "g.bgg_id,g.match_state AS game_match_state,"
        "q.updated_at AS ai_evidence_at FROM market_listings l LEFT JOIN games g ON g.id=l.game_id "
        "LEFT JOIN queue_controls q ON q.name='ai_category_evidence:'||l.id WHERE "+where+" ORDER BY l.id",ids or []).fetchall()
    items=[]
    for row in rows:
        item=dict(row)
        photo_source=item.pop('listing_photos_csv') or item.pop('image_url') or ''
        item.pop('image_url',None)
        item['stored_photo_count']=len(__import__('re').findall(r'https://[^\s,\"\']+',photo_source))
        signature=db.execute("SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE id=?",(row['id'],)).fetchone()[0]
        item['observations']=[dict(r) for r in db.execute("SELECT id,observed_at,analysis_status,listing_type,verification_state,verification_reason,bgg_id FROM observations WHERE signature=? ORDER BY observed_at DESC,id DESC LIMIT 3",(signature,))]
        item['deal']=[dict(r) for r in db.execute("SELECT lifecycle,verification_state,confirmed,bgg_id FROM deals WHERE signature=?",(signature,))]
        item['override_count']=db.execute("SELECT COUNT(*) FROM listing_overrides WHERE signature=? OR item_id=(SELECT vinted_item_id FROM market_listings WHERE id=?)",(signature,row['id'])).fetchone()[0]
        items.append(item)
    positives=[r for r in items if r['ai_evidence_at'] is not None]
    filtered=[r for r in positives if r['lifecycle']=='AUTO_FILTERED']
    progressed=[r for r in positives if r['lifecycle']=='ACTIVE' and r['match_state'] in ('MATCHED','BGG_MATCH_REQUIRED','BGG_MATCH_REVIEW') and r['enrichment_state']!='PENDING_ANALYSIS']
    diagnostics=[dict(r) for r in db.execute("SELECT name,value,updated_at,text_value FROM queue_controls WHERE name IN ('diag:ai_engine','diag:local_analysis','diag:bgg_local_match','diag:bgg_match_review_write')")]
    return {'captured_at':int(time.time()*1000),'listing_count':len(items),'ai_evidence_count':len(positives),
        'ai_positive_filtered':len(filtered),'ai_positive_bgg_progressed':len(progressed),
        'cohort_pass':len(positives)>0 and len(progressed)==len(positives) and not filtered and (not ids or len(positives)==len(ids)),
        'scope':'Recorded visual category evidence only; BGG identity/trust remains independently gated.',
        'diagnostics':diagnostics,'listings':items}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--db',type=Path)
    parser.add_argument('--ids',help='Comma-separated canonical listing IDs from the original audit')
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    ids=[int(n) for n in args.ids.split(',')] if args.ids else None
    if ids and (len(ids)>100 or any(n<=0 for n in ids)):
        parser.error('Expected at most 100 positive listing IDs')
    if args.db:
        with closing(sqlite3.connect(args.db.resolve().as_uri()+'?mode=ro',uri=True)) as db:
            result=report(db,ids)
    else:
        adb=adb_path()
        version=subprocess.run([adb,'shell','dumpsys','package',PKG],capture_output=True,text=True,check=True).stdout
        subprocess.run([adb,'shell','am','force-stop',PKG],check=True)
        try:
            with tempfile.TemporaryDirectory(prefix='ludo-ai-recovery-') as tmp:
                path=Path(tmp)/DB
                if not dump(adb,'databases/'+DB,path):
                    raise SystemExit('Database non leggibile via run-as; nessun dato modificato.')
                dump(adb,'databases/'+DB+'-wal',str(path)+'-wal')
                with closing(sqlite3.connect(path.as_uri()+'?mode=ro',uri=True)) as db:
                    result=report(db,ids)
                result['version']=[line.strip() for line in version.splitlines() if 'versionName=' in line or 'versionCode=' in line]
        finally:
            subprocess.run([adb,'shell','am','start','-n',PKG+'/.MainActivity'],check=True)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print('AI evidence={ai_evidence_count}; filtered={ai_positive_filtered}; BGG progressed={ai_positive_bgg_progressed}; cohort_pass={cohort_pass}'.format(**result))
    print('Report:',args.output.resolve())

if __name__=='__main__':
    main()
