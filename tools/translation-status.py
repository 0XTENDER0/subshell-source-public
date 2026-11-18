#!/usr/bin/env python3
"""TRANSLATE09_STATUS_TOOL_V1 (#299): what is left to translate.

Usage: python3 -I tools/translation-status.py [LANG ...] [--list] [--check]

Compares app/src/main/res/values/strings.xml (English, the source) with each
app/src/main/res/values-<lang>/strings.xml (a BCP 47 tag such as es-419 lives in
values-b+es+419, the only form aapt2 accepts for a numeric region; pass it as
es-419). For each language it prints how many
strings are translated, which are missing, which are extra (no longer in
English) and which have a different set of format arguments (%1$s, %2$d, ...),
a mistake that crashes or garbles text at run time.

--list   also print every missing name with its English text
--check  exit 1 if any language has extra names or format-argument mismatches
         (missing strings are not an error: they fall back to English)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
STRING = re.compile(r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
ARG = re.compile(r"%(?:\d+\$)?[sd]|%\d+\$[a-zA-Z]")


PLURALS = re.compile(r'<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>', re.S)
ITEM = re.compile(r'<item\s+quantity="([^"]+)"[^>]*>(.*?)</item>', re.S)


def load(path):
    out = {}
    source = path.read_text(encoding="utf-8")
    for m in STRING.finditer(source):
        name, attrs, text = m.groups()
        out[name] = (text, 'translatable="false"' in attrs)
    for m in PLURALS.finditer(source):
        # A plurals entry is one unit; its text is its forms joined, so format
        # arguments are compared over all forms.
        forms = ITEM.findall(m.group(2))
        out[m.group(1)] = (" | ".join(text for _, text in forms), False)
    return out


def args(text):
    return sorted(set(ARG.findall(text)))


def lang_of(folder):
    """values-nb -> nb, values-pt-rBR -> pt-rBR, values-b+es+419 -> es-419."""
    name = folder.name[len("values-"):]
    return name[2:].replace("+", "-") if name.startswith("b+") else name


def main(argv):
    want_list = "--list" in argv
    check = "--check" in argv
    langs = [a for a in argv if not a.startswith("--")]
    english = load(RES / "values/strings.xml")
    translatable = {n: t for n, (t, no) in english.items() if not no}
    dirs = sorted(p for p in RES.glob("values-*") if (p / "strings.xml").is_file())
    dirs = [p for p in dirs if re.fullmatch(r"values-([a-z]{2,3}(-r[A-Z]{2})?|b\+[a-z]{2,3}(\+[A-Za-z0-9]+)*)", p.name)]
    if langs:
        dirs = [p for p in dirs if lang_of(p) in langs]
    bad = False
    print(f"English: {len(translatable)} strings to translate")
    for d in dirs:
        lang = lang_of(d)
        have = {n: t for n, (t, no) in load(d / "strings.xml").items()}
        missing = [n for n in translatable if n not in have]
        extra = [n for n in have if n not in translatable]
        mismatch = [n for n in have if n in translatable and args(have[n]) != args(translatable[n])]
        done = len(translatable) - len(missing)
        pct = 100 * done // max(1, len(translatable))
        print(f"\n{lang}: {done}/{len(translatable)} translated ({pct}%), "
              f"{len(missing)} missing, {len(extra)} extra, {len(mismatch)} with different format arguments")
        for n in extra:
            print(f"  extra    {n}")
        for n in mismatch:
            print(f"  ARGS     {n}: English {args(translatable[n])} vs {args(have[n])}")
        if want_list:
            for n in missing:
                print(f"  missing  {n}: {translatable[n]}")
        if extra or mismatch:
            bad = True
    if not dirs:
        print("\nNo translations yet (no values-<lang>/strings.xml).")
    return 1 if (check and bad) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
