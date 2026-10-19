# Ermis

Ermis is a native Telos mail client, with an inbox inspired by Gmail and an IMAP/SMTP workflow inspired by FairEmail. It does not open a webmail provider to read or send mail.

## Connect an account

Open **Mail accounts → Add account**. Enter your email address, password (or provider app password), IMAP server and SMTP server. The username defaults to your email address. IMAP uses verified TLS, normally port 993. SMTP supports implicit TLS (normally 465), or required STARTTLS (normally 587). Both servers are verified before the account is saved. Invalid certificates and plaintext connections are not accepted.

Gmail supports an app password and an optional **Google / microG sign-in** button. This opens Android's account chooser and requests the selected authenticator's mail-access token with user consent. It works only when that installed authenticator grants the requested mail scope to Telos; it is not a bundled Google OAuth client or a guarantee that Google will approve access. If the provider refuses or requests unsupported configuration, use an app password. OAuth accounts use XOAUTH2, and background checks request refreshed tokens from the authenticator; tokens are not saved in the mail vault. Credentials are encrypted with an Android Keystore key. Removing an account removes its local credentials and mail cache, not its messages on the server.

## Read and send

Use **All accounts** or an account chip to filter the inbox. Search matches cached sender, subject and preview text. Starred mail has a separate filter. **Sync** checks the server; periodic checks run through Android WorkManager and can be delayed by battery restrictions. Notifications require Android notification permission.

Open a message to read its text, reply, save a note or save an attachment using the Android document picker. HTML is converted to readable text; scripts and remote tracking images are not loaded. MIME depth, part count and body sizes are bounded. Opening a message also enforces a cumulative 24 MB raw multipart parsing budget across nested parts, so unusually large or deeply nested messages may be refused before their attachments can be listed. Saving an attachment uses the same selected MIME structure that produced the attachment list. The current inbox keeps a bounded recent-message cache; this is not a complete offline archive or a server-side search client. Notes are stored locally; Gmail accounts also synchronize note sidecars in the Ermis-Notes folder, keyed by Message-ID. Conversation grouping uses Message-ID, In-Reply-To and References within each account, rather than grouping unrelated mail with the same subject. Open a conversation and use its sender chips to move between cached replies. Folder management is not implemented.

**Compose** selects the sending account and accepts recipients, a subject, message and file attachments. The file picker can access document providers, including compatible cloud storage providers. Telos Files private connections are not automatically document providers. For large files, upload separately and paste the sharing link; Ermis does not silently upload attachments to a third party. A send action is explicit and reports server failures; a failed request is never automatically resent. Each outgoing message is limited to 20 MB of attachments in total and 15 MB per file. SMTP success is reported separately from a failed Sent-folder copy, so the user is not told to resend an already sent message.

The inbox and dialogs wrap text and scroll with larger fonts. They use the shared Telos glass theme.
