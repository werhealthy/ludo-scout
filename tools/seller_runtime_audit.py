#!/usr/bin/env python3
"""Local evidence only. No HTTP. Stops/reopens app to copy a consistent DB+WAL."""
import argparse, sqlite3, subprocess, tempfile
from pathlib import Path
from catalog_phase1_audit import adb_path, dump, PKG, DB

def report(db):
    total, ids, names = db.execute("SELECT COUNT(*),SUM(CASE WHEN COALESCE(seller_id,'')<>'' THEN 1 ELSE 0 END),SUM(CASE WHEN COALESCE(seller_id,'')<>'' OR COALESCE(seller_name,'')<>'' THEN 1 ELSE 0 END) FROM market_listings WHERE lifecycle='ACTIVE'").fetchone()
    print(f'ACTIVE={total};seller_id={ids or 0}/{total};seller_coverage={names or 0}/{total}')
    pairs=db.execute("SELECT COALESCE(SUM(n*(n-1)/2),0) FROM (SELECT COUNT(*) n FROM market_listings WHERE lifecycle='ACTIVE' AND COALESCE(seller_id,'')<>'' GROUP BY seller_id)").fetchone()[0]
    print(f'same_seller_pairs={pairs}')
    for row in db.execute("SELECT source,state,COUNT(*),MIN(next_attempt_at) FROM processing_jobs WHERE source IN ('SELLER_BACKFILL','CATALOG_HEALTH','CATALOG_RECOVERY') GROUP BY source,state"):
        print('maintenance_job',row)
    for row in db.execute("SELECT name,value,updated_at,text_value FROM queue_controls WHERE name IN ('diag:seller_backfill_schedule','diag:seller_backfill','diag:seller_backfill_result','vinted_paused','diag:t2b_exclusive','vinted_http_window_count','vinted_http_last_code','vinted_http_circuit_until','t2_ledger:last','t2_ledger:physical') OR name LIKE 'diag:vinted_trace:%' ORDER BY updated_at,value"):
        print('diagnostic',row)
    print('once_markers=',db.execute("SELECT COUNT(*) FROM queue_controls WHERE name LIKE 'seller_backfill_once:%'").fetchone()[0])

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--db',type=Path,help='Read an already copied DB instead of accessing phone')
    args=parser.parse_args()
    if args.db:
        with sqlite3.connect(args.db.resolve().as_uri()+'?mode=ro',uri=True) as db: report(db)
        return
    adb=adb_path()
    p=subprocess.run([adb,'shell','dumpsys','package',PKG],capture_output=True,text=True,check=True)
    for line in p.stdout.splitlines():
        if 'versionName=' in line or 'versionCode=' in line: print(line.strip())
    subprocess.run([adb,'shell','am','force-stop',PKG],check=True)
    try:
        with tempfile.TemporaryDirectory(prefix='ludo-seller-') as td:
            base=Path(td)/DB
            if not dump(adb,f'databases/{DB}',base): raise SystemExit('Database non leggibile via run-as')
            dump(adb,f'databases/{DB}-wal',str(base)+'-wal')
            with sqlite3.connect(base.as_uri()+'?mode=ro',uri=True) as db: report(db)
    finally:
        subprocess.run([adb,'shell','am','start','-n',PKG+'/.MainActivity'],check=True)

if __name__=='__main__': main()
