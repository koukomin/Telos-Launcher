# Media redesign and call/Recents fixes

Baseline: v1.42.12 / main 82f2222e.

The design groups sources by content rather than by backend. Listen contains music, YouTube Music and radio; Watch contains local video, YouTube, TV and Stremio. Stable source IDs, saved source order, ViewModels and source-specific SaveableStateProvider keys remain. Navigation rail decisions use available width, height and font scale. MediaFrame still supplies each screen’s actions; no source or playback controller is replaced.

| Source | Retained functionality | Presentation |
| --- | --- | --- |
| Local music | Library, albums/artists/genres, queue, playlists, lyrics, tags, AudioFx, sharing | Artwork shelves and listening filters |
| Radio | Favorites, search, custom stations/imports, recordings, scheduling, history/settings | Listening artwork and track-like station rows |
| Local video | Folders/movies/series, watched/resume/Trakt, share/delete, complete player tools | Thumbnail-led watch grid; episode rows retained |
| YouTube Music | Shared session, search, favorites/playlists/import, moods/radio, background playback | Music home, artwork rows and library shelves |
| YouTube | Search/links, video player, quality/queue/background, account/library actions | Home feed, account playlists and 16:9 cards |
| TV | Sources/countries, custom streams/imports, favorites/ordering, EPG and player | Larger 16:9 channel cards |
| Stremio | Account/library/add-ons, torrent/transport/cache, playback preferences and continuation | Watch filters, larger poster shelves and font-aware library grid; source/player controls preserved |

Home parsing is bounded and handles classic video renderers, modern lockup view models and music song rows. Client versions come from the current Music page rather than a fixed January version. Downloader merges extractor visitor cookies with account credentials so mix requests retain their visitor identity. Feed errors surface instead of silently yielding an empty screen. This is not a guarantee that private provider endpoints or account sign-in always work.

Call callbacks retain their authoritative state; terminal calls cannot remain primary, service teardown clears references and hangup selects live conference roots. A delayed, single-call-only Telecom fallback cannot end a replacement call. Pocket filtering exempts the explicit termination touch gesture while retaining other pocket filtering.

Recents handling uses system callbacks only. Removal-start metadata captures tasks before they disappear. Refresh notifications are coalesced separately from definitive removals; overflow forces reconnect rather than treating missing information as an empty task list. The opt-in, Shizuku permission requirement, last-task/profile checks and freeze exclusions remain.

Validation includes parser/cookie fixtures, call-state/touch policy tests, actual call-button touch geometry and listening/watch screenshot cases. Root/Shizuku/OEM task callbacks, carrier calls, signed-in personal feeds and physical playback require device/account verification. Full visual acceptance is pending screenshot review; this document does not claim all device states tested.

Account UI now distinguishes native session sign-in from the separate browser session in all supported base languages. Auto-playlist shelves use translated platform/favorites labels, and account playlists use artwork cards instead of duplicated chip rows. The last Listen and Watch source is saveable across recreation. Navigation-rail policy tests cover short landscape and enlarged fonts; screenshot fixtures cover compact/wide listening and watch cards.

The YouTube Music mini player expands to an artwork-led player with position/seek controls, previous/next, stop and the existing queue. The current MediaController is the source of playback state; selecting a queue item uses the existing resolve/play path. Wide short windows use side-by-side artwork and controls; portrait content scrolls naturally at large fonts. No separate playback engine or service is introduced.
