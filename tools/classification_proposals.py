"""Adapt offline model answers into shadow proposals; never apply or request AI.

Source input, model and contract association are operator-supplied, not attested.
Exact title/brand equality prevents reuse after edits. No provider credentials,
HTTP, ledger mutation or database access. All proposals require review.
"""
import argparse
import hashlib
import json
from pathlib import Path
from classification_benchmark import compare, indexed


def content_key(row, model, contract):
    content=[row['listing_id'],row['title'],row['brand'],model,contract]
    return hashlib.sha256(json.dumps(content,ensure_ascii=False,separators=(',',':')).encode('utf-8')).hexdigest()


def proposals(sample, source_input, answers, model, contract):
    if not isinstance(model,str) or not model.strip() or not isinstance(contract,str) or not contract.strip():
        raise ValueError('model and contract required')
    if not 1 <= len(sample) <= 64 or not 1 <= len(source_input) <= 64:
        raise ValueError('bounded sample required')
    current,source,predictions=indexed(sample),indexed(source_input),indexed(answers)
    for row in list(current.values())+list(source.values()):
        if not isinstance(row.get('title'),str) or not row['title'].strip() or not isinstance(row.get('brand'),str):
            raise ValueError('title and brand required')
    compare(source_input,answers)
    if any(a.get('language','UNKNOWN')!='UNKNOWN' or a.get('bgg_verdict','UNKNOWN')!='UNKNOWN' for a in answers):
        raise ValueError('identity/language claims unsupported')
    rows=[]
    for key,item in current.items():
        original=source.get(key)
        answer=predictions.get(key)
        same=original is not None and (item['title'],item['brand'])==(original['title'],original['brand'])
        status='STALE_INPUT' if not same else 'MISSING_ANSWER' if answer is None else 'PROPOSAL'
        usable=answer if status=='PROPOSAL' else None
        rows.append(dict(listing_id=key,status=status,content_key=content_key(item,model,contract),
                         preserved_type=item.get('previous_type'),proposed_type=usable['category'] if usable else None,
                         runtime_type=None,needs_review=True,apply_authorized=False,
                         confidence=usable.get('confidence') if usable else None,
                         confidence_kind='MODEL_SELF_REPORT_UNCALIBRATED',
                         evidence=usable['evidence'] if usable else None,
                         language='UNKNOWN',bgg_verified=False))
    return dict(version=1,mode='SHADOW_ONLY',model=model,contract=contract,
                source_provenance='OPERATOR_SUPPLIED_NOT_ATTESTED',provider_calls=0,catalog_writes=0,records=rows)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['sample','source-input','answers','model','contract','output']:
        parser.add_argument('--'+name,required=True)
    args=parser.parse_args()
    paths=[Path(p).resolve() for p in [args.sample,args.source_input,args.answers]]
    output=Path(args.output).resolve()
    if output in paths:parser.error('output must not overwrite inputs')
    sample,source=[json.loads(p.read_text(encoding='utf-8'))['records'] for p in paths[:2]]
    answers=json.loads(paths[2].read_text(encoding='utf-8'))
    result=proposals(sample,source,answers,args.model,args.contract)
    with output.open('x',encoding='utf-8') as f:
        json.dump(result,f,ensure_ascii=False,indent=2,allow_nan=False)


if __name__=='__main__':main()
