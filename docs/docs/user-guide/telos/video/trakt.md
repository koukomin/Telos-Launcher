# Trakt scrobbling

Telos Video can report what you watch to [Trakt](https://trakt.tv). It is **off by default**, and it needs a Trakt
application of your own.

## Set up

1. Sign in at trakt.tv, open `trakt.tv/oauth/applications` and choose **New Application**. Enter any name and the
   redirect address `urn:ietf:wg:oauth:2.0:oob`, then save.
2. In Telos Video open the gear icon (**Video services**) and tap **Trakt scrobbling**.
3. Enter the **client id** and **client secret**, then tap **Sign in**. Telos shows a code and the address
   `trakt.tv/activate` (with **Copy code** and **Open**). Enter the code there and wait until the dialog says you are signed in.
4. Switch on **Scrobble to Trakt**.

## What is sent

- Only videos that Telos recognized: an episode (`Show.S01E02` or `1x02` in the file name) or a movie with a year in the
  file name. Other videos are never sent.
- Start, pause and stop with the progress. A stop at 80 percent or more marks the title as watched.
- Title, year, season and episode, the TMDB id when it is already known, and the progress go to `api.trakt.tv`, and only
  after you signed in and switched scrobbling on.
- If a stop at 80 percent or more cannot be sent (no network, server error), it is kept in a queue of up to 50 entries and
  sent later as a history entry with the original time. **Clear queue** empties it. Signing out also clears it.

## Secrets

The client secret and the tokens are encrypted on the device and are not part of backups or device transfers.
It was not tested on a device.
