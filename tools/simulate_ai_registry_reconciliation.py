#!/usr/bin/env python3
"""Read-only simulation of proposal-to-snapshot links; numeric IDs are coverage only."""
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

EXTERNAL_FIELDS = ("item_id", "vinted_item_id", "item_url", "listing_url", "url")


def external_keys(record: dict[str, Any]) -> set[tuple[str, str]]:
    result: set[tuple[str, str]] = set()
    for field in EXTERNAL_FIELDS:
        value = record.get(field)
        if isinstance(value, str) and value.strip():
            kind = "id" if field in ("item_id", "vinted_item_id") else "url"
            result.add((kind, value.strip()))
    return result


def simulate(registry: dict[str, Any], snapshot: dict[str, Any]) -> dict[str, Any]:
    entries = registry.get("entries")
    rows = snapshot.get("rows")
    if not isinstance(entries, list) or not isinstance(rows, list):
        raise ValueError("registry_or_snapshot_shape_invalid")
    if registry.get("source_snapshot_id") != snapshot.get("snapshot_id"):
        raise ValueError("snapshot_id_mismatch")

    proposals = [e for e in entries if isinstance(e, dict) and e.get("record_state") == "PROPOSED"]
    row_id_counts = Counter(str(r.get("source_id")) for r in rows if isinstance(r, dict))
    by_key: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        if isinstance(row, dict):
            for key in external_keys(row):
                by_key[key].append(row)

    unique = ambiguous = missing = no_external_key = hash_conflict = 0
    for proposal in proposals:
        keys = external_keys(proposal)
        if not keys:
            no_external_key += 1
            continue
        candidates: dict[int, dict[str, Any]] = {}
        for key in keys:
            for row in by_key.get(key, []):
                candidates[id(row)] = row
        if not candidates:
            missing += 1
            continue
        if len(candidates) != 1 or any(len(by_key[key]) > 1 for key in keys if key in by_key):
            ambiguous += 1
            continue
        row = next(iter(candidates.values()))
        proposal_hash = proposal.get("source_hash")
        if proposal_hash and row.get("source_hash") and proposal_hash != row.get("source_hash"):
            hash_conflict += 1
            continue
        # An exact external key is necessary; hash equality alone never creates a link.
        unique += 1

    numeric_coverage = sum(
        1 for p in proposals
        if isinstance(p.get("listing_id"), (int, str))
        and row_id_counts.get(str(p.get("listing_id")), 0) == 1
    )
    numeric_duplicate_rows = sum(
        1 for p in proposals
        if row_id_counts.get(str(p.get("listing_id")), 0) > 1
    )
    exact_id_count = sum(bool(external_keys(p)) for p in proposals)
    return {
        "source_snapshot_id": snapshot.get("snapshot_id"),
        "snapshot_rows": len(rows),
        "registry_entries": len(entries),
        "proposals": len(proposals),
        "numeric_id_coverage_only_not_identity": numeric_coverage,
        "numeric_duplicate_source_rows": numeric_duplicate_rows,
        "proposals_with_external_key": exact_id_count,
        "uniquely_linkable_by_exact_external_key": unique,
        "ambiguous_external_key_matches": ambiguous,
        "missing_external_key_match": missing,
        "proposals_missing_external_key": no_external_key,
        "source_hash_conflicts_after_external_match": hash_conflict,
        "actually_updateable_proven": 0,
        "updateability_limit": "no read-only local current-value database supplied; no proposal may be applied from this simulation",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registry", required=True, type=Path)
    parser.add_argument("--snapshot-manifest", required=True, type=Path)
    args = parser.parse_args()
    registry = json.loads(args.registry.read_text(encoding="utf-8"))
    snapshot = json.loads(args.snapshot_manifest.read_text(encoding="utf-8"))
    print(json.dumps(simulate(registry, snapshot), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
