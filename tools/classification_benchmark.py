"""Offline, review-only comparison of externally supplied model answers.

No HTTP client, API key, provider call, photo download or database connection.
Input sample: {records: [...]} with listing_id, title, previous_type, bgg_id.
Answers and independent references are JSON arrays. Do not use model answers
as references. Titles alone cannot validate the product's language.
"""
import argparse
import json
import math
from pathlib import Path

CATEGORIES = {'BASE_GAME', 'EXPANSION', 'ACCESSORY_COMPONENT', 'BUNDLE', 'NON_GAME', 'UNKNOWN'}
PRIOR = {'GAME': 'BASE_GAME', 'ACCESSORY': 'ACCESSORY_COMPONENT',
         'COMPONENTS': 'ACCESSORY_COMPONENT', 'EMPTY_BOX': 'ACCESSORY_COMPONENT',
         'UNCERTAIN': 'UNKNOWN'}

def indexed(rows):
    result = {}
    for row in rows:
        key = row.get('listing_id')
        if type(key) is not int or key in result:
            raise ValueError('listing_id must be a unique integer')
        result[key] = row
    return result

def compare(sample, answers):
    source, predictions = indexed(sample), indexed(answers)
    if predictions.keys() - source.keys():
        raise ValueError('answer contains IDs outside the sample')
    result = []
    for key, item in source.items():
        a = predictions.get(key)
        flags = []
        if a is None:
            a = {'category': 'UNKNOWN', 'confidence': None, 'needs_review': True,
                 'evidence': 'Missing answer', 'language': 'UNKNOWN', 'bgg_verdict': 'UNKNOWN'}
            flags.append('MISSING_ANSWER')
        category, score = a.get('category'), a.get('confidence')
        if category not in CATEGORIES:
            raise ValueError('invalid category')
        if score is not None and (type(score) not in (int, float) or not math.isfinite(score) or not 0 <= score <= 100):
            raise ValueError('confidence must be finite, 0..100 or null')
        if type(a.get('needs_review')) is not bool:
            raise ValueError('needs_review must be boolean')
        if not isinstance(a.get('evidence'), str) or not a['evidence'].strip():
            raise ValueError('evidence is required')
        raw_language = a.get('language', 'UNKNOWN')
        if raw_language != 'UNKNOWN':
            flags.append('UNSUPPORTED_LANGUAGE')
        verdict = a.get('bgg_verdict', 'UNKNOWN')
        if verdict not in {'SUPPORTED', 'CONFLICT', 'UNKNOWN'}:
            raise ValueError('invalid BGG verdict')
        # An LLM claim cannot validate an identity: keep it separate from validation.
        previous = item.get('previous_type')
        normalized_previous = PRIOR.get(previous, previous)
        prior_status = 'MISSING' if previous is None else 'UNKNOWN' if normalized_previous == 'UNKNOWN' else 'KNOWN'
        if normalized_previous is not None and normalized_previous not in CATEGORIES:
            flags.append('UNRECOGNIZED_PREVIOUS_TYPE')
            prior_status = 'UNRECOGNIZED'
            normalized_previous = None
        result.append({'listing_id': key, 'previous_type': previous,
                       'prior_status': prior_status,
                       'proposed_type': category,
                       'type_changed': None if prior_status != 'KNOWN' else normalized_previous != category,
                       'confidence': score, 'confidence_kind': 'MODEL_SELF_REPORT_UNCALIBRATED',
                       'evidence': a['evidence'], 'method': 'EXTERNAL_MODEL_ANSWER',
                       'needs_review': bool(flags) or a['needs_review'] or category == 'UNKNOWN',
                       'raw_language': raw_language, 'language': 'UNKNOWN',
                       'previous_bgg_id': item.get('bgg_id'), 'model_bgg_verdict': verdict,
                       'bgg_verified': False, 'flags': flags,
                       'ai_cost_eur': None})
    return result

def metrics(rows, references):
    predictions, truth = indexed(rows), indexed(references)
    if truth.keys() - predictions.keys():
        raise ValueError('reference IDs outside the sample')
    tp = fp = fn = abstentions = correct = 0
    confusion = {}
    for key, ref in truth.items():
        expected, actual = ref.get('category'), predictions[key]['proposed_type']
        if expected not in CATEGORIES:
            raise ValueError('invalid reference category')
        confusion.setdefault(expected, {}).setdefault(actual, 0)
        confusion[expected][actual] += 1
        correct += expected == actual
        if expected != 'UNKNOWN':
            tp += expected == actual == 'BASE_GAME'
            fp += actual == 'BASE_GAME' and expected != 'BASE_GAME'
            fn += expected == 'BASE_GAME' and actual not in {'BASE_GAME', 'UNKNOWN'}
            abstentions += expected == 'BASE_GAME' and actual == 'UNKNOWN'
    n = len(rows)
    return {'evaluated': len(truth), 'total': n,
            'accuracy': correct / len(truth) if truth else None,
            'base_game_precision': tp / (tp + fp) if tp + fp else None,
            'base_game_false_positives': fp, 'base_game_false_negatives': fn,
            'base_game_abstentions': abstentions,
            'unknown_fraction': sum(r['proposed_type'] == 'UNKNOWN' for r in rows) / n if n else None,
            'changed_known_prior': sum(r.get('type_changed') is True for r in rows),
            'missing_prior': sum(r.get('prior_status') == 'MISSING' for r in rows),
            'unknown_prior': sum(r.get('prior_status') == 'UNKNOWN' for r in rows),
            'unrecognized_prior': sum(r.get('prior_status') == 'UNRECOGNIZED' for r in rows),
            'confusion_matrix': confusion, 'cost_per_listing_eur': None,
            'ai_calls_by_this_tool': 0}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sample', required=True)
    p.add_argument('--answers', required=True)
    p.add_argument('--references')
    p.add_argument('--model', required=True)
    p.add_argument('--output', required=True)
    args = p.parse_args()
    sources = [Path(x).resolve() for x in [args.sample, args.answers, args.references] if x]
    dest = Path(args.output).resolve()
    if dest in sources:
        p.error('output must not overwrite any input')
    if args.references and (sources[2] == sources[1] or sources[2].samefile(sources[1])):
        p.error('references must not be the model answers file')
    sample = json.loads(sources[0].read_text(encoding='utf-8'))['records']
    answers = json.loads(sources[1].read_text(encoding='utf-8'))
    refs = json.loads(sources[2].read_text(encoding='utf-8')) if args.references else []
    rows = compare(sample, answers)
    report = {'model': args.model, 'dry_run': True, 'records': rows, 'metrics': metrics(rows, refs),
              'reference_provenance': 'SUPPLIER_DECLARED_INDEPENDENT_NOT_VERIFIED' if refs else 'NO_REFERENCES'}
    with dest.open('x', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=2, allow_nan=False)

if __name__ == '__main__':
    main()
