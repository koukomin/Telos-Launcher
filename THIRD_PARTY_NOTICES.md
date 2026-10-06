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
