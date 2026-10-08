# Torrents in Telos Downloads

Magnet links and `.torrent` files, with a choice of files, seeding limits, speed limits, encryption and IP block lists.

::: tip At a glance
Paste a magnet link or choose a `.torrent` file. Telos shows the name, size and **list of files**, you pick what you want (and
how important each file is), and the torrent downloads in the background, survives a restart, and can **seed** afterwards until
a ratio or time limit. Everything runs in one torrent session shared with [Telos Video](../video/streams-torrents-subtitles.md).
:::

::: warning Use torrents only for content you are allowed to share
Torrent networks show your IP address to the other peers, and while a torrent runs you upload pieces to them. A block list
reduces unwanted connections, it does not hide your IP address.
:::

Built on libtorrent (through libtorrent4j, MIT / BSD-3-Clause). The layout and the feature set follow ideas of
[LibreTorrent](https://github.com/proninyaroslav/libretorrent) (GPL-3.0-or-later), Gopeed and Ketch; no code of those
projects was copied.

## Adding a torrent

| Way | How |
| --- | --- |
| Torrents tab | **Add** opens the torrent sheet. Paste a magnet link or a `.torrent` address (http or https), or choose a `.torrent` file |
| All downloads | Pasting a magnet link or `.torrent` address into the normal **Add** sheet moves you to the torrent sheet |
| From another app | **Share** the link as text to **Telos Downloads**, or open a magnet link, a `.torrent` address or a `.torrent` file with it. Telos Video is offered for the same links; you choose. Video streams while it downloads and deletes everything afterwards, Downloads keeps the files |
| From Telos Video | In **Play from the web**, a magnet link or `.torrent` address gets the button **Download in Telos Downloads** |

**The file list first.** A `.torrent` file or address is read right away. For a magnet link press **Get file list**: Telos asks
the swarm for the torrent's description (up to 75 seconds; this needs DHT or trackers and at least one peer that answers).
The sheet then shows name, number of files and size. Every file has a checkbox and a priority (**Skip, Low, Normal, High,
Maximum**); at least one file must stay selected. **Add without file list** starts the magnet link right away and downloads everything
until you change it in the details.

The same sheet asks for the **folder**, **Download in order** (sequential), the seeding limits and **Add paused**.

## What you can do with a torrent

Tap a torrent card for the details sheet.

| Tab | Content |
| --- | --- |
| Files | Every file with progress, a checkbox and a priority, **while it downloads too**. Tap a finished file to open it |
| Peers | Address, client, down and up speed, progress and flags (D/d downloading, U/u uploading, O optimistic unchoke, S snubbed, I incoming, C outgoing, E encrypted, P uTP) |
| Trackers | Address, tier, state (working, updating, not contacted, error with the number of failures and the tracker's message). **Add tracker** takes an http, https or udp address |
| Info | Size, info hash, pieces, private flag, uploaded and received bytes, ratio, seeding time, seeding limits (changeable), magnet link (copy), working folder, saved to, **Open folder** |

Above the tabs: the speeds, the ratio, seeds and peers, a **piece map** (a bar of up to 120 cells; the stronger the colour,
the more pieces of that part are there), and the buttons **Pause / Resume, Recheck files** (force recheck), **Reannounce**,
**Remove**, and the switch **Download in order**.

The cards of the Torrents tab show state, percent, size, ETA, down and up speed, ratio, and seeds and peers. The header
sums the speed of all active torrents. Swiping is not used in this tab; the button on the card pauses and resumes.

## Where the files go

libtorrent writes real files with normal file paths. Android's folder picker (Storage Access Framework) and the public
Downloads collection do not give such paths. So Telos works like this:

1. The data is written to a **working folder in the storage of the app** (`Android/data/<app>/files/torrents/<task>`).
2. When all the files you chose are complete, Telos **copies them to your folder** (or `Downloads/Telos` on Android 10 and newer,
   or the app's Downloads folder below Android 10), keeping the folder structure of the torrent. The state shows "Copying to the folder".
3. The torrent is reported as completed (notification, **Open**). If it seeds, it **seeds from the working folder**, so for a
   while two copies exist and you need the space twice.
4. When seeding ends, or you remove the torrent, the working folder is deleted.

Removing a torrent asks whether the finished files stay in your folder. A torrent that never finished leaves nothing behind.
Folders that Telos created inside your folder stay (they can be empty).

When the file list is known, Telos checks before a torrent starts that the working folder has room for what is left to download.

## Seeding

- Per torrent: **stop at 100 %**, **stop at ratio** (none, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0) and **stop after** (none, 30 min, 1 h, 3 h, 12 h, 1 day, 3 days). Whichever limit is reached first ends seeding. With no limit and no stop at 100 %, a torrent seeds until you pause or remove it.
- The defaults for new torrents are in the settings (default: seed to a ratio of 1.0, no time limit).
- The ratio is uploaded divided by received (over the whole life of the torrent); a torrent that was complete when it was added uses its size.
- A seeding torrent shows as **Seeding**, does not use one of the "downloads at the same time" slots and stays in the foreground service
  notification. Pausing it stops seeding; resuming checks the files quickly and goes on.
- Android 15 limits the foreground service type used here ("data sync") to about six hours a day. When it ends, the running
  torrents are paused, not failed.

## Rules from Telos Downloads that torrents follow

**Wi-Fi only** and **pause when the battery is low** apply (everything waits and starts again by itself). The global speed limit applies to
torrents when no torrent download limit is set. Notifications, the foreground service, **Pause all / Resume all**, search and the
retry with backoff for failures work as for other downloads. A disk error, for example a full storage, ends the torrent
with an error (**Retry** tries again).

## Resume after a restart

Resume data (which pieces are there, the file choices, trackers, totals) is saved every 30 seconds while a torrent runs, when
you pause it, when the metadata arrives, and when the job ends. After a restart of the app or the phone the torrents that
were running start again, check their files and continue. The magnet link and the `.torrent` file are kept next to the resume data, in the private storage of Telos.

## Settings

Menu > **Settings** > **Torrents**.

| Setting | Default | Notes |
| --- | --- | --- |
| DHT | on | Finds peers without a tracker. A magnet link without trackers needs it |
| Peer exchange (PEX) | on | Takes effect for torrents that start after the change |
| Local peer discovery | on | Peers on the same network |
| uTP | on | UDP connections that give way to other traffic |
| Encryption | prefer | Prefer encrypted, Require encrypted (peers without encryption are refused) or Off |
| Listening port | random | Random at every start of the session, or a fixed port from 1024 to 65535 |
| UPnP, NAT-PMP | on | Ask the router to open the port |
| Download limit, upload limit | none | 128 KB/s to 10 MB/s |
| Torrents downloading at once | 3 | The others wait in the torrent queue ("Waiting in the torrent queue") |
| Torrents seeding at once | 3 | 0 to 10 |
| Connections | 200 | 50 to 1000 |
| New torrents | ratio 1.0 | Do not seed, seed until a ratio, seed for at most a time, download in order |
| Peer block lists | all off | See below |

Because there is **one session for all of Telos**, these settings also apply when Telos Video streams a torrent, and the
two never fight for the listening port. The streamer's torrent is not part of the queue limits.

## Peer block lists

The block lists are the same system as in Telos Video (and the web app block lists): nothing is downloaded until you switch a
list on, the list comes directly from its own server to your phone, it can be updated automatically (off, daily, weekly; Wi-Fi only
by default) and the previous version stays when an update fails. The filter is applied to the one torrent session, so it covers
downloads, seeding and streaming. Settings > Torrents > **Peer block lists** shows how many ranges are active, and each list shows
its licence or terms, **what it is**, when it was fetched and the **date written in the list itself** (from its header or, for
gzip files, from the gzip header).

Formats read: PeerGuardian p2p (`name:1.2.3.4-1.2.3.5`), eMule `ipfilter.dat`, CIDR netsets with `#` and `;` comments
(FireHOL, Spamhaus), tab separated ranges (DShield), `a-b` and single addresses, plain, gzip or zip. IPv6 is skipped (the filter is IPv4). Lists that
contain private ranges (bogons) have these cut out (10/8, 100.64/10, 127/8, 169.254/16, 172.16/12, 192.168/16) so that devices on
your own network are not blocked.

### Which lists are offered, and why

Checked on **2026-10-08** from the list files themselves and the pages of their projects. "Date" is the date the data says
about itself.

| List | Offered | Date in the data (2026-10-08 check) | Terms | Remarks |
| --- | --- | --- | --- | --- |
| **Spamhaus DROP** `spamhaus.org/drop/drop.txt` | yes | Last-Modified 2026-10-07 17:01 UTC (in the file header) | "Free of charge", credit must be given to The Spamhaus Project, date and copyright text stay with the data; fetch at most once an hour (Telos fetches daily at most) | Security list: networks run by cybercrime and bulletproof hosters. Small (about 1,670 ranges) and precise |
| **FireHOL level 1** `firehol_level1.netset` | yes | This File Date 2026-10-08 04:41 UTC, "Update Frequency: 1 min" | A mix: FireHOL (GPL v2 tools); its sources keep their terms. The file lists as its sources: DShield (CC BY-NC-SA 2.5, non-commercial), Feodo Tracker (CC0), Spamhaus DROP and bogons. The FireHOL project says some lists may have special licences and to check the source | Security list with "minimum false positives" (about 4,600 ranges). Bogons are cut out by Telos |
| **Naunter BT_BlockLists** `bt_blocklists.gz` | yes, marked | gzip header 2026-10-08 03:15 UTC (about 687,000 lines) | Unlicense for the project, `README`: "will be retained, but it will not be maintained actively" (notice 2024-10-22), "updated automatically by Github Workflow". Its entries come from other lists (for example iBlocklist), whose terms were **not** checked | The only large p2p list (named organisations that watch swarms). Kept because the file is still regenerated, with the maintenance warning in its description |
| codebucket.de Transmission list | **no** | last modified 2025-01-17 | not checked | Stale (21 months) |
| abuse.ch Feodo Tracker IP blocklist | **no** | "Last updated: 2026-03-04", 5 entries | CC0 (their terms page) | Too small and 7 months old; its data is already part of FireHOL level 1 |
| DShield block list | **no** | updated 2026-10-08 | CC BY-NC-SA 2.5 in the file header | Only the top 20 /24 networks; also part of FireHOL level 1; non-commercial licence |
| FireHOL level 2 and 3 | **no** | 2026-10-08 | per source: includes blocklist.de, GreenSnow, CINS Army, bruteforceblocker, myip, vxvault, DShield | Terms of several sources not verified |
| Emerging Threats compromised IPs | **no** | Last-Modified 2026-10-07 | terms page not read | Not verified |
| CINS Army list | **no** | Last-Modified 2026-10-08 | no terms found on the page | Not verified |
| blocklist.de all.txt | **no** | Last-Modified 2026-10-08 | terms not read | Not verified; mostly SSH/mail attackers, not useful for torrents |
| Bluetack / iBlocklist | **no** | not checked | not checked (current terms of the free lists were not verified) | Not verified |
| Tor exit lists | **no** | | | Informational, not offered |

::: info What these lists do, honestly
Spamhaus DROP and FireHOL level 1 are **security** lists: they keep your torrent session away from networks that run
malware and attacks. They are actively maintained, but they are not "anti-snooper" p2p lists. The classic p2p lists
(Bluetack and similar) are not maintained any more by anyone Telos could verify; Naunter's file is the only large one, and
its author says it is not actively maintained. You can add your own list by https address or file under
**Add list by address** and **Import from a file**.
:::

## Status and limitations

- **Implemented:** everything on this page.
- **Not tested on a device.** The code compiles and the logic (file selection, seeding rules, list formats, piece map, magnet parsing) has
  unit tests, but nothing could be run on a phone or in an emulator while writing this. Expect rough edges, especially around
  magnet metadata, copying to a folder chosen with the folder picker, and the foreground service.
- Not there: a time schedule, queue ordering by drag, RSS feeds, a speed limit for a single torrent in the interface (the field exists
  internally), web seeds in the interface, creating torrents, IPv6 block lists, a backup of the list, extracting archives.
- Torrents and the first `.torrent` file chosen: if a magnet link finds no peers the file list does not appear; use **Add without file list** and wait, or try another link.
- A file you skip is not downloaded, but the pieces at its edges that it shares with wanted files are, so a skipped file may
  exist partly on disk in the working folder; it is never copied to your folder.
- While a torrent seeds from the working folder, the copy in your folder is a plain copy: deleting or moving it does not stop
  seeding, and changes to it are not seen.
- Telos Video's torrent is not counted against "torrents downloading at once" and its data is deleted when the player closes.
