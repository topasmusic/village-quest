#!/usr/bin/env python3
"""Check local Markdown paths, wiki navigation and declared Stable versions.

Standard library only; no writes or network. Does not prove gameplay accuracy,
remote URL availability, Markdown fragment anchors or release readiness.
"""
import argparse
import os
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
LINES = ('26.3', '26.2', '26.1.2', '1.21.11')
LINK = re.compile(r'!?\[[^\]\n]*\]\(\s*(<[^>\n]+>|[^\s)]+)(?:\s+["\'][^\n]*?["\'])?\s*\)')
REFERENCE = re.compile(r'^\s{0,3}\[[^\]\n]+\]:\s*(<[^>\n]+>|\S+)', re.MULTILINE)


def prose(text):
    """Keep line numbers while excluding fenced and inline code examples."""
    lines, fence = [], None
    for line in text.splitlines(keepends=True):
        marker = re.match(r'^\s{0,3}(`{3,}|~{3,})', line)
        if marker and fence is None:
            fence = marker[1]
            lines.append('\n')
        elif fence is not None:
            if re.match(r'^\s{0,3}' + re.escape(fence[0]) + '{' + str(len(fence)) + r',}\s*$', line):
                fence = None
            lines.append('\n')
        else:
            lines.append(re.sub(r'(`+).*?\1', '', line))
    return ''.join(lines)


def links(text):
    text = prose(text)
    for pattern in (LINK, REFERENCE):
        for match in pattern.finditer(text):
            yield text.count('\n', 0, match.start()) + 1, match[1].strip('<>')


def local_target(root, page, target):
    parts = urlsplit(target)
    if parts.scheme or parts.netloc or not parts.path:
        return None
    raw = unquote(parts.path)
    unresolved = (root / raw.lstrip('/')) if raw.startswith('/') else page.parent / raw
    lexical = Path(os.path.abspath(unresolved))
    path = unresolved.resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError('outside repository')
    # Windows otherwise accepts wrong-case paths that fail in Linux CI.
    current = root.resolve()
    for part in lexical.relative_to(root.absolute()).parts:
        if not current.is_dir() or part not in {p.name for p in current.iterdir()}:
            raise ValueError('missing path or incorrect filename case')
        current /= part
    return path


def check_links(root, page):
    errors = []
    for line, target in links(page.read_text(encoding='utf-8')):
        try:
            local_target(root, page, target)
        except ValueError as error:
            errors.append(f'{page.relative_to(root).as_posix()}:{line}: {target}: {error}')
    return errors


def check_navigation(root, line):
    wiki = root / line / 'docs/wiki'
    index = wiki / 'README.md'
    if not index.is_file():
        return [f'{line}: missing wiki README.md']
    linked = set()
    for _, target in links(index.read_text(encoding='utf-8')):
        try:
            path = local_target(root, index, target)
            if path:
                linked.add(path)
        except ValueError:
            pass  # The path check reports the precise broken link separately.
    return [f'{page.relative_to(root).as_posix()}: missing from wiki navigation'
            for page in sorted(wiki.glob('*.md')) if page != index and page.resolve() not in linked]


def check_stable(root, line):
    changelog = root / line / 'CHANGELOG.md'
    candidates = re.findall(r'^## (\d+\.\d+\.\d+)(?:\s.*)?$', changelog.read_text(encoding='utf-8'), re.MULTILINE)
    if not candidates:
        return [f'{line}/CHANGELOG.md: no Stable heading']
    stable = candidates[0]
    errors = []
    for name in ('README.md', 'docs/wiki/README.md'):
        page = root / line / name
        match = re.search(r'Current stable release:\s*`([^`]+)`', page.read_text(encoding='utf-8'))
        if match and match[1] != stable:
            errors.append(f'{line}/{name}: declared Stable {match[1]}, changelog Stable {stable}')
        elif not match and line in ('26.3', '26.2'):
            errors.append(f'{line}/{name}: missing explicit Current stable release declaration ({stable})')
    return errors


def check_root_stable(root, selected):
    text = (root / 'README.md').read_text(encoding='utf-8')
    section = re.search(r'Current stable releases:\s*\n(.*?)(?:\n\n[^-\n]|\Z)', text, re.DOTALL)
    if not section:
        return ['README.md: missing Current stable releases list']
    declared = {}
    for version, targets in re.findall(r'^- `(\d+\.\d+\.\d+)[^`]*`.*?for Minecraft (.+)$', section[1], re.MULTILINE):
        for target in re.findall(r'`([^`]+)`', targets):
            declared[target] = version
    errors = []
    for line in selected:
        stable = re.findall(r'^## (\d+\.\d+\.\d+)(?:\s.*)?$', (root / line / 'CHANGELOG.md').read_text(encoding='utf-8'), re.MULTILINE)
        if stable and declared.get(line) != stable[0]:
            errors.append(f'README.md: {line} declared Stable {declared.get(line)}, changelog Stable {stable[0]}')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--line', action='append', choices=LINES, help='Repeat for selected lines; default: all supported lines')
    args = parser.parse_args()
    selected = args.line or LINES
    pages = {ROOT / 'README.md', ROOT / 'VERSION_SUPPORT.md'}
    errors = check_root_stable(ROOT, selected)
    for line in selected:
        pages.add(ROOT / line / 'README.md')
        pages.update((ROOT / line / 'docs/wiki').glob('*.md'))
        errors.extend(check_navigation(ROOT, line))
        errors.extend(check_stable(ROOT, line))
    for page in sorted(pages):
        errors.extend(check_links(ROOT, page))
    for error in errors:
        print(error)
    print(f'{"FAIL" if errors else "PASS"}: {len(pages)} documents; {len(errors)} issues. Local paths/navigation/declared Stable only; content and remote URLs not verified.')
    return bool(errors)


if __name__ == '__main__':
    raise SystemExit(main())
