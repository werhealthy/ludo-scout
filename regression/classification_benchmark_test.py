import importlib.util
from pathlib import Path
import unittest
import json
import subprocess
import sys
import tempfile

PATH = Path(__file__).parents[1] / 'tools/classification_benchmark.py'

class BenchmarkTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(PATH.exists(), 'offline comparison tool missing')
        spec = importlib.util.spec_from_file_location('benchmark', PATH)
        self.m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.m)
        self.sample = [{'listing_id': 1, 'title': 'Andor Solo Erweiterung',
                        'previous_type': 'BASE_GAME', 'bgg_id': '127398'}]
        self.answer = [{'listing_id': 1, 'category': 'EXPANSION', 'confidence': 100,
                        'evidence': 'Erweiterung', 'needs_review': False,
                        'language': 'DE', 'bgg_verdict': 'UNKNOWN'}]

    def test_title_language_is_not_product_evidence(self):
        r = self.m.compare(self.sample, self.answer)[0]
        self.assertEqual(r['language'], 'UNKNOWN')
        self.assertEqual(r['raw_language'], 'DE')
        self.assertIn('UNSUPPORTED_LANGUAGE', r['flags'])
        self.assertTrue(r['needs_review'])
        self.assertEqual(r['confidence_kind'], 'MODEL_SELF_REPORT_UNCALIBRATED')

    def test_missing_answer_is_unknown_and_never_a_disappearance(self):
        r = self.m.compare(self.sample, [])[0]
        self.assertEqual(r['proposed_type'], 'UNKNOWN')
        self.assertTrue(r['needs_review'])

    def test_duplicates_extra_ids_and_invalid_confidence_fail(self):
        for a in [self.answer * 2, [dict(self.answer[0], listing_id=2)],
                  [dict(self.answer[0], confidence=float('nan'))],
                  [dict(self.answer[0], needs_review='No')],
                  [dict(self.answer[0], category='made_up')]]:
            with self.subTest(a=a), self.assertRaises(ValueError):
                self.m.compare(self.sample, a)

    def test_previous_empty_box_mapping_and_no_invented_prior(self):
        sample = [dict(self.sample[0], previous_type='EMPTY_BOX')]
        a = [dict(self.answer[0], category='ACCESSORY_COMPONENT', language='UNKNOWN')]
        self.assertFalse(self.m.compare(sample, a)[0]['type_changed'])
        sample[0]['previous_type'] = None
        self.assertIsNone(self.m.compare(sample, a)[0]['type_changed'])

    def test_unannotated_sample_has_no_accuracy(self):
        rows = self.m.compare(self.sample, self.answer)
        m = self.m.metrics(rows, [])
        self.assertIsNone(m['base_game_precision'])
        self.assertEqual(m['evaluated'], 0)

    def test_unknown_prior_is_not_changed_known_classification(self):
        a = [dict(self.answer[0], category='BASE_GAME', language='UNKNOWN')]
        for previous, status in [('UNCERTAIN', 'UNKNOWN'), ('UNKNOWN', 'UNKNOWN'),
                                 (None, 'MISSING'), ('unsupported', 'UNRECOGNIZED')]:
            rows = self.m.compare([dict(self.sample[0], previous_type=previous)], a)
            self.assertEqual(rows[0]['prior_status'], status)
            self.assertIsNone(rows[0]['type_changed'])
            self.assertEqual(self.m.metrics(rows, [])['changed_known_prior'], 0)

    def test_manual_smoke_outputs_do_not_become_ground_truth(self):
        fixture = json.loads((PATH.parents[1] / 'regression/fixtures/classification_smoke.json').read_text())
        reports = [self.m.compare(fixture['sample'], answers) for answers in fixture['answers'].values()]
        self.assertEqual([x['proposed_type'] for x in reports[0]], [x['proposed_type'] for x in reports[1]])
        self.assertEqual(sum('UNSUPPORTED_LANGUAGE' in x['flags'] for x in reports[1]), 3)
        for report in reports:
            self.assertIsNone(self.m.metrics(report, [])['accuracy'])
            self.assertTrue(all(x['language'] == 'UNKNOWN' and not x['bgg_verified'] for x in report))

    def test_cli_does_not_overwrite_input_or_existing_output(self):
        with tempfile.TemporaryDirectory() as directory:
            d = Path(directory)
            sample, answers, out = d / 'sample.json', d / 'answers.json', d / 'out.json'
            sample.write_text(json.dumps({'records': self.sample}))
            answers.write_text(json.dumps(self.answer))
            before = sample.read_bytes()
            cmd = [sys.executable, str(PATH), '--sample', str(sample), '--answers', str(answers), '--model', 'test']
            self.assertNotEqual(subprocess.run(cmd + ['--output', str(sample)], capture_output=True).returncode, 0)
            self.assertEqual(sample.read_bytes(), before)
            self.assertEqual(subprocess.run(cmd + ['--output', str(out)], capture_output=True).returncode, 0)
            previous = out.read_bytes()
            self.assertNotEqual(subprocess.run(cmd + ['--output', str(out)], capture_output=True).returncode, 0)
            self.assertEqual(out.read_bytes(), previous)
            self.assertNotEqual(subprocess.run(cmd + ['--references', str(answers), '--output', str(d / 'self.json')], capture_output=True).returncode, 0)

    def test_false_positive_false_negative_and_abstention(self):
        rows = [{'listing_id': 1, 'proposed_type': 'BASE_GAME'},
                {'listing_id': 2, 'proposed_type': 'EXPANSION'},
                {'listing_id': 3, 'proposed_type': 'UNKNOWN'}]
        refs = [{'listing_id': 1, 'category': 'EXPANSION'},
                {'listing_id': 2, 'category': 'BASE_GAME'},
                {'listing_id': 3, 'category': 'BASE_GAME'}]
        m = self.m.metrics(rows, refs)
        self.assertEqual((m['base_game_false_positives'], m['base_game_false_negatives']), (1, 1))
        self.assertEqual(m['base_game_abstentions'], 1)
        self.assertEqual(m['unknown_fraction'], 1 / 3)

    def test_reduction_uses_same_independently_labelled_rows(self):
        rows = self.m.compare([
            {'listing_id': 1, 'previous_type': 'BASE_GAME'},
            {'listing_id': 2, 'previous_type': 'NON_GAME'},
            {'listing_id': 3, 'previous_type': 'UNCERTAIN'},
        ], [dict(self.answer[0], listing_id=i, category='NON_GAME', language='UNKNOWN')
            for i in (1, 2, 3)])
        report = self.m.error_reduction(rows, [
            {'listing_id': i, 'category': 'NON_GAME'} for i in (1, 2, 3)])
        self.assertEqual(report['paired_evaluated'], 2)
        self.assertEqual(report['baseline_errors'], 1)
        self.assertEqual(report['model_errors_or_abstentions'], 0)
        self.assertEqual(report['relative_error_reduction'], 1)

    def test_abstaining_cannot_be_reported_as_eliminating_errors(self):
        row = {'listing_id': 1, 'proposed_type': 'UNKNOWN',
               'previous_type': 'BASE_GAME', 'prior_status': 'KNOWN'}
        report = self.m.error_reduction([row], [{'listing_id': 1, 'category': 'NON_GAME'}])
        self.assertEqual(report['relative_error_reduction'], 0)
        self.assertEqual(report['model_abstentions'], 1)

    def test_no_denominator_means_no_reduction_claim(self):
        row = {'listing_id': 1, 'proposed_type': 'NON_GAME',
               'previous_type': 'NON_GAME', 'prior_status': 'KNOWN'}
        for refs in ([], [{'listing_id': 1, 'category': 'UNKNOWN'}],
                     [{'listing_id': 1, 'category': 'NON_GAME'}]):
            self.assertIsNone(self.m.error_reduction([row], refs)['relative_error_reduction'])

    def test_unknown_reference_does_not_count_as_accuracy(self):
        result = self.m.metrics([{'listing_id': 1, 'proposed_type': 'UNKNOWN'}],
                                [{'listing_id': 1, 'category': 'UNKNOWN'}])
        self.assertIsNone(result['accuracy'])
        self.assertEqual(result['evaluated'], 0)
        self.assertEqual(result['unknown_references'], 1)

if __name__ == '__main__':
    unittest.main()
