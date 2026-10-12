#!/usr/bin/env python3
"""Run all offline language/context regressions. No UI, browser or game process."""
import argparse
from pathlib import Path
import subprocess
import sys

LINES=('26.3','26.2','26.1.2','1.21.11')


def run_python(root: Path, arguments: list) -> int:
    return subprocess.run([sys.executable,'-B',*map(str,arguments)],cwd=root,check=False).returncode


def check(root: Path, lines: list[str]) -> int:
    commands=[['-m','unittest','discover','-s','tools/tests','-p','test_*.py']]
    for line in lines:
        commands.append(['tools/validate_resources.py','--line',line])
        commands.append(['tools/localization_quality.py','--root',line])
    commands.append(['tools/validate_docs.py',*[arg for line in lines for arg in ('--line',line)]])
    for arguments in commands:
        result=run_python(root,arguments)
        if result:
            return result
    return 0


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    parser.add_argument('--line',action='append',choices=LINES)
    args=parser.parse_args()
    result=check(args.root.resolve(),args.line or list(LINES))
    if result==0:
        print('Offline localization quality gate PASS. Native layout/navigation tests run through Gradle test/check.')
    return result


if __name__=='__main__':
    raise SystemExit(main())
