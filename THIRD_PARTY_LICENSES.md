# Third-party software

Edit:RiN's own code and original icons are licensed under the GNU General Public License, version 3 (GPL-3.0-or-later).
Third-party software retains its own license terms.

## p5.js 1.11.5

p5.js is created by the Processing Foundation and p5.js contributors.
It is distributed under the GNU Lesser General Public License, version 2.1.

- Upstream: https://github.com/processing/p5.js/tree/v1.11.5
- License: third_party/licenses/p5-LGPL-2.1.txt
- Corresponding source and build scripts: third_party/sources/p5.js-v1.11.5-source.tar.gz
- Runtime: www/p5-v1.min.js (the Android project URL `p5.min.js` is a compatibility alias served from this file)

## p5.js 2.3.4 and WebGPU addon

p5.js is created by the Processing Foundation and p5.js contributors.
It is distributed under the GNU Lesser General Public License, version 2.1.

- Upstream: https://github.com/processing/p5.js/tree/v2.3.4
- License: third_party/licenses/p5-LGPL-2.1.txt
- Corresponding source and build scripts: third_party/sources/p5.js-v2.3.4-source.tar.gz
- Distribution: https://registry.npmjs.org/p5/-/p5-2.3.4.tgz
- Runtime: www/p5-v2.min.js and www/p5.webgpu.js (unmodified official minified distributions)
- WebGPU availability depends on the browser, GPU, and driver.

## p5.sound 1.0.1 for p5.js 1.x

The official p5.js 1.11.5 distribution includes p5.sound 1.0.1. This matching addon
provides the original sequencing, polyphonic synthesis, and WAV recording APIs.
This older p5.sound distribution is licensed under the MIT License.

- Upstream: https://github.com/processing/p5.js-sound/tree/a14a134fbee078cc3519752f09033eb614c87ee6
- Distribution: https://github.com/processing/p5.js/blob/v1.11.5/lib/addons/p5.sound.min.js
- License: third_party/licenses/p5-sound-v1-MIT.txt
- Corresponding source and build scripts: third_party/sources/p5.sound-v1.0.1-source.tar.gz (commit a14a134fbee078cc3519752f09033eb614c87ee6; its minified build matches the p5.js 1.11.5 addon byte for byte)
- Runtime: www/p5.sound-v1.min.js (unmodified official distribution)

## p5.sound 0.4.1 for p5.js 2.x

p5.sound is created by the Processing Foundation and contributors.
It is distributed under the GNU Lesser General Public License, version 2.1.

- Upstream: https://github.com/processing/p5.sound.js/tree/v0.4.1
- License: third_party/licenses/p5-sound-LGPL-2.1.txt
- Corresponding source and build scripts: third_party/sources/p5.sound-0.4.1-source.tgz
- Runtime: www/p5.sound.min.js

## Android runtime dependencies

The application uses AndroidX / Jetpack Compose, Google Material Components,
Kotlin and Kotlin coroutines, and their resolved runtime dependencies.
Apache-2.0.txt is included under third_party/licenses.

The build generates licenses/THIRD_PARTY_NOTICES.txt and licenses/DEPENDENCIES.txt
inside APK assets from the exact releaseRuntimeClasspath artifacts, their POM
license declarations, and bundled LICENSE / NOTICE / COPYING entries. The full
notice text is available from Settings > License information.

The same generated notices and dependency list accompany the release project in
third_party/resolved. They do not change the upstream terms.

## p5.brush 2.2.1

- Author: Alejandro Campos. License: MIT, third_party/licenses/p5-brush-MIT.txt
- Upstream: https://github.com/acamposuribe/p5.brush/tree/v2.2.1
- Runtime: www/p5.brush-2.2.1.js (requires p5.js 2.3.4 and a WEBGL canvas in Edit:RiN)
- Distribution: https://registry.npmjs.org/p5.brush/-/p5.brush-2.2.1.tgz

## Website fonts

The [official website](https://rin-code-dev.github.io/Edit-RiN/) uses self-hosted,
unmodified Space Grotesk and IBM Plex Mono fonts under the SIL Open Font License 1.1.

- Space Grotesk: https://github.com/floriankarsten/space-grotesk; license: website/assets/fonts/Space-Grotesk-OFL.txt
- IBM Plex Mono: https://github.com/IBM/plex; license: website/assets/fonts/IBM-Plex-Mono-OFL.txt
- Pinned source URLs and font checksums: website/assets/fonts/SOURCES.txt
