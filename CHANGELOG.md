# Changelog

## 1.0.9 — 2026-09-24

- Complete names declared in the current work's JavaScript files, with the current file first.
- Defer full-document highlighting, folding and project symbol scans during continuous typing in large works.
- Add per-work live numeric and color parameters declared in JavaScript comments, with saved values and backup support.
- Add an in-app user guide for the complete workflow in English, Japanese and Simplified Chinese.

## 1.0.8 — 2026-09-22

- Add offline p5.brush support with per-work selection and backup support.
- Add a Custom theme with adjustable background and accent colors.
- Open Work settings from the toolbar to manage files, assets, and runtime options.
- Search across all JavaScript files in a work and jump to matching code.
- Jump from console errors to the corresponding JavaScript file and line.
- Fold and unfold multiline JavaScript blocks in the editor.
- Improve editor responsiveness, transitions, and support for high-refresh-rate displays.
- Reduce memory use when importing assets and improve saving and restoring works.
- Fix JavaScript tabs after restoring works and support reloading locally saved works.
- Protect external-folder work files with a verified pending copy and a recoverable previous copy.
- Validate backup assets in temporary storage before adding them to the app's asset library.
- Separate work persistence and preview run state from the main activity.

## 1.0.7 — 2026-09-13

- Add an About section for app information, update checks, developer links and optional support.
- Remove favorites from the work-selection gallery.
- Reduce recording transfer memory use and improve recording settings, progress and saved-media sharing.
- Limit thumbnail caches and improve layouts for landscape and larger text.
- Share a distinct recording attachment with X and validate it before opening the app.

## 1.0.6 — 2026-09-13

- Improve MP4/GIF recording and add configurable capture and sharing options.
- Add the corrected monochrome `/R\_` icon and ensure X shares the latest recording.

## 1.0.5 — 2026-09-13

- Browse works in a thumbnail gallery with search, sorting and favorites.
- Edit multiple JavaScript files using tabs.
- Export and import individual works with their assets and runtime settings.
- Preview assets and insert loading code into the editor.
- View changes before restoring a saved revision.
- Resize the console and see parameter hints for p5.js code completion.
- Improve landscape layouts and responsiveness.

## 1.0.4 — 2026-09-10

- Migrate to Gradle 9.5.0 and Android Gradle Plugin 9.3.2 with built-in Kotlin and Compose compiler 2.2.10.
- Update license generation for Gradle 9's Groovy XML package.

- Unify settings and work panels with the editor's outlined surfaces and compact headers.
- Add category navigation in portrait settings and adapt wide layouts to available width and font size.
- Keep work-setting dialog content scrollable with visible actions and explicit theme colors.
- Adapt runtime selection, asset management, and account import to short screens and larger text.

## 1.0.3 — 2026-09-09

- Renamed the app and project from Edit:KIRO to Edit:RiN.
- Kept the Android application ID and signing identity so existing installations can update normally.
- Fixed missing text in the runtime settings dialog in landscape orientation.
- Added scrolling and explicit theme colors to the runtime settings dialog.
- Updated the backup filename, bundled sample, documentation, update checker, and GitHub links for Edit:RiN.

## 1.0.2 — 2026-09-09

- Fixed preview startup with p5.js 2.x.
- Kept p5.sound off by default and started audio on the first preview interaction when enabled.
- Removed the visible audio-start prompt so the behavior works consistently in every language.
- Fixed fullscreen touch coordinates while preserving the logical canvas size.
- Added compatibility coverage for p5.js 1.x and 2.x, p5.sound, 2D, WebGL, and resized touch input.

## 1.0.1 — 2026-09-08

- Added per-work p5.js 2.3.3 and 1.11.5 selection.
- Added p5.sound 0.4.1, audio assets, synthesis, and analysis.
- Added public-work import from p5.js Web Editor accounts.
- Detected p5.js and p5.sound settings from imported projects.
- Fixed loading `works.json` files with newer positive format version numbers.

## 1.0.0 — 2026-09-08

- First stable release.
- Added code editing, live preview, saved works, backups, screenshots, and recording.
- Hid the portrait preview while editing by default.
- Added silent startup update checks that appear only when a newer stable release exists.
- Added Japanese, English, and Chinese interfaces.

## Beta history

### 1.0.0-beta.4

- Added update checks in Settings.
- Simplified the README and consolidated update history.

### 1.0.0-beta.3

- Updated the four bundled sketches and the simple circle starter.
- Set dark mode as the default and tidied Settings.
- Fixed controls in narrow previews.

### 1.0.0-beta.2

- Updated author credits and licensing.
- Improved icons and title layout.
- Prepared distribution builds and license notices.
