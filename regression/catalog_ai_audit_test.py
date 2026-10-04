import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / 'tools/catalog_ai_audit.py'


class CatalogAuditTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(TOOL.exists(), 'Full archive audit is not implemented')
        sys.path.insert(0, str(ROOT / 'tools'))
        spec = importlib.util.spec_from_file_location('catalog_ai_audit', TOOL)
        self.m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.m)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / 'archive.zip'

    def archive(self, listings=None, games=None, legacy=None, manifest_changes=None):
        sections = {'listings': listings or [], 'games': games or [],
                    'legacy_deals': legacy or []}
        manifest = {'format': 'ludo-classification-audit-v1', 'read_only': True,
                    'snapshot_atomic': False, 'completed_at': 1,
                    'counts': {k: len(v) for k, v in sections.items()}}
        manifest.update(manifest_changes or {})
        with zipfile.ZipFile(self.path, 'w') as z:
            z.writestr('manifest.json', json.dumps(manifest))
            for name, rows in sections.items():
                z.writestr(name + '.jsonl', '\n'.join(json.dumps(r) for r in rows))
        return self.path

    def row(self, id=1, **fields):
        return dict(id=id, vinted_title='Game ' + str(id), brand='Maker',
                    lifecycle='ACTIVE', last_seen=id, **fields)

    def test_full_archive_no_eight_row_limit_and_no_writes(self):
        path = self.archive([self.row(i) for i in range(1, 101)])
        before = path.read_bytes()
        report = self.m.audit(path, 10)
        self.assertEqual(report['counts']['canonical_listings'], 100)
        self.assertEqual(report['plan']['planned_records'], 100)
        self.assertEqual(report['plan']['requests_required'], 13)
        self.assertEqual(path.read_bytes(), before)
        self.assertEqual(report['archive_sha256'], hashlib.sha256(before).hexdigest())
        self.assertEqual((report['provider_calls'], report['catalog_writes']), (0, 0))
        self.assertIsNone(report['accuracy'])

    def test_budget_does_not_pretend_full_ai_coverage(self):
        report = self.m.audit(self.archive([self.row(i) for i in range(1, 801)]), 10)
        self.assertEqual(report['plan']['planned_records'], 720)
        self.assertEqual(report['plan']['unplanned_records'], 80)
        self.assertEqual(report['plan']['remaining_calls_declared'], 90)
        self.assertFalse(report['plan']['full_ai_coverage_possible'])

    def test_duplicate_exact_identity_is_not_sent_twice(self):
        report = self.m.audit(self.archive([
            self.row(1, vinted_item_id='123'), self.row(2, vinted_item_id='123')]), 10)
        self.assertEqual(report['counts']['duplicate_identity_groups'], 1)
        self.assertEqual(report['plan']['planned_records'], 1)
        self.assertEqual(report['batches'][0]['records'][0]['listing_id'], 2)
        self.assertIn('DUPLICATE_EXACT_ITEM', report['findings'][0]['flags'])

    def test_historical_rows_are_counted_but_not_sent(self):
        rows = [dict(self.row(i), lifecycle=s) for i, s in enumerate(
            ['ACTIVE', 'AUTO_FILTERED', 'SOLD', 'USER_HIDDEN', 'ARCHIVED'], 1)]
        report = self.m.audit(self.archive(rows), 10)
        self.assertEqual(report['counts']['canonical_listings'], 5)
        self.assertEqual(report['plan']['eligible_records'], 2)

    def test_missing_game_and_conflicting_saved_identity_prioritized(self):
        report = self.m.audit(self.archive([
            self.row(1), self.row(2, game_id=99, vinted_item_id='7'),
            self.row(3, game_id=10, vinted_item_id='8')],
            games=[{'id': 10, 'bgg_id': '123', 'canonical_name': 'A'}],
            legacy=[{'id': 1, 'vinted_item_id': '8', 'bgg_id': '456'}]), 10)
        flags = {r['listing_id']: r['flags'] for r in report['findings']}
        self.assertIn('MISSING_SAVED_GAME', flags[2])
        self.assertIn('CONFLICTING_SAVED_BGG', flags[3])
        self.assertEqual(report['batches'][0]['records'][0]['listing_id'], 3)

    def test_stale_legacy_text_never_becomes_current_classification(self):
        report = self.m.audit(self.archive([self.row(1, legacy_signature='s')],
            legacy=[{'id': 1, 'signature': 's', 'vinted_title': 'Old',
                     'brand': 'Maker', 'listing_type': 'BASE_GAME'}]), 10)
        self.assertIsNone(report['findings'][0]['stored_type'])

    def test_duplicate_canonical_rows_with_different_bgg_are_a_conflict(self):
        report = self.m.audit(self.archive([
            self.row(1, game_id=10, vinted_item_id='123'),
            self.row(2, game_id=11, vinted_item_id='123')], games=[
                {'id': 10, 'bgg_id': '10'}, {'id': 11, 'bgg_id': '11'}]), 10)
        for row in report['findings']:
            self.assertIn('CONFLICTING_SAVED_BGG', row['flags'])

    def test_old_duplicate_risk_prioritizes_latest_payload(self):
        report = self.m.audit(self.archive([
            self.row(1, game_id=99, vinted_item_id='123'),
            self.row(2, vinted_item_id='123'), self.row(3)]), 10)
        self.assertEqual(report['batches'][0]['records'][0]['listing_id'], 2)

    def test_long_unicode_rows_use_actual_transport_byte_limit(self):
        rows = [dict(self.row(i), vinted_title='猫' * 300) for i in range(1, 10)]
        report = self.m.audit(self.archive(rows), 10)
        for batch in report['batches']:
            body = {'request_id': 'x' * 80, 'records': batch['records']}
            self.assertLessEqual(len(json.dumps(body, ensure_ascii=False,
                separators=(',', ':')).encode('utf8')), 4096)
        self.assertEqual(report['plan']['planned_records'], 9)

    def test_single_oversized_record_is_explicitly_excluded_not_truncated(self):
        report = self.m.audit(self.archive([dict(self.row(), vinted_title='猫' * 2000)]), 10)
        self.assertEqual(report['plan']['oversized_records'], 1)
        self.assertEqual(report['plan']['planned_records'], 0)

    def test_manifest_mismatch_duplicate_ids_invalid_counter_rejected(self):
        for changes in [{'format': 'wrong'}, {'counts': {'listings': 99}}]:
            with self.assertRaises(ValueError):
                self.m.audit(self.archive([self.row()], manifest_changes=changes), 10)
        with self.assertRaises(ValueError):
            self.m.audit(self.archive([self.row(), self.row()]), 10)
        for calls in (-1, 101, True, 1.2):
            with self.assertRaises(ValueError):
                self.m.audit(self.archive([self.row()]), calls)

    def test_cli_refuses_overwriting_sources_or_existing_report(self):
        path = self.archive([self.row()])
        output = Path(self.tmp.name) / 'report.json'
        cmd = [sys.executable, str(TOOL), '--archive', str(path), '--calls-reserved', '10']
        self.assertNotEqual(subprocess.run(cmd + ['--output', str(path)], capture_output=True).returncode, 0)
        self.assertEqual(subprocess.run(cmd + ['--output', str(output)], capture_output=True).returncode, 0)
        before = output.read_bytes()
        self.assertNotEqual(subprocess.run(cmd + ['--output', str(output)], capture_output=True).returncode, 0)
        self.assertEqual(output.read_bytes(), before)


if __name__ == '__main__':
    unittest.main()
