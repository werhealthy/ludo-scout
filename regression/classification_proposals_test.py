import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
try:
    from classification_proposals import proposals
except ImportError:
    proposals = None

class ProposalTests(unittest.TestCase):
    def setUp(self):
        self.sample=[dict(listing_id=1,title='Game + playmat',brand='Maker',previous_type='ACCESSORY'),
                     dict(listing_id=2,title='Empty box',brand='',previous_type='EMPTY_BOX')]
        self.source=[dict(listing_id=r['listing_id'],title=r['title'],brand=r['brand']) for r in self.sample]
        self.answers=[dict(listing_id=1,category='BUNDLE',confidence=100,evidence='game plus playmat',needs_review=False,language='UNKNOWN',bgg_verdict='UNKNOWN')]
    def call(self):
        self.assertIsNotNone(proposals,'Offline proposal adapter is missing')
        return proposals(self.sample,self.source,self.answers,'model-v1','contract-v1')
    def test_changed_content_is_not_reused(self):
        for field in ['title','brand']:
            with self.subTest(field=field):
                old=self.sample[0][field];self.sample[0][field]='Changed'
                row=self.call()['records'][0]
                self.assertEqual(row['status'],'STALE_INPUT')
                self.assertIsNone(row['proposed_type'])
                self.sample[0][field]=old
    def test_no_model_claim_can_authorize_apply(self):
        row=self.call()['records'][0]
        self.assertEqual(row['proposed_type'],'BUNDLE')
        self.assertTrue(row['needs_review'])
        self.assertFalse(row['apply_authorized'])
        self.assertEqual(row['preserved_type'],'ACCESSORY')
        self.assertFalse(row['bgg_verified'])
    def test_missing_answer_preserves_empty_box(self):
        row=self.call()['records'][1]
        self.assertEqual(row['status'],'MISSING_ANSWER')
        self.assertEqual(row['preserved_type'],'EMPTY_BOX')
        self.assertIsNone(row['proposed_type'])
    def test_merged_accessory_class_never_invents_subtype(self):
        self.answers[0]['category']='ACCESSORY_COMPONENT'
        row=self.call()['records'][0]
        self.assertIsNone(row['runtime_type'])
        self.assertEqual(row['preserved_type'],'ACCESSORY')
    def test_invalid_or_duplicate_answers_rejected(self):
        for change in [lambda a:a.append(copy.deepcopy(a[0])),lambda a:a[0].update(confidence=float('nan')),lambda a:a[0].update(listing_id=99)]:
            old=copy.deepcopy(self.answers);change(self.answers)
            self.assertIsNotNone(proposals,'Offline proposal adapter is missing')
            with self.assertRaises(ValueError):self.call()
            self.answers=old
    def test_identity_language_claims_rejected(self):
        for field,value in [('language','IT'),('bgg_verdict','SUPPORTED')]:
            old=self.answers[0][field];self.answers[0][field]=value
            self.assertIsNotNone(proposals,'Offline proposal adapter is missing')
            with self.assertRaises(ValueError):self.call()
            self.answers[0][field]=old
    def test_content_key_binds_model_and_contract(self):
        first=self.call()['records'][0]['content_key']
        for model,contract in [('other','contract-v1'),('model-v1','other')]:
            other=proposals(self.sample,self.source,self.answers,model,contract)['records'][0]['content_key']
            self.assertNotEqual(first,other)
    def test_inputs_are_not_modified(self):
        before=copy.deepcopy((self.sample,self.source,self.answers));self.call()
        self.assertEqual((self.sample,self.source,self.answers),before)
    def test_unreported_confidence_stays_unknown(self):
        del self.answers[0]['confidence']
        self.assertIsNone(self.call()['records'][0]['confidence'])

if __name__=='__main__':unittest.main()
