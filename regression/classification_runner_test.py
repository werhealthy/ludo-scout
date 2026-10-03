import importlib.util
import unittest
import sys
import copy
import json
import urllib.error
from unittest.mock import patch
from pathlib import Path

PATH = Path(__file__).parents[1] / 'tools/classification_benchmark_runner.py'

class RunnerTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(PATH.exists(), 'protected runner absent')
        sys.path.insert(0,str(PATH.parent))
        spec = importlib.util.spec_from_file_location('runner', PATH)
        self.m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.m)

    def test_monthly_budget_and_calls_are_hard_stops(self):
        ledger = {'months': {}}
        for _ in range(8): ledger = self.m.reserve(ledger, '2026-10', 'op', '1', '.10')
        with self.assertRaises(ValueError): self.m.reserve(ledger, '2026-10', 'other', '1', '.10')
        with self.assertRaises(ValueError): self.m.reserve({'months': {}}, '2026-10', 'op', '.005', '.10')
        with self.assertRaises(ValueError): self.m.reserve({'months': {}}, '2026-10', 'op', '1', '.005')
        with self.assertRaises(ValueError): self.m.reserve({'months': {}}, '2026-10', 'op', 'NaN', '.10')

    def test_failed_requests_keep_reservation_and_never_retry(self):
        events=[];state={'months':{}}
        def save(v): events.append('save');state.clear();state.update(v)
        def call(body): events.append('call');raise RuntimeError('provider unavailable')
        with self.assertRaises(RuntimeError):
            self.m.run([{'listing_id':1,'title':'Hive','brand':'Gen42'}],lambda:state,save,call,'2026-10','op','1','.10')
        self.assertEqual(events,['save','call','save'])
        self.assertEqual(state['months']['2026-10']['operations']['op']['responses'][0]['status'],'FAILED_OR_INVALID')
        self.assertEqual(state['months']['2026-10']['reserved_eur'],'.01')

    def test_http_failure_records_only_numeric_status_without_retry(self):
        for code in [429, 503]:
            state={'months':{}};calls=[]
            def save(value):state.clear();state.update(copy.deepcopy(value))
            def call(body):
                calls.append(body)
                raise urllib.error.HTTPError('https://example.invalid/?key=SECRET',code,'SECRET',{'X-Secret':'SECRET'},None)
            with self.assertRaises(urllib.error.HTTPError):
                self.m.run([{'listing_id':1,'title':'Hive'}],lambda:copy.deepcopy(state),save,call,'2026-10','op')
            entry=state['months']['2026-10']['operations']['op']['responses'][0]
            self.assertEqual(entry.get('http_status'),code)
            self.assertEqual(entry['error_kind'],'HTTPError')
            self.assertEqual(len(calls),1)
            self.assertEqual(state['months']['2026-10']['reserved_eur'],'.01')
            self.assertNotIn('SECRET',json.dumps(state))

    def test_invalid_sample_and_oversized_payload_make_no_call(self):
        for sample in [[{'listing_id':1,'title':'x'*5000,'brand':''}],
                       [{'listing_id':1,'title':'x','brand':''}]*2]:
            with self.assertRaises(ValueError):self.m.payload(sample)

    def test_usage_and_missing_candidates_stop_processing(self):
        with self.assertRaises(ValueError):self.m.response({'usageMetadata':{'promptTokenCount':1}},[1])
        with self.assertRaises(ValueError):self.m.response({'usageMetadata':{'promptTokenCount':50000}},[1])

    def valid_response(self, ids):
        return {'usageMetadata':{'promptTokenCount':100,'candidatesTokenCount':200},
                'candidates':[{'finishReason':'STOP','content':{'parts':[{'text':json.dumps([
                    {'listing_id':i,'category':'UNKNOWN','confidence':0,'evidence':'insufficient',
                     'needs_review':True,'language':'UNKNOWN'} for i in ids])}]}}]}

    def test_full_sample_eight_requests_and_excluded_pending_references(self):
        state={'months':{}};calls=[]
        sample=json.loads((PATH.parents[1]/'regression/fixtures/classification_input64.json').read_text())['records']
        def save(value):state.clear();state.update(copy.deepcopy(value))
        def call(body):
            data=json.loads(body['contents'][0]['parts'][0]['text'].split('DATA:\n')[1])
            calls.append(body);return self.valid_response([r['listing_id'] for r in data])
        answers=self.m.run(sample,lambda:copy.deepcopy(state),save,call,'2026-10','op')
        self.assertEqual((len(calls),len(answers)),(8,64))
        self.assertEqual(state['months']['2026-10']['reserved_eur'],'.08')
        self.assertEqual(len(state['months']['2026-10']['operations']['op']['responses']),8)
        refs=json.loads((PATH.parents[1]/'regression/fixtures/classification_reference64.json').read_text())['records']
        self.assertEqual(sum(r['reference_status']=='TEXT_REFERENCE' for r in refs),44)
        self.assertEqual(sum(r['reference_status']=='PENDING' for r in refs),20)

    def test_disabled_corrupt_ledger_and_small_whole_batch_block(self):
        sample=[{'listing_id':i,'title':'Hive'} for i in range(9)]
        for state,budget in [({'disabled':True,'months':{}},'.10'),({'months':{}},'.01')]:
            events=[]
            with self.assertRaises(ValueError):self.m.run(sample,lambda:state,lambda x:events.append('save'),lambda x:events.append('call'),'2026-10','op',batch=budget)
            self.assertEqual(events,[])
        state=self.m.reserve({'months':{}},'2026-10','op','1','.10')
        state['months']['2026-10']['calls_reserved']=-1
        with self.assertRaises(ValueError):self.m.reserve(state,'2026-10','op','1','.10')

    def test_failed_durable_reservation_prevents_transport(self):
        calls=[]
        def save(value):raise OSError('ledger unavailable')
        with self.assertRaises(OSError):self.m.run([{'listing_id':1,'title':'Hive'}],lambda:{'months':{}},save,lambda x:calls.append(x),'2026-10','op')
        self.assertEqual(calls,[])

    def test_invalid_response_stops_after_one_call_and_retains_budget(self):
        state={'months':{}};calls=[]
        def save(value):state.clear();state.update(copy.deepcopy(value))
        def call(body):calls.append(body);return self.valid_response([999])
        with self.assertRaises(ValueError):self.m.run([{'listing_id':1,'title':'Hive'}],lambda:copy.deepcopy(state),save,call,'2026-10','op')
        self.assertEqual(len(calls),1)
        self.assertEqual(state['months']['2026-10']['reserved_eur'],'.01')

    def test_first_run_bootstrap_is_durable_and_missing_existing_ledger_blocks(self):
        for exists in [False,True]:
            events=[]
            with patch.dict('os.environ',{'GITHUB_REPOSITORY':'werhealthy/ludo-scout','GH_TOKEN':'test','GITHUB_SHA':'base'}):
                ledger=self.m.GitHubLedger()
            def request(path,method='GET',data=None):
                events.append((path,method))
                if path.startswith('/contents') and method=='GET':raise urllib.error.HTTPError('test',404,'missing',{},None)
                if path.startswith('/git/ref/heads'):
                    if exists:return {}
                    raise urllib.error.HTTPError('test',404,'missing',{},None)
                if method=='PUT':return {'content':{'sha':'saved'}}
                return {}
            ledger.request=request
            with patch.dict('os.environ',{'GITHUB_SHA':'base'}):
                if exists:
                    with self.assertRaises(RuntimeError):ledger.load()
                    self.assertFalse(any(method=='PUT' for _,method in events))
                else:
                    self.assertEqual(ledger.load()['version'],1)
                    self.assertEqual(ledger.sha,'saved')
                    self.assertEqual(events[-1],('/contents/ledger.json','PUT'))

if __name__=='__main__':unittest.main()
