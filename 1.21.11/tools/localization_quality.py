#!/usr/bin/env python3
"""Offline localization review gate. Validation never approves its own inputs."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

LANGUAGES = ('en_us', 'de_de', 'es_es')
CONTEXTS = {
    'item': {'speaker': 'Item tooltip / registry', 'display': 'Inventory tooltip',
             'meaning': 'Keep item identity; lore explains acquisition or use, not a different item.'},
    'quest': {'speaker': 'Quest giver / objective tracker', 'display': 'Offer, objective, reward or chronicle',
              'meaning': 'Match the named quest, chapter, actual objectives, delivery items and unlocks.'},
    'screen': {'speaker': 'Interface', 'display': 'Heading, card, button, status or tooltip',
               'meaning': 'Describe the current action/state; long bodies must wrap or scroll completely.'},
    'message': {'speaker': 'Server feedback / NPC dialogue', 'display': 'Chat or dialogue',
                'meaning': 'Preserve success/failure reason, next action, speaker and recipients.'},
    'command': {'speaker': 'Command feedback', 'display': 'Chat / command help',
                'meaning': 'Command literals remain unchanged; surrounding explanations are localized.'},
    'reward': {'speaker': 'Reward selection', 'display': 'Reward card, confirmation or collection notice',
               'meaning': 'Distinguish earned, pending and collected rewards; preserve quantities and ownership.'},
    'text': {'speaker': 'Gameplay service', 'display': 'Status, objective or shop explanation',
             'meaning': 'Preserve materials, prices, quantities and conditions; parameters carry runtime values.'},
    'reputation': {'speaker': 'Reputation service', 'display': 'Trust incident, access or reparation feedback',
                   'meaning': 'Keep track, change sign, prerequisites and personal/village scope.'},
    'entity': {'speaker': 'Entity registry', 'display': 'NPC name / entity tooltip',
               'meaning': 'Name the actual NPC or caravan role consistently.'},
    'block': {'speaker': 'Block registry', 'display': 'Block name', 'meaning': 'Keep block/item identity.'},
    'itemGroup': {'speaker': 'Creative inventory', 'display': 'Tab title', 'meaning': 'Mod category title.'},
    'key': {'speaker': 'Controls interface', 'display': 'Key binding / category', 'meaning': 'Actual bound action.'},
    'bossbar': {'speaker': 'Encounter service', 'display': 'Encounter progress bar', 'meaning': 'Actual encounter state.'},
    'gui': {'speaker': 'Vanilla interface integration', 'display': 'GUI label', 'meaning': 'Actual UI action.'},
    'hud': {'speaker': 'Route overlay', 'display': 'Minimap / HUD heading and footer',
            'meaning': 'Match route direction, status and controls without changing bound command names.'},
    'tag': {'speaker': 'Item tag registry', 'display': 'Ingredient / tag name',
            'meaning': 'Name the actual ingredient group; legacy key namespaces remain compatible.'},
}
GLOSSARY = {
    'de_de': {'forbidden': [r'\bWallet\b', r'\bLedger\b', r'\bQuestmaster\b',
                            r'\bWayshrine\b', r'\bHomestead\b', r'\b(?:Farming|Crafting)-Ruf\b'],
              'preferred': {'wallet': 'Geldbeutel', 'ledger': 'Buch (Dorfbuch/Karawanenbuch nach Kontext)',
                            'questmaster': 'Questmeister', 'spider fangs': 'Fangzähne',
                            'farming reputation': 'Ruf in der Landwirtschaft'}},
    'es_es': {'forbidden': [r'\b(?:Wayfinder|Orientador)\b'],
              'preferred': {'surveyor compass': 'brújula del explorador', 'planks': 'tablones',
                            'spider fangs': 'colmillos'}},
    'en_us': {'forbidden': [], 'preferred': {'spider fangs': 'Fangs', 'surveyor compass': 'Surveyor’s Compass'}},
}


def digest(value: str) -> str:
    return hashlib.sha256(value.encode('utf-8')).hexdigest()


def source_digest(path: Path) -> str:
    return digest(path.read_text(encoding='utf-8-sig').replace('\r\n', '\n'))


def catalogs(root: Path) -> dict:
    folder = root / 'src/main/resources/assets/village-quest/lang'
    return {locale: json.loads((folder / (locale + '.json')).read_text(encoding='utf-8'))
            for locale in LANGUAGES}


def source_files(root: Path) -> dict:
    # Also catches dynamically constructed localization keys and indirect unlock changes.
    files = {path.relative_to(root).as_posix(): path
            for folder in ('src/main/java', 'src/client/java')
            for path in sorted((root / folder).rglob('*.java'))}
    files.update({path.relative_to(root).as_posix(): path
                  for path in sorted((root / 'src/main/resources/data').rglob('*.json'))})
    return files


def entry_review(key: str, values: dict, note: str) -> dict:
    return {'context': key.split('.')[0], 'subject': key,
            'note': note, 'translations': {locale: digest(values[locale][key]) for locale in LANGUAGES}}


def source_uses(root: Path, keys: list[str]) -> dict:
    """Index literal consumers and explicit dynamic key prefixes; do not guess an exact consumer."""
    result = {key: [] for key in keys}
    for name, path in source_files(root).items():
        text = path.read_text(encoding='utf-8-sig')
        text = re.sub(r'"([^"\r\n]*)"\s*\+\s*VillageQuest\.MOD_ID\s*\+\s*"([^"\r\n]*)"',
                      lambda match: '"' + match[1] + 'village-quest' + match[2] + '"', text)
        literals = {value for value in re.findall(r'"([^"\r\n]*)"', text) if 'village-quest' in value}
        for key in keys:
            if key in literals:
                result[key].append({'path': name, 'match': 'literal'})
            elif any(len(value) > len('item.village-quest.') and key.startswith(value)
                     for value in literals if value.endswith(('.', '_'))):
                result[key].append({'path': name, 'match': 'dynamic-prefix'})
    return result


def build_review(root: Path, note: str) -> dict:
    """One-time migration helper, never called by validation/build or exposed as a CLI refresh-all."""
    values = catalogs(root)
    review = {'schema': 1, 'contexts': CONTEXTS, 'glossary': GLOSSARY,
            'keys': {key: entry_review(key, values, note) for key in sorted(values['en_us'])},
            'sources': {name: {'sha256': source_digest(path), 'note': note}
                        for name, path in source_files(root).items()}}
    uses = source_uses(root, list(review['keys']))
    for key, entry in review['keys'].items():
        entry['uses'] = uses[key]
        entry['trace'] = ('Indexed literal/dynamic consumers; also inspect source contracts.' if uses[key]
                          else 'Registry-derived or runtime-composed key: inspect source contracts; no exact consumer inferred.')
    return review


def validate_review(root: Path, review: dict) -> list[str]:
    errors = []
    if review.get('schema') != 1:
        return ['unsupported or missing context review schema']
    values = catalogs(root)
    entries = review.get('keys', {})
    actual_keys = set().union(*(set(value) for value in values.values()))
    for key in sorted(actual_keys):
        entry = entries.get(key)
        if not isinstance(entry, dict):
            errors.append(f'{key}: missing context review')
            continue
        context = review.get('contexts', {}).get(entry.get('context'))
        if not isinstance(context, dict) or any(not context.get(field) for field in ('speaker', 'display', 'meaning')):
            errors.append(f'{key}: unknown context or incomplete speaker/display/meaning')
        if entry.get('subject') != key or not str(entry.get('note', '')).strip():
            errors.append(f'{key}: missing subject or review note')
        for locale in LANGUAGES:
            value = values[locale].get(key)
            if not isinstance(value, str):
                errors.append(f'{locale}/{key}: missing translation')
            elif entry.get('translations', {}).get(locale) != digest(value):
                errors.append(f'{locale}/{key}: text review is stale; inspect context and review this key')
            if isinstance(value, str) and not key.startswith('command.'):
                # Canonical rules cannot be waived by weakening a review sidecar.
                for pattern in GLOSSARY[locale]['forbidden']:
                    if re.search(pattern, value):
                        errors.append(f'{locale}/{key}: glossary violation: {pattern}')
    for key in sorted(set(entries) - actual_keys):
        errors.append(f'{key}: removed key still present in context review')
    files = source_files(root)
    sources = review.get('sources', {})
    for name, path in files.items():
        entry = sources.get(name)
        if not isinstance(entry, dict):
            errors.append(f'{name}: missing source review; inspect affected dialogue/objectives/unlocks')
        elif not str(entry.get('note', '')).strip() or entry.get('sha256') != source_digest(path):
            errors.append(f'{name}: source review is stale; inspect affected dialogue/objectives/unlocks')
    for name in sorted(set(sources) - set(files)):
        if not safe_source_name(name):
            errors.append(f'{name}: invalid source path in review')
        else:
            errors.append(f'{name}: removed source still present in context review')
    if review.get('glossary') != GLOSSARY:
        errors.append('glossary review differs from canonical rules')
    return errors


def safe_source_name(name: str) -> bool:
    path = Path(name)
    return (not path.is_absolute() and '\\' not in name and '..' not in path.parts
            and ((name.startswith(('src/main/java/', 'src/client/java/')) and name.endswith('.java'))
                 or (name.startswith('src/main/resources/data/') and name.endswith('.json'))))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--review-key', action='append', default=[])
    parser.add_argument('--review-source', action='append', default=[])
    parser.add_argument('--note', help='Concrete source/context evidence for the explicitly selected inputs')
    args = parser.parse_args()
    root = args.root.resolve()
    contract = root / 'tools/localization-review.json'
    try:
        review = json.loads(contract.read_text(encoding='utf-8'))
        if args.review_key or args.review_source:
            if not args.note or not args.note.strip():
                parser.error('Targeted review requires --note with concrete context evidence')
            values = catalogs(root)
            for key in args.review_key:
                if key in values['en_us']:
                    if key.split('.')[0] not in CONTEXTS:
                        parser.error(f'Define context before reviewing {key}')
                    review['keys'][key] = entry_review(key, values, args.note)
                    review['keys'][key]['uses'] = source_uses(root, [key])[key]
                    review['keys'][key]['trace'] = 'Targeted source/context review; see note and source contracts.'
                elif any(key in catalog for catalog in values.values()):
                    parser.error(f'Complete all three translations before reviewing {key}')
                else:
                    review['keys'].pop(key, None)
            for name in args.review_source:
                if not safe_source_name(name):
                    parser.error(f'Invalid source path: {name}')
                path = root / name
                if path.exists():
                    review['sources'][name] = {'sha256': source_digest(path), 'note': args.note}
                else:
                    review['sources'].pop(name, None)
            contract.write_text(json.dumps(review, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        errors = validate_review(root, review)
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'Localization quality failed: {error}', file=sys.stderr)
        return 1
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'Localization context/glossary review PASS: {root.name}, '
          f'{len(review["keys"])} keys / 3 locales, {len(review["sources"])} source contracts.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
