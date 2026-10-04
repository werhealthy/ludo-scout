import copy
import importlib.util
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).parents[1]
sys.path.insert(0, str(ROOT / 'tools'))

class ResumeTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('resume', ROOT / 'tools/classification_resume_plan.py')
        self.m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.m)
        self.sample = json.loads((ROOT / 'regression/fixtures/classification_input64.json').read_text())['records']
        self.rows = [dict(listing_id=r['listing_id'], category='UNKNOWN', confidence=0,
                          evidence='insufficient', needs_review=True, language='UNKNOWN', bgg_verdict='UNKNOWN')
                     for r in self.sample[:16]]
        entries = [dict(status='VALIDATED',model='gemini-3.1-flash-lite',answers=self.rows[i:i+8]) for i in [0,8]]
        entries.append(dict(status='FAILED_OR_INVALID',model='gemini-3.1-flash-lite',error_kind='HTTPError'))
        self.ledger = dict(version=1,disabled=False,months={'2026-10':dict(reserved_eur='.03',calls_reserved=3,
                            operations={'source':dict(reserved_eur='.03',calls_reserved=3,responses=entries)})})

    def plan(self, **kwargs):
        return self.m.plan(self.sample,self.sample,self.ledger,'2026-10','2026-10','source',**kwargs)

    def test_partial_plan_reuses_16_selects_40_and_preserves_8_missing(self):
        before = copy.deepcopy(self.ledger)
        p = self.plan()
        self.assertEqual(p['reused_answers'], self.rows)
        self.assertEqual(p['selected_records'], self.sample[16:56])
        self.assertEqual(p['deferred_ids'],[r['listing_id'] for r in self.sample[56:]])
        self.assertEqual(p['planned_calls'],5)
        self.assertEqual(p['additional_reserved_eur'],'.05')
        self.assertFalse(p['complete_after_success'])
        self.assertEqual(p['provider_calls_executed'],0)
        self.assertEqual(self.ledger,before)

    def test_budget_and_disabled_ledger_stop_or_reduce_plan(self):
        self.assertEqual(self.plan(monthly='.04')['planned_calls'],1)
        self.assertEqual(self.plan(batch='.02')['planned_calls'],2)
        self.ledger['disabled']=True
        with self.assertRaises(ValueError):self.plan()

    def test_changed_source_input_duplicate_or_unknown_answers_block(self):
        changed=copy.deepcopy(self.sample);changed[0]['title']='different'
        with self.assertRaises(ValueError):self.m.plan(self.sample,changed,self.ledger,'2026-10','2026-10','source')
        for invalid in [self.rows[0],dict(self.rows[0],listing_id=-1)]:
            ledger=copy.deepcopy(self.ledger)
            ledger['months']['2026-10']['operations']['source']['responses'][1]['answers'][0]=invalid
            with self.assertRaises(ValueError):self.m.plan(self.sample,self.sample,ledger,'2026-10','2026-10','source')

    def test_exhausted_budget_has_no_selected_records(self):
        self.ledger['months']['2026-10'].update(calls_reserved=8,reserved_eur='.08')
        p=self.plan()
        self.assertEqual(p['planned_calls'],0)
        self.assertEqual(p['selected_records'],[])
        self.assertEqual(len(p['deferred_ids']),48)

    def test_next_month_can_plan_all_remaining_without_resetting_old_reservations(self):
        p=self.m.plan(self.sample,self.sample,self.ledger,'2026-11','2026-10','source')
        self.assertEqual(p['planned_calls'],6)
        self.assertTrue(p['complete_after_success'])
        self.assertEqual(self.ledger['months']['2026-10']['calls_reserved'],3)

if __name__=='__main__':unittest.main()
