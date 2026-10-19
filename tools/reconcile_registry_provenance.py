#!/usr/bin/env python3
"""Read-only provenance reconciliation for the existing registry/snapshot."""
from __future__ import annotations
import argparse, json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

KEYS=("item_id","vinted_item_id","item_url","listing_url","url")
def external(r:dict[str,Any])->set[tuple[str,str]]:
    out=set()
    for k in KEYS:
        v=r.get(k)
        if isinstance(v,str) and v.strip(): out.add(("id" if k.endswith("item_id") else "url",v.strip()))
    return out

def main()->int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--registry',required=True,type=Path); ap.add_argument('--snapshot-manifest',required=True,type=Path)
    ap.add_argument('--reader-items',required=True,type=Path,help='JSON array from read-only reader for the SAME snapshot')
    ap.add_argument('--out',required=True,type=Path)
    a=ap.parse_args(); reg=json.loads(a.registry.read_text(encoding='utf-8')); man=json.loads(a.snapshot_manifest.read_text(encoding='utf-8')); local=json.loads(a.reader_items.read_text(encoding='utf-8'))
    entries=reg['entries']; rows=man['rows']; reader=local['items']
    sid=reg.get('source_snapshot_id'); assert sid==man.get('snapshot_id')==local.get('snapshot_id'), 'snapshot_id_mismatch'
    expected=int(local.get('expected_count', -1))
    if len(reader)!=expected or len({str(r.get('source_id')) for r in reader})!=expected:
        raise ValueError(f"reader_page_incomplete: got {len(reader)} unique items, expected {expected}")
    reader_by_id=defaultdict(list)
    for r in reader: reader_by_id[str(r.get('source_id'))].append(r)
    result=[]; counts=Counter()
    for e in entries:
        lid=str(e.get('listing_id')); m=[r for r in rows if str(r.get('source_id'))==lid]; now=reader_by_id.get(lid,[])
        proof={"registry_id":reg.get('registry_id'),"source_snapshot_id":sid,"registry_listing_id":e.get('listing_id'),"manifest_source_id_candidate":int(lid) if lid.isdigit() else lid,"same_id_in_manifest_count":len(m),"reader_current_snapshot_id":local.get('snapshot_id'),"reader_current_rows_for_candidate_id":len(now),"manifest_snapshot_hashes":[r.get('source_hash') for r in m],"current_reader_hashes":[r.get('source_hash') for r in now],"external_keys_registry":sorted([list(x) for x in external(e)]),"external_keys_local":sorted([list(x) for r in now for x in external(r)]),"listing_id_source_id_export_proven":False,"post_snapshot_identity_history_available":False}
        if len(m)>1 or len(now)>1: state='conflictual'; reason='duplicate_source_id'
        elif len(m)==1 and len(now)==1 and m[0].get('source_hash')!=now[0].get('source_hash'):
            state='conflictual'; reason='snapshot_content_hash_changed_for_candidate_id'
        elif len(m)==1: state='without_evidence'; reason='listing_id_source_id_export_lineage_not_documented'
        else: state='without_evidence'; reason='no_manifest_row_for_candidate_numeric_id'
        counts[state]+=1; counts['updateable']+=0
        result.append({"listing_id":e.get('listing_id'),"state":state,"reason":reason,"updateable":False,"proof":proof})
    doc={"schema_version":1,"snapshot_id":sid,"registry_id":reg.get('registry_id'),"classification_counts":dict(counts),"totals":{"entries":len(entries),"proposals":sum(e.get('record_state')=='PROPOSED' for e in entries)},"limitations":["Registry schema omits vinted_item_id, URL, original source_id, exported database identity, and export log.","Manifest contains source_id/source_hash but no Vinted identity; matching numerical values alone are not proof.","Current snapshot identity alone cannot establish post-snapshot edits, deletions, or recreation without local history.","No local SQLite read was available: operator reported single_authorized_device_required (device_count=0)."],"entries":result}
    a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(doc,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({"snapshot_id":sid,"entries":len(entries),"proposals":doc['totals']['proposals'],"counts":dict(counts),"map":str(a.out)},ensure_ascii=False,indent=2));return 0
if __name__=='__main__': raise SystemExit(main())
