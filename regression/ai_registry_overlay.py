import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main" / "java" / "it" / "vintedaffari" / "app"

def read(path):
    return path.read_text(encoding="utf-8")

def check(condition, label):
    if not condition:
        raise AssertionError(label)

import_ui = read(APP / "AiBetaRegistryImportUi.java")
store = read(APP / "AiBetaRegistryStore.java")
view = read(APP / "AiBetaRegistryView.java")
dialog = read(APP / "AiBetaRealDialog.java")
activity = read(APP / "MainActivity.java")
parser = read(APP / "AiBetaRegistry.java")
registry_ui = read(APP / "AiBetaRegistryUi.java")
draft = read(APP / "AiBetaRegistryDraft.java")
settings = read(APP / "AiBetaSettings.java")

check("Intent.ACTION_OPEN_DOCUMENT" in import_ui and "Intent.CATEGORY_OPENABLE" in import_ui, "SAF open-document flow absent")
check('intent.setType("application/json")' in import_ui and "REQUEST_CODE = 5811" in import_ui, "JSON SAF request contract changed")
check("openInputStream(selected)" in import_ui and "readBounded(input)" in import_ui, "selected file is not read with a size bound")
check("FLAG_GRANT_READ_URI_PERMISSION" in import_ui and "takePersistableUriPermission" not in import_ui, "unexpected persistent document permission")
check("getNoBackupFilesDir()" in store and "AtomicFile" in store, "private atomic registry storage absent")
check("SQLiteDatabase.OPEN_READONLY" in view and 'getDatabasePath("vinted_affari.db")' in view, "current metadata query is not read-only against the app database")
check('COLUMNS = "id,vinted_item_id,vinted_title,category_normalized,language_code"' in view and 'queryChunks(db, "vinted_item_id", itemIds, rows)' in view, "overlay does not read title and stable external ID")
check("entry.itemId != null" in view and "EXTERNAL_ID_NOT_FOUND" in view and "UNVERIFIED_NUMERIC_CANDIDATE" in view, "numeric ID is not fail-closed")
check("QUERY_FAILED" in view and "DATABASE_MISSING" in view, "query failure and database absence are not distinct")
check("getWritableDatabase" not in view and "INSERT" not in view and "UPDATE" not in view and "DELETE" not in view, "registry view contains a catalog write")
check("AiBetaRegistryImportUi.handle(this,request,result,data)" in activity, "MainActivity does not dispatch the SAF result")
check("Importa registro AI (JSON)" in dialog and "Mostra registro AI" in dialog and "AiBetaRegistryUi.open" in dialog and "adapter.getFilter().filter(s)" in registry_ui, "registry controls/search absent from existing comparison UI")
check("static String[] rows(Context context, AiBetaRegistry registry)" in view and "category_normalized" in view and "language_code" in view and "valueLabel" in view and "ID esterno nel registro" in view and "corrispondenze non valutabili" in view, "current metadata overlay list/status absent")
check("AI — non verificato" in view and "paired_ab_baseline" in view and "Proposta A/B corretta" in view and "esito non conservato" in view, "proposal provenance/review status presentation absent")
check("AiBetaSettings" in registry_ui and "readByItemId" in registry_ui and "preview.stillMatches(current.listing)" in registry_ui, "draft is not encrypted or stale-checked")
check("before_value" in draft and "after_value" in draft and "UNKNOWN" in draft and "never applies values" in draft, "single reversible non-catalog draft contract absent")
check("BUSY.compareAndSet(false, true)" in registry_ui and "saved.has(AiBetaRegistryDraft.SETTINGS_KEY)" in registry_ui and "Annulla bozza" in registry_ui and "AiBetaRegistryDraft.clear(saved)" in registry_ui, "single-draft serialization/cancel path absent")
check("AndroidKeyStore" in settings and "AES/GCM/NoPadding" in settings and "AtomicFile" in settings and "getNoBackupFilesDir()" in settings, "private settings are not encrypted/atomic/no-backup")
check("MAX_BYTES = 2 * 1024 * 1024" in parser and "MAX_ENTRIES = 2000" in parser, "parser bounds absent")
check("candidatePromptExecuted" in parser and "ID mancante o duplicato" in parser and 'allowed.add("item_id")' in parser, "candidate/duplicate/optional external ID validation absent")

if len(sys.argv) > 1:
    data_path = Path(sys.argv[1])
    registry = json.loads(data_path.read_text(encoding="utf-8"))
    root_keys = {"schema_version", "registry_id", "source_snapshot_id", "generated_at", "model_id", "model_id_status", "historical_prompt_id", "corrected_ab_prompt_id", "candidate_prompt_commit", "candidate_prompt_executed", "entries"}
    entry_keys = {"listing_id", "original_type", "original_language", "original_source", "corrected_type", "corrected_language", "corrected_source", "record_state", "review_flags"}
    check(set(registry) == root_keys, "registry root fields changed")
    check(registry["schema_version"] == 1 and registry["source_snapshot_id"] == "5c02314d-fd73-41b4-b1b3-be5448f374cd", "registry version/snapshot mismatch")
    check(registry["candidate_prompt_commit"] == "7fabd03a256e0bab12a093c227d569d0acdb9b4a" and registry["candidate_prompt_executed"] is False, "candidate provenance mismatch")
    entries = registry["entries"]
    check(len(entries) == 1658, "unexpected entry count")
    allowed_entry_keys = {frozenset(entry_keys), frozenset(entry_keys | {"item_id"})}
    check(all(frozenset(row) in allowed_entry_keys for row in entries), "entry fields are not minimized")
    ids = [row["listing_id"] for row in entries]
    check(len(ids) == len(set(ids)), "duplicate listing IDs")
    check(sum(row["record_state"] == "PROPOSED" for row in entries) == 1657, "historical proposal count mismatch")
    check(sum(row["record_state"] == "UNRESOLVED" for row in entries) == 1, "unresolved record count mismatch")
    check(sum(row["corrected_source"] is not None for row in entries) == 5, "paired A/B count mismatch")
    check({row["listing_id"] for row in entries if "LANGUAGE_REANALYSIS" in row["review_flags"]} == {1016, 1034, 1068, 1108, 1110, 1343}, "language reanalysis flags mismatch")
    check({row["listing_id"] for row in entries if "CATEGORY_REVIEW" in row["review_flags"]} == {1229}, "category review flag mismatch")
    check(all("source_hash" not in row and "title" not in row and "evidence" not in row and "photo" not in row and "author" not in row for row in entries), "transfer contains unnecessary listing evidence")
    expected = {1016: "IT", 1034: "FR", 1068: "FR", 1099: "NL", 1229: "DE"}
    actual = {row["listing_id"]: row["corrected_language"] for row in entries if row["corrected_source"] is not None}
    check(actual == expected, "paired A/B values changed")
    print(f"OK: 1658 unique entries, 5 separate A/B pairs, 6 language-review flags, 1 category-review flag; {data_path.stat().st_size} bytes")
else:
    print("OK: SAF/private-store/read-only-overlay source contract")
