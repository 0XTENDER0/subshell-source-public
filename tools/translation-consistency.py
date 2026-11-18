#!/usr/bin/env python3
"""TRANSLATE15_CONSISTENCY_TOOL_V1 (#299): is a translation consistent?

Usage: python3 -I tools/translation-consistency.py LANG [--list] [--counts]

A review aid next to tools/translation-status.py (which checks completeness and
format arguments). It reads app/src/main/res/values/strings.xml (English) and
values-<lang>/strings.xml (es-419 lives in values-b+es+419) and reports:

1. same English, different translations: one English text translated in more
   than one way (a button called two things on two screens);
2. glossary: a string whose English uses a term from docs/translation/GLOSSARY.md
   but whose translation uses none of the forms in that language's column;
3. names inside sentences: a short label (a screen, tab or setting name such as
   "Joined") quoted inside a longer sentence whose translation does not contain
   the label's own translation.

Every finding is a question for a person, not an error: a sentence may say the
same thing in other words. Without --list only the counts are printed.
--counts prints how many English strings use each glossary term.
"""
import collections
import html
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
GLOSSARY = ROOT / "docs/translation/GLOSSARY.md"
STRING = re.compile(r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
PLURALS = re.compile(r'<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>', re.S)
ITEM = re.compile(r'<item\s+quantity="([^"]+)"[^>]*>(.*?)</item>', re.S)
WORD = r"[^\W\d_]"


def clean(text):
    text = re.sub(r"<[^>]+>", "", text)
    text = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", " ")
    text = html.unescape(text).strip()
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]
    return text


def load(path):
    """name -> text; a plurals entry is its `other` form."""
    out = {}
    source = path.read_text(encoding="utf-8")
    for name, attrs, text in STRING.findall(source):
        if 'translatable="false"' not in attrs:
            out[name] = clean(text)
    for name, body in PLURALS.findall(source):
        items = dict(ITEM.findall(body))
        text = items.get("other") or next(iter(items.values()), "")
        out[name] = clean(text)
    return out


def folder(lang):
    if "-" in lang and lang.split("-", 1)[1].isdigit():
        return "values-b+" + lang.replace("-", "+")
    return "values-" + lang


def term_regex(term):
    # A capitalised term is a name (You, New, Home): match it as written, so
    # "you" in a sentence is not the You tab.
    flags = 0 if term[:1].isupper() else re.I
    return re.compile(rf"(?<!{WORD}){re.escape(term)}(?!{WORD})", flags)


def has_form(text, forms):
    # Anywhere in a word: compounds (moderatortillatelse) count.
    low = text.lower()
    return any(form.lower() in low for form in forms)


def glossary(lang):
    """[(english forms, lang forms or [])] from the GLOSSARY.md table."""
    rows, column = [], None
    for line in GLOSSARY.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if column is None:
            if cells and cells[0] == "English":
                if lang not in cells:
                    sys.exit(f"no column {lang} in {GLOSSARY.name}")
                column = cells.index(lang)
            continue
        if set(cells[0]) <= set("-: "):
            continue
        english = [t.strip() for t in cells[0].split(",") if t.strip()]
        forms = [t.strip() for t in cells[column].split(",") if t.strip()] if column < len(cells) else []
        rows.append((english, forms))
    return rows


def main(argv):
    show = "--list" in argv
    args = [a for a in argv if not a.startswith("--")]
    if len(args) != 1:
        sys.exit(__doc__)
    lang = args[0]
    english = load(RES / "values/strings.xml")
    if "--counts" in argv:
        for terms, _ in glossary(lang):
            rxs = [term_regex(t) for t in terms]
            n = sum(1 for text in english.values() if any(r.search(text) for r in rxs))
            print(f"{n:5d}  {', '.join(terms)}")
        return 0
    path = RES / folder(lang) / "strings.xml"
    if not path.exists():
        sys.exit(f"{path} does not exist")
    local = load(path)
    both = {n: (english[n], local[n]) for n in english if n in local}

    # 1. Same English, different translations.
    by_english = collections.defaultdict(lambda: collections.defaultdict(list))
    for name, (en, tr) in both.items():
        by_english[en][tr].append(name)
    duplicates = {en: v for en, v in by_english.items() if len(v) > 1}

    # 2. Glossary terms.
    missed = []
    for terms, forms in glossary(lang):
        if not forms:
            continue
        rxs = [term_regex(t) for t in terms]
        for name, (en, tr) in both.items():
            if any(r.search(en) for r in rxs) and not has_form(tr, forms):
                missed.append((", ".join(terms), name, en, tr))

    # 3. Names inside sentences: a capitalised short label that appears
    # capitalised in the middle of a longer sentence.
    labels = collections.defaultdict(set)
    for en, tr in both.values():
        words = en.split()
        if 1 <= len(words) <= 5 and en[:1].isupper() and "%" not in en and not en.endswith((".", "?", "!", "…", ":")):
            labels[en].add(tr)
    names = []
    for label, translations in labels.items():
        rx = re.compile(rf"(?<!{WORD}){re.escape(label)}(?!{WORD})")
        for name, (en, tr) in both.items():
            if en == label or len(en) < len(label) + 8:
                continue
            for m in rx.finditer(en):
                before = en[: m.start()].rstrip()
                if not before or before[-1] in ".?!:—-–(\"'":
                    continue  # sentence start: capitalised anyway
                if not any(t.lower() in tr.lower() for t in translations):
                    names.append((label, sorted(translations), name, en, tr))
                break

    print(f"{lang}: {len(both)} translated strings compared with English")
    print(f"  {len(duplicates)} English texts translated in more than one way")
    print(f"  {len(missed)} strings where a glossary term's translation is not used")
    print(f"  {len(names)} sentences that do not use a quoted name's translation")
    if show:
        if duplicates:
            print("\n== Same English, different translations ==")
            for en, variants in sorted(duplicates.items()):
                print(f"  {en}")
                for tr, ns in sorted(variants.items()):
                    print(f"      {tr}    [{', '.join(sorted(ns)[:4])}{' …' if len(ns) > 4 else ''}]")
        if missed:
            print("\n== Glossary term not used ==")
            for term, name, en, tr in missed:
                print(f"  [{term}] {name}\n      en: {en}\n      {lang}: {tr}")
        if names:
            print("\n== Name inside a sentence not translated the same ==")
            for label, translations, name, en, tr in names:
                print(f"  \"{label}\" = {' / '.join(translations)}  in {name}\n      en: {en}\n      {lang}: {tr}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
