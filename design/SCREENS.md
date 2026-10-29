# FieldStamp — UI inventory for redesign

Screenshots of every screen and state of the current Android app (Jetpack Compose, Material 3), captured on an
emulator at 1080×2400 px (420 dpi, ≈ 411×914 dp). UI language is Indonesian. Data shown is **sample data**
(fictional school "SD Negeri Contoh 01"); the camera image is the emulator's synthetic scene, not a real photo.

## What the app is
Offline-first field documentation for Indonesian field workers, project teams and civil servants (ASN):
take a photo → stamp it with time, place name, GPS, project/activity (optionally inside a Canva-made PNG frame)
→ organize by day/project → generate a monthly A4 PDF report on the phone. No account, no server.

Primary users work outdoors, often one-handed, in sunlight, on mid-range Android phones, sometimes offline.

## Navigation
Bottom bar: **Beranda** (Home) · **Foto** (library) · **Laporan** (reports) · **Setelan** (settings).
Capture is the main Home action and a FAB on Foto. Projects and templates are reached from Home ("Kelola")
and Settings. Camera → Review → back to Camera (rapid capture loop).

## Screen index

### Home and projects
| File | Screen / state |
| --- | --- |
| `01_home_populated` | Home: date masthead, capture context (project/activity), "Ambil foto" (primary), import, active template, today's photos, management links |
| `02_home_project_picker_sheet` | Project chooser bottom sheet (with "Proyek baru") |
| `03_projects_list` | Projects list |
| `04_project_detail` / `05_project_detail_menu` | Project detail with activities; overflow (edit / archive / delete) |
| `70_empty_home` | Home on first launch (no data) |
| `73_empty_projects` / `74_new_project_dialog` | Empty projects, create dialog |

### Camera and capture review
| File | Screen / state |
| --- | --- |
| `06_camera_with_template_guide` | Live camera with PNG frame guide, GPS chip (±m), flash, lens switch, amber shutter |
| `81_camera_no_template_location_not_allowed` | Camera, text-only stamp, location not yet allowed |
| `82_location_permission_system_dialog` | Android location permission dialog (system UI) |
| `78_camera_permission_system_dialog` | Android camera permission dialog (system UI) |
| `79_camera_permission_denied` / `80_camera_permission_permanently_denied` | Camera denied → retry / open settings |
| `44_capture_review_top` → `45_capture_review_form` → `46_capture_review_bottom` | Review after shutter: stamped preview, time, coordinates, place name (auto, editable), caption, category, project/activity/template pickers, Ulangi / Simpan |
| `83_capture_review_no_location` | Review when location permission is denied |
| `47_capture_discard_confirm` | Discard confirmation |

### Photo library
| File | Screen / state |
| --- | --- |
| `07_photos_grid` | Grid grouped by day, filter chips (month, project, no caption, no GPS), camera FAB |
| `08_photos_search_open` | Search field open |
| `09_month_picker_dialog` | Month filter dialog |
| `15_photos_selection_mode` | Multi-select (share / delete) |
| `52_photos_filter_no_results` | Filters with no matches |
| `71_empty_photos` | Library empty |
| `10_photo_detail_stamped` / `11_photo_detail_original` | Detail with Stamped/Original toggle |
| `12_photo_detail_metadata` | Metadata list (time, zone, source, project, activity, place, coordinates, template, size) |
| `13_photo_detail_menu` | Save to gallery / save as file / open map / delete |
| `14_photo_edit_sheet` | Edit sheet (re-renders the stamp from the original) |

### Templates (PNG frames)
| File | Screen / state |
| --- | --- |
| `16_templates_list` / `16b_templates_list_pro` | Templates: Canva how-to notice, basic text stamp, imported frames, free-tier limit notice |
| `75_templates_basic_only` | Only the built-in text stamp |
| `17_basic_stamp_editor` | Editor for the text-only stamp |
| `18_template_editor_top` | Editor: live preview with sample data, name |
| `19_template_editor_fields` | Field list (checkbox + reorder) |
| `20_template_editor_custom_box` / `21_template_editor_text_backdrop` | Position chips incl. "Atur sendiri" + box sliders, text size, color, backdrop (Gelap / Warna template / Tanpa), 24-hour |
| `22_template_import_system_picker` | System file picker (Android UI) |
| `23_template_jpg_whitekey_dialog` | JPG/opaque image → "make white area transparent?" |
| `24_template_editor_after_whitekey` | Editor after converting a JPG frame |
| `24b_editor_discard_confirm` | Discard unsaved template |
| `25_template_import_error_dialog` | Unusable file explanation |
| `26_paywall_from_template_limit` | Paywall reached from the free template limit (offline state) |

