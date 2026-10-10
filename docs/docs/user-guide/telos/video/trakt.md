# Trakt scrobbling

Telos Video can report what you watch to [Trakt](https://trakt.tv). It is **off by default**, and it needs a Trakt
application of your own.

## Set up

1. Sign in at trakt.tv, open `trakt.tv/oauth/applications` and choose **New Application**. Enter any name and, as the
   redirect address, exactly `telos-trakt://callback` (the dialog shows it with a **Copy address** button). Save it.
2. In Telos Video open the gear icon (**Video services**) and tap **Trakt scrobbling**.
3. Enter the **client id** and **client secret** of your Trakt application and tap **Sign in with browser**. The browser
   opens trakt.tv: log in and approve there (Telos never sees your Trakt password) and you return to Telos, which says you
   are signed in. The pending sign-in lasts 10 minutes and survives Telos being closed in the meantime.
4. Switch on **Scrobble to Trakt**.

**Use a code instead** is the fallback: Telos shows a code and the address `trakt.tv/activate` (with **Copy code** and
**Open**); enter the code there. This path can keep the older redirect address `urn:ietf:wg:oauth:2.0:oob`.

Messages: *Sign in was denied on Trakt* (you pressed Deny), *The sign in answer did not match this request and was
ignored* (the answer did not carry the secret state of your request, so it was discarded) and *The sign in took longer than
10 minutes* (start again). The redirect address is a custom scheme, so the secret state and the client secret protect the
exchange. You must create your own Trakt application; no client secret is built into Telos.

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
