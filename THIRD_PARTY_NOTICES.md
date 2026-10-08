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
| [Transistor](https://codeberg.org/y20k/transistor) | MIT | Copyright (c) 2015-22 - Y20K.org (full notice below) |
| [TagLib wrapper (Kyant0/taglib)](https://github.com/Kyant0/taglib) | Apache-2.0 | Copyright 2025 Kyant. Bundles TagLib (LGPL-2.1 / MPL-1.1 upstream) |
| [baresip](https://github.com/baresip/baresip), [baresip-studio](https://github.com/juha-h/baresip-studio) | BSD-3-Clause | Planned, not yet included |

## Trademarks

The name "Thor" and the Thor logo and icon are trademarks of Trinadh Thatakula and are not licensed
under the GPL (see Thor's `TRADEMARK.md`). Telos does not use them. Thor is only referred to by name
to say that parts of Telos are based on it. Telos is not affiliated with or endorsed by Thor.

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
| Naunter BT_BlockLists | https://github.com/Naunter/BT_BlockLists/raw/master/bt_blocklists.gz | Unlicense |

## libtorrent4j and libtorrent (torrent streaming in Telos Video)

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
