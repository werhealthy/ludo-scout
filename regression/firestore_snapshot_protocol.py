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
        self.staging = {"id": sid, "rows": rows, "uploaded": set(), "attempted": set(), "cleaned": set(),
                        "hash": digest(rows), "state": "UPLOADING"}
        self.manifests[sid] = {"complete": False, "count": len(rows), "hash": self.staging["hash"]}

    def materialize(self, sid, interrupt_after=None):
        # Mirrors store.begin(): every MATERIALIZING restart discards partial rows.
        self.staging = {"id": sid, "rows": [], "uploaded": set(), "attempted": set(), "cleaned": set(),
                        "hash": "", "state": "MATERIALIZING"}
        captured_view = [dict(r) for r in self.catalog]
        for index, row in enumerate(captured_view):
            self.staging["rows"].append(row)
            if interrupt_after is not None and index + 1 == interrupt_after:
                return False
        self.staging["hash"] = digest(captured_view)
        self.manifests[sid] = {"complete": False, "count": len(captured_view), "hash": self.staging["hash"]}
        self.staging["state"] = "STAGE_MANIFEST"
        self.staging["state"] = "UPLOADING"
        return True

    def upload(self, limit, interrupted=False):
        s = self.staging
        todo = [r for r in s["rows"] if r["id"] not in s["uploaded"]][:limit]
        # Remote set() is idempotent; interruption after server commit but before
        # local cursor persistence simply repeats the same IDs on retry.
        s["attempted"].update(r["id"] for r in todo)
        for row in todo:
            self.remote[(s["id"], row["id"])] = row["hash"]
        if interrupted:
            return
        s["uploaded"].update(r["id"] for r in todo)

    def cleanup_aborted(self, limit=80):
        s = self.staging
        if s["state"] == "ABORTING":
            self.manifests.pop(s["id"], None)  # seal first: no later row creates
            s["state"] = "ABORTING_SEALED"
            return
        todo = sorted(s["attempted"] - s["cleaned"])[:limit]
        for item_id in todo:
            self.remote.pop((s["id"], item_id), None)  # absent deletes are idempotent
        s["cleaned"].update(todo)
        if not todo:
            self.staging = {}

    def finish_and_publish(self):
        s = self.staging
        if len(s["uploaded"]) != len(s["rows"]):
            return False
        if digest(self.catalog) != s["hash"]:
            s["state"] = "ABORTING"
            return False
        s["state"] = "MANIFEST"
        if self.manifests[s["id"]] != {"complete": False, "count": len(s["rows"]), "hash": s["hash"]}:
            s["state"] = "ABORTING"
            return False
        self.manifests[s["id"]]["complete"] = True
        s["state"] = "PUBLISH"
        self.current = s["id"]
        return True

    def cleanup_old_page(self, snapshot_id, limit):
        # Mirrors cleanupOld(): deleted IDs vanish, so every pass starts at the
        # first remaining ID rather than trusting a durable cursor.
        ids = sorted(key[1] for key in self.remote if key[0] == snapshot_id)[:limit]
        for item_id in ids:
            del self.remote[(snapshot_id, item_id)]
        if not ids:
            self.manifests.pop(snapshot_id, None)
        return ids

    def cleanup_old_finalize(self, snapshot_id, late_write=None):
        # The final first-page check must see a late row before deleting its
        # completed parent manifest; completed manifests deny future writes.
        if late_write is not None:
            late_write(self)
        if any(key[0] == snapshot_id for key in self.remote):
            return False
        self.manifests.pop(snapshot_id, None)
        return not any(key[0] == snapshot_id for key in self.remote)


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
        self.assertFalse(m.manifests["snap-a"]["complete"])
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
        self.assertFalse(m.manifests["building"]["complete"])
        self.assertEqual("old", m.current)

    def test_incomplete_or_changed_snapshot_cannot_become_current(self):
        m = SnapshotModel([{"id": 1, "hash": "a"}, {"id": 2, "hash": "b"}], current="old")
        m.capture("partial")
        m.upload(1)
        self.assertFalse(m.finish_and_publish())
        self.assertEqual("old", m.current)
        self.assertFalse(m.manifests["partial"]["complete"])

    def test_aborted_generation_seals_before_cleaning_ambiguous_uploads(self):
        m = SnapshotModel([{"id": 1, "hash": "a"}], current="old")
        m.capture("aborted")
        m.upload(1, interrupted=True)  # remote commit without local uploaded acknowledgement
        m.staging["state"] = "ABORTING"
        m.cleanup_aborted()
        self.assertNotIn("aborted", m.manifests)
        self.assertEqual("ABORTING_SEALED", m.staging["state"])
        m.cleanup_aborted()
        self.assertFalse(any(key[0] == "aborted" for key in m.remote))
        m.cleanup_aborted()
        self.assertEqual({}, m.staging)

    def test_android_source_keeps_catalog_read_only_and_publication_gated(self):
        self.assertIn("SQLiteDatabase.OPEN_READONLY", WORKER)
        self.assertIn('source.execSQL("BEGIN")', WORKER)
        self.assertIn('source.execSQL("ROLLBACK")', WORKER)
        self.assertIn("source.rawQuery(QUERY,null)", WORKER)
        self.assertIn("sourceHash(catalog)", WORKER)
        self.assertIn('store.state("MANIFEST")', WORKER)
        self.assertIn('store.state("PUBLISH")', WORKER)
        self.assertIn('"STAGE_MANIFEST".equals(state.state)', WORKER)
        self.assertIn('manifest.put("complete",false)', WORKER)
        self.assertIn('manifestRef.update("complete",true)', WORKER)
        self.assertIn('"ABORTING_SEALED".equals(state.state)', WORKER)
        self.assertIn('store.markUploadAttempted(done)', WORKER)
        self.assertIn('upload_attempted=1', STORE)
        self.assertIn('upload_attempted=1 AND cleaned=0', STORE)
        self.assertIn("state.contentHash.equals(sourceHash(catalog))", WORKER)
        self.assertIn("MAX_PER_PASS = 80", WORKER)
        self.assertIn("MAX_PER_DAY = 2400", WORKER)
        self.assertIn("MAX_BYTES", STORE)
        self.assertIn("MAX_ROWS", STORE)
        self.assertIn("sync_v2", WORKER)
        self.assertIn("getAfter(", RULES)
        self.assertIn("stagingSnapshot(uid, deviceId, snapshotId)", RULES)
        self.assertIn("resource.data.complete == false", RULES)
        self.assertIn("snapshotId != currentSnapshotId(uid, deviceId)", RULES)
        self.assertIn("FirestoreSnapshotWorker.class", SYNC)
        self.assertIn("deleteRowsInBatches()", STORE)
        self.assertIn("MAX_DATABASE_BYTES = MAX_BYTES - AUXILIARY_RESERVE_BYTES", STORE)
        self.assertIn("sidecarBytes(databaseFile)", STORE)
        self.assertIn('"MATERIALIZING".equals(state.state)', WORKER)
        self.assertIn("store.begin(id,previous)", WORKER)
        cleanup = WORKER.split("private Result cleanupOld", 1)[1].split("private static void materialize", 1)[0]
        self.assertIn("orderBy(FieldPath.documentId()).limit(cap)", cleanup)
        self.assertNotIn("startAfter", cleanup)
        self.assertNotIn("previousCursor(", cleanup)
        self.assertIn("if(!finalCheck.isEmpty())return Result.success()", cleanup)
        self.assertLess(cleanup.index("beforeDelete="), cleanup.index("snapshot(db,owner,device,s.previousId).delete()"))

        sqlite_catches = WORKER.split("catch(SQLiteException full)", 1)[1].split("catch(Exception failure)", 1)[0]
        self.assertNotIn("resetIdle()", sqlite_catches)

    def test_sqlite_full_after_remote_commit_preserves_idempotent_retry_state(self):
        self.assertIn("store.markUploadAttempted(done)", WORKER)
        self.assertIn("Tasks.await(batch.commit(),35,TimeUnit.SECONDS);store.markUploaded(done)", WORKER)
        m = SnapshotModel([{"id": 1, "hash": "a"}, {"id": 2, "hash": "b"}], current="old")
        m.capture("same-generation")
        m.upload(1, interrupted=True)  # remote commit succeeded; SQLite acknowledgement failed
        self.assertEqual("same-generation", m.staging["id"])
        self.assertEqual({("same-generation", 1): "a"}, m.remote)
        self.assertEqual(set(), m.staging["uploaded"])
        m.upload(1)  # replay the same set() and persist the local acknowledgement
        while len(m.staging["uploaded"]) < len(m.staging["rows"]):
            m.upload(1)
        self.assertTrue(m.finish_and_publish())
        self.assertEqual("same-generation", m.current)
        self.assertEqual(2, len([key for key in m.remote if key[0] == "same-generation"]))

    def test_old_snapshot_cleanup_restart_catches_late_row_behind_previous_page(self):
        m = SnapshotModel([], current="new")
        m.manifests["old"] = {"complete": True}
        m.remote.update({("old", "2"): "a", ("old", "4"): "b", ("old", "8"): "c"})
        self.assertEqual(["2"], m.cleanup_old_page("old", 1))
        # Simulate an unexpected late write whose ID sorts before the old cursor.
        m.remote[("old", "1")] = "late"
        while m.cleanup_old_page("old", 1):
            pass
        self.assertFalse(any(snapshot_id == "old" for snapshot_id, _ in m.remote))
        self.assertNotIn("old", m.manifests)

    def test_old_snapshot_cleanup_rechecks_after_manifest_delete(self):
        m = SnapshotModel([], current="new")
        m.manifests["old"] = {"complete": True}
        late = lambda model: model.remote.__setitem__(("old", "5"), "late")
        self.assertFalse(m.cleanup_old_finalize("old", late))
        self.assertIn("old", m.manifests)
        self.assertEqual(["5"], m.cleanup_old_page("old", 1))
        self.assertTrue(m.cleanup_old_finalize("old"))

    def test_android_sidecar_bound_and_full_failure_leave_current_snapshot_untouched(self):
        # Reserve space for SQLite auxiliaries and check the complete sidecar set.
        self.assertIn("db.setMaximumSize(MAX_DATABASE_BYTES)", STORE)
        self.assertIn("MAX_BYTES = 32L * 1024L * 1024L", STORE)
        self.assertIn("AUXILIARY_RESERVE_BYTES = 2L * 1024L * 1024L", STORE)
        self.assertIn('catch(SQLiteException full)', WORKER)
        self.assertIn("private void deleteRowsInBatches()", STORE)
        self.assertIn('putString("snapshot_error","local_snapshot_limit")', WORKER)
        m = SnapshotModel([{"id": 1, "hash": "a"}], current="published")
        self.assertFalse(m.materialize("retryable", interrupt_after=1))
        self.assertEqual("published", m.current)
        self.assertNotIn("retryable", m.manifests)
        self.assertEqual("MATERIALIZING", m.staging["state"])
        self.assertTrue(m.materialize("retryable"))


if __name__ == "__main__":
    unittest.main()
