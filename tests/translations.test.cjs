// Static checks for UI translations: Japanese source strings are the lookup keys in AppLanguage.kt.
const { readFileSync, readdirSync } = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { test } = require('node:test');

const dir = path.join(__dirname, '..', 'android/app/src/main/java/com/hikariatelier/app');
const langSource = readFileSync(path.join(dir, 'AppLanguage.kt'), 'utf8');
const str = '"((?:[^"\\\\]|\\\\.)*)"';
const entryRe = new RegExp(`^\\s*${str}\\s+to\\s*\\(\\s*${str}\\s+to\\s+${str}\\s*\\)\\s*,?\\s*$`, 'gm');
const entries = [...langSource.replace(/\bto\s*\r?\n\s*\(/g, 'to (').matchAll(entryRe)]
  .map(m => ({ ja: m[1], en: m[2], zh: m[3] }));
const hasJapanese = s => /[\u3040-\u30ff\u3400-\u9fff]/.test(s);
const placeholders = s => (s.match(/%(?:\d+\$)?[sd]/g) || []).sort().join(',');

test('translation table is parsed', () => {
  assert.ok(entries.length > 500, `parsed only ${entries.length} entries`);
});

test('translation keys are unique', () => {
  const dup = entries.map(e => e.ja).filter((k, i, all) => all.indexOf(k) !== i);
  assert.deepEqual([...new Set(dup)], [], 'duplicate keys in uiTranslations');
});

test('every translation has English and Chinese text with matching placeholders', () => {
  const problems = [];
  for (const { ja, en, zh } of entries) {
    if (!en.trim() || !zh.trim()) problems.push(`empty: ${ja}`);
    if (placeholders(ja) !== placeholders(en)) problems.push(`en placeholders differ: ${ja}`);
    if (placeholders(ja) !== placeholders(zh)) problems.push(`zh placeholders differ: ${ja}`);
    if (hasJapanese(en) && !/[A-Za-z]{3}/.test(en)) problems.push(`en looks untranslated: ${ja}`);
  }
  assert.deepEqual(problems, []);
});

test('Japanese literals passed to uiText/text have a translation', () => {
  const keys = new Set(entries.map(e => e.ja));
  const call = /\b(?:uiText|text|translateUi)\(\s*"((?:[^"\\$]|\\.)*)"/g;
  const missing = [];
  for (const file of readdirSync(dir).filter(f => f.endsWith('.kt') && f !== 'AppLanguage.kt')) {
    const source = readFileSync(path.join(dir, file), 'utf8');
    for (const m of source.matchAll(call)) {
      if (hasJapanese(m[1]) && !keys.has(m[1])) missing.push(`${file}: ${m[1]}`);
    }
  }
  assert.deepEqual([...new Set(missing)], [], 'add these strings to uiTranslations');
});
