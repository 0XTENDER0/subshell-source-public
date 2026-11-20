#!/usr/bin/env python3
"""TRANSLATE16_STRING_CONTEXT_V1 (#299): tell translators where a text is used.

Usage: python3 -I tools/annotate-strings.py [--check]

Rewrites the `<!-- Used in: ... -->` comment above every string and plurals entry
in app/src/main/res/values/strings.xml from the Kotlin files that reference it
(`R.string.name`, `R.plurals.name`) and the XML resources that use `@string/name`.
Safe to run again: old "Used in" comments are replaced. With --check it changes
nothing and exits 1 if the file would change.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main"
XML = MAIN / "res/values/strings.xml"
ENTRY = re.compile(r'^(\s*)<(string|plurals)\s+name="([^"]+)"')
USED = re.compile(r'^\s*<!-- Used in: .* -->\s*$')


def usages():
    refs = {}
    pattern = re.compile(r"R\.(?:string|plurals)\.(\w+)")
    for path in (MAIN / "java").rglob("*.kt"):
        for name in set(pattern.findall(path.read_text(encoding="utf-8"))):
            refs.setdefault(name, set()).add(path.stem)
    for path in (MAIN / "res").rglob("*.xml"):
        if "values" in path.parent.name:
            continue
        for name in set(re.findall(r"@string/(\w+)", path.read_text(encoding="utf-8"))):
            refs.setdefault(name, set()).add(path.stem)
    manifest = (MAIN / "AndroidManifest.xml").read_text(encoding="utf-8")
    for name in set(re.findall(r"@string/(\w+)", manifest)):
        refs.setdefault(name, set()).add("AndroidManifest")
    return refs


def main(argv):
    refs = usages()
    lines = XML.read_text(encoding="utf-8").split("\n")
    out = []
    for line in lines:
        if USED.match(line):
            continue
        m = ENTRY.match(line)
        if m:
            where = sorted(refs.get(m.group(3), []))
            if where:
                shown = ", ".join(where[:3]) + (f" +{len(where) - 3}" if len(where) > 3 else "")
                out.append(f"{m.group(1)}<!-- Used in: {shown} -->")
        out.append(line)
    new = "\n".join(out)
    if new == "\n".join(lines):
        return 0
    if "--check" in argv:
        print("strings.xml context comments are out of date: run tools/annotate-strings.py")
        return 1
    XML.write_text(new, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
