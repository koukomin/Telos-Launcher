## Τι άλλαξε / What changed

### Ελληνικά

- Οι εφαρμογές που έχουν ανατεθεί σε shutters και τα στοιχεία φακέλων περνούν πλέον από την κοινή διαδικασία κλειδώματος και ξεπαγώματος πριν ανοίξουν.
- Η εκκίνηση παγωμένων εφαρμογών από συρτάρι και dock περιμένει να επιβεβαιώσει το Android ότι η εφαρμογή είναι ενεργή και δεν βρίσκεται πλέον σε αναστολή.
- Ενημέρωση README, οδηγού χρήσης και Pages. Απαιτείται εξουσιοδοτημένο backend για το ξεπάγωμα. Η συμπεριφορά στη συσκευή με το συγκεκριμένο πρόβλημα χρειάζεται επιβεβαίωση.

### English

- Shutter targets and folder children now share the app-lock and verified thaw-before-launch path used by launcher icons.
- Frozen-app launches from the drawer and dock wait for Android to report an enabled, unsuspended package before opening its launcher activity.
- Updated README, user guide and Pages source. Thawing requires an authorized backend. Physical-device confirmation is still required for the reported suspension dialog.

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
