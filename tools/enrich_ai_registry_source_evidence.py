#!/usr/bin/env python3
"""Attach exact source-row hashes to a proposal registry without rerunning AI."""
from __future__ import annotations

import argparse
import copy
import json
import re
from pathlib import Path
from typing import Any

HASH_RE = re.compile(r"[0-9a-f]{64}\Z")
VERIFIED_ROOT_HASH = "c911db60035a411ed22501628133e61029d4018d69821452faa4693213d640ed"


def enrich_registry(registry: dict[str, Any], source_map: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(registry, dict) or not isinstance(registry.get("entries"), list):
        raise ValueError("registry_shape_invalid")
    snapshot_id = registry.get("source_snapshot_id")
    if not isinstance(snapshot_id, str) or not snapshot_id:
        raise ValueError("registry_snapshot_missing")
    if not isinstance(source_map, dict) or source_map.get("snapshot_id") != snapshot_id:
        raise ValueError("source_snapshot_mismatch")
    evidence = source_map.get("evidence")
    if (not isinstance(evidence, dict)
            or evidence.get("row_hash_matches_manifest") != 1658
            or evidence.get("remote_rows") != 1658
            or evidence.get("root_hash") != VERIFIED_ROOT_HASH):
        raise ValueError("historical_map_integrity_not_verified")
    if evidence.get("ai_rerun") is not False:
        raise ValueError("unexpected_inference_flag")

    by_source_id: dict[int, dict[str, str]] = {}
    source_rows = source_map.get("entries")
    if not isinstance(source_rows, list):
        raise ValueError("source_map_entries_invalid")
    for row in source_rows:
        if not isinstance(row, dict):
            raise ValueError("source_map_row_invalid")
        try:
            source_id = int(row["source_id"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError("source_id_invalid") from exc
        if source_id <= 0 or source_id in by_source_id:
            raise ValueError("source_id_missing_or_ambiguous")
        if not HASH_RE.fullmatch(row.get("source_hash", "")):
            raise ValueError("source_hash_invalid")
        if row.get("historical_link_status") not in (
                "PROVEN_PROPOSAL_TO_SNAPSHOT_ROW",
                "SNAPSHOT_ROW_LINKED_BUT_PROPOSAL_UNRESOLVED"):
            raise ValueError("historical_source_link_not_verified")
        if row.get("catalog_updateable") is not False:
            raise ValueError("catalog_write_authorization_present")
        by_source_id[source_id] = row

    entries = registry["entries"]
    if len(entries) != len(by_source_id):
        raise ValueError("source_registry_count_mismatch")
    enriched = copy.deepcopy(registry)
    seen_listing_ids: set[int] = set()
    for entry in enriched["entries"]:
        if not isinstance(entry, dict):
            raise ValueError("registry_entry_invalid")
        try:
            listing_id = int(entry["listing_id"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError("listing_id_invalid") from exc
        if listing_id <= 0 or listing_id in seen_listing_ids:
            raise ValueError("listing_id_missing_or_ambiguous")
        seen_listing_ids.add(listing_id)
        source = by_source_id.get(listing_id)
        if source is None:
            raise ValueError("listing_id_has_no_exact_source_row")
        # The registry builder contract explicitly sets listing_id = source["source_id"].
        if int(source.get("listing_id", -1)) != listing_id:
            raise ValueError("historical_listing_id_source_id_conflict")
        if source.get("registry_state") != entry.get("record_state"):
            raise ValueError("historical_registry_state_conflict")
        # Snapshot, root hash, source ID, registry state and row hash are validated above.
        entry["source_id"] = int(source["source_id"])
        entry["source_hash"] = source["source_hash"]
        entry["source_hash_version"] = 1

    if seen_listing_ids != set(by_source_id):
        raise ValueError("source_registry_id_set_mismatch")
    return enriched


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registry", required=True, type=Path)
    parser.add_argument("--source-map", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.registry.resolve() == args.output.resolve():
        parser.error("output_must_not_overwrite_original_registry")

    registry = json.loads(args.registry.read_text(encoding="utf-8"))
    source_map = json.loads(args.source_map.read_text(encoding="utf-8"))
    enriched = enrich_registry(registry, source_map)
    encoded = json.dumps(enriched, ensure_ascii=False, indent=2) + "\n"
    if len(encoded.encode("utf-8")) > 2 * 1024 * 1024:
        raise SystemExit("enriched_registry_too_large")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(encoded, encoding="utf-8")
    print(json.dumps({
        "output": str(args.output),
        "entries": len(enriched["entries"]),
        "source_id_and_hash_attached": sum("source_hash" in row for row in enriched["entries"]),
        "external_item_id_attached": sum(bool(row.get("item_id")) for row in enriched["entries"]),
        "historical_map_root_hash": source_map["evidence"]["root_hash"],
        "historical_identity_proven": 0,
        "original_registry_overwritten": False,
        "ai_rerun": False,
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
