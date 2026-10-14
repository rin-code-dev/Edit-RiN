#!/usr/bin/env python3
"""
Verify Kotlin source files for static consistency and common patch issues:
1. Validates that all R.drawable.* references in Kotlin code actually exist in res/drawable/.
2. Checks for known broken symbols/patterns from external patch documents:
   - 'target.rawText' (EditorColorTarget has 'originalText')
   - 'target.range.first' / 'target.range.last' (target.range is TextRange, which lacks .first/.last)
   - 'fun formatColorReplacement' (broken helper pattern that bypasses replaceColorTarget)
"""

import sys
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KOTLIN_DIR = ROOT / 'android' / 'app' / 'src' / 'main' / 'java' / 'com' / 'hikariatelier' / 'app'
DRAWABLE_DIR = ROOT / 'android' / 'app' / 'src' / 'main' / 'res' / 'drawable'


def check_drawables(problems):
    if not DRAWABLE_DIR.exists():
        return
    existing_drawables = {p.stem for p in DRAWABLE_DIR.glob('*')}
    drawable_ref_pattern = re.compile(r'R\.drawable\.([a-zA-Z0-9_]+)')

    for kt in KOTLIN_DIR.glob('*.kt'):
        text = kt.read_text(encoding='utf-8')
        for lineno, line in enumerate(text.splitlines(), start=1):
            code_part = line.split('//')[0]
            for match in drawable_ref_pattern.finditer(code_part):
                name = match.group(1)
                if name not in existing_drawables:
                    problems.append(f"{kt.name}:{lineno}: R.drawable.{name} does not exist in res/drawable/")


def check_broken_symbols(problems):
    # Pattern 1: target.rawText on EditorColorTarget
    raw_text_target_pattern = re.compile(r'\btarget\.rawText\b')
    # Pattern 2: formatColorReplacement function definition
    format_color_pattern = re.compile(r'fun\s+formatColorReplacement\s*\(')
    # Pattern 3: target.range.first or target.range.last on EditorColorTarget
    color_target_range_first_last = re.compile(r'\btarget\.range\.(?:first|last)\b')

    for kt in KOTLIN_DIR.glob('*.kt'):
        text = kt.read_text(encoding='utf-8')
        for lineno, line in enumerate(text.splitlines(), start=1):
            code_part = line.split('//')[0].strip()
            if not code_part or code_part.startswith('*'):
                continue

            if raw_text_target_pattern.search(code_part):
                problems.append(
                    f"{kt.name}:{lineno}: Found 'target.rawText'. EditorColorTarget has 'originalText', not 'rawText'."
                )

            if format_color_pattern.search(code_part):
                problems.append(
                    f"{kt.name}:{lineno}: Found 'formatColorReplacement'. Use 'replaceColorTarget(editingValue, target, swatch)' instead."
                )

            if color_target_range_first_last.search(code_part):
                problems.append(
                    f"{kt.name}:{lineno}: Found 'target.range.first/last'. EditorColorTarget.range is a TextRange (use replaceColorTarget instead)."
                )


def main():
    problems = []
    check_drawables(problems)
    check_broken_symbols(problems)

    if problems:
        print(f"FAILED: Found {len(problems)} static issue(s) in Kotlin sources:")
        for p in problems:
            print(f"  - {p}")
        sys.exit(1)
    else:
        print("PASS: Kotlin sources verified successfully (drawables & patch symbols).")
        sys.exit(0)


if __name__ == '__main__':
    main()
