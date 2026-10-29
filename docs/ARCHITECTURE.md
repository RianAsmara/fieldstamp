# FieldStamp architecture

## Shape

A single Gradle module (`:app`) with package-level boundaries. The app is small enough that multiple Gradle
modules would add build complexity without a practical benefit; packages mirror the suggested module split
so they can be extracted later.

```
app/                 Application, AppContainer (manual DI), MainActivity, routes, ViewModel helpers
core/model           Domain models, TemplateConfig (JSON), Formats (shared by UI, stamp and PDF)
core/database        Room entities, DAOs, database, mappers
core/storage         FileStore (app-owned files, atomic writes), ExportManager, PdfPrintAdapter
core/image           StampLayout (pure geometry/lines), StampRenderer (Canvas), BitmapIO (EXIF-aware decode)
core/location        One-shot foreground location (platform LocationManager)
core/permissions     Runtime permission state incl. "permanently denied"
core/billing         Entitlement policy (pure), BillingManager (Play Billing 9.1)
core/settings        DataStore preferences + cached entitlement
core/designsystem    Tokens (color, type, spacing) and shared Compose components
data                 ProjectRepository, PhotoRepository, TemplateRepository, PhotoProcessor
report               ReportRepository, ReportGrouping, ReportLayoutEngine, PdfReportRenderer, ReportGenerator, ZipExporter
feature/*            home, camera (capture + review), photos, projects, templates, reports, settings, paywall
```

Dependency injection is a hand-written `AppContainer`. ViewModels are created with `appViewModel { container, savedStateHandle -> ... }`.
UI state is exposed as `StateFlow` and collected with `collectAsStateWithLifecycle`. Business logic lives in
repositories and pure objects (`StampLayout`, `ReportGrouping`, `ReportLayoutEngine`, `EntitlementPolicy`), which is what most tests target.

### Library versions
Compose BOM 2026.09.00, Navigation 2.10.2 (type-safe routes), Room 2.8.5 + KSP 2.3.12, CameraX 1.6.2,
DataStore 1.3.0, Play Billing 9.1.0, Coil 3.6.3 (no network module), kotlinx-serialization 1.12.0.
compileSdk 37 (required by current AndroidX), targetSdk 36 (current Play requirement), minSdk 26.

## Data model (Room, schema v2)

Schema JSON is exported to `app/schemas/`. Images are never stored in the database: rows hold
**paths relative to the app files directory**, so records survive relocation of the data directory.

| Table | Key columns | Notes |
| --- | --- | --- |
| `projects` | id, name, description, createdAt, updatedAt, archivedAt? | Archive instead of delete for normal use |
| `activities` | id, projectId? → projects (SET NULL), name, description | Standalone activities allowed |
| `templates` | id, name, sourcePath, widthPx, heightPx, configurationJson, isDefault | PNG copied byte-for-byte; config stored separately |
| `photos` | id, projectId?, activityId?, templateId? (all SET NULL), source, originalPath, renderedPath?, thumbnailPath?, capturedAtEpochMillis, captureTimeZoneId, captureEpochDay, latitude?, longitude?, locationAccuracyMeters?, locationCapturedAtEpochMillis?, caption, category?, widthPx, heightPx | Indexed on captureEpochDay, capturedAt, projectId, activityId |
| `reports` | id, title, projectId?, activityId?, periodStartEpochDay, periodEndEpochDay, status (DRAFT/GENERATED/FAILED), notes, closingNotes, preparedBy, includeGps, includeSignatureBlock, layout, useBranding, useStampedPhotos, pdfPath?, generatedAt?, pdfOutdated, lastError? | |
| `report_photos` | id, reportId → reports (CASCADE), photoRecordId? → photos (SET NULL), reportEpochDay, displayOrder, captionOverride?, metadataSnapshot? (JSON) | Index (reportId, reportEpochDay, displayOrder) |

**Dates.** `captureEpochDay` is computed in the capture's own time zone at save time, so a later
zone change never moves a photo to another day. `report_photos.reportEpochDay` defaults to the capture day
and can be reassigned within the report period (cross-midnight work) without touching the capture timestamp.

