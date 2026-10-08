# Languages

Telos is translated into **35 languages**. Greek comes first: every new screen and setting is written in English and
Greek together, and then translated into the other languages in the same change.

## Supported languages

| Group | Languages |
| --- | --- |
| Greek | Greek (Ελληνικά) |
| European Union | German, French, Italian, Spanish, Portuguese (Portugal), Dutch, Polish, Czech, Slovak, Slovenian, Croatian, Bulgarian, Romanian, Hungarian, Danish, Swedish, Finnish, Estonian, Latvian, Lithuanian, Irish, Maltese |
| Other European | Russian, Ukrainian, Turkish, Norwegian (Bokmål) |
| Asia | Chinese (Simplified and Traditional), Japanese, Korean, Hindi, Bengali, Arabic |
| Also, from the original project | Portuguese (Brazil), Catalan, Basque, Belarusian, Persian, Hebrew, Indonesian, Thai, Vietnamese, Malay, Azerbaijani, Esperanto, Interlingua, English (UK, US) |

## Choosing the language

- **Android 13 and newer:** Android settings > System > Languages > App languages > Telos.
- **Older Android:** Telos follows the language of the phone.
- Search matches [Greek with or without accents and in Greeklish](./features/search-catalogue), whatever the app language is.

## How complete are the translations

The texts of the original launcher come from its translators. The texts that Telos added (the built-in apps, settings, the
sidebar, backup, notes, calendar, weather alerts and so on) were translated by machine and have not been checked by native
speakers, so some wording can be wrong or stiff. A string that has no translation falls back to English.
The documentation pages and this site are in English only.

## Helping

Translations are the `strings*.xml` files of `core/i18n/src/main/res/values-<language>/`. Fix a wording there and send a pull
request. The file `strings_telos_translations.xml` of each language holds the machine translated Telos texts. The helper
scripts in `tools/i18n/` list the strings a language still misses.
