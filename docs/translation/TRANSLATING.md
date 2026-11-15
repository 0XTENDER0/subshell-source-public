# Translating Subshell

Issue: #299. Text moves from Kotlin code into Android string resources in
slices; you can translate what has already moved. Anything not yet moved, or
not yet translated, shows in English (per string, nothing breaks).

## Where the text lives

- English (source): `app/src/main/res/values/strings.xml`
- A language: `app/src/main/res/values-<code>/strings.xml`
  - Norwegian Bokmål `values-nb`, Latin American Spanish `values-es-419`.
- To offer a language in Android's per-app language setting, add one line to
  `app/src/main/res/xml/locales_config.xml` (maintainer does this).
- A release ships only finished languages: when a language is ready, the
  maintainer adds it to `SUBSHELL_RELEASE_LOCALE_FILTER` in
  `app/build.gradle.kts` and to `app/src/release/res/xml/locales_config.xml`
  and `app/src/benchmark/res/xml/locales_config.xml`. Until then debug and
  test builds show it, a release does not.

Copy the English file, keep every `name="..."`, translate only the text
between the tags. Leave out strings you are unsure of; they fall back to English.

## Naming convention (for code, slice 1 onwards)

`<area>_<what>`, lower case: `settings_clean_links_title`,
`post_comments_count` (plural), `error_network_unavailable`.
Strings that must never be translated carry `translatable="false"`
(the app name, technical identifiers).

## Rules for translators

1. Do not change or translate `name="..."`.
2. Keep format arguments exactly: `%1$s`, `%2$d`, `%%`. You may reorder them
   (`%2$s ... %1$s`) if your language needs it. Never delete one.
3. Plurals use `<plurals>` with `<item quantity="one|other">`. Spanish needs
   `one`, `many`, `other`; Norwegian `one`, `other`.
4. Escape an apostrophe as `\'`, keep `&amp;`, `&lt;`.
5. Do not translate: "Subshell", "Reddit", "Modmail", subreddit names (r/...),
   usernames, setting values that are quoted from Reddit.
6. Short UI labels (buttons, tabs) stay short; they must fit on a phone.
7. Tone: plain and direct, same as the English.

## Where a text is used

Above every entry in `strings.xml` a comment says which screens or classes use it
(`<!-- Used in: SettingsScreen, Settings03Navigation -->`). The comments are made by
`python3 -I tools/annotate-strings.py` (maintainer; rerun after adding strings).

## Plurals

A count with a noun is a `<plurals>` entry. English has `one` and `other`; give each
form your language needs (Spanish: `one`, `many`, `other`; Norwegian: `one`, `other`).
`%1$d` is the number. Never build a plural by hand-adding "s".

## What is left to translate

    python3 -I tools/translation-status.py nb --list

prints, for the language, how many strings are translated, which are missing
(with the English text), which names no longer exist in English, and any string
whose format arguments (`%1$s`, `%2$d`) differ from the English one. Add
`--check` to make extra names and argument mistakes an error.

## How to test

Android 13+: Settings > System > Languages > App languages > Subshell, pick
the language. Older Android follows the phone's language.
