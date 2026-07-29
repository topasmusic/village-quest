#!/usr/bin/env python3
"""Validate Village Quest JSON resources and localization parity."""

from __future__ import annotations

from collections import Counter
import argparse
import json
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
VERSION_LINES = ("26.2", "26.1.2", "1.21.11")
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
    if not isinstance(value, str):
        return Counter()
    return Counter(PLACEHOLDER.findall(value))


def validate_line(version: str) -> tuple[dict[str, object], list[str]]:
    errors: list[str] = []
    resources = ROOT / version / "src" / "main" / "resources"
    for path in sorted(resources.rglob("*.json")):
        try:
            read_json(path)
        except (json.JSONDecodeError, DuplicateKeyError) as error:
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
            errors.append(f"{version}/{language}: missing keys: {', '.join(missing)}")
        if extra:
            errors.append(f"{version}/{language}: extra keys: {', '.join(extra)}")
        for key in sorted(set(english) & set(catalog)):
            if placeholders(english[key]) != placeholders(catalog[key]):
                errors.append(
                    f"{version}/{language}: placeholder mismatch for {key}: "
                    f"{placeholders(english[key])} != {placeholders(catalog[key])}"
                )

    source_roots = (ROOT / version / "src" / "main" / "java", ROOT / version / "src" / "client" / "java")
    for source_root in source_roots:
        if not source_root.exists():
            continue
        for path in sorted(source_root.rglob("*.java")):
            text = path.read_text(encoding="utf-8")
            for key in TRANSLATABLE.findall(text):
                if ".village-quest." not in key or key.endswith("."):
                    continue
                if key not in english:
                    errors.append(f"{path.relative_to(ROOT)}: missing en_us key {key}")

    return english, errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--line",
        choices=VERSION_LINES,
        help="validate one intentionally divergent development line without requiring cross-line key parity",
    )
    args = parser.parse_args()

    if args.line:
        english, errors = validate_line(args.line)
        if errors:
            print("Resource validation failed:", file=sys.stderr)
            for error in errors:
                print(f"- {error}", file=sys.stderr)
            return 1
        print(f"Validated JSON resources and {len(english)} localization keys for {args.line}.")
        return 0

    errors: list[str] = []
    english_by_line: dict[str, dict[str, object]] = {}
    for version in VERSION_LINES:
        english, line_errors = validate_line(version)
        english_by_line[version] = english
        errors.extend(line_errors)

    reference = english_by_line[VERSION_LINES[0]]
    for version in VERSION_LINES[1:]:
        catalog = english_by_line[version]
        missing = sorted(set(reference) - set(catalog))
        extra = sorted(set(catalog) - set(reference))
        if missing or extra:
            errors.append(
                f"{version}/en_us differs from {VERSION_LINES[0]} "
                f"(missing={missing}, extra={extra})"
            )

    if errors:
        print("Resource validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        f"Validated JSON resources and {len(reference)} localization keys "
        f"across {len(VERSION_LINES)} maintained lines."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
