# Telos Files: σύγκριση MiXplorer και Solid Explorer

Ημερομηνία ελέγχου: **14 Οκτωβρίου 2026**. Οι δυνατότητες των άλλων apps ελέγχθηκαν στις δημοσιευμένες λίστες των δημιουργών τους. Οι δυνατότητες του Telos ελέγχθηκαν στον κώδικα του ενεργού development branch· δεν πρόκειται για δοκιμή σε συσκευή ούτε για δήλωση πλήρους συμβατότητας.

Πηγές: [MiXplorer Silver – επίσημη καταχώριση](https://play.google.com/store/apps/details?id=com.mixplorer.silver), [MiXplorer – επίσημη ιστοσελίδα](https://www.mixplorer.com/), [Solid Explorer – επίσημη καταχώριση](https://play.google.com/store/apps/details?id=pl.solidexplorer2).

«—» σημαίνει ότι δεν τεκμηριώνεται στην επίσημη λίστα που ελέγχθηκε, όχι ότι αποδεδειγμένα απουσιάζει. «Μερικό» σημαίνει ότι υπάρχει συγκεκριμένος περιορισμός ή ότι η λειτουργία βρίσκεται σε άλλο Telos app.

## Πλοήγηση και εμφάνιση

| Δυνατότητα | MiXplorer | Solid | Telos στο development branch |
| --- | --- | --- | --- |
| Καρτέλες φακέλων | Ναι | Δύο πάνελ | Προστέθηκαν ανεξάρτητες καρτέλες |
| Δύο πάνελ | Ναι | Ναι | Προστέθηκαν· δίπλα-δίπλα σε μεγάλη οθόνη, κάθετα σε μικρή |
| Drag-and-drop μεταξύ πάνελ | Ναι | Ναι | Προστέθηκε εσωτερικό drag-and-drop με επιλογή αντιγραφής/μετακίνησης· κοινό Copy/Cut/Paste |
| Λίστα και πλέγμα | Ναι | Ναι | Υπάρχουν, με δύο μεγέθη previews |
| Ταξινόμηση/προβολή ανά φάκελο | Ναι | — | Προστέθηκαν, μαζί με επιλογή κρυφών αρχείων ανά φάκελο |
| Σελιδοδείκτες και breadcrumbs | Ναι | Ναι | Υπάρχουν |
| Προσαρμογή κουμπιών/μενού | Ναι | — | Προστέθηκε επιλογή έως δύο συντομεύσεων προβολής/ταξινόμησης/κρυφών/ανανέωσης· όχι πλήρης αναδιαμόρφωση όλων των μενού |
| Skins και icon sets | Ναι | Ναι | Χρησιμοποιείται το κοινό θέμα Telos· δεν υπάρχει ανεξάρτητος skin editor |
| Mouse/keyboard/trackpad | — | Ναι | Προστέθηκαν ειδικές συντομεύσεις καρτελών, αναζήτησης, επιλογής και αρχείων, και Backspace για τον γονικό φάκελο· η πλήρης περιήγηση με βελάκια/τηλεχειριστήριο TV δεν έχει δοκιμαστεί σε συσκευή |
| Ρυθμίσεις/σελιδοδείκτες export-import | Ναι | — | Προστέθηκε ασφαλές JSON export/import εμφάνισης, φακέλων, toolbar, σελιδοδεικτών και workspace έως 16 καρτέλες· χωρίς κωδικούς/συνδέσεις |

## Εργασίες και αναζήτηση

| Δυνατότητα | MiXplorer | Solid | Telos |
| --- | --- | --- | --- |
| Copy/move μεταξύ διαφορετικών storages | Ναι | Ναι | Υπάρχει streaming μεταφορά· περιορισμοί σε root destinations |
| Παράλληλες εργασίες | Ναι | — | Ανεξάρτητες εργασίες ανά καρτέλα· μία ενεργή ανά καρτέλα |
| Ουρά εργασιών, pause/resume, ιστορικό | Ναι για tasks | — | Προστέθηκε μόνιμη ουρά τοπικών μεταφορών με pause/resume, ιστορικό και ρητή συνέχεια μετά από restart· όχι ακόμη remote/archive/vault resume |
| Μαζική μετονομασία | Ναι | Ναι | Προστέθηκε: tokens, αρίθμηση, literal/regex αντικατάσταση, preview, έλεγχος συγκρούσεων |
| Recursive search | Προχωρημένη | Indexed | Υπάρχει τοπική/root και προστέθηκε remote subfolder search με όρια 5.000 entries/16 επιπέδων/60 δευτερολέπτων/300 αποτελεσμάτων· archive/vault τρέχουσα λίστα |
| Indexed search και φίλτρα μεγέθους/ημερομηνίας | Προχωρημένη | Ναι | Προστέθηκαν τύπος/επέκταση, μέγεθος και ημερομηνίες, με προσωρινό τοπικό index 1 λεπτού· όχι μόνιμος database index |
| Αυτόματες συλλογές αρχείων | Ναι | Ναι | Προστέθηκαν read-only συλλογές πρόσφατων/σελιδοδεικτών/τύπων, με περιορισμένο τοπικό index και ένδειξη μερικών αποτελεσμάτων |
| Storage analyzer | — | Ναι | Προστέθηκε για τοπικούς φακέλους: κατανομή ανά τύπο, μεγαλύτερα αρχεία, ακύρωση και όριο 50.000 entries |
| Εύρεση διπλότυπων, μεγάλων αρχείων/junk | — | Ναι | Προστέθηκαν μεγαλύτερα αρχεία και byte-verified διπλότυπα, με φακέλους που παραλείπονται (ονόματα ή διαδρομές)· δεν υπάρχει αυτόματο junk cleanup |
| Trash και undo | — | — | Προστέθηκε κοινός κάδος Files/Viewer/Media με επαναφορά και διατήρηση· μη υποστηριζόμενες πηγές έχουν μόνο ρητή οριστική διαγραφή |

## Storage, σύνδεση και διαμοιρασμός

| Δυνατότητα | MiXplorer | Solid | Telos |
| --- | --- | --- | --- |
| Εσωτερικός χώρος/SD/USB OTG | Ναι | Ναι | Μέσω Android volumes και Storage Access Framework |
| Δικός του USB filesystem driver | FAT32/exFAT/NTFS | — | Δεν υπάρχει· εξαρτάται από το Android/provider |
| SMB, SFTP, FTP/FTPS, WebDAV | Ναι | Ναι | Υπάρχουν clients |
| SMB1 συμβατότητα | Ναι | — | smbj για σύγχρονο SMB· όχι legacy SMB1 |
| Nextcloud/ownCloud | WebDAV | Ναι | Υπάρχουν |
| Google Drive/Dropbox/OneDrive | Ναι | Ναι | Άμεσοι clients με δικό σου OAuth client ID· εναλλακτικά system provider |
| Box/Yandex | Ναι | Ναι | Γενικό WebDAV όπου το παρέχει η υπηρεσία· όχι dedicated OAuth clients |
| Mega | Ναι | Add-on | Δεν υπάρχει direct client |
| MediaFire/SugarSync | Ναι | Ναι | Δεν υπάρχουν direct clients |
| pCloud/HiDrive/Mail.ru/4Sync/4Shared/Meo | Ναι | — | Δεν υπάρχουν dedicated clients |
| Cloud app ήδη συνδεδεμένο στο κινητό | — | — | Υπάρχει επιλογή system document provider, εφόσον το app εκθέτει φακέλους |
| FTP/HTTP/WebDAV/DLNA server | Ναι | FTP και web sharing | Προστέθηκε read-only HTTP share σε ιδιωτικό LAN όσο είναι ορατό το dialog· όχι FTP/WebDAV/DLNA server |
| MiX Send-to/TCP/Bluetooth transfer | Ναι | — | Android Share υπάρχει· όχι MiX-compatible peer/server protocol |
| Ανάδρομη αναζήτηση/ανάλυση remote storage | Προχωρημένη | Μερικά εργαλεία cloud | Προστέθηκε περιορισμένη recursive remote αναζήτηση· όχι πλήρης remote ανάλυση |

## Archives, ασφάλεια και viewers

| Δυνατότητα | MiXplorer | Solid | Telos |
| --- | --- | --- | --- |
| ZIP/7z/TAR δημιουργία/ανάγνωση | Ναι | Ναι | ZIP, 7z και tar.gz δημιουργία· περισσότερα TAR variants για ανάγνωση |
| AES-256 ZIP | Ναι | Encryption αναφέρεται ξεχωριστά | Υπάρχει |
| Encrypted ZIP/7z ανάγνωση | Ναι | — | Υπάρχει· password μόνο στη μνήμη |
| RAR/RAR5 | Ανάγνωση | Ναι | Ανάγνωση στο default flavor· όχι encrypted ή split RAR |
| Split ZIP/7z και τροποποίηση archive | Ναι | — | Split ZIP δημιουργία/ανάγνωση. Unencrypted zip γράφεται μέσα (νέο αρχείο, φάκελος, διαγραφή, μετονομασία). 7z/tar/ISO/RAR μένουν ανάγνωση |
| CAB/ISO/WIM/DMG/virtual disks και λοιπά 7-Zip formats | Πολλά formats | — | ISO 9660/Joliet (`iso`, `img`) ανοίγει ως φάκελος, ανάγνωση. Τα υπόλοιπα μόνο εικονίδιο archive |
| Standalone encryption/password/fingerprint | Aescrypt/containers | AES-256/fingerprint | Προστέθηκε chunked AES-256-GCM encrypted-file workflow με κωδικό· όχι fingerprint/container υποστήριξη |
| gocryptfs/EncFS containers | Ναι | — | Δεν υπάρχουν |
| Cryptomator | — | — | Υπάρχει read-only, experimental, τοπικό vault |
| Root file operations | Ναι | Ναι | Υπάρχουν, απαιτούν root· δεν έχουν device validation εδώ |
| Backup app data με root | Ναι | — | Δεν υπάρχει μέσα στο Files |
| APK signer | v1/v2/v3 | — | Δεν υπάρχει μέσα στο Files |
| Εικόνες/ήχος/video/PDF/EPUB/text | Ναι | Built-in media/text | Ανοίγουν στους Telos Viewer/Media· μέρος του ενιαίου οικοσυστήματος |
| HTML/MHT/CHM/MAFF και font viewer | Ναι | — | Δεν υπάρχει πλήρης αντίστοιχη κάλυψη |
| RAW/TIFF και ειδικοί codecs | Ναι/add-ons | — | HEIC/HEIF, camera RAW (embedded JPEG), HEVC/H.265 video thumbs στο ίδιο catalog με τον Viewer |
| FUSE mounts (ISO, archives) | Ναι | — | Χωρίς kernel FUSE: ISO και archives ως εικονικοί φάκελοι. Εγγραφή μόνο σε unencrypted zip |
| SMB browse με όνομα/IP | Ναι | Ναι | Hostname, `.local`, IP, `smb://host/share`, mDNS κοντινών υπολογιστών, λίστα shares |

## Σειρά υλοποίησης των υπολοίπων

1. Drag-and-drop, keyboard shortcuts και ασφαλής task queue με pause/resume.
2. Storage analyzer, byte-verified duplicate finder, virtual collections και advanced/indexed search.
3. Trash/restore και διαδικασία export/import των ρυθμίσεων χωρίς εξαγωγή κωδικών.
4. Web/FTP sharing server και remote search/analysis με όρια και cancellation.
5. Archive editing/split volumes και επιπλέον formats με πραγματικά fixtures.
6. Standalone encrypted-file workflow και πρόσθετοι cloud clients/containers.

Κάθε ομάδα χρειάζεται δικούς της ελέγχους. Οι νέες καρτέλες, τα πάνελ, οι ρυθμίσεις ανά φάκελο και η μαζική μετονομασία είναι η πρώτη υλοποίηση, όχι δήλωση ότι ολοκληρώθηκαν όλα τα παραπάνω. Το UI ακολουθεί Material/Telos και οι απαραίτητες μεταφράσεις προστίθενται πρώτα στα ελληνικά και στα αγγλικά.

## Σύγκριση με Voyager

Πηγή: [Voyager](https://github.com/AlanHuang99/Voyager). Ελέγχθηκε στον κώδικα, όχι σε συσκευή.

| Δυνατότητα Voyager | Telos |
| --- | --- |
| SFTP με ιδιωτικό κλειδί και έλεγχο host key | Ναι |
| Δημιουργία ζεύγους κλειδιών SSH | Προστέθηκε: κλειδί RSA 3072 που μένει κρυπτογραφημένο στο κινητό, με αντιγραφή του δημόσιου κλειδιού για το `authorized_keys` |
| Πρόσβαση μέσω Shizuku | Προστέθηκε: λειτουργία «Πρόσβαση μέσω Shizuku» στο μενού, για Android/data, Android/obb και φακέλους που δεν διαβάζει η εφαρμογή (χωρίς root) |
| Άμεση αναπαραγωγή από WebDAV/SFTP/SMB | Βίντεο και μουσική παίζουν απευθείας χωρίς κατέβασμα· τα υπόλοιπα αρχεία κατεβαίνουν πρώτα |
| Αναζήτηση σε όλους τους χώρους | Προστέθηκε επιλογή «Αναζήτηση σε όλους τους χώρους» (εσωτερικός, SD, USB) |
| Εξαιρέσεις στα διπλότυπα | Προστέθηκε |
| Πλοήγηση με τηλεχειριστήριο/πληκτρολόγιο | Μερικό: συντομεύσεις πληκτρολογίου και Backspace· δεν έχει δοκιμαστεί σε TV |