**Deletion rules.**
- Deleting a project/activity/template clears the reference; photos stay.
- Excluding a photo from a report deletes only the `report_photos` row.
- Deleting a photo sets `photoRecordId = NULL`: the report shows an explicit "source deleted" entry and
  blocks generation until the user removes it. Nothing disappears silently.
- Deleting a report cascades its items and removes its PDF; photos stay.

**Migrations.** `exportSchema = true`; every schema change must bump the version, commit the new schema JSON,
and add an explicit `Migration` to `FieldStampDatabase.MIGRATIONS` with a `MigrationTestHelper` test.
v1 → v2 adds `photos.locationName TEXT` (covered by `androidTest/MigrationTest`).
Destructive fallback is deliberately not enabled.

## Files

```
files/photos/original/  untouched capture or imported copy
files/photos/rendered/  stamped JPEG (q90, long edge ≤ 2560 px, EXIF time/offset/GPS)
files/photos/thumb/     480 px thumbnail
files/templates/        imported PNG overlays
files/reports/          generated PDFs
files/branding/         organization logo
files/tmp/              in-progress writes, staged captures/imports, ZIP exports
```
Writes go to a temp file and are renamed into place; `.part` leftovers and temp files older than 24 h are
removed on startup, orphaned image files older than 1 h are pruned. Sharing uses `FileProvider`
(only rendered/original/reports/tmp are exposed). Auto Backup is disabled (sensitive data).

## Capture and image pipeline

1. CameraX `Preview` + `ImageCapture` bound to the screen lifecycle (released when the screen stops).
   The selected PNG is drawn over the preview at its own aspect ratio as a framing guide.
2. On shutter: device time + zone are recorded; a GPS fix is attached only if it is real and ≤ 2 min old.
3. Review screen (state in `SavedStateHandle` + route args, so it survives process death): caption,
   category, project/activity, template, include-location toggle, live low-res preview.
   Imports never use the current location; only the file's own EXIF GPS/time are used, and a missing
   EXIF time is disclosed.
4. Save: original moved into place → `StampRenderer` composes:
   photo (EXIF-oriented, cover-fit, decoded with `inSampleSize`) → PNG overlay (alpha preserved) →
   text block (configured fields; missing values omitted) → JPEG + EXIF → thumbnail → DB insert.
   Any failure deletes the files; nothing is recorded as saved.
5. Editing metadata re-renders from the original and replaces rendered/thumbnail files.

Canvas size: template aspect if a template is used, otherwise the photo's; long edge ≤ 2560 px and never upscaled.

**Template import.** Transparent PNGs are stored byte-for-byte. JPGs and opaque PNGs (e.g. free Canva, which cannot
export transparency) trigger an optional white-key conversion: `WhiteKeyer` clears only the **largest connected
near-white region** (the photo window, min 5% of the image, channels ≥ 232), so small white text and logos stay
opaque; pixels bordering the region fade by brightness to hide JPEG noise. The result is saved as a new PNG (≤ 3000 px);
the user's original file is untouched. The editor preview shows the composition before saving.

**Custom text box.** Besides five fixed anchors, a template can use `StampAnchor.CUSTOM` with a `StampBox` (left/top/width/height
as fractions). Text shrinks to fit (`StampLayout.fitTextSize`, floor 1.1% of the short edge) and is clipped with an ellipsis as a
last resort. `BackdropStyle.SOLID` fills the box opaquely with a color sampled from the template under the box
(`ColorSampler`), which hides placeholder text printed in the PNG (e.g. "[NAMA KEGIATAN]") without editing the PNG.
Older saved configs map `backdrop=true/false` to SHADE/NONE.

**Place name** (`PlaceNameResolver`): reverse geocoding with the platform `Geocoder` (free, no API key; Google-backed
on GMS devices, so it needs internet and sends coordinates). It runs automatically in the review screen when a fix is
available and *Nama lokasi otomatis* is on, can be triggered manually from photo detail, and is always user-editable.
Results are cached on a ~11 m grid. `PlaceNameFormatter` builds "street, kelurahan/desa, kecamatan, kabupaten/kota,
provinsi" and drops plus codes, postal codes and the country. Offline the field stays empty (never guessed).
Stored in `photos.locationName` (schema v2), snapshotted into reports, and shown on the stamp ("Lokasi: …"), in the PDF and in the ZIP manifest.

