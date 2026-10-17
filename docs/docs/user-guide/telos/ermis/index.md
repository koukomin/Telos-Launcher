# Ermis

Ermis is the built-in email client. The layout follows the Gmail app: rounded search bar with menu and profile letter, Primary/Starred inbox rows with coloured avatars and stars, an extended Compose button, a conversation screen, and a compose sheet with To / Subject lines. The feature set follows [FairEmail](https://github.com/M66B/FairEmail) (GPL-3.0): IMAP and SMTP accounts, conversation grouping, send, and per-message notes. FairEmail source is not copied. Screens use the Telos blur theme.

Ermis is a [virtual app](../#how-the-built-in-apps-work). Switch it on in [Telos Store](../store/).

## Accounts

If **microG** or Play services is installed and a Google account is signed in, Ermis can use that account with IMAP/SMTP **XOAUTH2** (no app password). Otherwise add a Gmail address with an [app password](https://support.google.com/accounts/answer/185833), or any IMAP/SMTP provider with TLS (IMAP 993, SMTP 587 STARTTLS or 465). Passwords are encrypted in the Android Keystore. Google may still ask you to grant mail access the first time.

## Notifications

Ermis checks IMAP for unseen mail about every 15 minutes while the launcher can run workers. New mail shows a notification that opens Ermis.

## Attachments and cloud

Compose can attach files from this phone **or from the same cloud and network storages as Telos Files** (Nextcloud, WebDAV, Drive, Dropbox, OneDrive, SFTP, SMB, FTP, and a cloud app folder). Those connections are the ones saved in Settings > Integrations > Cloud and network storage.

## Large files

If an attachment is 15 MB or larger, Ermis offers to send a link instead of the file: upload to [transfer.sh](https://transfer.sh) and paste the URL, or open [WeTransfer](https://wetransfer.com) or [transfer.it](https://transfer.it). You can still keep it as a normal attachment.

## Inbox

Rows show sender, subject, snippet and date. Search filters from, subject and body. Sync loads the latest INBOX messages. Open a row to read the body.

## Notes on Gmail mail

Each message has a **Note** field. Notes are stored on the phone, keyed by Message-ID.

For Gmail accounts, saving a note also APPENDS it to the IMAP folder `Ermis-Notes` with header `X-Ermis-For`. Sync reads that folder back so notes survive a reinstall of Telos on the same account. This is the same idea as Simple Gmail Notes (sidecar notes on a mail), not Google Keep.

## What it does not do yet

OpenPGP, S/MIME, push IDLE in the background, HTML compose, attachments, filters, and a full FairEmail settings tree. Gmail OAuth through a Google Cloud client ID is not used; microG/Play account tokens are.

## Licence

FairEmail and NetGuard are products of FairCode B.V., GPL-3. See [THIRD_PARTY_NOTICES.md](https://github.com/koukomin/Telos-Launcher/blob/main/THIRD_PARTY_NOTICES.md).
