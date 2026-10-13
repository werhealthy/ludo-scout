"""Offline source regression: Actions may build beta, never publish it.

This does not prove that old repository credentials have been revoked.
"""
from pathlib import Path
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
sys.path.insert(0, str(Path(__file__).resolve().parent))
from manual_beta_version_code import derive_version_code


class BetaDistributionGateTests(unittest.TestCase):
    def test_no_actions_workflow_uses_firebase_release_authority(self):
        forbidden = re.compile(
            r"FIREBASE_SERVICE_ACCOUNT_JSON|GOOGLE_APPLICATION_CREDENTIALS|"
            r"appdistribution[/:.]|firebase-tools|firebase_admin", re.I
        )
        paths = sorted(WORKFLOWS.glob("*.y*ml"))
        self.assertTrue(paths)
        for path in paths:
            with self.subTest(workflow=path.name):
                self.assertIsNone(forbidden.search(path.read_text()))

    def test_push_and_manual_beta_runs_remain_build_only(self):
        source = (WORKFLOWS / "android-beta.yml").read_text()
        self.assertIn("workflow_dispatch:", source)
        self.assertIn("version_code_suffix:", source)
        self.assertIn("required: true", source)
        self.assertIn("VERSION_CODE_SUFFIX: ${{ inputs.version_code_suffix }}", source)
        self.assertIn("MIN_INSTALLED_VERSION_CODE = 1_002_033", (ROOT / "regression" / "manual_beta_version_code.py").read_text())
        self.assertIn("regression/manual_beta_version_code.py", source)
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.version_code_suffix || github.run_number", source)
        self.assertRegex(source, r"push:\s+branches:\s+- beta")
        self.assertIn("assembleDebug", source)
        self.assertIn("apksigner verify --print-certs", source)
        self.assertIn("actions/upload-artifact@v4", source)
        self.assertNotRegex(source, r"(?i)distribute|FIREBASE_APP_ID|FIREBASE_TESTERS")

    def test_beta_workflow_keeps_readonly_repository_permissions(self):
        source = (WORKFLOWS / "android-beta.yml").read_text()
        self.assertRegex(source, r"permissions:\s+contents: read")
        self.assertNotRegex(source, r"(?:contents|id-token|actions): write")

    def test_manual_version_code_suffix_is_strictly_upgrade_compatible(self):
        self.assertEqual(1002034, derive_version_code("2034"))
        self.assertEqual(1002035, derive_version_code("2035"))

    def test_manual_version_code_rejects_bad_floor_and_overflow_inputs(self):
        for value in ("", "abc", "-1", "2_034", "2033"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                derive_version_code(value)
        with self.assertRaisesRegex(ValueError, "signed 32-bit"):
            derive_version_code("2146483648")


if __name__ == "__main__":
    unittest.main()
