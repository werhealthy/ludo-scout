"""Prepare a manual benchmark continuation offline; never execute the plan.

Requires the input fixture from the source run for equality checking. Its
association with that run is supplied by the operator, not proven by this tool.
No credentials, network calls, ledger writes, provider calls or catalog writes.
"""
import argparse
import copy
from decimal import Decimal
import json
from pathlib import Path

from classification_benchmark import compare, indexed
from classification_benchmark_runner import MODEL, MAX_CALLS, RESERVATION, money, payload, reserve


def plan(sample, source_input, ledger, month, source_month, source_operation, monthly='1', batch='.10'):
    if not 1 <= len(sample) <= 64:
        raise ValueError('sample must contain 1..64 records')
    indexed(sample)
    if sample != source_input:
        raise ValueError('source input differs; reuse blocked')
    for i in range(0,len(sample),8):payload(sample[i:i+8])
    if ledger.get('version') != 1 or type(ledger.get('disabled')) is not bool or ledger['disabled']:
        raise ValueError('disabled or invalid ledger')
    monthly, batch = money(monthly), money(batch)
    if monthly > 1 or batch > Decimal('.10'):
        raise ValueError('benchmark budget ceiling exceeded')
    source = ledger['months'][source_month]['operations'][source_operation]
    reused=copy.deepcopy(source.get('reused_answers',[]))
    compare(sample,reused)
    if any(r.get('language') != 'UNKNOWN' or r.get('bgg_verdict','UNKNOWN') != 'UNKNOWN' for r in reused):
        raise ValueError('unsupported inherited identity or language')
    for entry in source['responses']:
        if entry['status'] not in {'VALIDATED','FAILED_OR_INVALID','RESERVED_REQUEST_UNKNOWN'} or entry['model'] != MODEL:
            raise ValueError('invalid source response or model')
        if entry['status'] == 'VALIDATED':
            rows=entry['answers']
            if not isinstance(rows,list) or not 1 <= len(rows) <= 8:
                raise ValueError('invalid source answers')
            compare(sample,rows)
            if any(r.get('language') != 'UNKNOWN' or r.get('bgg_verdict','UNKNOWN') != 'UNKNOWN' for r in rows):
                raise ValueError('unsupported source identity or language')
            reused.extend(copy.deepcopy(rows))
    indexed(reused)
    completed={r['listing_id'] for r in reused}
    missing=[r for r in sample if r['listing_id'] not in completed]
    current=ledger['months'].get(month,dict(calls_reserved=0,reserved_eur='0',operations={}))
    for item in [current,source]:
        if type(item['calls_reserved']) is not int or not 0 <= item['calls_reserved'] <= MAX_CALLS:
            raise ValueError('invalid call reservations')
        if money(item['reserved_eur']) < RESERVATION * item['calls_reserved']:
            raise ValueError('inconsistent monetary reservations')
    if source['calls_reserved'] != len(source['responses']):
        raise ValueError('source response accounting inconsistent')
    headroom=max(Decimal('0'),monthly-money(current['reserved_eur']))
    slots=min(MAX_CALLS-current['calls_reserved'],int(headroom/RESERVATION),int(batch/RESERVATION))
    selected=copy.deepcopy(missing[:slots*8])
    calls=(len(selected)+7)//8
    preview=copy.deepcopy(ledger)
    operation='offline-resume-preview'
    while operation in current['operations']:operation+='-'
    for _ in range(calls):preview=reserve(preview,month,operation,str(monthly),str(batch))
    additional=RESERVATION*calls
    return dict(dry_run=True,provider_calls_executed=0,ledger_writes=0,catalog_writes=0,
                source_month=source_month,source_operation=source_operation,
                source_input_provenance='OPERATOR_SUPPLIED_RUN_ASSOCIATION_NOT_AUTOMATICALLY_VERIFIED',
                reused_answers=reused,selected_records=selected,
                deferred_ids=[r['listing_id'] for r in missing[len(selected):]],
                planned_calls=calls,additional_reserved_eur=format(additional,'f').lstrip('0') if additional else '0',
                complete_after_success=len(selected)==len(missing),
                execution_authorized=False,automatic_retry=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['sample','source-input','ledger','month','source-month','source-operation','output']:
        parser.add_argument('--'+name,required=True)
    parser.add_argument('--monthly',default='1')
    parser.add_argument('--batch',default='.10')
    args=parser.parse_args()
    inputs=[Path(p).resolve() for p in [args.sample,args.source_input,args.ledger]]
    output=Path(args.output).resolve()
    if output in inputs:parser.error('output must not overwrite inputs')
    sample,source=[json.loads(p.read_text(encoding='utf-8'))['records'] for p in inputs[:2]]
    ledger=json.loads(inputs[2].read_text(encoding='utf-8'))
    result=plan(sample,source,ledger,args.month,args.source_month,args.source_operation,args.monthly,args.batch)
    with output.open('x',encoding='utf-8') as f:json.dump(result,f,ensure_ascii=False,indent=2,allow_nan=False)


if __name__=='__main__':main()
