# Search in the Telos apps

Every Telos app has the same search bar, and each search is limited to what that app is about.

## The search bar

A rounded field with a search icon, a **Clear** button and room for filter chips. In apps with a top bar, the
search replaces the title while it is open. When nothing matches you see **No results for "..."** instead of an
empty screen.

## What each app searches

| App | Search finds |
| --- | --- |
| Phone | Contacts (by name or number), calls in the call history, contact groups, call recordings |
| Messages | Conversations (name, number, text) and, inside a conversation, its messages |
| Notes | Title, text and labels |
| Calendar | Events |
| Photos | Pictures by file name, folder, date |
| Photos document viewer | Text inside a PDF ([see Searching in a PDF](./photos/office-editing#searching-in-a-pdf)) |
| Video | Videos only: title, file name and folder |
| Music | Songs, albums and artists |
| Radio | Stations and favorites |
| Files | Files and folders of the folder you are in, with a deeper search below it; network connections by name, type, host and user |
| Downloads | Downloads by name and link |
| Voice Recorder, Screen Recorder, Screenshot | Recordings and screenshots by name and date |
| Store | Installed apps and Telos apps by name, package, source, note and category |
| Network | Apps in the firewall and WireGuard lists, DNS providers, blocklists, rules and logs |

An app never shows results from another app. The Video player lists no music, the dialer no files.

## Greek, accents and Greeklish

Search works the same way in every app:

- Upper and lower case do not matter (`ΓΙΑΝΝΗΣ`, `Γιάννης`).
- Accents do not matter: `Γιαννης` finds `Γιάννης`, `καφες` finds `καφές`. The final sigma `ς` and `σ` are the same.
- Latin letters find Greek text (Greeklish): `giannis` finds `Γιάννης`, `kalimera` finds `Καλημέρα`, `thelw` finds `θέλω`,
  `mpala` finds `μπάλα`.
- Greek letters find Latin text: `γιαννης` finds `Giannis`.
- Numbers match phone numbers whatever the spaces, dashes or a leading `+` or `00`.
- Several words must all match (`giannis papa`), in any order.
- Better matches come first: whole word, then beginning of a word, then inside a word.

Letters that sound alike are merged, so a few similar words may match too.

## Limits

- Search inside the Telos apps does not use the network.
- The text of documents is searched only inside a PDF you have open, not across all files.
- Plugins search by their own rules, so Greeklish may not work for them. The
  [launcher search](./launcher/search) is a separate system.
