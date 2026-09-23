#!/usr/bin/env python3
"""Validate the self-contained Village Quest 26.3 source/audit package."""

from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
LANGUAGES = ("en_us", "de_de", "es_es")
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[a-zA-Z]")
TRANSLATABLE = re.compile(r'(?:Component|Text)\.translatable\(\s*"([^"]+)"')


class DuplicateKeyError(ValueError):
    pass


def unique_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKeyError(f"duplicate key {key!r}")
        result[key] = value
    return result


def read_json(path: Path) -> object:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle, object_pairs_hook=unique_object)


def placeholders(value: object) -> Counter[str]:
    return Counter(PLACEHOLDER.findall(value)) if isinstance(value, str) else Counter()


def validate() -> tuple[dict[str, object], list[str]]:
    errors: list[str] = []
    resources = ROOT / "src" / "main" / "resources"
    for path in sorted(resources.rglob("*.json")):
        try:
            read_json(path)
        except (OSError, json.JSONDecodeError, DuplicateKeyError) as error:
            errors.append(f"{path.relative_to(ROOT)}: {error}")

    lang_dir = resources / "assets" / "village-quest" / "lang"
    catalogs: dict[str, dict[str, object]] = {}
    for language in LANGUAGES:
        path = lang_dir / f"{language}.json"
        try:
            value = read_json(path)
        except (OSError, json.JSONDecodeError, DuplicateKeyError) as error:
            errors.append(f"{path.relative_to(ROOT)}: {error}")
            continue
        if not isinstance(value, dict):
            errors.append(f"{path.relative_to(ROOT)}: localization root must be an object")
            continue
        catalogs[language] = value

    english = catalogs.get("en_us", {})
    for language in LANGUAGES[1:]:
        catalog = catalogs.get(language, {})
        missing = sorted(set(english) - set(catalog))
        extra = sorted(set(catalog) - set(english))
        if missing:
            errors.append(f"{language}: missing keys: {', '.join(missing)}")
        if extra:
            errors.append(f"{language}: extra keys: {', '.join(extra)}")
        for key in sorted(set(english) & set(catalog)):
            if placeholders(english[key]) != placeholders(catalog[key]):
                errors.append(
                    f"{language}: placeholder mismatch for {key}: "
                    f"{placeholders(english[key])} != {placeholders(catalog[key])}"
                )

    for source_root in (ROOT / "src" / "main" / "java", ROOT / "src" / "client" / "java"):
        if not source_root.exists():
            continue
        for path in sorted(source_root.rglob("*.java")):
            text = path.read_text(encoding="utf-8")
            for key in TRANSLATABLE.findall(text):
                if ".village-quest." in key and not key.endswith(".") and key not in english:
                    errors.append(f"{path.relative_to(ROOT)}: missing en_us key {key}")

    return english, errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--line",
        choices=("26.3",),
        help="accepted for command compatibility with the repository-root validator",
    )
    parser.parse_args()

    english, errors = validate()
    if errors:
        print("Resource validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"Validated JSON resources and {len(english)} localization keys for 26.3.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
