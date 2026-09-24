# Edit:RiN ver1.0.9

### Live parameters

Add these comments to `sketch.js` or another JavaScript file, then open Parameters from the preview actions. Values are available through `rinParams` and are saved with the work and its backup:

```js
// @rin number speed "Speed" 0 3 1 0.1
// @rin number strokeSize "Stroke size" 1 16 4 1
// @rin color ink "Color" #BA90E2

function draw() {
  background(20);
  stroke(rinParams.ink);
  strokeWeight(rinParams.strokeSize);
  circle(width / 2, height / 2, 80 + sin(frameCount * 0.02 * rinParams.speed) * 40);
}
```

A p5.js editor for making art on Android.

[Download](https://github.com/rin-code-dev/Edit-RiN/releases) · [Changelog](CHANGELOG.md) · [Issues](https://github.com/rin-code-dev/Edit-RiN/issues)

- Code editing and live preview
- Fullscreen, screenshots and recording
- Saved works and backups
- File tabs, revision comparison and single-work ZIP sharing
- Asset previews and loading-code insertion
- Per-work images, audio, fonts and data: [Asset guide](ASSETS.md)
- Import public works from a p5.js Web Editor account
- Per-work p5.js 2.3.3 / 1.11.5 runtime selection
- p5.sound playback, synthesis and analysis
- Custom themes and fonts
- Japanese, English and Chinese UI
- In-app user guide in English, Japanese and Simplified Chinese
- Live numeric and color controls for each work

Requires Android 6.0 or later.
Checks for stable updates silently at startup and only notifies when an update is available.
“Hide preview while editing” is enabled by default to free up portrait editing space.

## Build

JDK 25 + Android SDK Platform 35 + Build Tools 36.0.0.
Gradle 9.5.0 (Wrapper), Android Gradle Plugin 9.3.2, built-in Kotlin and Compose compiler 2.2.10.

```sh
cd android
./gradlew assembleDebug
```

See [RELEASE.md](RELEASE.md) for signed builds.

## Support

If you would like to support Edit:RiN development, optional tips are welcome on [OFUSE](https://ofuse.me/rincode).

## License

Modification, reuse or redistribution requires prior contact, permission and attribution to rin-code-dev. See [LICENSE](LICENSE).

[Privacy](PRIVACY.md) · [Third-party notices](THIRD_PARTY_LICENSES.md)

## 日本語

Androidでp5.jsの作品を書いて、その場で動かせるアプリです。
ver1.0.9。作品ごとにp5.js 2.3.3 / 1.11.5を選択でき、p5.soundにも対応しています。

© 2026 rin-code-dev · Made with p5.js and Jetpack Compose.
