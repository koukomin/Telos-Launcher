# Streams, torrents, subtitles and online services

Playing from the web, streaming torrents while they download, subtitles from files and OpenSubtitles, posters from
TMDB, and the optional Trakt.tv connection. This page also lists every setting in the **Video services** dialog. The
library and the player are on [Library and player](./library-player).

## Video services

Tap the **gear** icon in the library top bar ("Video services"). Changes are saved with **Save** and are encrypted
where they are secret.

| Setting | Default | Effect |
| --- | --- | --- |
| TMDB API key | empty | Use TMDB for posters, descriptions and ratings instead of Wikipedia |
| Subtitle sources | all on | Which sources are asked and in which order (switch and arrows), see [Subtitles](#subtitles) |
| OpenSubtitles User-Agent | empty | Optional. Empty uses the generic test agent of the old OpenSubtitles API |
| OpenSubtitles API key | empty | Only for the optional OpenSubtitles.com source. The key comes from opensubtitles.com (Consumers) |
| OpenSubtitles user name and password | empty | Only for the optional OpenSubtitles.com source: your account, which that API requires for downloads |
| Network folders | none | Folders of your Telos Files network storages whose videos appear in the library, see [Network storages](./library-player#network-storages) |
| Languages | `en` | Comma separated codes, most wanted first, for example `el,en`. Spaces are removed |
| Download subtitles automatically | off | Fetches a subtitle without asking when a video has none |
| Torrents: Only on Wi-Fi | on | A torrent refuses to start on a metered (mobile data) connection |
| Player: Play in a separate process | off | <Badge type="warning" text="experimental" /> Runs the player in its own process |
| Trakt.tv | not connected | Client id and secret, **Connect Trakt**, scrobbling switch, **Sign out** |

All keys and passwords are encrypted with the Android Keystore before they are stored.

## Playing from the web

Tap the **link** icon in the top bar ("Play from the web").

| Field or button | Behavior |
| --- | --- |
| Address or magnet link | Web address of a video or stream (HLS, DASH, MP4 and other plain video links, RTSP), a magnet link, or the address of a `.torrent` file |
| **Paste** | Inserts the clipboard |
| Automatic fill | A magnet link, `http://` or `https://` address on the clipboard is filled in when the dialog opens |
| **Choose .torrent file** | Opens a file picker for a `.torrent` file on the phone |
| **Play** | Starts playback. A torrent starts when the address or file is a torrent, otherwise it is played as a stream |
| **Cancel** | Closes the dialog |

How the address is treated: it is a **torrent** when it starts with `magnet:` or ends in `.torrent` (query string
ignored). Everything else is handed to the player as a stream.

Other apps and browsers can also open `magnet:` links, `.torrent` links and `.torrent` files in Telos Video directly.

## Torrents

Telos Video uses libtorrent (through libtorrent4j) to fetch the pieces of the chosen video in order. The player
reads them through a small web server that listens only on `127.0.0.1` of your phone, on a random port with a random
secret token in the address.

::: warning Only play content you may watch
Torrents are a way to receive files. Use them only for content you are allowed to watch. Torrent networks show your IP
address to other peers, and while a torrent runs you also share the pieces you have already downloaded.
:::

### What happens, step by step

1. You give an address, magnet link or file. Nothing starts before that.
2. If **Only on Wi-Fi** is on and the connection is metered, it stops with "Torrent streaming is set to Wi-Fi only".
3. For a magnet link Telos asks peers for the torrent's description for up to 90 seconds ("Finding peers..." and
   "This can take a minute."). A dead link ends with "No answer from peers, the link may be dead".
4. A `.torrent` address is downloaded first (not larger than 8 MB); a content or file address is read from the phone.
5. Only the **video files** of the torrent are downloaded (`mkv mp4 avi mov webm m4v ts mpg mpeg wmv flv`), in
   sequential order, and the first and last pieces first so that players find the headers. A torrent without video
   files ends with "This torrent has no video file".
6. The biggest video starts, the others become the playlist.
7. The player shows "Buffering..." and then plays. A status line shows "Peers N, N KB/s, N% downloaded".

### Peer block lists

In the **Video services** dialog, under Torrents, **Peer block lists** can make the torrent session refuse
connections to listed IP address ranges (IPv4). Nothing is downloaded until you switch a list on.

- **Offered list:** Naunter BT_BlockLists (Unlicense), `github.com/Naunter/BT_BlockLists`. The project says it is no
  longer actively maintained, although its automatic workflow still runs.
- **Your own lists:** **Add list by address** (https only) or **Import from a file**. Accepted formats are PeerGuardian
  p2p (`name:1.2.3.4-1.2.3.5`), eMule `ipfilter.dat`, CIDR (`1.2.3.0/24`), `a-b` ranges and single addresses, plain or
  as gzip or zip. IPv6 entries are skipped.
- **Which server is contacted:** only the address shown under the list, directly from your phone. Updates ask with
  `If-None-Match` / `If-Modified-Since`, so an unchanged list is not downloaded again. A list may not exceed 160 MB of text.
- **Automatic update:** Off, Daily or Weekly (default weekly once a list is on), by default only on Wi-Fi and not when
  the battery is low. The setting is shared with the web app block lists. **Update now** ignores the Wi-Fi setting.
- If an update fails, the previous list stays in use and the error is shown under the list.
- The filter is applied when a torrent starts and again after a list changes while a torrent runs.
- Switching a downloaded list off deletes its data. Imported lists keep their data until you remove them.
- A block list reduces connections to unwanted peers. It does not hide your IP address from other peers.

### Stop and cleanup

- Everything is stopped and the downloaded data is **deleted when the player closes**.
- Each start clears older torrent folders in the cache first.
- With **Play in a separate process** the whole player process ends, so nothing stays in memory.

### Tips

- Prefer torrents with many peers; "Finding peers" can take a minute.
- A slow download stalls playback. Wait for more buffering, or pick another source.
- Keep the phone on Wi-Fi: torrents use a lot of data.

## Subtitles

Tap **Subtitles...** in the player's top bar:

| Choice | Behavior |
| --- | --- |
| **From a file** | Pick any file. SRT, VTT, ASS / SSA and TTML are recognised. The file is converted to UTF-8 (see below) and kept in the cache |
| **Search online** | Searches the subtitle sources for the video, in your languages |

Embedded subtitle tracks, the delay and the look of subtitles are in the [playback options](./library-player#playback-options-the-three-dot-menu).

### Subtitle sources (no account needed)

Subtitle search works **without any account or key**. **Video services > Subtitle sources** lists the sources. Each has a
switch and arrows to change the order. A search asks the sources from the top and stops at the first one that has
results; **Search all sources** in the dialog asks every switched-on source and merges the results.

| Source | Account | How it is asked | Notes |
| --- | --- | --- | --- |
| OpenSubtitles (old REST API, `rest.opensubtitles.org`) | none | By the **file hash** of the video (when the file can be read) and then by title, season and episode | Needs only a User-Agent. The generic `TemporaryUserAgent` works for testing; a free registered agent from opensubtitles.org (field "OpenSubtitles User-Agent") is more reliable. OpenSubtitles has announced this API as deprecated, so it may stop working |
| Podnapisi (`podnapisi.net`) | none | By title, season, episode or year, and language | Public search of the web site. Telos could not test it from the development environment, so treat it as experimental |
| OpenSubtitles.com (new API) | your API key, and user name and password for downloads | By title, season, episode, year and file hash | Optional. Skipped when no key is entered |

How results are ordered: first by your **language order** (the first language in the list wins), then an **exact file
hash match**, how similar the release name is to the video file name, the number of downloads, and hearing impaired
subtitles last. Rows show the language, downloads, "HI", "exact match" and the source.

What happens to a downloaded subtitle: zip and gzip files are unpacked, the text is converted to UTF-8 (Byte order mark,
UTF-16, UTF-8 or the code page that fits the language, for example Windows-1253 for Greek or Windows-1251 for Russian;
the source's stated character set is used when given), and the file is stored in the cache folder `subtitles`. The
subtitle chosen for a video is remembered, so the next time the same file (same name and size) opens with it again
without asking the internet. Failed sources are named in the dialog and the next one is used.

The file hash is read from the video file for local videos. It is not available for web addresses, torrents and
network storages, which are searched by name only.

### Automatic subtitles

When **Download subtitles automatically** is on and a video has no subtitles yet, Telos searches the sources in the
background when the video starts. It takes the best result in your first language that has one, otherwise the best
result, and adds it. It also works for torrents. Failures are ignored quietly.

### Subtitle details

| Topic | Detail |
| --- | --- |
| Formats | SRT, VTT, ASS / SSA, TTML, and embedded tracks |
| Languages | ISO 639-1 codes such as `en`, `el`, `de` (also `pt-BR`, which counts as `pt`). Wrong codes simply give no results |
| Storage | Downloaded subtitles stay in the cache until Android clears it |
| Delay | -/+ 0.1 and 0.5 s in the playback options, for subtitles loaded from a file or the internet. Not for tracks inside the video |
| Style | Size, colour and edge (none, outline, shadow, box) in the playback options, remembered |

## Posters and descriptions (TMDB and Wikipedia)

Without any key, posters and descriptions come from **Wikipedia** (English). With your own free **TMDB API key**, TMDB
is used instead, which finds more titles and adds ratings. To get a key, create a free account at
[themoviedb.org](https://www.themoviedb.org), open its settings, API, and request a key, then paste it in Video
services.

Answers are saved on the device in `video_metadata.json`, one entry per title and source, so each title is asked for
only once. This product uses the TMDB API but is not endorsed or certified by TMDB.

## Trakt.tv

<Badge type="info" text="optional" /> Trakt tracks what you watch. Create an application at
`trakt.tv/oauth/applications` with the redirect address `urn:ietf:wg:oauth:2.0:oob`.

### Connect

1. In **Video services > Trakt.tv** enter the application's **Client id** and **Client secret**.
2. Tap **Connect Trakt** ("Contacting Trakt..."). A code and the address `trakt.tv/activate` are shown.
3. Open `trakt.tv/activate` in a browser, sign in to Trakt and enter the code.
4. The dialog says "Connected, scrobbling". If it fails you see "Sign in failed" or "Could not reach Trakt".

### What it does

| Feature | Behavior |
| --- | --- |
| Scrobbling | Telos reports start, pause and stop of movies and episodes while you watch |
| Watched | Trakt marks a title watched when it is stopped at 80 percent or more. Telos keeps its own watched marks (shown in the library) from the titles it scrobbled |
| Watchlist | **Add to Trakt watchlist** on a title's detail view. It shows "Added to your Trakt watchlist" or "Could not add to the watchlist" |
| Pause | A switch pauses scrobbling without signing out |
| Sign out | Forgets the tokens and the local watched marks |

Titles are matched by the name Telos recognised from the file name, so good file names give good matches. Tokens are
refreshed automatically and stored encrypted.

## Privacy summary

| Feature | Server | Data |
| --- | --- | --- |
| Web streams | The address you entered | Normal playback requests, with your IP address |
| Torrents | Trackers and peers | Your IP address, the torrent hash, the pieces you hold |
| Posters | `en.wikipedia.org` or TMDB | The recognised title and year |
| Subtitles | `api.opensubtitles.com` | Title, language, season and episode, your key and login |
| Trakt | `api.trakt.tv` | Recognised title, progress and your login |

## Limitations

- OpenSubtitles needs your own key and account, TMDB needs your own key.
- Torrent streaming depends on healthy peers. Many torrents never start.
- A torrent's data is deleted at the end. Nothing is kept or seeded afterwards.
- Subtitle search relies on file names. Wrong names give wrong results.
- Trakt matching uses titles, not ids, so ambiguous names may match the wrong show.
- No catalog or search for streams. You must provide the address.
- Network failures show a short error text, there is no automatic retry.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "Torrent streaming is set to Wi-Fi only" | Connect to Wi-Fi or switch the option off |
| "No answer from peers, the link may be dead" | The magnet link has no peers right now. Try another source |
| "This torrent has no video file" | The torrent holds other files only |
| "The torrent file is too large" | `.torrent` addresses are limited to 8 MB. Download it and use **Choose .torrent file** |
| Stream does not play | The address must point to a playable stream. Check it in a browser |
| No subtitle results | Add your OpenSubtitles key, user and password, and check the language codes |
| "Search failed" | Check the key and your network. The OpenSubtitles daily limit may also be reached |
| Trakt says "Sign in failed" | Check the client id and secret, and that the redirect address is set |
| Posters missing | Wait, or add a TMDB key. Check that the file name has the title |
