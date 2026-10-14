# Password-protected local files

For a single ordinary local file, select **Encrypt file** to create a separate `.telos-aes` file. A password of at least eight characters and confirmation are required. **Decrypt file** creates a separate original-format file. Existing destinations are never overwritten, and the source is never deleted. Remote files, archive entries, vault contents and root-only paths are not part of this workflow.

Passwords stay in memory and are not saved in preferences, task history or backups. The dialog blocks screenshots. There is no password recovery and no biometric unlock for this format. Keep the source until you have verified that the encrypted result can be decrypted.

Telos uses an original, versioned authenticated format, not ZIP AES, EncFS, gocryptfs or Cryptomator. It is intended for Telos-to-Telos use. It has not undergone an independent security audit.

## Format version 1

The 52-byte header contains `TELOSAES`, a 32-bit version, a 32-bit PBKDF2 work factor, a random 16-byte salt, a random 8-byte nonce prefix, a 32-bit chunk size and a 64-bit plaintext length. Integer fields are big endian. Version 1 uses PBKDF2-HMAC-SHA256 with 600,000 iterations to derive a 256-bit AES key and fixes the chunk size to 65,536 bytes. Unsupported versions and work factors are rejected before running key derivation.

Each record contains a 32-bit plaintext length, ciphertext and a 16-byte AES-GCM authentication tag. Its 12-byte nonce consists of the file's random nonce prefix and the 32-bit record index. The complete header, record index and plaintext length are authenticated as additional data. The authenticated zero-length terminal record is mandatory, and trailing data is rejected. These checks detect corruption, wrong passwords, reordered chunks, truncated files and appended data.

Encryption and decryption use bounded chunk buffers. Decrypted chunks are verified before writing to a unique temporary directory under the application's private, no-backup storage. Nothing is copied to the public output path until the entire file, including its terminal tag, has authenticated. Ordinary failures or cancellation remove temporary work; a process interruption may leave private temporary files until application data is cleaned.

Final output is created exclusively (`CREATE_NEW`) and streamed from the verified temporary file. This supports shared/emulated storage but **is not an atomic publish**: power loss during this last copy can leave an incomplete destination. The source remains intact. Cancellation attempts to remove only the destination with the filesystem identity created by the operation; on filesystems without stable identity it leaves the incomplete output rather than risk deleting another application's replacement.

## Ελληνικά

Η **Κρυπτογράφηση αρχείου** δημιουργεί ξεχωριστό αρχείο `.telos-aes` με κωδικό και έλεγχο αυθεντικότητας AES-256-GCM. Η **Αποκρυπτογράφηση αρχείου** δημιουργεί ξεχωριστό αρχείο στην αρχική μορφή. Το αρχικό διατηρείται και δεν αντικαθίστανται υπάρχοντα αρχεία.

Ο κωδικός δεν αποθηκεύεται και δεν υπάρχει ανάκτηση αν χαθεί. Η μορφή είναι ειδική για το Telos, όχι ZIP AES ή κρυπτογραφημένος φάκελος άλλης εφαρμογής. Η αποκρυπτογράφηση ελέγχεται πλήρως σε ιδιωτικό προσωρινό χώρο πριν δημιουργηθεί το δημόσιο αποτέλεσμα. Αν διακοπεί το ρεύμα κατά την τελική αποθήκευση, το αποτέλεσμα μπορεί να είναι ελλιπές· κράτησε το αρχικό μέχρι να το ελέγξεις.
