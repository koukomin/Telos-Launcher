## Τι άλλαξε / What changed

### Ελληνικά

- **Media:** μία μπάρα σε Μουσική και Ραδιόφωνο. Τίτλος και χειριστήρια ενσωματωμένων players στην αρχική, ξεχωριστές ειδοποιήσεις και προτεραιότητα στον player που παίζει. Οι αποκλεισμοί player παραμένουν σεβαστοί.
- **Βίντεο, TV και Stremio Fork:** φωτεινότητα αριστερά, ένταση δεξιά, εικονίδια/ποσοστά και back πάνω αριστερά που σταματά την αναπαραγωγή. Ξεχωριστή ελαχιστοποίηση στην TV. Η φωτεινότητα παραθύρου επανέρχεται στην έξοδο.
- **Freeze:** διορθώσεις νιφάδας/ασπρόμαυρου, icon packs, εκκίνησης με ένα πάτημα και μεταβάσεων Απενεργοποίηση/Αναστολή. Έλεγχος πραγματικής κατάστασης και μαζικό ξεπάγωμα/επαναφορά αποθηκευμένων ομάδων, ακόμη και για εφαρμογές παγωμένες από άλλα εργαλεία.
- **Store:** προαιρετική αναβάθμιση frozen εφαρμογών μέσω Shizuku/root ή υποστηριζόμενων καταστάσεων Device Owner, με αποθήκευση και επαλήθευση επαναφοράς. Εγκατάσταση ιδιωτικών APK μέσω streaming, χωρίς φόρτωση ολόκληρου APK στη μνήμη. Το απλό Device Admin δεν αρκεί.
- Ενημερωμένα README, docs, Pages και μεταφράσεις των αλλαγών.

**Πρώτη αναβάθμιση:** αν το παλιό Store αποτυγχάνει με `pm install` / code 255, εγκαταστήστε χειροκίνητα το κατάλληλο APK παρακάτω πάνω από την υπάρχουσα εφαρμογή, χωρίς απεγκατάσταση.

### English

- **Media:** one mini player per Music/Radio page; built-in metadata and controls on the home screen, separate notifications and priority for playing sessions, respecting player exclusions.
- **Video, TV and Stremio Fork:** brightness-left/volume-right rails, icons/percentages and stop-and-back at the top left. Separate TV minimization; window brightness restored on exit.
- **Freeze:** consistent snowflake/grayscale and icon packs, one-tap launch recovery, verified Disable/Suspend transitions and actual-state scans with durable bulk unfreeze/restore, including apps frozen by other tools.
- **Store:** opt-in frozen-app updates with authorized Shizuku/root or supported Device Owner states, journaled restoration and bounded private-APK streaming. Plain Device Admin is insufficient.
- README, documentation, Pages and changed-string translations updated.

**First update:** if the older Store fails with `pm install` / code 255, manually install the matching APK over the existing app, without uninstalling.

Automated tests and the debug build passed. Device-level visuals, audio-session switching and privileged freeze/update integration remain untested. Bulk maintenance covers the current profile and excludes protected core packages. Android 14+ preserves disabled state where supported; older/OEM installers may enable an app during commit before restoration.

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

