# Third-party notices

Telos Launcher is licensed under the GNU General Public License 3.0 (see `LICENSE.txt`). It contains
code, designs and behaviour taken from the projects below. Their copyright notices are preserved
here, as their licenses require. This list is kept up to date together with the "Projects Telos is
based on" table in `readme.md`.

| Project | License | Copyright notice found in the project |
| --- | --- | --- |
| [Kvaesitso](https://github.com/MM2-0/Kvaesitso) | GPL-3.0 | Copyright (C) 2021-2026 MM2-0 and the Kvaesitso contributors |
| [Right Dialer (Goodwy/Dialer)](https://github.com/Goodwy/Dialer) | GPL-3.0 | Based by its author on [Simple Dialer](https://github.com/SimpleMobileTools/Simple-Dialer) and [Fossify Phone](https://github.com/FossifyOrg/Phone) |
| [Secure Dialer](https://github.com/Secure-Phone-apps/Secure-Dialer) | GPL-3.0 | Copyright (C) 2026 MovStore |
| [Ever Dialer](https://github.com/hari161008/Ever-Dialer) | GPL-3.0 | Copyright (c) 2024-2026 Ever Dialer Contributors; parts Copyright (C) 2026-present kitsumed (Med) |
| [Thor](https://github.com/trinadhthatakula/Thor) | GPL-3.0-or-later | Copyright (c) 2025-2026 Trinadh Thatakula |
| [Undead Wallpaper](https://github.com/maocide/UndeadWallpaper) | GPL-3.0 | The Undead Wallpaper authors |
| [Obtainium](https://github.com/ImranR98/Obtainium) | GPL-3.0 | The Obtainium authors |
| [Gopeed](https://github.com/GopeedLab/gopeed) | GPL-3.0 | Design ideas only for Telos Downloads, no code copied (copyright: the Gopeed authors) |
| [Ketch](https://github.com/linroid/Ketch) | Apache-2.0 | Design ideas only for Telos Downloads, no code copied (copyright: the Ketch authors) |
| [LibreTorrent](https://github.com/proninyaroslav/libretorrent) | GPL-3.0-or-later | Design ideas only for the torrent part of Telos Downloads (feature set and screens), no code copied (copyright: the LibreTorrent authors) |
| [Kite](https://github.com/zenzer0s/kite) | GPL-3.0 | Design ideas only for the video and audio sites part of Telos Downloads, no code copied (copyright: the Kite authors) |
| [AIO Video Downloader](https://github.com/shibaFoss/AIO-Video-Downloader) | custom licence (text not verified) | Ideas only, **no code used** (copyright: the AIO Video Downloader authors) |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Unlicense (public domain) | Used unchanged, inside the optional media build (see below) |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) (fork io.github.junkfood02.youtubedl-android) | GPL-3.0 | Copyright (c) the youtubedl-android authors (yausername, JunkFood02 and contributors), used unchanged as a Maven dependency in the optional media build |
| [RethinkDNS](https://github.com/celzero/rethink-app) | Apache-2.0 | Copyright (c) Celzero / The Rethink DNS Open Source Project (full notice below) |
| [firestack](https://github.com/celzero/firestack) | MPL-2.0 | Copyright (c) Celzero / The Rethink DNS Open Source Project; used unchanged as a Maven dependency |
| [WireGuard](https://www.wireguard.com) | see project | Implemented inside firestack; WireGuard is a registered trademark of Jason A. Donenfeld |
| [Transistor](https://codeberg.org/y20k/transistor) | MIT | Copyright (c) 2015-22 - Y20K.org (full notice below) |
| [TagLib wrapper (Kyant0/taglib)](https://github.com/Kyant0/taglib) | Apache-2.0 | Copyright 2025 Kyant. Bundles TagLib (LGPL-2.1 / MPL-1.1 upstream) |
| [PaperKnife+](https://github.com/potatameister/PaperKnifePlus) | GPL-3.0-or-later | Copyright (C) potatameister and PaperKnife+ contributors. PDF tools of Telos Photos (feature set, tool logic) adapted from it |
| [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) | Apache-2.0 | Copyright the Apache PDFBox authors and Tom Roush; used unchanged as a Maven dependency |
| [baresip](https://github.com/baresip/baresip), [baresip-studio](https://github.com/juha-h/baresip-studio) | BSD-3-Clause | Planned, not yet included |

## Trademarks

The name "Thor" and the Thor logo and icon are trademarks of Trinadh Thatakula and are not licensed
under the GPL (see Thor's `TRADEMARK.md`). Telos does not use them. Thor is only referred to by name
to say that parts of Telos are based on it. Telos is not affiliated with or endorsed by Thor.

## RethinkDNS and firestack (Telos Network)

Telos Network is based on RethinkDNS, https://github.com/celzero/rethink-app, Copyright (c) Celzero / The Rethink DNS Open Source Project,
licensed under the Apache License, Version 2.0. Its concept, feature set and structure were adapted; where code was adapted, the
original copyright notice is kept in the source file. Apache-2.0 is compatible with GPL-3.0. The Apache-2.0 license text:
http://www.apache.org/licenses/LICENSE-2.0

```
Copyright (c) Celzero / The Rethink DNS Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

firestack, https://github.com/celzero/firestack (the Go engine, built as `com.celzero:firestack`), is licensed under the Mozilla Public
License 2.0 (https://www.mozilla.org/MPL/2.0/). Telos uses it unchanged as a Maven dependency and has not modified any of its files.
Its source is available at the address above. Rethink, RethinkDNS and Celzero are names of their owners; Telos is not affiliated with them.

WireGuard is a registered trademark of Jason A. Donenfeld. The WireGuard protocol is implemented inside firestack.

## Transistor (MIT License)

```
The MIT License (MIT)

Copyright (c) 2015-22 - Y20K.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

## baresip-studio (SIP engine bridge)

Used in `services/comms/src/main/cpp/baresip.c`, `logger.h` and
`services/comms/src/main/java/com/tutpro/baresip/Api.kt` (JNI bridge to baresip).
Project: https://github.com/juha-h/baresip-studio

```
BSD 3-Clause License

Copyright (c) 2018, TutPro Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```


## Block lists offered by Telos (downloaded on request, not bundled)

Telos contains no copy of these lists. After the user switches a list on, the app downloads it directly from the
address below. The lists belong to their authors.

| List | Address | Licence |
| --- | --- | --- |
| StevenBlack unified hosts | https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts | MIT |
| AdGuard DNS filter | https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt | GPL-3.0 |
| Peter Lowe's ad and tracking server list | https://pgl.yoyo.org/adservers/ | the site invites combining and redistributing the list; no formal licence is stated |
| OISD small | https://small.oisd.nl/domainswild | GPL-3.0 |
| EasyList | https://easylist.to/easylist/easylist.txt | GPL-3.0 or CC BY-SA 3.0 |
| URLhaus host file | https://urlhaus.abuse.ch/downloads/hostfile/ | abuse.ch terms of use, https://urlhaus.abuse.ch/api/ |
| Naunter BT_BlockLists | https://raw.githubusercontent.com/Naunter/BT_BlockLists/master/bt_blocklists.gz | Unlicense for the project (its LICENSE file). The README says it is not actively maintained (notice 2024-10-22) while an automatic workflow regenerates the list. The entries come from other lists (for example iBlocklist, the Transmission lists of codebucket.de and others); their terms were not checked |
| Spamhaus DROP | https://www.spamhaus.org/drop/drop.txt | Free of charge (https://www.spamhaus.org/drop/): when used in a product, credit must be given to The Spamhaus Project, and the date and copyright text must stay with the file and data. Do not fetch automatically more than once an hour (Telos at most daily) |
| FireHOL level 1 | https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/firehol_level1.netset | A combination of DShield (CC BY-NC-SA 2.5, see the header of the DShield file), Feodo Tracker (CC0), Spamhaus DROP (terms above) and bogons. FireHOL (its scripts GPL v2) says that some lists may have special licences and that the source site must be checked before use (https://iplists.firehol.org). The list is fetched by the user's phone from FireHOL's repository; Telos does not redistribute it |

Lists that were looked at on 2026-10-08 and are **not** offered: the Transmission list of codebucket.de (last modified 2025-01-17),
abuse.ch Feodo Tracker IP blocklist (CC0; last updated 2026-03-04 with 5 entries), DShield block list (CC BY-NC-SA 2.5; also part of
FireHOL level 1), FireHOL level 2 and level 3, Emerging Threats compromised IPs, CINS Army, blocklist.de and Bluetack/iBlocklist (terms not
verified), Tor exit lists (informational only). Details: docs/docs/user-guide/telos/downloads/torrents.md.

## libtorrent4j and libtorrent (torrent streaming in Telos Video, torrent downloads in Telos Downloads)

libtorrent4j, https://github.com/aldenml/libtorrent4j (MIT), used unchanged as a Maven dependency.

```
Copyright (c) 2018-2025 Alden Torres

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

libtorrent, https://www.libtorrent.org (BSD-3-Clause), bundled in the native libraries of libtorrent4j:
Copyright (c) 2003-2020, Arvid Norberg. All rights reserved.

## yt-dlp, youtubedl-android, FFmpeg and Python (optional media build of Telos Downloads)

Only in builds made with `-Ptelos.media=true`. The default build does not contain any of this.

- yt-dlp, https://github.com/yt-dlp/yt-dlp: Unlicense (public domain dedication), the version that ships in the library and later ones the user downloads from GitHub with "Update the downloader". yt-dlp itself bundles and depends on other Python packages with their own licences (see the yt-dlp repository).
- youtubedl-android, https://github.com/yausername/youtubedl-android, fork https://github.com/JunkFood02/youtubedl-android (Maven: `io.github.junkfood02.youtubedl-android:library` and `:ffmpeg`, version 0.18.1): GPL-3.0 as stated in its Maven POM.
- FFmpeg, https://ffmpeg.org, as built in the `ffmpeg` artifact of youtubedl-android: LGPL-2.1+ or GPL, depending on the build options of that artifact (not verified here; the artifact page and the FFmpeg legal notes apply).
- Python (CPython), https://www.python.org: PSF licence, bundled in the `library` artifact as `libpython`.

## TMDB and OpenSubtitles (Telos Video online services)

This product uses the TMDB API but is not endorsed or certified by TMDB (https://www.themoviedb.org).
Subtitles are searched and downloaded through the OpenSubtitles API (https://www.opensubtitles.com).
Both services are used with the user's own API key.


## NextLib (FFmpeg decoders in Telos Video)

NextLib, https://github.com/anilbeesetti/nextlib (GPL-3.0), used unchanged as a Maven dependency. It
bundles FFmpeg (LGPL-2.1+, https://ffmpeg.org) and dav1d (BSD-2-Clause, https://code.videolan.org/videolan/dav1d).

## Next Player

The gestures, speed control and track choice of the Telos Video player follow the feature set of
Next Player, https://github.com/anilbeesetti/nextplayer (GPL-3.0). No code was copied.

## Nova Video Player

The network storage playback, library scanning from network folders and the subtitle search of Telos Video
are inspired by Nova Video Player, https://github.com/nova-video-player/aos-AVP (Apache-2.0, derived from
the Archos Video Player, Copyright Archos SA and the Nova Video Player contributors). It is implemented
independently with Media3 and Telos' own code. No source code and none of its prebuilt binaries (FFmpeg,
dav1d, torrentd) were copied. The Apache-2.0 license text: https://www.apache.org/licenses/LICENSE-2.0

## OpenSubtitles and Podnapisi

Telos Video can search subtitles through the OpenSubtitles REST API (https://www.opensubtitles.com and
https://rest.opensubtitles.org) and the public search of Podnapisi (https://www.podnapisi.net). The
OpenSubtitles file hash is computed with the algorithm published by OpenSubtitles. Telos is not endorsed
or certified by these services, and their terms of use apply to the subtitles. Subtitle sources are
contacted only when the user searches or has switched on automatic download.

## Trakt.tv

Telos Video uses the Trakt API (https://trakt.docs.apiary.io) with the user's own application
credentials. Telos is not endorsed or certified by Trakt.


## Libraries used by Telos Files

sshj and smbj (Apache-2.0, Copyright Jeroen van Erp and contributors), Apache Commons Net and Apache
Commons Compress (Apache-2.0, Copyright The Apache Software Foundation), OkHttp (Apache-2.0,
Copyright Square, Inc.), Bouncy Castle (MIT-style license, Copyright The Legion of the Bouncy Castle
Inc.). All are used unchanged as Maven dependencies.

## Cryptomator vault format

Reading Cryptomator vaults (https://cryptomator.org) is implemented from the published security
architecture and vault format description. No Cryptomator code was copied.

## Solid Explorer and MiXplorer

The layout and feature set of Telos Files are inspired by these file managers. No code was copied.
