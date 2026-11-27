'use strict';
const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const core = require('../www/blocks/core.js');
const catalogue = require('../www/blocks/catalogue.js');
const easy = require('../www/blocks/easy.js');

test('easy mode flattens nested calls, variable declarations, and count loops into 1-line items', () => {
  const source = [
    'let size = 80; // @slider(20, 200, 2) "Size"',
    'function setup() {',
    '  createCanvas(windowWidth, windowHeight);',
    '}',
    'function draw() {',
    '  background(10, 14, 26, 36);',
    '  blendMode(ADD);',
    '  fill("#a8c7fa");',
    '  for (let i = 0; i < 8; i++) {',
    '    circle(width / 2, height / 2, size);',
    '  }',
    '}'
  ].join('\n');

  const proj = core.parse(source);
  assert.equal(proj.ok, true);
  const graph = easy.buildEasyGraph(proj, { language: 'ja' });
  assert.equal(graph.ok, true);

  // Top level has variable stack + setup card + draw card (no rin_program wrapper)
  assert.equal(graph.roots.length, 3);
  assert.equal(graph.roots[0].kind, 'stack');
  assert.equal(graph.roots[0].items[0].easyKind, 'var_decl');
  // Trailing @slider comment is absorbed into var_decl instead of becoming an extra block
  assert.equal(graph.roots[0].items.length, 1);
  assert.equal(graph.roots[0].items[0].statementNodeIds.length, 2);

  const setupCard = graph.roots[1].items[0];
  assert.equal(setupCard.easyKind, 'event_fn');
  assert.equal(setupCard.title, '最初に一度だけ');
  assert.equal(setupCard.bodies.body.children.length, 1);
  assert.equal(setupCard.bodies.body.children[0].easyKind, 'call_stmt');

  const drawCard = graph.roots[2].items[0];
  assert.equal(drawCard.easyKind, 'event_fn');
  assert.equal(drawCard.title, '毎フレーム描く');
  const drawChildren = drawCard.bodies.body.children;
  assert.deepEqual(drawChildren.map(c => c.easyKind), ['trail_bg', 'blend_mode', 'color_call', 'repeat_count']);
  assert.equal(drawChildren[0].colorHex, '#0a0e1a');
  assert.equal(drawChildren[3].bodies.body.children.length, 1);

  const circleItem = drawChildren[3].bodies.body.children[0];
  assert.equal(circleItem.easyKind, 'call_stmt');
  assert.equal(circleItem.title, '円');
  assert.deepEqual(
    circleItem.capsules.map(c => easy.formatCapsuleDisplay(c.raw, c.role, 'ja')),
    ['中央X', '中央Y', 'size']
  );
  assert.deepEqual(
    circleItem.capsules.map(c => easy.formatCapsuleDisplay(c.raw, c.role, 'en')),
    ['Center X', 'Center Y', 'size']
  );
});