**Location** asks every enabled provider (fused, GPS, network) in parallel and takes the first real fix,
with a 15 s timeout. It does not depend on Google Play services and never requests background location.

## Report engine

- `ReportRepository.create` pre-selects all photos in the period/filters in chronological order.
- Builder: include/exclude, reorder within a day (`displayOrder`), report-only caption (`captionOverride`),
  move to another day, flags (no caption / no GPS / missing file / deleted source).
- `ReportGrouping`: groups by report date, orders by displayOrder → capture time → id (deterministic),
  omits empty days.
- `ReportLayoutEngine` (pure Kotlin, injectable `TextMeasurer`): A4 pagination with header, summary
  (photo count, documented days, per-activity breakdown), notes, daily sections in 1/2/3 columns, closing and
  signature block, a disclaimer, and a "page x of y" footer. Rules: headings are never orphaned, rows are never split,
  continuation pages repeat the day heading, images keep their aspect ratio, and over-long captions are truncated
  (reported, never overflowing).
- `PdfReportRenderer`: `android.graphics.pdf.PdfDocument`, Plex fonts, each image decoded at ~130 dpi for its
  box and released immediately.
- `ReportGenerator`: runs in the application scope, rejects duplicate starts, reports progress, and supports cancel.
  It validates every source first (missing → blocked), writes to a temp file, verifies `%PDF-`…`%%EOF`, then
  atomically promotes it. On success it stores snapshots and removes the superseded PDF. Failure or cancel keeps
  the previous PDF.
- **Snapshot/regeneration policy:** generation freezes caption, time/zone, names, category and coordinates into
  `metadataSnapshot`. Later edits mark the report `pdfOutdated` but never change the existing PDF. "Perbarui PDF"
  asks for confirmation and refreshes snapshots from current data.
- Optional ZIP export (Pro): JPEGs in `yyyy-MM-dd/` folders plus `manifest.csv` (UTF-8 BOM).

## Monetization

`EntitlementPolicy` (pure) defines the rules: free = 1 imported PNG template; Pro = unlimited templates,
report branding, large/compact layouts, ZIP export. Existing data is never locked.
`BillingManager`: one non-consumable INAPP product (ID from `gradle.properties`), pending purchases enabled,
acknowledge-not-consume, restore via `queryPurchasesAsync`, optional RSA signature verification
(`fieldstamp.playLicenseKey`). The cached entitlement records what Play last confirmed and expires after
30 days without re-confirmation; an authoritative "not owned" answer from Play revokes it. A debug-only toggle
exists for QA and is compiled out of release builds (`BuildConfig.DEBUG`).

## Design system

Principles: utilitarian and legible outdoors, honest (no invented data), lists over cards, one working color.

- **Color.** Primary "survey ink" teal `#0E5A6B` (dark theme: `#7FC8D8`). High-visibility amber `#F2A900`
  is reserved for capture actions. Warm paper neutrals (`#F7F7F5`; dark `#111416`). Dynamic color is off for
  predictable contrast. Status is always icon + text, never color alone.
- **Type.** IBM Plex Sans (UI) and IBM Plex Mono (time, coordinates, technical values), bundled (OFL).
  M3 scale: headlineSmall 22/28 semibold, titleMedium 16/22, bodyMedium 14/20, labelSmall 11/16.
- **Spacing.** 4 dp scale: 2, 4, 8, 12, 16, 24, 32. 16 dp gutter, 48 dp minimum touch targets.
- **Shape.** Radii 4/6/8/12/16 dp, used sparingly.
- **Components** (`core/designsystem/Components.kt`): FsTopBar, ListRow, SwitchRow, MetadataRow, SectionHeader,
  Notice (info/warning/error with icon), EmptyState, ErrorState, LoadingState, ConfirmDialog, PhotoThumbnail
  (explicit "file missing" tile), ProBadge, FsExtendedFab (label always exposed to accessibility services).
- **Navigation.** Bottom bar: Beranda, Foto, Laporan, Setelan. Capture is the primary Home action and a FAB on Foto.
  Projects and templates are reached from Home and Settings. There is no onboarding.
- **States.** Every data screen has loading, empty, populated and no-results (filters) states. Error, permission-denied,
  offline (billing) and Pro-locked states are implemented where relevant.
