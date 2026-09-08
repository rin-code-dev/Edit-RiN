# EDIT:KIRO ver1.0.2

A p5.js editor for making art on Android.

[Download](https://github.com/rin-code-dev/EDIT-KIRO/releases) · [Changelog](CHANGELOG.md) · [Issues](https://github.com/rin-code-dev/EDIT-KIRO/issues)

- Code editing and live preview
- Fullscreen, screenshots and recording
- Saved works and backups
- Per-work images, audio, fonts and data: [Asset guide](ASSETS.md)
- Import public works from a p5.js Web Editor account
- Per-work p5.js 2.3.3 / 1.11.5 runtime selection
- p5.sound playback, synthesis and analysis
- Custom themes and fonts
- Japanese, English and Chinese UI

Requires Android 6.0 or later.
Checks for stable updates silently at startup and only notifies when an update is available.
“Hide preview while editing” is enabled by default to free up portrait editing space.

## Build

JDK 17 + Android SDK 35

```sh
cd android
./gradlew assembleDebug
```

See [RELEASE.md](RELEASE.md) for signed builds.

## License

Modification, reuse or redistribution requires prior contact, permission and attribution to rin-code-dev. See [LICENSE](LICENSE).

[Privacy](PRIVACY.md) · [Third-party notices](THIRD_PARTY_LICENSES.md)

## 日本語

Androidでp5.jsの作品を書いて、その場で動かせるアプリです。正式版 ver1.0.2。作品ごとにp5.js 2.3.3 / 1.11.5を選択でき、p5.soundにも対応します。

© 2026 rin-code-dev · Made with p5.js and Jetpack Compose.
