"""Audit the complete Android classification export, without network or writes.

Produces structural findings and a bounded title/brand AI batch plan. It does
not rerun Java classification, validate BGG identities, apply corrections or
call a model. Counts cover the supplied archive, not the live phone database.
The reservation count is caller-declared, never a live server budget reading.
"""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import zipfile

MAX_SECTION = 128 * 1024 * 1024
MAX_TOTAL = 512 * 1024 * 1024
CURRENT = {'ACTIVE', 'AUTO_FILTERED'}


def load_archive(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('duplicate archive entries')
        if sum(x.file_size for x in archive.infolist()) > MAX_TOTAL:
            raise ValueError('archive exceeds audit bound')
        def read(name):
            info = archive.getinfo(name)
            if info.file_size > MAX_SECTION:
                raise ValueError('section exceeds audit bound')
            return archive.read(info).decode('utf-8')
        manifest = json.loads(read('manifest.json'))
        if manifest.get('format') != 'ludo-classification-audit-v1' or manifest.get('read_only') is not True:
            raise ValueError('unsupported export format')
        sections = {}
        for name in ('listings', 'games', 'legacy_deals'):
            rows = [json.loads(line) for line in read(name + '.jsonl').splitlines() if line.strip()]
            expected = manifest.get('counts', {}).get(name)
            if type(expected) is not int or expected != len(rows):
                raise ValueError('export row count mismatch: ' + name)
            ids = [row.get('id') for row in rows]
            if any(type(id) is not int or id <= 0 for id in ids) or len(ids) != len(set(ids)):
                raise ValueError('invalid or duplicate IDs: ' + name)
            sections[name] = rows
        return manifest, sections


def text(value):
    return value if isinstance(value, str) else ''


def identity(value):
    return '' if value in (None, '') else str(value)


def body_size(rows):
    return len(json.dumps({'request_id': 'x' * 80, 'records': rows},
                         ensure_ascii=False, separators=(',', ':')).encode('utf-8'))


def archive_digest(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def audit(path, calls_reserved):
    if type(calls_reserved) is not int or not 0 <= calls_reserved <= 100:
        raise ValueError('declared reservations must be an integer from 0 to 100')
    path = Path(path)
    digest = archive_digest(path)
    manifest, sections = load_archive(path)
    if archive_digest(path) != digest:
        raise ValueError('archive changed during audit; use a completed export')
    games = {row['id']: row for row in sections['games']}
    legacy_signatures, legacy_items, items = defaultdict(list), defaultdict(list), defaultdict(list)
    for row in sections['legacy_deals']:
        if row.get('signature'):
            legacy_signatures[row['signature']].append(row)
        if row.get('vinted_item_id'):
            legacy_items[identity(row['vinted_item_id'])].append(row)
    for row in sections['listings']:
        if row.get('vinted_item_id'):
            items[identity(row['vinted_item_id'])].append(row)
    findings, candidates = [], []
    for row in sections['listings']:
        title, brand = text(row.get('vinted_title')), text(row.get('brand'))
        item = identity(row.get('vinted_item_id'))
        game = games.get(row.get('game_id'))
        bgg = identity(game.get('bgg_id')) if game else ''
        flags = []
        if not title.strip(): flags.append('MISSING_TITLE')
        if not brand.strip(): flags.append('MISSING_BRAND')
        if not text(row.get('observed_text')).strip(): flags.append('MISSING_OBSERVED_TEXT')
        if not item: flags.append('MISSING_EXACT_ITEM')
        if row.get('game_id') and not game: flags.append('MISSING_SAVED_GAME')
        if not bgg: flags.append('NO_SAVED_BGG')
        if item and len(items[item]) > 1: flags.append('DUPLICATE_EXACT_ITEM')
        saved_bgg = {identity(r.get('bgg_id')) for r in legacy_items[item] if r.get('bgg_id')}
        for duplicate in items[item]:
            duplicate_game = games.get(duplicate.get('game_id'))
            if duplicate_game and duplicate_game.get('bgg_id'):
                saved_bgg.add(identity(duplicate_game['bgg_id']))
        if bgg: saved_bgg.add(bgg)
        if len(saved_bgg) > 1: flags.append('CONFLICTING_SAVED_BGG')
        if row.get('manual_review_required'): flags.append('EXISTING_REVIEW')
        if row.get('category_normalized') and not row.get('category_source'):
            flags.append('CATEGORY_PROVENANCE_MISSING')
        # Historical text/type is contextual only, and only with exact current texts.
        old = [r for r in legacy_signatures[row.get('legacy_signature')]
               if (text(r.get('vinted_title')), text(r.get('brand'))) == (title, brand)]
        types = {r.get('listing_type') for r in old if r.get('listing_type')}
        stored = next(iter(types)) if len(types) == 1 else None
        if len(types) > 1: flags.append('CONFLICTING_STORED_TYPES')
        finding = {'listing_id': row['id'], 'lifecycle': row.get('lifecycle'),
                   'stored_type': stored, 'flags': flags, 'bgg_verified': False,
                   'apply_authorized': False}
        findings.append(finding)
        if row.get('lifecycle') not in CURRENT or not title.strip():
            continue
        score = sum(weight for flag, weight in (
            ('CONFLICTING_SAVED_BGG', 100), ('MISSING_SAVED_GAME', 80),
            ('EXISTING_REVIEW', 60), ('CONFLICTING_STORED_TYPES', 50),
            ('CATEGORY_PROVENANCE_MISSING', 30), ('MISSING_OBSERVED_TEXT', 10)) if flag in flags)
        candidates.append((score, row.get('last_seen') or 0, row['id'], item,
                           {'listing_id': row['id'], 'title': title, 'brand': brand}))
    # Canonical item ID dedupe uses the most recently seen eligible row, never fuzzy title.
    representatives = {}
    priorities = {}
    for candidate in candidates:
        key = ('item', candidate[3]) if candidate[3] else ('listing', candidate[2])
        priorities[key] = max(priorities.get(key, 0), candidate[0])
        previous = representatives.get(key)
        if previous is None or candidate[1:3] > previous[1:3]:
            representatives[key] = candidate
    ordered = sorted((tuple([priorities[key]]) + row[1:] for key, row in representatives.items()),
                     key=lambda r: (-r[0], -r[1], -r[2]))
    all_batches, pending, oversized = [], [], []
    for candidate in ordered:
        payload = candidate[4]
        if body_size([payload]) > 4096:
            oversized.append(payload['listing_id'])
            continue
        if pending and (len(pending) == 8 or body_size(pending + [payload]) > 4096):
            all_batches.append({'records': pending}); pending = []
        pending.append(payload)
    if pending: all_batches.append({'records': pending})
    remaining = 100 - calls_reserved
    batches = all_batches[:remaining]
    planned = sum(len(b['records']) for b in batches)
    counts = Counter(row.get('lifecycle') or 'UNSPECIFIED' for row in sections['listings'])
    return {'format': 'ludo-catalog-ai-audit-v1', 'archive_sha256': digest,
            'source_completed_at': manifest.get('completed_at'),
            'snapshot_atomic': manifest.get('snapshot_atomic') is True,
            'scope': 'SUPPLIED_EXPORT_NOT_LIVE_PHONE',
            'provider_calls': 0, 'catalog_writes': 0, 'accuracy': None,
            'error_reduction': None, 'bgg_verified': False,
            'counts': {'canonical_listings': len(findings), 'lifecycle': dict(counts),
                       'duplicate_identity_groups': sum(len(rows) > 1 for rows in items.values())},
            'findings': findings,
            'plan': {'status': 'PLAN_ONLY_NOT_EXECUTED', 'calls_reserved_declared': calls_reserved,
                     'remaining_calls_declared': remaining, 'eligible_records': len(ordered),
                     'requests_required': len(all_batches), 'planned_requests': len(batches),
                     'planned_records': planned, 'unplanned_records': len(ordered) - planned,
                     'oversized_records': len(oversized), 'oversized_listing_ids': oversized,
                     'full_ai_coverage_possible': planned == len(ordered)},
            'batches': batches}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', required=True)
    parser.add_argument('--calls-reserved', required=True, type=int)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    source, output = Path(args.archive).resolve(), Path(args.output).resolve()
    if source == output or output.exists():
        parser.error('output must be a new file, separate from the archive')
    report = audit(source, args.calls_reserved)
    with output.open('x', encoding='utf-8') as file:
        json.dump(report, file, ensure_ascii=False, indent=2, allow_nan=False)


if __name__ == '__main__':
    main()
