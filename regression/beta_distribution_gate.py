"""Offline source regression: Actions may build beta, never publish it.

This does not prove that old repository credentials have been revoked.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"


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
        self.assertRegex(source, r"push:\s+branches:\s+- beta")
        self.assertIn("assembleDebug", source)
        self.assertIn("apksigner verify --print-certs", source)
        self.assertIn("actions/upload-artifact@v4", source)
        self.assertNotRegex(source, r"(?i)distribute|FIREBASE_APP_ID|FIREBASE_TESTERS")

    def test_beta_workflow_keeps_readonly_repository_permissions(self):
        source = (WORKFLOWS / "android-beta.yml").read_text()
        self.assertRegex(source, r"permissions:\s+contents: read")
        self.assertNotRegex(source, r"(?:contents|id-token|actions): write")


if __name__ == "__main__":
    unittest.main()
