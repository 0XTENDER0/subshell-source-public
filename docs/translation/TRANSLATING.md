# Translating Subshell

Issue: #299. Every text the app shows is in one file, `strings.xml`
(English): about 3 720 texts and 62 plural entries, roughly 19 400 words.
More than half are short labels (buttons, tabs, menu entries). You can
translate in parts: anything you leave out shows in English, string by
string, and nothing breaks.

## What you get and what you send back

- You get the current English `strings.xml` (attached to issue #299; the
  public source repository only holds released versions, which can be older).
- You send back your translated file, whole or in parts, as an attachment in
  issue #299. Name it `strings.xml` for the language, for example Latin
  American Spanish (`es-419`).
- The maintainer adds it to the app, checks it (see "What we check"), and
  posts a test build (APK) with your translation in the issue, so you can see
  it in the app. Release builds show only finished languages, so the app from
  the GitHub releases page stays English until the translation is done.

## How to translate the file

Copy the English file and translate only the text between the tags. Keep
everything else as it is. Leave out strings you are unsure of; they fall back
to English.

    <!-- Used in: CommentComposerScreen -->
    <string name="scr_reply_to_u_title">Reply to u/%1$s</string>

becomes

    <string name="scr_reply_to_u_title">Responder a u/%1$s</string>

The comment above each entry says where in the app's code the text is used.
It helps with context; you do not need to keep it.

## Rules

1. Do not change or translate `name="..."`.
2. Keep format arguments exactly: `%1$s` (a word or name), `%1$d` (a number).
   You may reorder them (`%2$s ... %1$s`) if your language needs it. Never
   delete one. `%%` is a literal percent sign.
3. Escape an apostrophe as `\'`. Keep `&amp;` and `&lt;` as they are.
4. Do not translate: "subshell", "Reddit", "Modmail", community names
   (`r/...`), usernames (`u/...`), and short codes such as "NSFW", "OP",
   "GIF".
5. Short labels (buttons, tabs, menu entries) must stay short; they have to
   fit on a phone screen.
6. Tone: plain and direct, the same as the English. Address the reader
   informally (Spanish: tú).
7. If a text is unclear, ask in the issue; quote its `name`.

## Plurals

A count with a noun is a `<plurals>` entry. Give every form your language
needs; Spanish uses `one`, `many` and `other` (`many` is for large round
numbers such as 1 000 000). `%1$d` is the number.

    <plurals name="backup_more_accounts">
        <item quantity="one">…and %1$d more account</item>
        <item quantity="other">…and %1$d more accounts</item>
    </plurals>

becomes

    <plurals name="backup_more_accounts">
        <item quantity="one">…y %1$d cuenta más</item>
        <item quantity="many">…y %1$d cuentas más</item>
        <item quantity="other">…y %1$d cuentas más</item>
    </plurals>

Never build a plural by adding a letter to a word yourself.

## What we check

When a file comes in, the maintainer runs

    python3 -I tools/translation-status.py es-419 --list --check

which shows how many strings are translated, which are missing (with the
English text), names that no longer exist in English, and any string whose
format arguments differ from the English one. Problems are reported back in
the issue.

Before you start, fill in your language's column in
[`GLOSSARY.md`](GLOSSARY.md): one choice for each word the app uses again and
again (post, community, mute, Joined …). Each part you send is then also run
through

    python3 -I tools/translation-consistency.py es-419 --list

which lists English texts translated in more than one way, strings that do not
use the glossary's word, and screen or setting names that read differently
inside a sentence than on their own. These are questions, not errors.

## For maintainers

- The language's file goes to `app/src/main/res/values-<code>/strings.xml`
  (`values-nb`; Latin American Spanish is `values-b+es+419`, the only folder
  name aapt2 accepts for the numeric region 419).
- To offer a language in Android's per-app language setting, add it to
  `app/src/main/res/xml/locales_config.xml`.
- A release ships only finished languages: when a language is done, add it to
  `SUBSHELL_RELEASE_LOCALE_FILTER` in `app/build.gradle.kts` and to
  `app/src/release/res/xml/locales_config.xml` and
  `app/src/benchmark/res/xml/locales_config.xml`.
- String names are `<area>_<what>` in lower case (`set_clean_links_title`);
  strings that are never translated carry `translatable="false"`.
- Above every entry a comment says which code uses it; regenerate them with
  `python3 -I tools/annotate-strings.py` after adding strings.
- Test a language on Android 13+: Settings > System > Languages > App
  languages > Subshell (debug or lane build), or
  `adb shell cmd locale set-app-locales <package> --locales es-419`.
