"""Manual opt-in continuation; no automatic retry, fallback or catalog write."""
import ast
import base64
import copy
import datetime
import json
import os
from pathlib import Path
import re
import urllib.request
import classification_benchmark_runner as runner
from classification_benchmark import compare, metrics, indexed
from classification_resume_plan import plan


def execute(sample, source_input, store, call, month, source_month, source_id, operation, refs, monthly='1', batch='.10'):
    saved=store.load()
    if operation in saved['months'].get(month,{}).get('operations',{}):
        raise ValueError('operation already exists; no automatic retry')
    selection=plan(sample,source_input,saved,month,source_month,source_id,monthly,batch)
    current=saved['months'].setdefault(month,dict(calls_reserved=0,reserved_eur='0',operations={}))
    current['operations'][operation]=dict(calls_reserved=0,reserved_eur='0',responses=[],
        reused_answers=selection['reused_answers'],source_month=source_month,source_operation=source_id)
    store.save(saved)
    error=None
    try:
        if selection['selected_records']:
            runner.run(selection['selected_records'],store.load,store.save,call,month,operation,monthly,batch)
    except Exception as caught:
        error=caught
    saved=store.load();op=saved['months'][month]['operations'][operation]
    answers=copy.deepcopy(op['reused_answers'])
    for entry in op['responses']:
        if entry['status']=='VALIDATED':answers.extend(entry['answers'])
    indexed(answers)
    ids={r['listing_id'] for r in answers}
    context={r['listing_id']:r for r in refs}
    enriched=[dict(r,previous_type=context.get(r['listing_id'],{}).get('previous_type'),
                   bgg_id=context.get(r['listing_id'],{}).get('previous_bgg_id')) for r in sample]
    records=compare(enriched,answers)
    evaluated=[r for r in records if r['listing_id'] in ids]
    references=[r for r in refs if r['listing_id'] in ids and r['reference_status']=='TEXT_REFERENCE']
    report=dict(model=runner.MODEL,records=records,accuracy=None,scope='TEXT_ONLY_NOT_PHYSICAL_OR_BGG_VALIDATION',
                answered_records=len(answers),reused_records=len(op['reused_answers']),
                missing_ids=[r['listing_id'] for r in sample if r['listing_id'] not in ids],
                complete=len(ids)==len(sample),text_reference_metrics=metrics(evaluated,references))
    report['text_reference_metrics']['scope']='COMPLETED_TEXT_REFERENCES_NOT_PHYSICAL_GROUND_TRUTH'
    report['text_reference_metrics']['ai_calls_by_this_tool']=len(op['responses'])
    op['report']=report;store.save(saved)
    if error is not None:raise error
    return report


def source_fixture(store, source_id, sample):
    if not re.fullmatch(r'[0-9]+',source_id):raise ValueError('invalid source run ID')
    meta=store.request('/actions/runs/'+source_id)
    if meta.get('status')!='completed' or meta.get('head_branch')!='beta' or meta.get('event')!='workflow_dispatch':
        raise ValueError('source must be a completed manual beta run')
    if meta.get('path')!='.github/workflows/classification-ai-benchmark.yml':raise ValueError('wrong source workflow')
    sha=meta['head_sha']
    if not re.fullmatch(r'[a-f0-9]{40}',sha):raise ValueError('invalid source commit')
    data=store.request('/contents/regression/fixtures/classification_input64.json?ref='+sha)
    source=json.loads(base64.b64decode(data['content']))['records']
    if source!=sample:raise ValueError('source fixture changed')
    # Inspect syntax only; never execute code from the source commit.
    data=store.request('/contents/tools/classification_benchmark_runner.py?ref='+sha)
    old=ast.parse(base64.b64decode(data['content']).decode('utf-8'))
    current=ast.parse(Path(runner.__file__).read_text(encoding='utf-8'))
    def contract(tree):
        names={'MODEL','INSTRUCTIONS','INPUT_BOUND','OUTPUT_BOUND','RESERVATION','MAX_CALLS'}
        return [ast.dump(n,include_attributes=False) for n in tree.body
                if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id in names for t in n.targets)
                or isinstance(n,ast.FunctionDef) and n.name in {'payload','response'}]
    if contract(old)!=contract(current):raise ValueError('source model or classification contract changed')
    ledger=store.load()
    months=[m for m,v in ledger['months'].items() if source_id in v['operations']]
    if len(months)!=1:raise ValueError('source operation missing or ambiguous')
    return source,months[0]


def main():
    source_id=os.environ.get('RESUME_SOURCE_RUN','').strip()
    if not source_id:
        return runner.main()
    if os.environ.get('AI_BENCHMARK_ENABLED')!='true' or os.environ.get('CONFIRM_FREE_TIER')!='true' or os.environ.get('GITHUB_REF')!='refs/heads/beta':
        raise ValueError('disabled or unconfirmed manual beta operation')
    key=os.environ['GEMINI_API_KEY']
    if not key.strip():raise ValueError('missing API key')
    root=Path(__file__).parents[1]
    sample=json.loads((root/'regression/fixtures/classification_input64.json').read_text())['records']
    refs=json.loads((root/'regression/fixtures/classification_reference64.json').read_text())['records']
    store=runner.GitHubLedger()
    source,source_month=source_fixture(store,source_id,sample)
    def call(body):
        req=urllib.request.Request('https://generativelanguage.googleapis.com/v1beta/models/'+runner.MODEL+':generateContent',
            data=json.dumps(body).encode(),headers={'x-goog-api-key':key,'Content-Type':'application/json'},method='POST')
        with urllib.request.build_opener(runner.NoRedirect).open(req,timeout=45) as result:return json.load(result)
    month=datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m')
    report=execute(sample,source,store,call,month,source_month,source_id,os.environ['GITHUB_RUN_ID'],refs,
        os.environ.get('AI_MONTHLY_BUDGET_EUR','1'),os.environ.get('AI_BATCH_BUDGET_EUR','.10'))
    print('Manual continuation recorded: '+str(report['answered_records'])+' total answers; '+str(len(report['missing_ids']))+' missing. No catalog writes.')


if __name__=='__main__':
    try:main()
    except Exception as error:
        print('Manual benchmark stopped safely: '+type(error).__name__+'. No retries. Reservations retained.')
        raise SystemExit(1)
