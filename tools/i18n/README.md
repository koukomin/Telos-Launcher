# Translating new strings

Every user-visible string of a new or changed feature must be translated into all supported languages
(the `values-*` folders of `core/i18n`). English is in `values/`, Greek and the others in `values-<code>/`.

1. Work in a scratch folder with `missing/` and `done/` subfolders and run, for every language code:
   `python3 tools/i18n/extract.py <code>` (writes `missing/<code>.json`: key -> English text, only keys the language does not have yet).
2. Translate each key (a tool or a person), write `done/<code>.part1.json` (key -> translation),
   and for plurals `done/<code>.plurals.json` (key -> {quantity: text}).
3. `python3 tools/i18n/build.py <code>` checks that the placeholders (%s, %d, %1$s, tags) are unchanged and writes
   `core/i18n/src/main/res/values-<code>/strings_telos_translations.xml`.

Run both scripts from the folder that has `missing/` and `done/`. Languages: el, de, es, fr, it, pt, pt-rBR, ru, tr, nl, pl, cs, sk, sl,
hr, bg, ro, hu, da, sv, fi, et, lv, lt, ga, mt, uk, nb-rNO, zh-rCN, zh-rTW, ja, ko, ar, hi, bn (and the older ones that came with the project).
