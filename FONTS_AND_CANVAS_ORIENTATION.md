# EDIT:KIRO template, fullscreen orientation and fonts

## New works

New works use the supplied simple circle template. The selected aspect ratio supplies the initial canvas dimensions. Responsive templates explicitly use `windowWidth`, `windowHeight` and `windowResized`; fixed templates retain their selected dimensions. The DVD-style bouncing `EDIT:KIRO` logo remains available as a bundled work at 4:3, alongside Axis, Halo and Gravity at 1:1. Existing saved source code is not replaced.

## Fullscreen orientation

Open the fullscreen preview's overflow controls and use the rotate icon to exchange the canvas width and height (for example, 960 x 540 becomes 540 x 960). This is a temporary fullscreen operation, not a change to the saved source or work ratio. Closing fullscreen restores the original canvas dimensions. Ordinary pane resizing still only scales the presentation.

This explicit command calls p5 `resizeCanvas`, so drawings should use `width` and `height` for responsive positioning. Hard-coded coordinates, custom render targets and cameras cannot be automatically rewritten. Paused sketches with `draw` are redrawn once; setup-only 2D sketches retain a fitted bitmap. Setup-only WebGL sketches should provide a `draw` callback to repaint after a canvas size change. Orientation cannot change during recording.

## Fonts

Settings > Appearance contains font import, a sample, reset to the default, and a ligature toggle. TTF, OTF and TTC files up to 20 MB are copied into private app storage and restored on app launch. The font is used by app typography and the code editor, not injected into artwork or the system keyboard. Unsupported glyphs depend on Android fallback fonts; TTC uses the default face.

Ligatures toggle OpenType `liga`, `clig` and `calt`. They only have a visible effect in supporting fonts; editing and copied code retain the original characters. Imported fonts are local installation settings and are not included in the existing work/settings ZIP backup; reimport the font on another installation.

## Verification

Run `node tests/runner.test.cjs` from the project root, then from `android`:

```sh
./gradlew testReleaseUnitTest assembleRelease lintRelease
```

Real-device checks should cover import/restart/reset, a coding-ligature font, long wrapped editor lines, narrow fullscreen controls, both orientations and paused/custom WebGL works.
