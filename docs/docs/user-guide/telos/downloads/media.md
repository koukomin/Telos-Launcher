# Video and audio from sites

Telos Downloads can save videos and audio from web sites (YouTube and hundreds of others), with quality choice, audio
extraction, subtitles and playlists. It uses [yt-dlp](https://github.com/yt-dlp/yt-dlp) through the
[youtubedl-android](https://github.com/yausername/youtubedl-android) library, which brings its own Python and FFmpeg.

::: warning You are responsible
You are responsible for what you download and for the terms of use of the sites. Many sites do not allow downloads, and
copyright law differs between countries. Download only what you have the right to. Telos does not check this for you.
:::

::: warning Optional, not in every build
The runtime (Python, yt-dlp, FFmpeg) adds about 70 MB to the APK (measured: debug APK with arm64-v8a and armeabi-v7a, 104 MB
without and 175 MB with it). So it is **optional**: a normal build does not include it, the Media tab then says so and
nothing in this page works. Builds made with `-Ptelos.media=true` (or `telos.media=true` in `gradle.properties`) include it.
See [Building](#building-with-the-runtime).
:::

## Download a video

1. **Add** > **Video or audio from a site**, or switch to the **Media** filter and tap **Add**. A single link to a known video site
   that you paste into the normal Add sheet, share to Telos Downloads or copy (see below) opens this sheet by itself.
2. **Analyze** is optional: it fetches the title, thumbnail, uploader, duration, the available resolutions and, for a playlist, the list
   of videos to choose from. Without it the usual resolutions are offered.
3. Choose the quality and the extras, then **Download**. A playlist makes one download for each ticked video.

| Option | What it does |
| --- | --- |
| Quality | Best, or a height limit (the best video up to that height, plus the best audio) |
| Audio only | Extracts the audio as **mp3**, **m4a** or **opus** (needs FFmpeg, which is part of the runtime) |
| Video container | Automatic, mp4, mkv or webm for the merged file |
| Subtitles | None, separate files, or embedded in the video; the languages are patterns such as `en.*,el` |
| Embed the thumbnail / metadata | Cover and tags inside the file |
| Cut sponsor segments | SponsorBlock "sponsor" segments of YouTube videos are removed |
| Cookies | See below |
| Folder | As for other downloads |

The estimated size comes from the analysis and can be wrong. The speed limit (global, or of the download) is given to yt-dlp when the
download starts.

## While it runs

The card shows the speed and remaining time from yt-dlp and what is happening: downloading, merging video and audio, extracting audio, adding
thumbnail and metadata, copying to the folder. Video and audio are two downloads one after the other, so the bar may be slow at
the end. yt-dlp works in a folder in the app storage; when it is done the results are copied to your folder (the same
way as the files of a torrent).

- **Pause** stops yt-dlp and keeps the partial files; **resume** starts it again and it continues from them (if the site allows it).
- Failed attempts are retried like other downloads if the cause can go away (network, "too many requests"). Other errors are explained in words:
  address not supported, login needed, private, not available in your country, not available any more, refused (try updating), no FFmpeg,
  storage full, quality not available.
- Removing an unfinished download deletes its partial files.

## Sites that need a login

yt-dlp can use cookies. Two ways, in the add sheet and in the settings:

- **Import cookies.txt**: pick a file in the Netscape format (browser extensions export it). It is kept in the private storage of the app
  and is **not** part of a backup.
- **Take the cookies of the Telos browser for this site**: pick **All / default** or one specific web app (each web app now has its own cookies, so choose the one where you are signed in). Without WebView profile support only the shared cookies exist. It copies the cookies that the web view of the [web apps](../launcher/desktop-and-overlays.md#web-apps) holds for the host of the
  link. Log in to the site in a Telos web app first. This is a best effort: the web view hands over names and values only, and the site may
  want more. Nothing about it was tested on a device.

"Use the imported cookies" in the add sheet decides per download. **Delete the cookies** removes the file. A site that asks to prove you are not a robot
usually needs cookies from a browser where you passed that check.

## Update the downloader

Sites change often, and an old yt-dlp is the most common reason for a failure. Settings > **Video and audio sites** > **Update the downloader**
fetches the newest yt-dlp from GitHub and shows the version and the date of the last update. **Supported sites** opens the yt-dlp list.
Turn on **Update yt-dlp automatically** and Telos fetches the newest version by itself once a day, on Wi-Fi and when the battery is not low (the switch is off until you turn it on).

## Capture

- **Share** a link to Telos Downloads: video site links open the media sheet.
- **Download with Telos** in the menu of a [web app](../launcher/desktop-and-overlays.md#web-apps) sends the address of the page you are on.
- **Detect links in the clipboard** (Settings, off by default): when the Downloads screen comes to the foreground, a copied link, video site link or magnet link
  is offered with one tap (**Add**). Android 12 and newer may show a note that the app read the clipboard.
- Telos does **not** register itself for "Open with" for video sites, so it does not take links away from your browser or other apps.

## What is contacted

The sites you download from, and `github.com` / `raw.githubusercontent.com` when you update yt-dlp. Analyzing a link already contacts the site. Nothing else, and
no telemetry.

## Building with the runtime

```
./gradlew assembleDefaultDebug -Ptelos.media=true
```

adds `io.github.junkfood02.youtubedl-android:library` and `:ffmpeg` (version 0.18.1, from Maven Central, GPL-3.0) and packs the native libraries
uncompressed-extractable (`useLegacyPackaging`), which the library needs to start Python and FFmpeg. The code that uses the library is in the source set
`services/downloads/src/mediaOn`; without the flag `mediaOff` is used and `MediaRuntime.isAvailable` is false.

## Status and limitations

- The media engine compiles with the runtime and has unit tests for its pure parts (progress lines, format selection, arguments, analysis, errors, links, cookies). **It was not run on a device**: starting Python, yt-dlp and FFmpeg, the real output format, the update and the cookie hand-over are untested.
- A playlist is not one download but one for each video. Live streams and very long videos are not specially handled.
- No aria2c (multi-connection external downloader) and no browser that finds media on pages.
- yt-dlp licence: Unlicense; youtubedl-android: GPL-3.0; FFmpeg: depends on its build (LGPL or GPL), see the [notices](https://github.com/koukomin/Telos-Launcher/blob/main/THIRD_PARTY_NOTICES.md).
