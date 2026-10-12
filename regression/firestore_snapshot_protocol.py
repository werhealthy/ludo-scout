"""Deterministic contract model for immutable Firestore snapshot publication.

The model exercises crash/source-change invariants and is paired with source
assertions tying the production Android worker to the same states and limits.
"""
import hashlib
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/it/vintedaffari/app"
WORKER = (JAVA / "FirestoreSnapshotWorker.java").read_text(encoding="utf-8")
STORE = (JAVA / "FirestoreSnapshotStore.java").read_text(encoding="utf-8")
SYNC = (JAVA / "FirestoreSync.java").read_text(encoding="utf-8")
RULES = (ROOT / "firestore.rules").read_text(encoding="utf-8")


def digest(rows):
    payload = "".join(f"{row['id']}:{row['hash']}\n" for row in rows)
    return hashlib.sha256(payload.encode()).hexdigest()


class SnapshotModel:
    """Tiny behavioral oracle for snapshot immutability and publication gate."""
    def __init__(self, catalog, current=None):
        self.catalog = catalog
        self.current = current
        self.remote = {}
        self.manifests = {}
        self.staging = {}

    def capture(self, sid):
        rows = [dict(r) for r in self.catalog]
        self.staging = {"id": sid, "rows": rows, "uploaded": set(),
                        "hash": digest(rows), "state": "UPLOADING"}

    def materialize(self, sid, interrupt_after=None):
        # Mirrors store.begin(): every MATERIALIZING restart discards partial rows.
        self.staging = {"id": sid, "rows": [], "uploaded": set(),
                        "hash": "", "state": "MATERIALIZING"}
        captured_view = [dict(r) for r in self.catalog]
        for index, row in enumerate(captured_view):
            self.staging["rows"].append(row)
            if interrupt_after is not None and index + 1 == interrupt_after:
                return False
        self.staging["hash"] = digest(captured_view)
        self.staging["state"] = "UPLOADING"
        return True

    def upload(self, limit, interrupted=False):
        s = self.staging
        todo = [r for r in s["rows"] if r["id"] not in s["uploaded"]][:limit]
        # Remote set() is idempotent; interruption after server commit but before
        # local cursor persistence simply repeats the same IDs on retry.
        for row in todo:
            self.remote[(s["id"], row["id"])] = row["hash"]
        if interrupted:
            return
        s["uploaded"].update(r["id"] for r in todo)

    def finish_and_publish(self):
        s = self.staging
        if len(s["uploaded"]) != len(s["rows"]):
            return False
        if digest(self.catalog) != s["hash"]:
            s["state"] = "ABORTING"
            return False
        s["state"] = "MANIFEST"
        self.manifests[s["id"]] = {"complete": True, "count": len(s["rows"]), "hash": s["hash"]}
        s["state"] = "PUBLISH"
        self.current = s["id"]
        return True


class SnapshotProtocolTests(unittest.TestCase):
    def test_catalog_change_while_uploading_never_mixes_or_publishes_stale_snapshot(self):
        initial = [{"id": 1, "hash": "a"}, {"id": 2, "hash": "b"}]
        m = SnapshotModel(initial, current="old")
        m.capture("snap-a")
        m.upload(1)
        m.catalog = [{"id": 1, "hash": "changed"}, {"id": 2, "hash": "b"}, {"id": 3, "hash": "c"}]
        m.upload(10)
        self.assertFalse(m.finish_and_publish())
        self.assertEqual("old", m.current)
        self.assertNotIn("snap-a", m.manifests)
        self.assertEqual({("snap-a", 1): "a", ("snap-a", 2): "b"}, m.remote)

    def test_interruption_resumes_same_materialized_generation_idempotently(self):
        m = SnapshotModel([{"id": 1, "hash": "a"}, {"id": 2, "hash": "b"}], current="old")
        m.capture("same-snapshot")
        m.upload(1, interrupted=True)
        persisted = m.staging
        m.staging = persisted  # process restart reloads the durable sidecar state
        while len(m.staging["uploaded"]) < len(m.staging["rows"]):
            m.upload(1)
        self.assertTrue(m.finish_and_publish())
        self.assertEqual("same-snapshot", m.current)
        self.assertEqual(2, m.manifests["same-snapshot"]["count"])
        self.assertEqual(2, len([k for k in m.remote if k[0] == "same-snapshot"]))

    def test_materialization_restart_discards_partial_sidecar_before_new_read_view(self):
        m = SnapshotModel([{"id": 1, "hash": "old-a"}, {"id": 2, "hash": "old-b"}], current="old")
        self.assertFalse(m.materialize("building", interrupt_after=1))
        m.catalog = [{"id": 1, "hash": "new-a"}, {"id": 2, "hash": "new-b"}, {"id": 3, "hash": "new-c"}]
        self.assertTrue(m.materialize("building"))
        self.assertEqual(["new-a", "new-b", "new-c"], [row["hash"] for row in m.staging["rows"]])
        self.assertEqual("UPLOADING", m.staging["state"])
        self.assertEqual("old", m.current)

    def test_incomplete_or_changed_snapshot_cannot_become_current(self):
        m = SnapshotModel([{"id": 1, "hash": "a"}, {"id": 2, "hash": "b"}], current="old")
        m.capture("partial")
        m.upload(1)
        self.assertFalse(m.finish_and_publish())
        self.assertEqual("old", m.current)
        self.assertNotIn("partial", m.manifests)

    def test_android_source_keeps_catalog_read_only_and_publication_gated(self):
        self.assertIn("SQLiteDatabase.OPEN_READONLY", WORKER)
        self.assertIn('source.execSQL("BEGIN")', WORKER)
        self.assertIn('source.execSQL("ROLLBACK")', WORKER)
        self.assertIn("source.rawQuery(QUERY,null)", WORKER)
        self.assertIn("sourceHash(catalog)", WORKER)
        self.assertIn('store.state("MANIFEST")', WORKER)
        self.assertIn('store.state("PUBLISH")', WORKER)
        self.assertIn("state.contentHash.equals(sourceHash(catalog))", WORKER)
        self.assertIn("MAX_PER_PASS = 80", WORKER)
        self.assertIn("MAX_PER_DAY = 2400", WORKER)
        self.assertIn("MAX_BYTES", STORE)
        self.assertIn("MAX_ROWS", STORE)
        self.assertIn("sync_v2", WORKER)
        self.assertIn("getAfter(", RULES)
        self.assertIn("snapshotId != currentSnapshotId(uid, deviceId)", RULES)
        self.assertIn("FirestoreSnapshotWorker.class", SYNC)
        self.assertIn('db.delete("snapshot_rows", null, null)', STORE)
        self.assertIn('"MATERIALIZING".equals(state.state)', WORKER)
        self.assertIn("store.begin(id,previous)", WORKER)


if __name__ == "__main__":
    unittest.main()
