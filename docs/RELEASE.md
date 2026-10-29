# Release guide

## 1. Identity and versioning

| Item | Value | Where |
| --- | --- | --- |
| Package name | `id.asmaraputra.fieldstamp` (debug: `.debug` suffix) | `app/build.gradle.kts` |
| versionCode / versionName | 1 / 1.0.0 | `app/build.gradle.kts` |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 | `app/build.gradle.kts` |

The package name is new, not taken from an existing Play listing. Check that it is available in Play Console
before the first upload; it cannot change after publishing. Bump `versionCode` for every upload.

Target SDK: Google Play requires API 36 for new apps and updates from 31 Aug 2026 (extension possible to
1 Nov 2026), checked on 28 Oct 2026 at developer.android.com/google/play/requirements/target-sdk.
Re-check before each release.

## 2. Signing (Play App Signing)

1. Create an **upload key** (keep it and its passwords out of the repository):
   ```bash
   keytool -genkeypair -v -keystore ~/keys/fieldstamp-upload.jks -alias upload \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Create `keystore.properties` in the project root (git-ignored):
   ```properties
   storeFile=/home/you/keys/fieldstamp-upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
3. Build: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
   Without `keystore.properties` the bundle is built unsigned and Play will reject it.
4. On first upload, enroll in Play App Signing (Google holds the app signing key, you keep the upload key).

The release build uses R8 (minify + resource shrinking). Rules for kotlinx-serialization are in `app/proguard-rules.pro`.

## 3. Billing setup

1. Play Console → Monetize → Products → In-app products → create a **one-time, non-consumable** product
   with ID exactly `fieldstamp_pro` (or change `fieldstamp.proProductId` in `gradle.properties`).
   Set price and Indonesian/English title and description, then activate it.
2. Optional but recommended: copy the Base64 licensing key (Monetization setup → Licensing) into
   `~/.gradle/gradle.properties` as `fieldstamp.playLicenseKey=...` to enable on-device signature checks.
3. Billing only works for builds installed from a Play testing track by a licence-tester account.
   Test: purchase, cancel, slow/declined test cards (pending), refund/revoke → "Pulihkan pembelian".
4. App behavior when unconfigured: if the product is missing or inactive the paywall says "Pro belum
   tersedia"; offline it says Google Play can't be reached. No fake purchase path exists in release builds.

There is no server-side purchase verification (no backend by design). See limitations in TEST_REPORT.md.

## 4. Play Console checklist

- [ ] Create app: name FieldStamp, default language Indonesian (id-ID), app, free with in-app purchases.
- [ ] Privacy policy URL: host `docs/PRIVACY_POLICY.md` publicly (e.g. GitHub Pages) and paste the URL. **Not hosted yet.**
- [ ] App access: "All functionality available without special access" (no login).
- [ ] Ads: **No ads.**
- [ ] Content rating questionnaire: utility/productivity, no user-to-user content, no sharing of location with
      other users through the app, no gambling or violence. Expected rating: everyone / 3+.
- [ ] Target audience: 18+ (workplace tool); not designed for children.
- [ ] Data Safety: see section 5.
- [ ] Permissions: CAMERA and foreground location only. No background location, no broad storage or media
      permissions, so no special permission declarations are needed.
- [ ] Government apps declaration: FieldStamp is **not** affiliated with or representing a government entity;
      answer accordingly and do not use agency names or logos in the listing.
- [ ] Store listing: copy in `docs/STORE_LISTING.md`; icon `docs/store/icon-512.png`; feature graphic
      `docs/store/feature-graphic-1024x500.png`; 2–8 phone screenshots (retake with realistic, non-personal photos).
- [ ] In-app product `fieldstamp_pro` created and active.
- [ ] Upload AAB to **Internal testing**; add testers; install from the Play link; run the smoke test in TEST_REPORT.md.
- [ ] Personal developer accounts created after Nov 2023 must run a **closed test with ≥ 12 testers for 14 days**
      before production access (check the current rule in Play Console).
- [ ] Production: staged rollout (e.g. 20%), watch Android vitals (crashes/ANRs), then 100%.

## 5. Data Safety (based on the actual implementation)

Reviewed against the code and dependencies in this repository (AndroidX, Kotlin, Coil without its network
module, Play Billing Library). No analytics, crash reporting, ads or network libraries are included.

| Question | Answer | Basis |
| --- | --- | --- |
| Does the app collect or share user data? | Photos, captions and reports: on device only. Coordinates: sent to the device geocoder when automatic place names are enabled (see Location). | No backend; network use = Play Billing + platform Geocoder |
| Location | **Precise location is transmitted** when "Nama lokasi otomatis" is on: coordinates go to the platform `Geocoder` (Google's geocoding service on GMS devices) to get a place name. Declare *Location → Precise location* as collected/shared for *App functionality*, processed ephemerally, optional (user can turn it off). **Confirm with Google's current guidance whether platform-Geocoder traffic counts as collection; declaring it is the conservative choice.** Otherwise location stays on the device. | `PlaceNameResolver`, `LocationProvider` |
| Photos | On device only | `FileStore` (internal storage) |
| Purchase history | Handled by Google Play Billing; the app keeps a local hash of the purchase token. Declare per Google's current guidance for Play Billing (usually not counted as developer collection; **verify in Play Console help at submission**) | `BillingManager` |
| User-initiated sharing (Share/Save/Print) | Not "sharing" under Play's definition (user-initiated) | `ExportManager` |
| Encryption in transit | Not applicable (no app data transmitted) | |
| Deletion | Users delete items in-app; uninstalling removes all data | |

Re-verify this table whenever a dependency is added.

## 6. Pre-release verification (run on the release build)

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintRelease :app:bundleRelease
```
Then install the release build from the internal testing track and check:
1. First launch offline, with no account.
2. Capture with GPS; capture with location denied.
3. Import a Canva PNG and capture with it.
4. Create a monthly report, reorder, edit a caption, generate, open, share.
5. Restart the app; data persists.
6. Purchase Pro with a licence tester, then restore.
