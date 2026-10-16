## Τι άλλαξε / What changed

### Ελληνικά

- Πλήρη ονόματα και ευανάγνωστες κάρτες στο Store με μεγάλες γραμματοσειρές. Πάτημα στην εφαρμογή ανοίγει τη σελίδα του project, χωρίς AI.
- Επιλογή εφαρμογής για dock/shutter με αποτελέσματα μόνο εφαρμογών και καθαρή αναζήτηση κάθε φορά. Κυλιόμενα μενού ενεργειών και σωστό κάτω περιθώριο στο Telos Media.
- Κανόνες Wi-Fi, δεδομένων κινητής, roaming και metered δικτύων: ανεξάρτητο προφίλ Downloader και κοινό προφίλ torrents / Video / Stremio.
- Εσωτερικό Tor στο Telos Network: επίλυση .onion μέσα στο VPN, προαιρετική εκκίνηση σε ορατά αιτήματα DNS και επιλογή εφαρμογών μόνο μέσω Tor. Οι επιλεγμένες εφαρμογές αποκλείονται όσο το Tor δεν είναι διαθέσιμο· UDP/QUIC δεν υποστηρίζεται.
- Web3 αναζήτηση ASCII ονομάτων με χωριστά αποτελέσματα ιστοσελίδας και wallet: .eth μέσω ENS Ideas / eth.limo και υποστηριζόμενα Unstoppable domains μέσω δικού σας HTTPS API proxy.
- Το Telos εξαιρείται από το VPN για να συνδέεται το ενσωματωμένο Tor. Η προστασία απαιτεί ενεργό VPN· για διακοπή του VPN χρειάζονται οι ρυθμίσεις Always-on/αποκλεισμού του Android. Δεν έχει ολοκληρωθεί δοκιμή σε πραγματική συσκευή.

### English

- Readable Store cards and full names with large fonts; app taps open source project pages without AI.
- App-only dock/shutter pickers with fresh searches, scrollable action menus and corrected Telos Media navigation-bar spacing.
- Wi-Fi, mobile-data, roaming and metered transfer rules: separate Downloader profile and shared torrent / Video / Stremio profile.
- Embedded Tor in Telos Network: onion DNS within the VPN, optional startup on visible DNS requests and Tor-only app selection. Selected apps block while Tor is unavailable; UDP/QUIC is unsupported.
- Read-only ASCII Web3 lookup separates websites and wallet addresses: .eth through ENS Ideas / eth.limo, supported Unstoppable names through your own HTTPS API proxy.
- Telos remains excluded from the VPN for embedded Tor relay access. Protection requires the running VPN; Android always-on/lockdown is needed across VPN stops. Physical-device validation remains outstanding.

---

## Which APK do I download?

Almost every phone made in the last ~8 years is 64-bit. **Download `telos-arm64-v8a.apk`** unless you know that your phone is old and 32-bit.

| File | For | How to know |
|---|---|---|
| **`telos-arm64-v8a.apk`** | All modern phones and tablets (64-bit ARM) | Choose this one if unsure |
| `telos-armeabi-v7a.apk` | Old 32-bit ARM phones, and some cheap phones with a 32-bit Android | Only if the 64-bit file says "App not installed" / "not compatible with your device" |

Install only one of them. They are signed with the same key, so you can later switch from one to the other without uninstalling. Updates inside **Telos Store** pick the right file for your phone automatically. Obtainium picks it automatically as well.

## Ποιο APK να κατεβάσω;

Σχεδόν όλα τα κινητά των τελευταίων ~8 ετών είναι 64-bit. **Κατέβασε το `telos-arm64-v8a.apk`**, εκτός αν ξέρεις ότι το κινητό σου είναι παλιό και 32-bit. Το `telos-armeabi-v7a.apk` είναι μόνο για παλιά 32-bit κινητά: αν το 64-bit λέει ότι δεν είναι συμβατό με τη συσκευή σου, δοκίμασε αυτό. Εγκατάστησε μόνο ένα από τα δύο.

---


- Tablet drawer: visible full-width setting and adaptive columns, preserving custom column counts.
- Συρτάρι tablet: ρύθμιση πλήρους πλάτους και αυτόματη προσαρμογή στηλών, με διατήρηση της χειροκίνητης επιλογής.
