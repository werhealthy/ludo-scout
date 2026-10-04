import copy
import importlib.util
import json
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).parents[1]
sys.path.insert(0,str(ROOT/'tools'))

class ExecuteTest(unittest.TestCase):
    def setUp(self):
        spec=importlib.util.spec_from_file_location('execute',ROOT/'tools/classification_resume_execute.py')
        self.m=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.m)
        self.sample=json.loads((ROOT/'regression/fixtures/classification_input64.json').read_text())['records']
        self.rows=[dict(listing_id=r['listing_id'],category='UNKNOWN',confidence=0,evidence='insufficient',needs_review=True,language='UNKNOWN',bgg_verdict='UNKNOWN') for r in self.sample[:16]]
        entries=[dict(status='VALIDATED',model=self.m.runner.MODEL,answers=self.rows[i:i+8]) for i in [0,8]]
        entries.append(dict(status='FAILED_OR_INVALID',model=self.m.runner.MODEL))
        self.state=dict(version=1,disabled=False,months={'2026-10':dict(calls_reserved=3,reserved_eur='.03',operations={'source':dict(calls_reserved=3,reserved_eur='.03',responses=entries)})})
        self.events=[]
        class Store:
            def load(inner):return copy.deepcopy(self.state)
            def save(inner,value):self.events.append('save');self.state=copy.deepcopy(value)
        self.store=Store()

    def call(self,body):
        self.events.append('call')
        rows=json.loads(body['contents'][0]['parts'][0]['text'].split('DATA:\n')[1])
        self.assertFalse({r['listing_id'] for r in rows}&{r['listing_id'] for r in self.rows})
        return dict(usageMetadata=dict(promptTokenCount=100,candidatesTokenCount=200),candidates=[dict(finishReason='STOP',content=dict(parts=[dict(text=json.dumps([dict(listing_id=r['listing_id'],category='UNKNOWN',confidence=0,evidence='insufficient',needs_review=True,language='UNKNOWN',bgg_verdict='UNKNOWN') for r in rows]))]))])

    def execute(self,call=None):
        return self.m.execute(self.sample,self.sample,self.store,call or self.call,'2026-10','2026-10','source','target',[])

    def test_five_calls_reuse_prior_answers_preserve_reservations_and_report_partial(self):
        report=self.execute()
        self.assertEqual(self.events.count('call'),5)
        self.assertEqual(self.state['months']['2026-10']['calls_reserved'],8)
        self.assertEqual(self.state['months']['2026-10']['operations']['source']['calls_reserved'],3)
        self.assertEqual(report['answered_records'],56)
        self.assertEqual(len(report['missing_ids']),8)
        self.assertFalse(report['complete'])
        self.assertEqual(report['reused_records'],16)
        self.assertEqual(report['text_reference_metrics']['evaluated'],0)

    def test_failure_keeps_partial_report_and_never_retries(self):
        def fail(body):self.events.append('call');raise RuntimeError('unavailable')
        with self.assertRaises(RuntimeError):self.execute(fail)
        self.assertEqual(self.events.count('call'),1)
        op=self.state['months']['2026-10']['operations']['target']
        self.assertEqual(op['report']['answered_records'],16)
        self.assertEqual(op['responses'][0]['status'],'FAILED_OR_INVALID')
        self.assertEqual(self.state['months']['2026-10']['reserved_eur'],'.04')

    def test_rerun_same_operation_cannot_duplicate_calls(self):
        self.execute()
        self.events=[]
        with self.assertRaises(ValueError):self.execute()
        self.assertEqual(self.events,[])

    def test_disabled_or_changed_fixture_never_calls(self):
        self.state['disabled']=True
        with self.assertRaises(ValueError):self.execute()
        self.assertEqual(self.events,[])

    def test_next_month_continuation_reuses_inherited_and_new_answers(self):
        self.execute();self.events=[]
        report=self.m.execute(self.sample,self.sample,self.store,self.call,'2026-11','2026-10','target','last',[])
        self.assertEqual(self.events.count('call'),1)
        self.assertEqual(report['reused_records'],56)
        self.assertEqual(report['answered_records'],64)
        self.assertTrue(report['complete'])
        self.assertEqual(self.state['months']['2026-10']['calls_reserved'],8)

    def test_failed_initial_metadata_save_prevents_provider_calls(self):
        def fail(value):raise OSError('write unavailable')
        self.store.save=fail
        with self.assertRaises(OSError):self.execute()
        self.assertEqual(self.events,[])

    def test_source_workflow_and_fixture_provenance_are_checked(self):
        import base64
        requests=[]
        class Source:
            def load(inner):return copy.deepcopy(self.state)
            def request(inner,path):
                requests.append(path)
                if path.startswith('/actions/'):
                    return dict(status='completed',head_branch='beta',event='workflow_dispatch',head_sha='a'*40,path='.github/workflows/classification-ai-benchmark.yml')
                raw=json.dumps(dict(records=self.sample)) if 'fixtures' in path else Path(self.m.runner.__file__).read_text()
                return dict(content=base64.b64encode(raw.encode()).decode())
        store=Source()
        # The real operation IDs are numeric. Replace just the fixture's key.
        self.state['months']['2026-10']['operations']['123']=self.state['months']['2026-10']['operations'].pop('source')
        source,month=self.m.source_fixture(store,'123',self.sample)
        self.assertEqual(source,self.sample);self.assertEqual(month,'2026-10')
        self.assertEqual(len(requests),3)
        changed=copy.deepcopy(self.sample);changed[0]['title']='changed'
        with self.assertRaises(ValueError):self.m.source_fixture(store,'123',changed)

if __name__=='__main__':unittest.main()