### Reports
| File | Screen / state |
| --- | --- |
| `27_reports_list` / `72_empty_reports` | Report list (status icon + text) / empty |
| `28_report_create` / `28b_report_create_month_picker` / `76_report_create_no_photos` | Create: period, project/activity filter, title, matching-photo count |
| `29_report_builder_photos_tab` | Builder "Foto" tab: day groups, reorder arrows, item menu, excluded photos |
| `30_report_item_menu` / `31_report_caption_dialog` / `32_report_date_dialog` | Report-only caption; move to another day (cross-midnight) |
| `33_report_toolbar_menu` / `34_report_delete_confirm` | Delete report |
| `35_report_settings_tab_top` / `36_report_settings_tab_bottom` | "Pengaturan": title, notes, prepared by, toggles, layout (Pro), branding (Pro) |
| `37_report_pdf_tab` | "PDF": open / share / save / print, ZIP export (Pro), integrity note |
| `38_report_regenerate_confirm` / `39_report_pdf_generated_snackbar` | Regenerate confirmation; success |
| `77_report_builder_empty` | Draft with no photos |

### Settings, Pro, privacy
| File | Screen / state |
| --- | --- |
| `40_settings_top` / `41_settings_middle` / `42_settings_bottom` | Capture settings (location, EXIF GPS, auto place name), report defaults, branding, Pro, storage, about |
| `43_settings_pro_debug_on` | Debug-only Pro test mode (not in release) |
| `49_paywall_pro_active` | Paywall when Pro is active |
| `50_privacy` / `51_privacy_bottom` | Privacy & data explainer |

### Dark theme
`60_dark_home`, `61_dark_photos`, `62_dark_photo_detail`, `63_dark_report_builder`, `64_dark_report_settings`,
`65_dark_settings`, `66_dark_templates`, `67_dark_template_editor`.

### Outputs (what users actually hand in)
`outputs/stamped_photo_school_template.jpg` (stamped photo with a school frame + custom text box),
`outputs/report_pdf_page1-1.png` and `report_sample.pdf` (A4 report), `outputs/template_input_school_frame.png`
(a user's Canva frame), `outputs/app_icon_512.png`, `outputs/feature_graphic.png`.

## Current design system (to replace or evolve)
- **Color:** primary "survey ink" teal `#0E5A6B` (dark: `#7FC8D8`); hi-vis amber `#F2A900` reserved for capture;
  warm paper neutrals `#F7F7F5` / dark `#111416`; status shown as icon + text, not color alone. No dynamic color.
- **Type:** IBM Plex Sans (UI), IBM Plex Mono (time, coordinates). Bundled (OFL).
- **Spacing:** 4-dp scale, 16-dp gutter, 48-dp touch targets. **Shape:** radii 4–16 dp, lists over cards.
- **Components:** top bar, list row, switch row, metadata row, section header, notice (info/warning/error),
  empty/error/loading states, confirm dialog, photo thumbnail with "file missing" tile, Pro badge, extended FAB.

## Constraints a redesign must keep
1. **Capture first.** One-tap access to the camera from Home; the shutter stays large, high-contrast, thumb-reachable.
2. **Outdoor legibility.** Strong contrast in sunlight; no low-contrast pastel text on photos; system font scaling.
3. **Honesty.** No invented stats or sample data presented as real; time/GPS are device data, never "verified";
   missing GPS/caption is shown neutrally, not as an error.
4. **Every state has a design:** loading, empty, populated, no results, error, permission denied (and permanently denied),
   offline (billing), Pro-locked.
5. **Accessibility:** labels for icon buttons, no color-only status, 48-dp targets, TalkBack-readable FABs.
6. **Indonesian copy** (keep strings; the redesign may shorten them). WIB/WITA/WIT time labels.
7. **Native Android:** Material 3 components where possible, edge-to-edge, bottom navigation, system pickers/dialogs
   (`22`, `78`, `82` are system UI and cannot be restyled).

## Known UX issues seen in these captures (good redesign targets)
- Template editor is one very long scroll (preview scrolls away while adjusting sliders, `19`–`21`); custom text box
  is positioned with four sliders instead of direct drag on the preview.
- Segmented button labels truncate when selected ("Warna template" with check mark, `20`/`21`).
- Long titles truncate in top bars ("Laporan Dokumentasi Kegiata…", `29`).
- Home shows both "Hari ini · 0 foto" and "Belum ada foto hari ini" (`70`): redundant.
- Report date picker renders in the device language (English month names, `32`) while the rest of the UI is Indonesian.
- Settings mixes toggles, text fields and navigation rows in one long list (`40`–`42`).
- Capture review places the form below a tall preview; on small screens the Save button competes with the keyboard.
- Photo grid time label overlays the thumbnail's bottom edge where stamp text also sits (`07`).
