# Third-party software

Edit:RiN's own code and original icons use the root Edit:RiN Source-Available License.
Third-party software retains its own license terms.

## p5.js 1.11.5

p5.js is created by the Processing Foundation and p5.js contributors.
It is distributed under the GNU Lesser General Public License, version 2.1.

- Upstream: https://github.com/processing/p5.js/tree/v1.11.5
- License: third_party/licenses/p5-LGPL-2.1.txt
- Corresponding source and build scripts: third_party/sources/p5.js-v1.11.5-source.tar.gz
- Runtime: www/p5-v1.min.js (www/p5.min.js is retained for compatibility)

## p5.js 2.3.3

p5.js is created by the Processing Foundation and p5.js contributors.
It is distributed under the GNU Lesser General Public License, version 2.1.

- Upstream: https://github.com/processing/p5.js/tree/v2.3.3
- License: third_party/licenses/p5-LGPL-2.1.txt
- Corresponding source and build scripts: third_party/sources/p5-2.3.3-source.tgz
- Runtime: www/p5-v2.min.js

## p5.sound 0.4.1

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
