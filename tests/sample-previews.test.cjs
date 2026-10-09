const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { createHash } = require('node:crypto');
const { join } = require('node:path');
const root = join(__dirname, '..');
const manifest = JSON.parse(readFileSync(join(root, 'www/sample-previews/manifest.json')));
const version = readFileSync(join(root, 'android/app/src/main/java/com/hikariatelier/app/EditorSessionViewModel.kt'), 'utf8')
  .match(/const val P5_VERSION_CURRENT = "([^"]+)"/)[1];
test('all bundled sample previews match the current canonical source and p5 runtime', () => {
  assert.deepEqual(Object.keys(manifest).sort(), ['halo', 'shapes', 'touch', 'gravity', 'wave-parameter',
    'webgpu', 'sound', 'camera', 'microphone', 'sensor'].sort());
  for (const [id, entry] of Object.entries(manifest)) {
    const source = readFileSync(join(root, 'www/samples', entry.source));
    assert.equal(createHash('sha256').update(source).digest('hex'), entry.sha256, `${id}: regenerate preview after source change`);
    assert.equal(entry.p5Version, version);
  }
});
test('bundled previews contain PNG image data at the bounded gallery resolution', () => {
  for (const id of Object.keys(manifest)) {
    const png = readFileSync(join(root, 'www/sample-previews', `${id}.png`));
    assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a', id);
    assert.ok(png.readUInt32BE(16) > 0 && png.readUInt32BE(16) <= 480, id);
    assert.ok(png.readUInt32BE(20) > 0 && png.readUInt32BE(20) <= 480, id);
    assert.ok(png.includes(Buffer.from('IDAT')), id);
  }
});
