# Test report — 28 Oct 2026

Environment: Linux, JDK 17.0.20, Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21. Device: Android Emulator 36.5 running AVD
API 35 (google_apis x86_64, 1080×2400 @ 420 dpi, emulated cameras). No physical device was available.

## Automated

| Command | Result |
| --- | --- |
| `./gradlew :app:testDebugUnitTest --rerun` | **70 passed, 0 failed** |
| `./gradlew :app:connectedDebugAndroidTest` (emulator API 35) | **16 passed, 0 failed** |
| `./gradlew :app:lintRelease` | **0 errors**, 8 warnings (UseKtx style suggestions), 2 hints |
| `./gradlew :app:bundleRelease` | Success → `app/build/outputs/bundle/release/app-release.aab` (~8.0 MB, **unsigned**: no upload key in the repo) |
| `./gradlew :app:assembleRelease` (temporary throwaway key, since deleted) | Success, ~4.0 MB minified APK |

Unit suites (JVM; DB suite uses Robolectric with in-memory Room):
- `FormatsTest` (7): WIB/WITA/WIT labels, UTC-offset fallback, local date computed in the capture zone, locale-independent coordinates, period labels.
- `TemplateConfigTest` (5): JSON round trip, invalid/unknown JSON, normalization, clamping.
- `StampLayoutTest` (9): missing values omitted (no substitutes), field order/visibility, canvas size and aspect, center crop, no upscaling, sample size.
- `ReportGroupingTest` (13): month bounds incl. leap year, inclusive boundaries, same-day grouping, chronological and manual ordering, deterministic ties, empty days omitted, cross-midnight reassignment, flags (no caption / no GPS / missing file / deleted source), 1,200-photo grouping.
- `ReportLayoutEngineTest` (12): every photo placed once, nothing below the content area, aspect ratio preserved and centered, no orphaned day headings, continuation headings, rows never split, over-long caption truncation reported, column widths, empty report, optional signature block, 465-photo pagination.
- `EntitlementPolicyTest` (8): offline grace, expiry, future timestamps, revocation, debug override, template limit.
- `DatabaseTest` (16): date-range queries, project/metadata/search filters, SET NULL and CASCADE behavior, preselection order, exclude ≠ delete, deleted photo leaves a visible missing item, caption override isolation, report-date reassignment preserving the capture time, reordering, outdated-PDF marking, idempotent include, a photo in multiple reports, empty month, 600-photo dataset.

Instrumentation suites (real Android graphics, PDF and file I/O; isolated in-memory DB and temp directory):
- `ImagePipelineTest` (7): PNG transparency reveals the photo while the frame stays opaque; EXIF rotation applied; text block drawn; JPEG valid with EXIF time, offset and GPS; GPS omitted when absent or disabled; original untouched; opaque PNG and JPEG rejected as templates; imported template bytes unchanged.
- `ReportPdfTest` (7): multi-page PDF with several photos per day (A4 595×841, opened with `PdfRenderer`); all three layouts; generated PDF bytes unchanged after a photo edit until regeneration, snapshots refreshed and the old PDF removed on regeneration; missing source blocks generation with no partial file; empty report rejected; cancel leaves no PDF and duplicate starts are rejected; very long captions.
- `AppSmokeTest` (2): launches with no login and walks all four tabs; report creation opens the builder.

## Bugs found and fixed during verification
1. **Generator cancel before start** left the state stuck on "Running" (found by `cancellationLeavesPreviousStateIntact`). Fixed with `invokeOnCompletion`.
2. **Preview overflow**: portrait previews drew over the form in capture review, the template editor and photo detail. Fixed with fixed-height, clipped containers.
3. **Location never resolved** when the fused provider was idle. The one-shot request now queries all enabled providers in parallel.
4. **Extended FAB labels missing from the accessibility tree** (TalkBack would announce an unlabeled button). Fixed with `FsExtendedFab` exposing a content description.
5. Lint `LocalContextGetResourceValueCall` (7 errors). Fixed with `LocalResources`.
6. "±5 m" wrapped mid-value. Fixed with a non-breaking space.

## Manual / visual QA (emulator, 1080×2400)
Screenshots in `docs/store/screenshots/`. Checked spacing, clipping, hierarchy, contrast, light and dark themes.

