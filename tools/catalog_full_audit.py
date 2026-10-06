#!/usr/bin/env python3
"""Read-only full catalog audit for Ludo Scout.

No AI, Vinted, Cloudflare or GitHub Actions calls are made.
When --db is omitted, the live Android DB/WAL/SHM are copied with run-as without
force-stopping the app; the copied snapshot is then opened read-only.
"""
import argparse
import json
import re
import sqlite3
import subprocess
import tempfile
import time
import unicodedata
from contextlib import closing
from pathlib import Path

from catalog_phase1_audit import adb_path, dump, PKG, DB

NON_GAME_CATEGORY_TERMS=(
    "musica","music","libri","books","abbigliamento","clothing","scarpe",
    "elettronica","beauty","bellezza","sport","casa","collectibles",
)
NEGATIVE_OBSERVATION_TYPES={"NON_GAME","ACCESSORY_COMPONENT","BUNDLE","EXPANSION"}

def norm(value):
    value="" if value is None else str(value)
    value=unicodedata.normalize("NFD",value)
    value="".join(ch for ch in value if unicodedata.category(ch)!="Mn")
    return re.sub(r"[^a-z0-9]+"," ",value.lower()).strip()

def text_value(value):
    return str(value or "").strip()

def table_exists(db,name):
    return db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",(name,)).fetchone() is not None

def columns(db,name):
    if not table_exists(db,name):
        return set()
    return {row[1] for row in db.execute(f"PRAGMA table_info({name})")}

def one(db,sql,args=()):
    row=db.execute(sql,args).fetchone()
    return row[0] if row else None

def distribution(db,column):
    return [
        {"value":row[0] if row[0] not in (None,"") else "(empty)","count":row[1]}
        for row in db.execute(f"SELECT COALESCE(NULLIF(TRIM({column}),''),'(empty)'),COUNT(*) FROM market_listings GROUP BY 1 ORDER BY 2 DESC,1")
    ]

def field_present(row,name):
    value=row[name]
    if name=="game_id":
        return value is not None
    return value is not None and str(value).strip()!=""

def latest_observation(db,signature):
    if not signature or not table_exists(db,"observations"):
        return None
    cols=columns(db,"observations")
    wanted=["id","observed_at","analysis_status","listing_type","verification_state","verification_reason","bgg_id"]
    selected=[c for c in wanted if c in cols]
    if not selected:
        return None
    order=[]
    if "observed_at" in cols: order.append("observed_at DESC")
    if "id" in cols: order.append("id DESC")
    sql="SELECT "+",".join(selected)+" FROM observations WHERE signature=?"
    if order: sql+=" ORDER BY "+",".join(order)
    sql+=" LIMIT 1"
    found=db.execute(sql,(signature,)).fetchone()
    return dict(found) if found else None

