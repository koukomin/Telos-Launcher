## Τι άλλαξε / What changed

### Ελληνικά

- Προετοιμασία Ironewolf: κλειδωμένος κώδικας IronFox, ξεχωριστό branding και υποδομή δρομολόγησης/καταχώρισης στον launcher.
- Τα κλειδωμένα web apps διατηρούν πλέον το αναγνωριστικό τους μετά την ταυτοποίηση, ώστε να χρησιμοποιείται το σωστό ξεχωριστό προφίλ.
- Διατηρούνται οι διορθώσεις του PR #35 για shutters, στοιχεία φακέλων και εκκίνηση frozen/suspended εφαρμογών.
- **Το Ironewolf δεν είναι ακόμη διαθέσιμο ως λειτουργικός browser.** Το Fenix runtime δεν περιλαμβάνεται σε αυτή την έκδοση. Το WebView παραμένει ενεργό· η νέα εγγραφή και η επιλογή μηχανής εμφανίζονται μόνο όταν ενσωματωθεί συμβατό runtime.

### English

- Ironewolf preparation: pinned IronFox sources, isolated branding and capability-gated virtual-app/renderer dispatch wiring.
- Locked web apps now preserve their saved shortcut key after authentication, retaining the correct isolated profile identity.
- Retains PR #35 fixes for shutters, folder children and frozen/suspended application launches.
- **Ironewolf is not yet a working browser in this release.** The Fenix runtime is not bundled. WebView remains active; the browser entry and engine selector only appear when a compatible embedded runtime is included.

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
