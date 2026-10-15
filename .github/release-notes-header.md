## Τι άλλαξε / What changed

### Ελληνικά

- **Store — παγωμένες εφαρμογές:** προσωρινό ξεπάγωμα πριν από εγκατάσταση μέσω Shizuku/root και επαναφορά της καταγεγραμμένης κατάστασης μετά. Διορθώθηκε η ταυτότητα κλήσης root και η ανάγνωση αποτελέσματος του installer.
- **Store — εικονίδια:** εμφάνιση δημοσιευμένων εικονιδίων και για μη εγκατεστημένες εφαρμογές από τις υποστηριζόμενες πηγές. Τα εγκατεστημένα εικονίδια και icon packs διατηρούν προτεραιότητα.
- **Store — σφάλματα:** πλήρες, επιλέξιμο μήνυμα αποτυχίας εγκατάστασης, αντί για κομμένο toast.
- **Dialer:** αποτυχία ανάγνωσης της βάσης αποκλεισμένων αριθμών δεν κλείνει την οθόνη επαφής. Εμφανίζεται μη διαθέσιμη κατάσταση με επανάληψη. Ενημέρωση SQLCipher χωρίς διαγραφή βάσης ή αλλαγή του αποθηκευμένου κλειδιού.
- Ενημερωμένα README, docs, Pages και μεταφράσεις των αλλαγών.

**Αναβάθμιση:** αν το παλιό Store αποτυγχάνει, εγκαταστήστε χειροκίνητα το κατάλληλο APK παρακάτω πάνω από την υπάρχουσα εφαρμογή, χωρίς απεγκατάσταση.

### English

- **Store — frozen apps:** temporarily thaw before Shizuku/root installation and restore the recorded state afterwards. Correct root caller identity and drain installer output before closing the remote process.
- **Store — icons:** show published icons for uninstalled apps from supported sources, retaining installed icons and icon packs first.
- **Store — diagnostics:** full, selectable installation errors instead of truncated toasts.
- **Dialer:** failed encrypted block-list reads no longer close contact details; an unavailable/retry state is shown. Update SQLCipher while retaining the database and stored key.
- README, documentation, Pages sources and changed-string translations updated.

**Update:** if the older Store fails, manually install the matching APK over the existing app, without uninstalling.

Physical-device Shizuku installation and the reported contact crash still need confirmation on the affected device. The contact report did not include a stack trace, so its exact cause is unconfirmed. No database reset is performed.

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

