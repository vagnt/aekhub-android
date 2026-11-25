# AEK Hub Android — οδηγίες για τον Claude

Εφαρμογή **Android** (Kotlin + Jetpack Compose) του aekhub.gr — αντίστοιχη του iOS (`vagnt/aekhub-ios`, SwiftUI).
Μιλάμε **ελληνικά** με τον χρήστη (Βαγγέλης, όχι προγραμματιστής): απλά, βήμα-βήμα.

Το αρχείο αυτό μένει **μικρό** (φορτώνεται σε κάθε μήνυμα = κόστος). Λεπτομέρειες/ιστορικό, αν χρειαστούν, σε `docs/claude/*.md` (διαβάζονται μόνο όταν αφορούν τη δουλειά)· εδώ μόνο ό,τι ισχύει παντού.

## Build & έκδοση
- Build ΜΟΝΟ στο GitHub Actions (`.github/workflows/build.yml`): κάθε push στο `main` → APK στα **Releases** (+ AAB για το Play Store όταν υπάρχει το κλειδί στα secrets). Μετά από push έλεγξε το run.
- **Κάθε έκδοση**: `versionCode` (+1) **και** `versionName` στο `app/build.gradle.kts`.
- Ροή: ξεχωριστό branch → pull request → ο χρήστης κάνει Merge.
- Μυστικά (keystore, κλειδιά) **ποτέ** στο repo — μόνο GitHub secrets.

## Κανόνες κώδικα
- **Ίδιο API με το iOS** (`https://aekhub.gr/wp-json/aekhub/v1/app/*`, `vagnt/aekhub-site` → `plugins/aekhub-app`). Μην αλλάζεις το API χωρίς να ελέγξεις ότι δεν σπάει το iOS.
- Δομή: `data/` (Api, Models, Prefs, Analytics) · `ui/screens`, `ui/components`, `ui/theme` · `push/` · `live/` · `widget/` · `ads/`.
- **Κανόνας τόνων**: **κεφαλαία** → χωρίς τόνους (`upperGr`)· πεζά μένουν με τόνους. Μην κάνεις κεφαλαία κείμενα που δεν ήταν.
- **Μεγάλα γράμματα συστήματος**: το UI μένει έως 100% (`AekTheme`), μόνο το κείμενο άρθρων μεγαλώνει (`LocalSysFont`)· τίτλοι/ονόματα με `FitText` (μικραίνει, δεν κόβει).
- Ό,τι αλλάζει στην εμφάνιση/λειτουργία, ο χρήστης συχνά το θέλει **ίδιο και στο iOS** — ρώτα αν πρέπει να γίνει και εκεί.
- Ό,τι δεν δοκιμάστηκε σε πραγματική συσκευή, το λες καθαρά στον χρήστη.
