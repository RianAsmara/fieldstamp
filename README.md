# FieldStamp

Offline-first Android app for standardized field photo documentation and monthly PDF activity reports.
Built for field workers, project teams, and Indonesian civil servants (ASN). No account, no backend, no analytics.

- Capture with CameraX; stamp date/time, optional GPS, project, activity, caption.
- Apply transparent PNG templates (e.g. designed in Canva). The PNG is never modified.
- Organize by project/activity/date; search and filter the local library.
- Build monthly reports: photos grouped by capture date, reorder, report-only captions, render an A4 PDF on-device.
- Freemium: one-time "Pro" in-app purchase via Google Play Billing; free tier is fully usable offline.

UI language: Indonesian.

## Requirements

| Tool | Version used |
| --- | --- |
| JDK | 17 |
| Android SDK | Platform 37 (compile), 36 (target), Build-Tools 36+ |
| Gradle | 9.8.2 (wrapper) |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 |

`local.properties` must point `sdk.dir` at your Android SDK.

## Common commands

```bash
./gradlew :app:assembleDebug                 # debug APK (app id suffix .debug)
./gradlew :app:testDebugUnitTest             # JVM + Robolectric tests
./gradlew :app:connectedDebugAndroidTest     # instrumentation tests (device/emulator required)
./gradlew :app:lintRelease                   # Android lint
./gradlew :app:bundleRelease                 # release AAB (signed if keystore.properties exists)
```

Outputs: `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/bundle/release/app-release.aab`.

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): modules, data model and migrations, image pipeline, report engine, design system.
- [docs/RELEASE.md](docs/RELEASE.md): signing, billing setup, Play Console steps, Data Safety, release checklist.
- [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md): privacy policy text (host it before publishing).
- [docs/STORE_LISTING.md](docs/STORE_LISTING.md): store copy (Indonesian and English) and asset checklist.
- [docs/TEST_REPORT.md](docs/TEST_REPORT.md): what was run, results, visual QA, known limitations.
