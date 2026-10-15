## Τι άλλαξε / What changed

### Ελληνικά

- **Store:** επιλογή τρόπου εγκατάστασης στις ρυθμίσεις: συνεδρία Android (προεπιλογή), Shizuku, root και διαθέσιμοι εξωτερικοί installers APK, όπως Thor/Island όταν εκθέτουν συμβατό χειριστή στο τρέχον προφίλ.
- **Αναβαθμίσεις:** Shizuku/root χρησιμοποιούν ξεχωριστά βήματα δημιουργίας, εγγραφής και ολοκλήρωσης session, με streaming του APK. Οι παγωμένες εφαρμογές ενημερώνονται με αποθήκευση και επαναφορά της αρχικής κατάστασης. Τα σφάλματα του Android session εμφανίζονται στο Store.
- **Dock:** κανονικά έγχρωμα εικονίδια χωρίς νιφάδα για frozen/suspended εφαρμογές και νέα αναζήτηση launcher activity μετά το ξεπάγωμα.
- **Freeze:** προαιρετική προστασία της εφαρμογής προσκηνίου, ανενεργή από προεπιλογή. Απαιτεί πρόσβαση χρήσης.
- **Συρτάρι:** ανεξάρτητη αδιαφάνεια/blur, απαλό ημιδιαφανές preset για νέες εγκαταστάσεις και πλήρες διαθέσιμο πλάτος στα tablets.
- **Ταπετσαρία GIF:** επιλογή κινούμενου GIF με προεπισκόπηση live wallpaper και παύση όταν δεν είναι ορατό ή ενεργοποιούνται οι σχετικές ρυθμίσεις εξοικονόμησης. Όριο εισόδου 32 MiB και 4 megapixels.
- **Ενσωματωμένες εφαρμογές:** σύντομα ονόματα στη γλώσσα του χρήστη χωρίς το «Telos». Η αναζήτηση διατηρεί `telos` και αγγλικά/ελληνικά ονόματα. Προτεραιότητα στα δικά τους εικονίδια και σταθερά εικονίδια στο Store/Recents.
- Μεταφράσεις σε όλες τις υποστηριζόμενες βασικές γλώσσες και ενημέρωση README, docs, Pages πηγών και credits του Aurora Store.

### English

- **Store:** selectable Android session installer (default), Shizuku, root and installed external APK handlers such as Thor/Island where supported in the current profile.
- **Updates:** explicit privileged create/write/commit sessions, bounded APK streaming and journaled frozen-state restoration. Android session failures now resolve the Store row and show diagnostics.
- **Dock:** normal-color frozen/suspended icons without snowflakes; resolve fresh launcher activities after thawing.
- **Freeze:** optional foreground-app protection, off by default, requiring Usage access.
- **Drawer:** independent opacity/blur, soft-glass defaults for fresh installs and full available width on tablets.
- **GIF wallpaper:** animated GIF live-wallpaper picker with visibility and configured power/thermal controls. Input limit: 32 MiB / 4 megapixels. Video playlist and speed controls remain video features.
- **Built-in apps:** localized short names without the Telos prefix, retaining `telos` and English/Greek search aliases; first-party icons and explicit Store/Recents artwork.
- Changed-string translations, README, docs, Pages sources and Aurora Store attribution updated.

**Εγκατάσταση / Installation:** αν το προηγούμενο Store αποτυγχάνει, εγκαταστήστε το κατάλληλο APK πάνω από την υπάρχουσα εφαρμογή χωρίς απεγκατάσταση. If the older Store fails, install the matching APK over the existing app without uninstalling.

Physical-device Shizuku/root updates, external installer callbacks and GIF/OEM visuals still need confirmation on the affected device. The drawer preset is inspired by modern launchers and does not claim exact proprietary OnePlus measurements. Third-party policy and Android profile restrictions still apply.

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