test('easy mode recognizes radial, grid, rainbow, and push/pop idioms and updates paired ranges atomically', () => {
  const radialTpl = catalogue.TEMPLATES.find(t => t.id === 'radialArrange');
  const gridTpl = catalogue.TEMPLATES.find(t => t.id === 'gridArrange');
  const rainbowTpl = catalogue.TEMPLATES.find(t => t.id === 'rainbowFill');
  const isoTpl = catalogue.TEMPLATES.find(t => t.id === 'isolateTransform');

  const source = `function draw() {\n${radialTpl.source}\n${gridTpl.source}\n${rainbowTpl.source}\n${isoTpl.source}\n}\n`;
  const proj = core.parse(source);
  assert.equal(proj.ok, true);
  const graph = easy.buildEasyGraph(proj, { language: 'ja' });
  const drawBody = graph.roots[0].items[0].bodies.body.children;
  assert.deepEqual(drawBody.map(c => c.easyKind), ['radial_loop', 'grid_loop', 'rainbow_style', 'isolate_transform']);

  // Updating radial count updates both `i < N` and `TWO_PI / N`
  const radial = drawBody[0];
  const updatedRadial = easy.proposeCapsuleEdit(proj, radial, 'count', '16');
  assert.equal(updatedRadial.ok, true, updatedRadial.error);
  assert.match(updatedRadial.source, /i < 16/);
  assert.match(updatedRadial.source, /TWO_PI \/ 16/);

  // Re-projecting still recognizes radial_loop!
  const reGraph = easy.buildEasyGraph(updatedRadial.projection, { language: 'en' });
  assert.equal(reGraph.roots[0].items[0].bodies.body.children[0].easyKind, 'radial_loop');
  assert.equal(reGraph.roots[0].items[0].bodies.body.children[0].title, 'Arrange in circle');

  // Updating grid cols updates both `gx < cols` and `/ cols`
  const grid = drawBody[1];
  const updatedGrid = easy.proposeCapsuleEdit(proj, grid, 'cols', '8');
  assert.equal(updatedGrid.ok, true, updatedGrid.error);
  assert.match(updatedGrid.source, /gx < 8/);
  assert.match(updatedGrid.source, /width\) \/ 8/);
  assert.equal(easy.buildEasyGraph(updatedGrid.projection).roots[0].items[0].bodies.body.children[1].easyKind, 'grid_loop');

  // Updating rainbow speed keeps valid rainbow idiom
  const rainbow = drawBody[2];
  const updatedRainbow = easy.proposeCapsuleEdit(proj, rainbow, 'speed', '5');
  assert.equal(updatedRainbow.ok, true, updatedRainbow.error);
  assert.equal(easy.buildEasyGraph(updatedRainbow.projection).roots[0].items[0].bodies.body.children[2].easyKind, 'rainbow_style');
});

test('complex constructs become protected 1-line detailed blocks without losing source', () => {
  const source = [
    'class Particle { constructor() { this.x = 0; } }',
    'function draw() {',
    '  while (false) { break; }',
    '  circle(100, 100, 40);',
    '}'
  ].join('\n');
  const proj = core.parse(source);
  const graph = easy.buildEasyGraph(proj, { language: 'ja' });
  assert.equal(graph.roots[0].items[0].easyKind, 'detailed_protected');
  const drawChildren = graph.roots[1].items[0].bodies.body.children;
  assert.equal(drawChildren[0].easyKind, 'detailed_protected');
  assert.equal(drawChildren[1].easyKind, 'call_stmt');
});

test('expression validation rejects malformed syntax and accepts creative coding expressions', () => {
  assert.equal(easy.validateExpression('width / 2 + sin(frameCount * 0.05) * 80').ok, true);
  assert.equal(easy.validateExpression('').ok, false);
  assert.equal(easy.validateExpression('10 +').ok, false);
  assert.equal(easy.validateExpression('10, 20').ok, false);
});

test('all WorkTemplate.kt BLOCKS templates parse cleanly in fixed and responsive modes and project in easy mode', () => {
  const kt = fs.readFileSync(path.join(__dirname, '..', 'android/app/src/main/java/com/hikariatelier/app/WorkTemplate.kt'), 'utf8');
  const names = ['BLOCK_FOLLOW', 'BLOCK_PAINT', 'BLOCK_PATTERN', 'BLOCK_SOUND', 'BLOCK_THREE', 'BLOCK_PARAMETERS'];
  for (const name of names) {
    const match = kt.match(new RegExp('internal val ' + name + '_TEMPLATE = """([\\s\\S]*?)"""\\.trimIndent\\(\\)'));
    assert.ok(match, `missing ${name}_TEMPLATE`);
    const responsive = match[1].replace(/^\n/, '').replace(/\n\s*$/, '') + '\n';
    const fixed = responsive.split('\nfunction windowResized()')[0].trimEnd().replace('createCanvas(windowWidth, windowHeight', 'createCanvas(800, 800') + '\n';
    for (const code of [responsive, fixed]) {
      const proj = core.parse(code);
      assert.equal(proj.ok, true, `${name} failed to parse: ${JSON.stringify(proj.diagnostics)}`);
      const graph = easy.buildEasyGraph(proj, { language: 'ja' });
      assert.equal(graph.ok, true, `${name} failed easy projection`);
      assert.ok(graph.roots.length >= 2, `${name} should have setup and draw cards`);
    }
  }
});
