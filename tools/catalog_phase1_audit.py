#!/usr/bin/env python3
import os, shutil, sqlite3, subprocess, tempfile
from pathlib import Path

PKG="it.vintedaffari.app"
DB="vinted_affari.db"

def adb_path():
    candidates=[]
    env=os.environ.get("ADB")
    if env: candidates.append(Path(env))
    local=os.environ.get("LOCALAPPDATA")
    if local: candidates.append(Path(local)/"Android"/"Sdk"/"platform-tools"/"adb.exe")
    found=shutil.which("adb")
    if found: candidates.append(Path(found))
    for p in candidates:
        if p.exists(): return str(p)
    raise SystemExit("ADB non trovato. Imposta ADB o installa Android platform-tools.")

def dump(adb, remote, local):
    with open(local,"wb") as out:
        p=subprocess.run([adb,"exec-out","run-as",PKG,"cat",remote],stdout=out,stderr=subprocess.PIPE)
    if p.returncode!=0:
        Path(local).unlink(missing_ok=True)
        return False
    return Path(local).stat().st_size>0

def cols(db, table):
    return {r[1] for r in db.execute(f"PRAGMA table_info({table})")}

def count(db, where="1"):
    return db.execute(f"SELECT COUNT(*) FROM market_listings WHERE {where}").fetchone()[0]

def pct(n,d):
    return 0 if not d else round(n*100.0/d,1)

def main():
    adb=adb_path()
    with tempfile.TemporaryDirectory(prefix="ludo-audit-") as td:
        base=Path(td)/DB
        if not dump(adb,f"databases/{DB}",base):
            raise SystemExit("Database non leggibile via run-as. Verifica che l'app installata sia la build debug.")
        dump(adb,f"databases/{DB}-wal",str(base)+"-wal")
        dump(adb,f"databases/{DB}-shm",str(base)+"-shm")

        db=sqlite3.connect(f"file:{base}?mode=ro",uri=True)
        ml=cols(db,"market_listings")
        if not ml:
            raise SystemExit("Tabella market_listings assente.")

        active="lifecycle='ACTIVE'"
        total=count(db,active)
        if total==0:
            print("Nessun annuncio ACTIVE nel catalogo.")
            return

        checks=[
            ("Titolo","vinted_title IS NOT NULL AND TRIM(vinted_title)<>''"),
            ("ID Vinted","vinted_item_id IS NOT NULL AND TRIM(vinted_item_id)<>''"),
            ("URL Vinted","vinted_url IS NOT NULL AND TRIM(vinted_url)<>''"),
            ("Foto","(image_url IS NOT NULL AND TRIM(image_url)<>'') OR (listing_photos_csv IS NOT NULL AND TRIM(listing_photos_csv)<>'')"),
            ("Venditore","(seller_id IS NOT NULL AND TRIM(seller_id)<>'') OR (seller_name IS NOT NULL AND TRIM(seller_name)<>'')"),
            ("Pubblicazione","published_label IS NOT NULL AND TRIM(published_label)<>''"),
            ("Lingua","language_code IS NOT NULL AND TRIM(language_code)<>''"),
            ("Descrizione","observed_text IS NOT NULL AND TRIM(observed_text)<>''"),
            ("Gioco collegato","game_id IS NOT NULL"),
        ]

        print("=== LUDO CATALOG AUDIT · FASE 1 ===")
        print(f"Annunci ACTIVE: {total}")
        for label,cond in checks:
            if any(tok in cond for tok in ("observed_text","game_id")) and not all(x in ml for x in [x for x in ("observed_text","game_id") if x in cond]):
                continue
            n=count(db,f"{active} AND ({cond})")
            print(f"{label:16} {n:4}/{total:<4} {pct(n,total):5.1f}%")

        photo_cond="((image_url IS NOT NULL AND TRIM(image_url)<>'') OR (listing_photos_csv IS NOT NULL AND TRIM(listing_photos_csv)<>''))"
        title_cond="(vinted_title IS NOT NULL AND TRIM(vinted_title)<>'')"
        seller_cond="((seller_id IS NOT NULL AND TRIM(seller_id)<>'') OR (seller_name IS NOT NULL AND TRIM(seller_name)<>''))"
        ai_ready=count(db,f"{active} AND {title_cond} AND {photo_cond}")
        print(f"AI pronta titolo+foto {ai_ready}/{total} {pct(ai_ready,total):.1f}%")

        seller_key="COALESCE(NULLIF(TRIM(seller_id),''),NULLIF(TRIM(seller_name),''))"
        rows=db.execute(f"""SELECT {seller_key} k,COUNT(*) n
            FROM market_listings
            WHERE {active} AND {seller_cond}
            GROUP BY k HAVING COUNT(*)>=2 ORDER BY n DESC""").fetchall()
        grouped=sum(r[1] for r in rows)
        pairs=sum(r[1]*(r[1]-1)//2 for r in rows)
        sellers=len(rows)
        seller_known=count(db,f"{active} AND {seller_cond}")
        print("\n=== BUNDLE CASUALI ===")
        print(f"Copertura venditore: {seller_known}/{total} {pct(seller_known,total):.1f}%")
        print(f"Venditori con >=2 annunci ACTIVE: {sellers}")
        print(f"Annunci già collegabili per venditore: {grouped}")
        print(f"Coppie same-seller rilevabili localmente: {pairs}")

        print("\n=== ULTIMI 50 ===")
        fresh=db.execute(f"""SELECT
          COUNT(*),
          SUM(CASE WHEN {photo_cond} THEN 1 ELSE 0 END),
          SUM(CASE WHEN {seller_cond} THEN 1 ELSE 0 END),
          SUM(CASE WHEN published_label IS NOT NULL AND TRIM(published_label)<>'' THEN 1 ELSE 0 END),
          SUM(CASE WHEN language_code IS NOT NULL AND TRIM(language_code)<>'' THEN 1 ELSE 0 END),
          SUM(CASE WHEN observed_text IS NOT NULL AND TRIM(observed_text)<>'' THEN 1 ELSE 0 END)
          FROM (SELECT * FROM market_listings WHERE {active} ORDER BY last_seen DESC LIMIT 50)""").fetchone()
        labels=["righe","foto","seller","pub","lingua","descr"]
        print(" · ".join(f"{k}={int(v or 0)}" for k,v in zip(labels,fresh)))

        print("\n=== MANCANZE PRIORITARIE ===")
        for label,cond in [("seller",seller_cond),("foto",photo_cond),("lingua","language_code IS NOT NULL AND TRIM(language_code)<>''")]:
            rows=db.execute(f"""SELECT id,COALESCE(vinted_title,''),COALESCE(vinted_item_id,'')
                FROM market_listings WHERE {active} AND NOT ({cond})
                ORDER BY last_seen DESC LIMIT 5""").fetchall()
            print(label+": "+(" | ".join(f"#{r[0]} {r[1][:55]}" for r in rows) if rows else "nessuna"))

if __name__=="__main__":
    main()