| Flow | Result |
| --- | --- |
| First launch, no account | ✅ Home, empty states, no fake data |
| Create project and activity inline from Home | ✅ |
| CameraX preview, flash/lens/GPS controls, template guide overlay | ✅ (emulated camera) |
| Capture with GPS (injected fix), caption, save, "Foto tersimpan" feedback | ✅ |
| Capture without a fresh fix | ✅ saved without location; no stale or fabricated coordinates |
| Import Canva-style PNG through the system document picker, configure, save | ✅ |
| Library grouped by day, filters, flags | ✅ |
| Report: create → builder → generate PDF → open/share/save/print actions shown | ✅ PDF inspected with `pdfinfo` and `pdftoppm` (2 pages, A4) |
| Airplane mode: app use, report creation; paywall shows "Google Play tidak dapat dihubungi" | ✅ |
| Process killed on the review screen → reopen restores photo, caption and location | ✅ |
| Minified release build: capture, template editor, report PDF | ✅ no crashes |
| Dark theme (Home) | ✅ |

Not verified: camera and location **permission-denied** screens (permissions were granted with adb); photo import
through the Photo Picker (template import through the document picker was verified); share targets; printing to a
printer; real Play purchases; API 26–29 devices (including the pre-API 30 `requestSingleUpdate` location path);
TalkBack walkthrough; font scaling at 200%; tablets.

## Update — place names (reverse geocoding)
- `./gradlew :app:testDebugUnitTest` → **77 passed** (new `PlaceNameFormatterTest` ×6, stamp place-name test).
- `./gradlew :app:connectedDebugAndroidTest` (emulator API 35) → **17 passed**, run twice. Includes the new
  `MigrationTest` (v1 → v2 keeps data and adds a NULL place name) and a PDF snapshot containing the place name.
- Emulator end to end: a capture with a GPS fix auto-filled the place name from Google's geocoder, it appeared on the stamp, and the field stayed editable.
- Real phone (Xiaomi 13T, Android 16 / HyperOS 3): the debug build is installed and an earlier capture produced a correct
  stamped JPEG (upright, EXIF time +07:00, GPS). Automated UI driving and instrumentation on that phone are blocked by
  HyperOS until "USB debugging (Security settings)" is enabled.

## Update — JPG / white-window templates
- Template picker accepts any image (files were grayed out before when they were not PNG).
- `./gradlew :app:testDebugUnitTest` → **81 passed** (new `WhiteKeyerTest` ×4: largest region only, white text preserved, JPEG noise, soft edges, gray window rejected).
- `./gradlew :app:connectedDebugAndroidTest` (emulator API 35) → **19 passed**, including real JPG → keyed PNG → composited photo, and a gray window reported as "no white area".
- Bug found by the new test and fixed: the staged copy was deleted before conversion could use it.
- Emulator UI: JPG selectable in picker → conversion dialog → editor preview shows transparent window with intact white header text.

## Update — custom text box over template panels
- `./gradlew :app:testDebugUnitTest` → **88 passed** (new `CustomBoxTest` ×7: fit-to-box sizing, clamping, dominant color, legacy config compatibility, JSON round trip).
- `./gradlew :app:connectedDebugAndroidTest` (emulator) → **20 passed**, incl. pixel test: solid box hides the template underneath, template outside the box unchanged, text drawn inside.
- Emulator UI with the user's school frame: "Atur sendiri" plus sliders, "Warna template" and black text; placeholders covered, data inside the panel.
- Also fixed: the template preview spun forever when its cached background file was truncated (now regenerated atomically; failures show a message).

## Known limitations and follow-ups
- Custom box is positioned with sliders (no drag handles yet). Each field is one line in a single block; fields are not aligned to individual icon rows.
- White-key conversion needs the photo area to be plain white and the largest white area in the design. A design with a bigger white margin than the window would key the margin instead (visible in the preview before saving).
- Place names need internet; offline captures keep an empty field (fill it by hand, or use the lookup button in photo detail later). Existing saved
  stamp configurations get the new "Nama lokasi" field **disabled** (normalization rule). New/default configs have it enabled.
- **Billing is unverified end to end.** It needs the Play Console product and a testing-track install. There is no
  server-side receipt validation; optional on-device RSA verification only.
- Indonesian UI only (strings are in resources, ready for `values-en`).
- PDF generation runs in the app process. If Android kills it mid-run, nothing is saved and the user retries. PDFs use
  `PdfDocument`, which keeps pages in memory; tested up to 30 photos on device. Reports over 300 photos show a warning.
  Test 300+ photos on a low-RAM device before release.
- Reports are monthly in the UI (arbitrary date ranges are supported by the data model only). No calendar view of days
  without photos.
- Template text is a single configurable block (5 anchor positions); no free placement or per-field styling.
- No duplicate detection. No ZIP export from the photo library (ZIP is per report, Pro).
- Auto Backup is disabled on purpose; users must export important data. Consider an explicit backup/export-all feature.
- Store screenshots use the emulator's synthetic camera scene and must be retaken.