def report(db):
    db.row_factory=sqlite3.Row
    ml=columns(db,"market_listings")
    if not ml:
        raise RuntimeError("Tabella market_listings assente")

    required=[
        "id","vinted_title","vinted_item_id","vinted_url","image_url","listing_photos_csv",
        "seller_id","seller_name","published_label","language_code","observed_text","game_id",
        "lifecycle","enrichment_state","match_state","category_normalized","legacy_signature",
        "temp_fingerprint","manual_review_required","last_error","last_seen",
    ]
    missing_schema=[c for c in required if c not in ml]
    if missing_schema:
        raise RuntimeError("Schema market_listings incompleto: "+", ".join(missing_schema))

    game_cols=columns(db,"games")
    has_games=bool(game_cols)
    has_queue=table_exists(db,"queue_controls")

    total=one(db,"SELECT COUNT(*) FROM market_listings") or 0
    active=one(db,"SELECT COUNT(*) FROM market_listings WHERE lifecycle='ACTIVE'") or 0
    filtered=one(db,"SELECT COUNT(*) FROM market_listings WHERE lifecycle='AUTO_FILTERED'") or 0

    completeness_fields={
        "title":("vinted_title",),
        "vinted_item_id":("vinted_item_id",),
        "vinted_url":("vinted_url",),
        "photos":("image_url","listing_photos_csv"),
        "seller":("seller_id","seller_name"),
        "published":("published_label",),
        "language":("language_code",),
        "observed_text":("observed_text",),
        "game_link":("game_id",),
    }

    rows=db.execute("""SELECT id,vinted_title,vinted_item_id,vinted_url,image_url,listing_photos_csv,
        seller_id,seller_name,published_label,language_code,observed_text,game_id,lifecycle,
        enrichment_state,match_state,category_normalized,legacy_signature,temp_fingerprint,
        manual_review_required,last_error,last_seen
        FROM market_listings ORDER BY id""").fetchall()

    completeness={name:0 for name in completeness_fields}
    snapshot_recoverable={"seller":0,"published":0,"photos":0,"language":0,"any":0}
    snapshot_recoverable_rows=[]
    missing_rows=[]
    intruders=[]
    contradictions=[]
    ai_positive=0
    ai_positive_filtered=0
    ai_positive_progressed=0
    ai_positive_filtered_rows=[]

    evidence_times={}
    if has_queue:
        for q in db.execute("SELECT name,updated_at FROM queue_controls WHERE name LIKE 'ai_category_evidence:%'"):
            try:
                evidence_times[int(str(q["name"]).split(":",1)[1])]=q["updated_at"]
            except Exception:
                pass

    games={}
    if has_games and "id" in game_cols:
        selected=["id"]
        for c in ("bgg_id","match_state","canonical_name","filter_reason"):
            if c in game_cols: selected.append(c)
        for g in db.execute("SELECT "+",".join(selected)+" FROM games"):
            games[g["id"]]=dict(g)

    for row in rows:
        rid=row["id"]
        lifecycle=row["lifecycle"] or ""
        if lifecycle=="ACTIVE":
            missing=[]
            for label,names in completeness_fields.items():
                ok=False
                for name in names:
                    if name=="game_id":
                        ok=ok or row[name] is not None
                    else:
                        ok=ok or (row[name] is not None and str(row[name]).strip()!="")
                if ok: completeness[label]+=1
                else: missing.append(label)
            if missing:
                missing_rows.append({
                    "id":rid,"title":row["vinted_title"] or "","missing":missing,
                    "match_state":row["match_state"],"enrichment_state":row["enrichment_state"],
                })

            if has_queue and row["vinted_item_id"] is not None and str(row["vinted_item_id"]).strip():
                snapshot_key="browser_snapshot:"+str(row["vinted_item_id"]).strip()
                snapshot_row=db.execute("SELECT text_value FROM queue_controls WHERE name=?",(snapshot_key,)).fetchone()
                snap=snapshot_row[0] if snapshot_row else None
                if snap:
                    try:
                        payload=json.loads(snap)
                    except Exception:
                        payload={}
                    available=[]
                    seller_missing=("seller" in missing)
                    published_missing=("published" in missing)
                    photos_missing=("photos" in missing)
                    language_missing=("language" in missing)
                    seller_value=text_value(payload.get("sellerId") or payload.get("sellerName"))
                    publication=payload.get("publication") or {}
                    publication_value=text_value(publication.get("raw") if isinstance(publication,dict) else "")
                    photos_value=payload.get("photos") if isinstance(payload.get("photos"),list) else []
                    language_value=text_value(payload.get("language"))
                    if seller_missing and seller_value:
                        snapshot_recoverable["seller"]+=1;available.append("seller")
                    if published_missing and publication_value:
                        snapshot_recoverable["published"]+=1;available.append("published")
                    if photos_missing and photos_value:
                        snapshot_recoverable["photos"]+=1;available.append("photos")
                    if language_missing and language_value:
                        snapshot_recoverable["language"]+=1;available.append("language")
                    if available:
                        snapshot_recoverable["any"]+=1
                        snapshot_recoverable_rows.append({
                            "id":rid,"title":row["vinted_title"] or "","available":available,
                        })

        signature=(row["legacy_signature"] or row["temp_fingerprint"] or "")
        obs=latest_observation(db,signature)
        reasons=[]

        category=norm(row["category_normalized"])
        if lifecycle=="ACTIVE" and category and any(term in category for term in NON_GAME_CATEGORY_TERMS):
            reasons.append("explicit_non_game_category")
        if lifecycle=="ACTIVE" and obs and obs.get("listing_type") in NEGATIVE_OBSERVATION_TYPES:
            reasons.append("latest_observation_"+obs.get("listing_type","").lower())
        if lifecycle=="ACTIVE" and ((row["match_state"] or "").startswith("AUTO_FILTERED") or row["match_state"]=="CATEGORY_INCOMPATIBLE"):
            reasons.append("active_with_filtered_match_state")
        if lifecycle=="ACTIVE" and row["game_id"] is None and row["enrichment_state"] not in ("PENDING_ANALYSIS","PENDING_ENRICHMENT","DEFERRED_LINK"):
            reasons.append("active_without_game_link")
        game=games.get(row["game_id"]) if row["game_id"] is not None else None
        bgg=(game or {}).get("bgg_id") if game else None
        game_state=(game or {}).get("match_state") if game else None
        if lifecycle=="ACTIVE" and row["match_state"]=="MATCHED" and not bgg:
            reasons.append("matched_without_bgg_id")
        if lifecycle=="AUTO_FILTERED" and bgg:
            reasons.append("filtered_with_bgg_id")
        if reasons:
            intruders.append({
                "id":rid,"title":row["vinted_title"] or "","lifecycle":lifecycle,
                "enrichment_state":row["enrichment_state"],"match_state":row["match_state"],
                "category":row["category_normalized"],"game_id":row["game_id"],
                "bgg_id":bgg,"game_match_state":game_state,
                "latest_observation":obs,"reasons":reasons,
            })

        if lifecycle=="ACTIVE":
            if row["enrichment_state"]=="AUTO_FILTERED" or (row["match_state"] or "").startswith("AUTO_FILTERED"):
                contradictions.append({"id":rid,"title":row["vinted_title"] or "","reason":"ACTIVE row still carries filtered state"})
        if row["match_state"]=="MATCHED" and row["game_id"] is None:
            contradictions.append({"id":rid,"title":row["vinted_title"] or "","reason":"MATCHED without game_id"})

        evidence_at=evidence_times.get(rid)
        if evidence_at is not None:
            ai_positive+=1
            if lifecycle=="AUTO_FILTERED":
                ai_positive_filtered+=1
                ai_positive_filtered_rows.append({
                    "id":rid,
                    "title":row["vinted_title"] or "",
                    "evidence_at":evidence_at,
                    "enrichment_state":row["enrichment_state"],
                    "match_state":row["match_state"],
                    "last_error":row["last_error"],
                    "game_id":row["game_id"],
                    "bgg_id":bgg,
                    "game_match_state":game_state,
                    "latest_observation":obs,
                })
            if lifecycle=="ACTIVE" and row["match_state"] in ("MATCHED","BGG_MATCH_REQUIRED","BGG_MATCH_REVIEW") and row["enrichment_state"]!="PENDING_ANALYSIS":
                ai_positive_progressed+=1

    duplicate_items=[]
    for d in db.execute("""SELECT vinted_item_id,COUNT(*) n,GROUP_CONCAT(id) ids
        FROM market_listings
        WHERE TRIM(COALESCE(vinted_item_id,''))<>''
        GROUP BY vinted_item_id HAVING COUNT(*)>1 ORDER BY n DESC,vinted_item_id"""):
        duplicate_items.append({"vinted_item_id":d["vinted_item_id"],"count":d["n"],"ids":d["ids"]})

    filtered_reasons=[
        {"match_state":r[0] or "(empty)","last_error":r[1] or "(empty)","count":r[2]}
        for r in db.execute("""SELECT match_state,last_error,COUNT(*) FROM market_listings
            WHERE lifecycle='AUTO_FILTERED'
            GROUP BY match_state,last_error ORDER BY COUNT(*) DESC,match_state,last_error""")
    ]

    bgg_states={}
    if has_games and "match_state" in game_cols:
        bgg_states={str(r[0] or "(empty)"):r[1] for r in db.execute("SELECT match_state,COUNT(*) FROM games GROUP BY match_state ORDER BY COUNT(*) DESC")}

    diagnostics=[]
    if has_queue:
        diagnostics=[dict(r) for r in db.execute("""SELECT name,value,updated_at,text_value FROM queue_controls
            WHERE name LIKE 'diag:%' ORDER BY updated_at DESC""")]


    ai_holds=[]
    if table_exists(db,"deals"):
        dc=columns(db,"deals")
        needed={"signature","lifecycle","verification_state","verification_reason"}
        if needed.issubset(dc):
            title_expr="COALESCE(vinted_title,'')" if "vinted_title" in dc else "''"
            for d in db.execute(f"""SELECT signature,{title_expr} AS title,verification_state,verification_reason
                FROM deals
                WHERE lifecycle='ACTIVE'
                  AND verification_state='MATCH_UNCERTAIN'
                  AND verification_reason LIKE 'AI_CATEGORY_REVIEW:%'
                ORDER BY signature"""):
                lid=one(db,"""SELECT id FROM market_listings
                    WHERE COALESCE(NULLIF(legacy_signature,''),temp_fingerprint)=?
                    ORDER BY last_seen DESC,id DESC LIMIT 1""",(d["signature"],))
                listing_detail=None
                if lid is not None:
                    listing_row=db.execute("""SELECT id,COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) signature,
                        lifecycle,enrichment_state,match_state,game_id,category_normalized,last_error
                        FROM market_listings WHERE id=?""",(lid,)).fetchone()
                    if listing_row:
                        listing_detail=dict(listing_row)
                        game_id=listing_detail.get("game_id")
                        game=games.get(game_id) if game_id is not None else None
                        listing_detail["bgg_id"]=(game or {}).get("bgg_id") if game else None
                        listing_detail["game_match_state"]=(game or {}).get("match_state") if game else None
                        obs=latest_observation(db,listing_detail.get("signature"))
                        listing_detail["latest_observation"]=obs
                ai_holds.append({
                    "listing_id":lid,
                    "title":d["title"],
                    "verification_state":d["verification_state"],
                    "verification_reason":d["verification_reason"],
                    "listing":listing_detail,
                })

    blocked_live=[
        r for r in intruders
        if r["lifecycle"]=="ACTIVE" and r["match_state"]=="BLOCKED_CLASSIFIER"
    ]
    filtered_historical_bgg=[
        r for r in intruders
        if r["lifecycle"]=="AUTO_FILTERED" and r.get("bgg_id")
    ]

    completeness_summary={
        name:{
            "present":count,
            "active_total":active,
            "percent":round((count*100.0/active),1) if active else 0.0,
        } for name,count in completeness.items()
    }

    return {
        "captured_at":int(time.time()*1000),
        "scope":"Read-only catalog state; no AI/Vinted/cloud calls. Intruder rows are audit candidates, not automatic deletions.",
        "counts":{"all":total,"active":active,"auto_filtered":filtered},
        "lifecycle":distribution(db,"lifecycle"),
        "enrichment_state":distribution(db,"enrichment_state"),
        "listing_match_state":distribution(db,"match_state"),
        "game_match_state":bgg_states,
        "active_completeness":completeness_summary,
        "ai_category_evidence":{
            "total":ai_positive,
            "still_filtered":ai_positive_filtered,
            "progressed_to_bgg":ai_positive_progressed,
            "still_filtered_rows":ai_positive_filtered_rows,
            "holds":ai_holds,
        },
        "excluded_live_listings":blocked_live,
        "filtered_with_historical_bgg":filtered_historical_bgg,
        "potential_intruders":intruders,
        "state_contradictions":contradictions,
        "missing_active_fields":missing_rows,
        "browser_snapshot_recoverable":{
            "counts":snapshot_recoverable,
            "rows":snapshot_recoverable_rows,
        },
        "duplicate_vinted_items":duplicate_items,
        "auto_filtered_reasons":filtered_reasons,
        "diagnostics":diagnostics,
    }

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db",type=Path,help="Existing local SQLite snapshot; skips ADB")
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args()

    if args.db:
        with closing(sqlite3.connect(args.db.resolve().as_uri()+"?mode=ro",uri=True)) as db:
            result=report(db)
    else:
        adb=adb_path()
        version=subprocess.run([adb,"shell","dumpsys","package",PKG],capture_output=True,text=True,check=True).stdout
        with tempfile.TemporaryDirectory(prefix="ludo-catalog-full-") as tmp:
            path=Path(tmp)/DB
            if not dump(adb,"databases/"+DB,path):
                raise SystemExit("Database non leggibile via run-as. Nessun dato modificato.")
            dump(adb,"databases/"+DB+"-wal",str(path)+"-wal")
            dump(adb,"databases/"+DB+"-shm",str(path)+"-shm")
            try:
                with closing(sqlite3.connect(path.resolve().as_uri()+"?mode=ro",uri=True)) as db:
                    result=report(db)
            except sqlite3.DatabaseError as error:
                raise SystemExit("Snapshot live SQLite non coerente; riesegui il comando. Nessun dato è stato modificato. "+str(error))
        result["version"]=[line.strip() for line in version.splitlines() if "versionName=" in line or "versionCode=" in line]

    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
    c=result["counts"]
    ai=result["ai_category_evidence"]
    print(f"Catalogo: all={c['all']} active={c['active']} filtered={c['auto_filtered']}")
    print(f"AI evidence: total={ai['total']} filtered={ai['still_filtered']} BGG-progressed={ai['progressed_to_bgg']}")
    for row in ai.get("still_filtered_rows",[]):
        print(f"  AI-FILTERED #{row['id']} {row['title']} | match={row['match_state']} | error={row['last_error']}")
    print(f"AI holds={len(ai.get('holds',[]))}; live-ma-esclusi={len(result['excluded_live_listings'])}; filtered-con-BGG-storico={len(result['filtered_with_historical_bgg'])}")
    for row in ai.get("holds",[]):
        listing=row.get("listing") or {}
        obs=listing.get("latest_observation") or {}
        print(f"  AI-HOLD #{row.get('listing_id')} {row.get('title','')} | "+
              f"listing={listing.get('lifecycle')}/{listing.get('enrichment_state')}/{listing.get('match_state')} "+
              f"game={listing.get('game_id')} bgg={listing.get('bgg_id')} gameMatch={listing.get('game_match_state')} "+
              f"obsType={obs.get('listing_type')} obsVerify={obs.get('verification_state')} | "+
              f"{row.get('verification_reason','')}")
    print(f"Contraddizioni={len(result['state_contradictions'])}; duplicati item={len(result['duplicate_vinted_items'])}")
    local=result.get("browser_snapshot_recoverable",{}).get("counts",{})
    print("Snapshot locali recuperabili: "+
          f"seller={local.get('seller',0)} published={local.get('published',0)} "+
          f"photos={local.get('photos',0)} language={local.get('language',0)} any={local.get('any',0)}")
    print("Completezza ACTIVE:")
    for name,data in result["active_completeness"].items():
        print(f"  {name}: {data['present']}/{data['active_total']} ({data['percent']}%)")
    print("Report:",args.output.resolve())

if __name__=="__main__":
    main()
