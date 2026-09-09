# Changelog

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
