/* Edit:RiN Easy Block Mode projection, capsule presets, and idiom patches.
   Operates purely on core.js AST projections; sketch.js remains the single source of truth. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('./vendor/acorn.js'), require('./core.js'), require('./catalogue.js'));
  } else {
    root.EditRinEasyBlocks = factory(root.acorn, root.EditRinBlocks, root.EditRinBlockCatalogue);
  }
}(typeof self !== 'undefined' ? self : globalThis, function (acorn, core, catalogue) {
  'use strict';

  const pickLang = (lang, ja, en, zh) => (lang === 'en' ? en : lang === 'zh' ? zh : ja);
  const labelOf = (obj, lang) => typeof obj === 'string' ? obj : (obj && (obj[lang] || obj.en || obj.ja) || '');

  const COLOR_CALL_NAMES = new Set(['fill', 'stroke', 'background', 'color', 'ambientMaterial', 'specularMaterial']);
  const BLEND_OPTIONS = [
    ['ADD', { ja: '光らせる (ADD)', en: 'Glow (ADD)', zh: '发光叠加 (ADD)' }],
    ['BLEND', { ja: '通常 (BLEND)', en: 'Normal (BLEND)', zh: '正常 (BLEND)' }],
    ['SCREEN', { ja: '明るく重ねる (SCREEN)', en: 'Screen', zh: '滤色 (SCREEN)' }],
    ['MULTIPLY', { ja: '濃く重ねる (MULTIPLY)', en: 'Multiply', zh: '正片叠底 (MULTIPLY)' }],
    ['DIFFERENCE', { ja: '色の差 (DIFFERENCE)', en: 'Difference', zh: '差值 (DIFFERENCE)' }]
  ];

  function nodeMap(projection) {
    const map = new Map();
    for (const n of projection?.nodes || []) map.set(n.id, n);
    return map;
  }

  function unwrapParenNode(byId, node) {
    let cur = node;
    while (cur && cur.kind === 'parenthesized' && cur.inputs?.expression) {
      cur = byId.get(cur.inputs.expression);
    }
    return cur || null;
  }

  function numericLiteralValue(byId, node) {
    const n = unwrapParenNode(byId, node);
    if (!n) return null;
    if (n.kind === 'literal' && n.valueType === 'number' && Number.isFinite(Number(n.fields?.value?.value))) {
      return Number(n.fields.value.value);
    }
    if (n.kind === 'unary' && (n.fields?.operator?.value === '-' || n.fields?.operator?.value === '+')) {
      const inner = numericLiteralValue(byId, byId.get(n.inputs?.argument));
      if (inner != null) return n.fields.operator.value === '-' ? -inner : inner;
    }
    return null;
  }

  function rgbToHex(r, g, b) {
    const clamp = v => Math.max(0, Math.min(255, Math.round(Number(v) || 0)));
    return '#' + [clamp(r), clamp(g), clamp(b)].map(x => x.toString(16).padStart(2, '0')).join('');
  }

  function hexToRgb(hex) {
    let h = String(hex || '').trim();
    if (!h.startsWith('#')) h = '#' + h;
    if (/^#[0-9a-f]{3}$/i.test(h)) h = '#' + h.slice(1).split('').map(c => c + c).join('');
    const m = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(h);
    return m ? { r: parseInt(m[1], 16), g: parseInt(m[2], 16), b: parseInt(m[3], 16) } : { r: 10, g: 14, b: 26 };
  }

  function validateExpression(raw, sourceType = 'script') {
    const text = String(raw == null ? '' : raw).trim();
    if (!text) return { ok: false, errorCode: 'empty_expression' };
    if (!acorn) return { ok: true, expression: text };
    try {
      const expr = acorn.parseExpressionAt(text, 0, { ecmaVersion: 'latest', sourceType, preserveParens: true });
      if (text.slice(expr.end).trim() !== '') return { ok: false, errorCode: 'invalid_expression' };
      const callProg = acorn.parse('__rin_fn__(' + text + ')', { ecmaVersion: 'latest', sourceType, preserveParens: true });
      const call = callProg.body[0]?.expression;
      if (callProg.body.length !== 1 || call?.type !== 'CallExpression' || call.arguments.length !== 1 || call.arguments[0].type === 'SpreadElement') {
        return { ok: false, errorCode: 'invalid_expression' };
      }
      return { ok: true, expression: text };
    } catch (_) {
      return { ok: false, errorCode: 'invalid_expression' };
    }
  }

  function roleForCallArg(calleeName, argKey, index) {
    if (argKey === 'x' || argKey === 'x1' || argKey === 'x2' || argKey === 'x3' || argKey === 'eyeX' || argKey === 'centerX') return 'x';
    if (argKey === 'y' || argKey === 'y1' || argKey === 'y2' || argKey === 'y3' || argKey === 'eyeY' || argKey === 'centerY') return 'y';
    if (['diameter', 'width', 'height', 'size', 'radius', 'tubeRadius'].includes(argKey)) return 'size';
    if (['angle', 'start', 'stop'].includes(argKey) || /^rotate/.test(calleeName || '')) return 'angle';
    if (argKey === 'weight') return 'weight';
    if (argKey === 'color') return 'color';
    if (argKey === 'text') return 'string';
    if (['fps', 'amount', 'factor', 'brightness', 'shininess'].includes(argKey)) return 'number';
    if (calleeName === 'translate' && index === 0) return 'x';
    if (calleeName === 'translate' && index === 1) return 'y';
    return 'any';
  }

  function formatCapsuleDisplay(raw, role, lang = 'ja') {
    const compact = String(raw == null ? '' : raw).replace(/\s+/g, ' ').trim();
    if (!compact) return '0';
    const noSpace = compact.replace(/\s+/g, '');
    if (noSpace === 'width/2' || noSpace === 'width*0.5') return pickLang(lang, '中央X', 'Center X', '中心X');
    if (noSpace === 'height/2' || noSpace === 'height*0.5') return pickLang(lang, '中央Y', 'Center Y', '中心Y');
    if (noSpace === 'width' || noSpace === 'windowWidth') return pickLang(lang, '画面の幅', 'Canvas W', '画布宽');
    if (noSpace === 'height' || noSpace === 'windowHeight') return pickLang(lang, '画面の高さ', 'Canvas H', '画布高');
    if (noSpace === 'mouseX') return pickLang(lang, '指のX', 'Pointer X', '指针X');
    if (noSpace === 'mouseY') return pickLang(lang, '指のY', 'Pointer Y', '指针Y');
    if (noSpace === 'mouseIsPressed') return pickLang(lang, '押している間', 'While pressed', '按下时');
    if (noSpace === '!mouseIsPressed') return pickLang(lang, '押していない間', 'When not pressed', '未按下时');
    if (noSpace === 'keyIsPressed') return pickLang(lang, 'キー押下中', 'Key held', '按键中');
    if (noSpace === 'PI') return '180° (PI)';
    if (noSpace === 'TWO_PI') return '360° (2π)';
    if (noSpace === 'HALF_PI' || noSpace === 'PI/2') return '90°';
    if (noSpace === 'PI/4') return '45°';
    const randMatch = /^random\(\s*([^,]+?)\s*,\s*([^)]+?)\s*\)$/.exec(compact);
    if (randMatch) return pickLang(lang, `乱数 ${randMatch[1]}〜${randMatch[2]}`, `Rand ${randMatch[1]}–${randMatch[2]}`, `随机 ${randMatch[1]}–${randMatch[2]}`);
    const randOne = /^random\(\s*([^,)]+?)\s*\)$/.exec(compact);
    if (randOne) return pickLang(lang, `乱数 0〜${randOne[1]}`, `Rand 0–${randOne[1]}`, `随机 0–${randOne[1]}`);
    const minScale = /^min\(width,\s*height\)\s*\*\s*([0-9.]+)$/.exec(compact);
    if (minScale) return pickLang(lang, `画面比 ×${minScale[1]}`, `Screen ×${minScale[1]}`, `画面 ×${minScale[1]}`);
    if (/^(?:width\s*\/\s*2\s*\+\s*)?sin\(/.test(compact)) return pickLang(lang, '波の動き ~', 'Wave ~', '波浪 ~');
    if (/^(?:height\s*\/\s*2\s*\+\s*)?cos\(/.test(compact)) return pickLang(lang, '円の動き ~', 'Cosine ~', '余弦 ~');
    if (/^noise\(/.test(compact)) return pickLang(lang, '自然なゆらぎ', 'Smooth noise', '自然噪波');
    if (/^lerp\(/.test(compact)) return pickLang(lang, 'なめらか補間', 'Smooth lerp', '平滑插值');
    if (compact.length > 18) return compact.slice(0, 17) + '…';
    return compact;
  }

  function presetsForRole(role, lang = 'ja', visibleNames = []) {
    const p = (expr, ja, en, zh, group = 'preset') => ({
      expr,
      label: pickLang(lang, ja, en, zh),
      group
    });
    const list = [];
    if (role === 'x') {
      list.push(
        p('width / 2', '画面の中央 (横)', 'Canvas center X', '画面水平中心'),
        p('mouseX', '指・マウスの横位置', 'Pointer X', '手指/鼠标 X'),
        p('width / 2 + sin(frameCount * 0.04) * 120', '左右にゆらゆら揺れる', 'Sway left & right', '左右平滑摆动'),
        p('random(0, width)', 'ランダムな横位置', 'Random X across canvas', '随机横向位置'),
        p('0', '左端 / 原点 (0)', 'Origin (0)', '原点 (0)')
      );
    } else if (role === 'y') {
      list.push(
        p('height / 2', '画面の中央 (縦)', 'Canvas center Y', '画面垂直中心'),
        p('mouseY', '指・マウスの縦位置', 'Pointer Y', '手指/鼠标 Y'),
        p('height / 2 + sin(frameCount * 0.04) * 120', '上下にゆらゆら揺れる', 'Bob up & down', '上下平滑摆动'),
        p('random(0, height)', 'ランダムな縦位置', 'Random Y across canvas', '随机纵向位置'),
        p('0', '上端 / 原点 (0)', 'Origin (0)', '原点 (0)')
      );
    } else if (role === 'size') {
      list.push(
        p('80 + sin(frameCount * 0.05) * 30', '呼吸するように伸縮', 'Breathing pulse', '呼吸般缩放'),
        p('min(width, height) * 0.25', '画面サイズに合わせる (25%)', 'Fit 25% of screen', '适配画面 25%'),
        p('min(width, height) * 0.5', '画面サイズに合わせる (50%)', 'Fit 50% of screen', '适配画面 50%'),
        p('random(16, 72)', 'ランダムな大きさ', 'Random size', '随机大小'),
        p('width', '画面の幅いっぱい', 'Full canvas width', '铺满画布宽度'),
        p('height', '画面の高さいっぱい', 'Full canvas height', '铺满画布高度')
      );
    } else if (role === 'angle') {
      list.push(
        p('frameCount * 0.015', 'ゆっくり回り続ける', 'Spin slowly', '缓慢持续旋转'),
        p('frameCount * 0.05', '速く回り続ける', 'Spin quickly', '快速持续旋转'),
        p('sin(frameCount * 0.04) * 0.5', '振り子のように揺れる', 'Pendulum swing', '钟摆摆动'),
        p('PI / 4', '45° 傾ける', '45 degrees', '旋转 45°'),
        p('HALF_PI', '90° 傾ける', '90 degrees', '旋转 90°')
      );
    } else if (role === 'condition') {
      list.push(
        p('mouseIsPressed', '画面を押している間', 'While screen is pressed', '按住屏幕时'),
        p('!mouseIsPressed', '画面を押していない間', 'When not pressed', '未按屏幕时'),
        p('mouseX > width / 2', '指が画面の右半分にある', 'Pointer is on right half', '指针在右半边'),
        p('mouseY > height / 2', '指が画面の下半分にある', 'Pointer is on lower half', '指针在下半边'),
        p('random() < 0.5', '50% の確率で', '50% random chance', '50% 随机概率'),
        p('frameCount % 30 === 0', '一定のリズムごと (30フレーム)', 'Every 30 frames', '每 30 帧一次')
      );
    } else if (role === 'alpha') {
      list.push(
        p('16', '長い残像 (16)', 'Long trails (16)', '长残影 (16)'),
        p('36', 'ほどよい残像 (36)', 'Medium trails (36)', '适中残影 (36)'),
        p('80', '短い残像 (80)', 'Short trails (80)', '短残影 (80)'),
        p('255', '残像なし (255)', 'No trail / solid (255)', '无残影 (255)')
      );
    } else if (role === 'count') {
      for (const n of [4, 6, 8, 12, 16, 24]) {
        list.push(p(String(n), `${n}`, `${n}`, `${n}`));
      }
    } else {
      list.push(
        p('frameCount * 0.02', '時間とともに増える', 'Time counter', '随时间增加'),
        p('sin(frameCount * 0.05)', '波のリズム (-1〜1)', 'Sine wave (-1 to 1)', '正弦波 (-1～1)'),
        p('noise(frameCount * 0.01)', '自然なゆらぎ (0〜1)', 'Smooth noise (0 to 1)', '平滑噪波 (0～1)'),
        p('random(0, 100)', '乱数 (0〜100)', 'Random (0 to 100)', '随机数 (0～100)')
      );
    }

    const safeVars = (visibleNames || []).filter(name =>
      typeof name === 'string' &&
      !catalogue.EVENT_NAMES.includes(name) &&
      !['preload', 'setup', 'draw'].includes(name)
    );
    for (const v of safeVars.slice(0, 12)) {
      list.push({
        expr: v,
        label: pickLang(lang, `変数: ${v}`, `Var: ${v}`, `变量: ${v}`),
        group: 'variable'
      });
    }
    return list;
  }

  function sliderConfigForRole(role, currentNumeric) {
    const val = Number.isFinite(currentNumeric) ? currentNumeric : 0;
    if (role === 'alpha') return { min: 0, max: 255, step: 1 };
    if (role === 'count') return { min: 1, max: Math.max(36, Math.ceil(Math.abs(val))), step: 1 };
    if (role === 'weight') return { min: 0.5, max: Math.max(30, Math.ceil(Math.abs(val))), step: 0.5 };
    if (role === 'angle') return { min: -6.28, max: 6.28, step: 0.05 };
    if (role === 'speed') return { min: 0, max: Math.max(20, Math.ceil(Math.abs(val))), step: 0.5 };
    if (role === 'x' || role === 'y' || role === 'size') {
      const hi = Math.max(400, Math.ceil(Math.abs(val) * 1.5));
      return { min: role === 'size' ? 0 : -200, max: hi, step: Number.isInteger(val) ? 1 : 0.5 };
    }
    const span = Math.max(100, Math.ceil(Math.abs(val) * 1.5));
    return { min: Math.min(0, val - span), max: val + span, step: Number.isInteger(val) ? 1 : 0.1 };
  }

  function isSimpleCallStmt(byId, stmtNode, calleeName) {
    if (!stmtNode || stmtNode.kind !== 'expression_statement') return null;
    const expr = byId.get(stmtNode.inputs?.expression);
    if (!expr || !['p5_call', 'call'].includes(expr.kind)) return null;
    if (calleeName && expr.calleeName !== calleeName) return null;
    return expr;
  }

  // Match `for (let <v> = 0; <v> < <countExpr>; <v>++) { ... }`
  function matchRepeatFor(byId, forNode) {
    if (!forNode || forNode.kind !== 'for') return null;
    const init = byId.get(forNode.inputs?.init);
    const test = byId.get(forNode.inputs?.test);
    const update = byId.get(forNode.inputs?.update);
    const body = byId.get(forNode.inputs?.body);
    if (!init || !test || !update || !body || body.kind !== 'block') return null;
    if (init.kind !== 'variable_declaration' || init.fields?.kind?.value !== 'let') return null;
    const decls = init.inputs?.declarations || [];
    if (decls.length !== 1) return null;
    const decl = byId.get(decls[0]);
    const nameNode = decl && byId.get(decl.inputs?.name);
    const initVal = decl && byId.get(decl.inputs?.init);
    if (!nameNode || nameNode.kind !== 'identifier' || numericLiteralValue(byId, initVal) !== 0) return null;
    const varName = nameNode.fields?.name?.value;
    if (!varName) return null;
    if (test.kind !== 'binary' || test.fields?.operator?.value !== '<') return null;
    const testLeft = byId.get(test.inputs?.left);
    const testRight = byId.get(test.inputs?.right);
    if (!testLeft || testLeft.kind !== 'identifier' || testLeft.fields?.name?.value !== varName || !testRight) return null;
    const isPlusPlus = update.kind === 'update' && update.fields?.operator?.value === '++' &&
      byId.get(update.inputs?.argument)?.fields?.name?.value === varName;
    const isPlusEqOne = update.kind === 'assignment' && update.fields?.operator?.value === '+=' &&
      byId.get(update.inputs?.left)?.fields?.name?.value === varName &&
      numericLiteralValue(byId, byId.get(update.inputs?.right)) === 1;
    if (!isPlusPlus && !isPlusEqOne) return null;
    return {
      varName,
      countNode: testRight,
      bodyBlock: body,
      bodyIds: body.inputs?.body || []
    };
  }

  // Match `fill("hsl(" + floor((frameCount * <speed> + <offset>) % 360) + ", 85%, 65%)")`
  function matchRainbowCall(byId, callNode) {
    if (!callNode || callNode.kind !== 'p5_call' || !['fill', 'stroke'].includes(callNode.calleeName)) return null;
    const args = callNode.inputs?.args || [];
    if (args.length !== 1) return null;
    const arg = unwrapParenNode(byId, byId.get(args[0]));
    if (!arg) return null;
    const m = /^(['"])hsl\(\1\s*\+\s*floor\(\(\s*frameCount\s*\*\s*(.+?)\s*\+\s*(.+?)\s*\)\s*%\s*360\)\s*\+\s*(['"]),\s*85%,\s*65%\)\4$/.exec(arg.raw.trim());
    if (!m) return null;
    const stripOuterParens = s => {
      const t = s.trim();
      return /^\([^()]+\)$/.test(t) ? t.slice(1, -1).trim() : t;
    };
    return {
      mode: callNode.calleeName,
      argNodeId: arg.id,
      speedRaw: stripOuterParens(m[2]),
      offsetRaw: stripOuterParens(m[3])
    };
  }

  // Match `push(); translate(cx, cy); for (let i = 0; i < count; i++) { rotate(TWO_PI / count); ... } pop();`
  function matchRadialIdiom(byId, listIds, index) {
    if (index + 3 >= listIds.length) return null;
    const s0 = byId.get(listIds[index]);
    const s1 = byId.get(listIds[index + 1]);
    const s2 = byId.get(listIds[index + 2]);
    const s3 = byId.get(listIds[index + 3]);
    const pushCall = isSimpleCallStmt(byId, s0, 'push');
    const transCall = isSimpleCallStmt(byId, s1, 'translate');
    const popCall = isSimpleCallStmt(byId, s3, 'pop');
    if (!pushCall || !transCall || !popCall) return null;
    if ((pushCall.inputs?.args || []).length !== 0 || (popCall.inputs?.args || []).length !== 0) return null;
    const transArgs = transCall.inputs?.args || [];
    if (transArgs.length !== 2) return null;
    const rep = matchRepeatFor(byId, s2);
    if (!rep || rep.bodyIds.length < 1) return null;
    const firstStmt = byId.get(rep.bodyIds[0]);
    const rotCall = isSimpleCallStmt(byId, firstStmt, 'rotate');
    if (!rotCall || (rotCall.inputs?.args || []).length !== 1) return null;
    const rotArg = unwrapParenNode(byId, byId.get(rotCall.inputs.args[0]));
    if (!rotArg || rotArg.kind !== 'binary' || rotArg.fields?.operator?.value !== '/') return null;
    const numLeft = byId.get(rotArg.inputs?.left);
    const denRight = byId.get(rotArg.inputs?.right);
    if (!numLeft || numLeft.raw.trim() !== 'TWO_PI' || !denRight) return null;
    if (unwrapParenNode(byId, denRight).raw.trim() !== unwrapParenNode(byId, rep.countNode).raw.trim()) return null;
    return {
      statementIds: [s0.id, s1.id, s2.id, s3.id],
      cxNode: byId.get(transArgs[0]),
      cyNode: byId.get(transArgs[1]),
      countNode: rep.countNode,
      rotDenomNode: denRight,
      forNode: s2,
      bodyBlock: rep.bodyBlock,
      innerBodyIds: rep.bodyIds.slice(1),
      varName: rep.varName
    };
  }

  // Match `for (let gy = 0; gy < rows; gy++) { for (let gx = 0; gx < cols; gx++) { push(); translate(((gx + 0.5) * width) / cols, ((gy + 0.5) * height) / rows); ... pop(); } }`
  function matchGridIdiom(byId, stmtNode) {
    const outer = matchRepeatFor(byId, stmtNode);
    if (!outer || outer.bodyIds.length !== 1) return null;
    const innerForNode = byId.get(outer.bodyIds[0]);
    const inner = matchRepeatFor(byId, innerForNode);
    if (!inner || inner.bodyIds.length < 3) return null;
    const first = byId.get(inner.bodyIds[0]);
    const second = byId.get(inner.bodyIds[1]);
    const last = byId.get(inner.bodyIds[inner.bodyIds.length - 1]);
    const pushCall = isSimpleCallStmt(byId, first, 'push');
    const transCall = isSimpleCallStmt(byId, second, 'translate');
    const popCall = isSimpleCallStmt(byId, last, 'pop');
    if (!pushCall || !transCall || !popCall) return null;
    const transArgs = transCall.inputs?.args || [];
    if (transArgs.length !== 2) return null;
    const tx = unwrapParenNode(byId, byId.get(transArgs[0]));
    const ty = unwrapParenNode(byId, byId.get(transArgs[1]));
    if (!tx || !ty || tx.kind !== 'binary' || ty.kind !== 'binary' || tx.fields?.operator?.value !== '/' || ty.fields?.operator?.value !== '/') return null;
    const txDenom = byId.get(tx.inputs?.right);
    const tyDenom = byId.get(ty.inputs?.right);
    if (!txDenom || !tyDenom) return null;
    if (unwrapParenNode(byId, txDenom).raw.trim() !== unwrapParenNode(byId, inner.countNode).raw.trim() ||
        unwrapParenNode(byId, tyDenom).raw.trim() !== unwrapParenNode(byId, outer.countNode).raw.trim()) return null;
    return {
      statementIds: [stmtNode.id],
      colsNode: inner.countNode,
      txDenomNode: txDenom,
      rowsNode: outer.countNode,
      tyDenomNode: tyDenom,
      innerForNode,
      innerBodyIds: inner.bodyIds.slice(2, -1),
      gxName: inner.varName,
      gyName: outer.varName
    };
  }

  // Match balanced `push(); ... pop();` in the same statement list
  function matchIsolatePushPop(byId, listIds, index) {
    const startStmt = byId.get(listIds[index]);
    const pushCall = isSimpleCallStmt(byId, startStmt, 'push');
    if (!pushCall || (pushCall.inputs?.args || []).length !== 0) return null;
    let depth = 1;
    for (let j = index + 1; j < listIds.length; j++) {
      const cur = byId.get(listIds[j]);
      if (isSimpleCallStmt(byId, cur, 'push') && (byId.get(cur.inputs.expression).inputs?.args || []).length === 0) {
        depth++;
      } else if (isSimpleCallStmt(byId, cur, 'pop') && (byId.get(cur.inputs.expression).inputs?.args || []).length === 0) {
        depth--;
        if (depth === 0) {
          return {
            pushNode: startStmt,
            popNode: cur,
            endIndex: j,
            innerIds: listIds.slice(index + 1, j),
            statementIds: listIds.slice(index, j + 1)
          };
        }
      }
    }
    return null;
  }

  function hasJumpOut(byId, ids) {
    for (const id of ids) {
      const n = byId.get(id);
      if (n && ['return', 'break', 'continue'].includes(n.kind)) return true;
    }
    return false;
  }

  function shortCodeSummary(raw) {
    const clean = String(raw || '').replace(/\s+/g, ' ').trim();
    return clean.length > 26 ? clean.slice(0, 25) + '…' : clean;
  }

  function parameterKindFromComment(commentText) {
    const t = String(commentText || '');
    if (/@slider\b/.test(t)) return 'slider';
    if (/@color\b/.test(t)) return 'color';
    if (/@toggle\b/.test(t)) return 'toggle';
    if (/@choice\b/.test(t)) return 'choice';
    if (/@xy\b/.test(t)) return 'xy';
    return null;
  }

  function buildEasyGraph(projection, options = {}) {
    const lang = options.language || 'ja';
    if (!projection?.ok) return { ok: false, items: new Map(), roots: [] };
    const byId = nodeMap(projection);
    const items = new Map();

    function register(item) {
      items.set(item.id, item);
      return item;
    }

    function argumentLabel(calleeName, index) {
      const key = catalogue.P5_CALLS[calleeName]?.args?.[index] || String(index);
      const map = {
        width: ['幅', 'W', '宽'],
        height: ['高さ', 'H', '高'],
        diameter: ['大きさ', 'size', '直径'],
        radius: ['半径', 'radius', '半径'],
        tubeRadius: ['太さ', 'tube', '管径'],
        color: ['色', 'color', '颜色'],
        text: ['文字', 'text', '文字'],
        angle: ['角度', 'angle', '角度'],
        size: ['大きさ', 'size', '大小'],
        weight: ['太さ', 'weight', '粗细'],
        factor: ['倍率', 'scale', '倍数'],
        minimum: ['最小', 'min', '最小'],
        maximum: ['最大', 'max', '最大'],
        file: ['素材', 'file', '文件'],
        fps: ['FPS', 'fps', '帧率'],
        brightness: ['明るさ', 'light', '亮度'],
        shininess: ['光沢', 'shine', '光泽'],
        start: ['開始角', 'start', '起始角'],
        stop: ['終了角', 'stop', '结束角'],
        amount: ['強さ', 'amount', '强度'],
        mode: ['種類', 'mode', '模式'],
        x: ['横', 'x', 'X'],
        y: ['縦', 'y', 'Y']
      };
      const row = map[key];
      if (row) return pickLang(lang, row[0], row[1], row[2]);
      return key;
    }

    function projectList(listIds, containerMeta) {
      const out = [];
      let i = 0;
      while (i < listIds.length) {
        const id = listIds[i];
        const node = byId.get(id);
        if (!node) { i++; continue; }

        // 1. Radial arrangement idiom
        const radial = matchRadialIdiom(byId, listIds, i);
        if (radial) {
          const bodyChildren = projectList(radial.innerBodyIds, {
            parentId: radial.forNode.id,
            input: 'body',
            headSkip: 1,
            tailSkip: 0
          });
          out.push(register({
            id: radial.forNode.id,
            primaryNodeId: radial.forNode.id,
            easyKind: 'radial_loop',
            statementNodeIds: radial.statementIds,
            loc: node.loc ? { start: node.loc.start, end: byId.get(radial.statementIds[3])?.loc?.end || node.loc.end } : null,
            start: node.ownedStart ?? node.start,
            end: byId.get(radial.statementIds[3])?.end ?? node.end,
            title: pickLang(lang, '円形に並べる', 'Arrange in circle', '环形排列'),
            colour: '#365f8b',
            visibleNames: radial.forNode.visibleNames || [],
            capsules: [
              { key: 'cx', label: pickLang(lang, '中心X', 'x', '中心X'), raw: radial.cxNode.raw, role: 'x', targetIds: [radial.cxNode.id] },
              { key: 'cy', label: pickLang(lang, '中心Y', 'y', '中心Y'), raw: radial.cyNode.raw, role: 'y', targetIds: [radial.cyNode.id] },
              { key: 'count', label: pickLang(lang, '個数', 'count', '数量'), raw: radial.countNode.raw, role: 'count', targetIds: [radial.countNode.id, radial.rotDenomNode.id] }
            ],
            bodies: {
              body: {
                children: bodyChildren,
                parentId: radial.forNode.id,
                input: 'body',
                headSkip: 1,
                tailSkip: 0
              }
            }
          }));
          i += 4;
          continue;
        }

        // 2. Grid arrangement idiom
        const grid = matchGridIdiom(byId, node);
        if (grid) {
          const bodyChildren = projectList(grid.innerBodyIds, {
            parentId: grid.innerForNode.id,
            input: 'body',
            headSkip: 2,
            tailSkip: 1
          });
          out.push(register({
            id: node.id,
            primaryNodeId: node.id,
            easyKind: 'grid_loop',
            statementNodeIds: grid.statementIds,
            loc: node.loc,
            start: node.ownedStart ?? node.start,
            end: node.end,
            title: pickLang(lang, '縦横に並べる', 'Arrange in grid', '网格排列'),
            colour: '#365f8b',
            visibleNames: grid.innerForNode.visibleNames || [],
            capsules: [
              { key: 'cols', label: pickLang(lang, '横の数', 'cols', '列数'), raw: grid.colsNode.raw, role: 'count', targetIds: [grid.colsNode.id, grid.txDenomNode.id] },
              { key: 'rows', label: pickLang(lang, '縦の数', 'rows', '行数'), raw: grid.rowsNode.raw, role: 'count', targetIds: [grid.rowsNode.id, grid.tyDenomNode.id] }
            ],
            bodies: {
              body: {
                children: bodyChildren,
                parentId: grid.innerForNode.id,
                input: 'body',
                headSkip: 2,
                tailSkip: 1
              }
            }
          }));
          i += 1;
          continue;
        }

        // 3. Balanced push() / pop() isolate block
        const iso = matchIsolatePushPop(byId, listIds, i);
        if (iso && !hasJumpOut(byId, iso.innerIds)) {
          const bodyChildren = projectList(iso.innerIds, {
            parentId: containerMeta.parentId,
            input: containerMeta.input,
            afterNodeId: iso.pushNode.id,
            beforeNodeId: iso.popNode.id
          });
          out.push(register({
            id: iso.pushNode.id,
            primaryNodeId: iso.pushNode.id,
            easyKind: 'isolate_transform',
            statementNodeIds: iso.statementIds,
            loc: iso.pushNode.loc ? { start: iso.pushNode.loc.start, end: iso.popNode.loc?.end || iso.pushNode.loc.end } : null,
            start: iso.pushNode.ownedStart ?? iso.pushNode.start,
            end: iso.popNode.end,
            title: pickLang(lang, '移動・回転の範囲を分ける', 'Isolate transform', '独立变换范围'),
            colour: '#2f6861',
            visibleNames: iso.pushNode.visibleNames || [],
            capsules: [],
            bodies: {
              body: {
                children: bodyChildren,
                parentId: containerMeta.parentId,
                input: containerMeta.input,
                afterNodeId: iso.pushNode.id,
                beforeNodeId: iso.popNode.id
              }
            }
          }));
          i = iso.endIndex + 1;
          continue;
        }

        // Absorb same-line trailing comment (e.g. // @slider...) into a statement
        const nextNode = i + 1 < listIds.length ? byId.get(listIds[i + 1]) : null;
        const hasSameLineComment = nextNode && nextNode.kind === 'comment' &&
          node.kind !== 'comment' && node.loc && nextNode.loc &&
          nextNode.loc.start.line === node.loc.end.line;
        const ownedStmtIds = hasSameLineComment ? [node.id, nextNode.id] : [node.id];
        const step = hasSameLineComment ? 2 : 1;

        // 4. Function declaration
        if (node.kind === 'function') {
          const fnName = node.fields?.name?.value || '';
          const isEvent = !!node.event;
          const evMeta = isEvent ? catalogue.EVENTS.find(e => e.name === fnName) : null;
          let title;
          if (isEvent) {
            const cleanEventTitles = {
              setup: ['最初に一度だけ', 'Once at start', '启动时执行一次'],
              draw: ['毎フレーム描く', 'Every frame', '每一帧绘制'],
              preload: ['先に素材を読み込む', 'Preload assets', '预先加载素材'],
              mousePressed: ['押したとき', 'When pressed', '按下时'],
              mouseReleased: ['離したとき', 'When released', '松开时'],
              mouseDragged: ['押したまま動かしたとき', 'When dragged', '按住拖动时'],
              mouseMoved: ['動かしたとき', 'When moved', '移动时'],
              mouseClicked: ['クリックしたとき', 'When clicked', '点击时'],
              doubleClicked: ['ダブルクリックしたとき', 'When double clicked', '双击时'],
              mouseWheel: ['ホイールを回したとき', 'When wheel scrolls', '滚轮滚动时'],
              touchStarted: ['指が触れたとき', 'When touched', '手指触碰时'],
              touchMoved: ['指を動かしたとき', 'When touch moves', '手指移动时'],
              touchEnded: ['指を離したとき', 'When touch ends', '手指离开时'],
              keyPressed: ['キーを押したとき', 'When key pressed', '按下按键时'],
              keyReleased: ['キーを離したとき', 'When key released', '松开按键时'],
              keyTyped: ['文字キーを入力したとき', 'When key typed', '输入字符时'],
              windowResized: ['画面サイズが変わったとき', 'When window resizes', '画面大小改变时'],
              deviceMoved: ['端末を動かしたとき', 'When device moves', '移动设备时'],
              deviceTurned: ['端末を回したとき', 'When device turns', '转动设备时'],
              deviceShaken: ['端末を振ったとき', 'When device shaken', '摇晃设备时']
            };
            const row = cleanEventTitles[fnName];
            title = row ? pickLang(lang, row[0], row[1], row[2]) : (evMeta ? labelOf(evMeta.label, lang) : fnName);
          } else {
            const paramList = (node.parameters || []).map(p => p.name || '').filter(Boolean).join(', ');
            title = pickLang(lang, `関数 ${fnName}(${paramList})`, `Function ${fnName}(${paramList})`, `函数 ${fnName}(${paramList})`);
          }
          const bodyChildren = projectList(node.inputs?.body || [], {
            parentId: node.id,
            input: 'body',
            headSkip: 0,
            tailSkip: 0
          });
          out.push(register({
            id: node.id,
            primaryNodeId: node.id,
            easyKind: isEvent ? 'event_fn' : 'custom_fn',
            fnName,
            isTopContainer: !containerMeta.parentId,
            statementNodeIds: ownedStmtIds,
            loc: node.loc,
            start: node.ownedStart ?? node.start,
            end: (hasSameLineComment ? nextNode.end : node.end),
            title,
            colour: '#585184',
            visibleNames: node.visibleNames || [],
            capsules: [],
            bodies: {
              body: {
                children: bodyChildren,
                parentId: node.id,
                input: 'body',
                headSkip: 0,
                tailSkip: 0
              }
            }
          }));
          i += step;
          continue;
        }

        // 5. ExpressionStatement (calls, assignments, updates)
        if (node.kind === 'expression_statement') {
          const expr = unwrapParenNode(byId, byId.get(node.inputs?.expression));
          if (expr && (expr.kind === 'p5_call' || (expr.kind === 'call' && typeof expr.calleeName === 'string' && /^[\w$]+(?:\.[\w$]+)*$/.test(expr.calleeName)))) {
            const callee = expr.calleeName;
            const argIds = expr.inputs?.args || [];
            const argNodes = argIds.map(aid => byId.get(aid));

            // 5a. Rainbow style idiom
            const rainbow = matchRainbowCall(byId, expr);
            if (rainbow) {
              out.push(register({
                id: node.id,
                primaryNodeId: expr.id,
                easyKind: 'rainbow_style',
                rainbowMode: rainbow.mode,
                argNodeId: rainbow.argNodeId,
                statementNodeIds: ownedStmtIds,
                loc: node.loc,
                start: node.ownedStart ?? node.start,
                end: hasSameLineComment ? nextNode.end : node.end,
                title: rainbow.mode === 'stroke'
                  ? pickLang(lang, '虹色の線', 'Rainbow stroke', '彩虹描边')
                  : pickLang(lang, '虹色の塗り', 'Rainbow fill', '彩虹填充'),
                colour: '#226b72',
                visibleNames: expr.visibleNames || [],
                capsules: [
                  { key: 'speed', label: pickLang(lang, '速さ', 'speed', '速度'), raw: rainbow.speedRaw, role: 'speed' },
                  { key: 'offset', label: pickLang(lang, 'ずらし', 'shift', '偏移'), raw: rainbow.offsetRaw, role: 'number' }
                ]
              }));
              i += step;
              continue;
            }

            // 5b. Motion-trail background idiom: background(r, g, b, alpha) or background(gray, alpha)
            if (expr.p5 && callee === 'background' && (argNodes.length === 4 || argNodes.length === 2) && argNodes.every(Boolean)) {
              const nums = argNodes.slice(0, argNodes.length - 1).map(n => numericLiteralValue(byId, n));
              if (nums.every(v => v != null)) {
                const hex = nums.length === 3 ? rgbToHex(nums[0], nums[1], nums[2]) : rgbToHex(nums[0], nums[0], nums[0]);
                const alphaNode = argNodes[argNodes.length - 1];
                out.push(register({
                  id: node.id,
                  primaryNodeId: expr.id,
                  callNodeId: expr.id,
                  easyKind: 'trail_bg',
                  colorHex: hex,
                  rgbNodeIds: argNodes.slice(0, argNodes.length - 1).map(n => n.id),
                  statementNodeIds: ownedStmtIds,
                  loc: node.loc,
                  start: node.ownedStart ?? node.start,
                  end: hasSameLineComment ? nextNode.end : node.end,
                  title: pickLang(lang, '残像つきの背景', 'Trail background', '残影背景'),
                  colour: '#226b72',
                  visibleNames: expr.visibleNames || [],
                  capsules: [
                    { key: 'alpha', label: pickLang(lang, '濃さ', 'trail', '浓度'), raw: alphaNode.raw, role: 'alpha', targetIds: [alphaNode.id] }
                  ]
                }));
                i += step;
                continue;
              }
            }

            // 5c. blendMode dropdown block
            if (expr.p5 && callee === 'blendMode' && argNodes.length === 1 && argNodes[0]?.kind === 'identifier') {
              const curMode = argNodes[0].fields?.name?.value || 'BLEND';
              out.push(register({
                id: node.id,
                primaryNodeId: expr.id,
                callNodeId: expr.id,
                modeNodeId: argNodes[0].id,
                blendValue: curMode,
                blendOptions: BLEND_OPTIONS.map(([val, lbl]) => [labelOf(lbl, lang), val]),
                easyKind: 'blend_mode',
                statementNodeIds: ownedStmtIds,
                loc: node.loc,
                start: node.ownedStart ?? node.start,
                end: hasSameLineComment ? nextNode.end : node.end,
                title: pickLang(lang, '色の重なり方', 'Blend mode', '混合模式'),
                colour: '#226b72',
                visibleNames: expr.visibleNames || [],
                capsules: []
              }));
              i += step;
              continue;
            }

            // 5d. Pure color call (background / fill / stroke / ambientMaterial / specularMaterial)
            if (expr.p5 && COLOR_CALL_NAMES.has(callee) && argNodes.length > 0 && argNodes.every(Boolean)) {
              const singleHex = argNodes.length === 1 && argNodes[0].kind === 'literal' &&
                argNodes[0].valueType === 'string' && /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i.test(String(argNodes[0].fields?.value?.value || ''));
              const wrappedHex = argNodes.length === 1 && !!expr.colorSwatch;
              const allNums = (argNodes.length === 1 || argNodes.length === 3) ? argNodes.map(n => numericLiteralValue(byId, n)) : null;
              const pureNumericColor = allNums && allNums.every(v => v != null && v >= 0 && v <= 255);
              if (singleHex || wrappedHex || pureNumericColor) {
                let swatch = '#6c5ce7';
                if (singleHex) {
                  const rawHex = String(argNodes[0].fields.value.value);
                  swatch = /^#[0-9a-f]{3}$/i.test(rawHex)
                    ? '#' + rawHex.slice(1).split('').map(c => c + c).join('').toLowerCase()
                    : rawHex.toLowerCase();
                } else if (wrappedHex) {
                  swatch = String(expr.colorSwatch).toLowerCase();
                } else if (allNums.length === 1) {
                  swatch = rgbToHex(allNums[0], allNums[0], allNums[0]);
                } else {
                  swatch = rgbToHex(allNums[0], allNums[1], allNums[2]);
                }
                const entry = catalogue.P5_CALLS[callee];
                out.push(register({
                  id: node.id,
                  primaryNodeId: expr.id,
                  callNodeId: expr.id,
                  easyKind: 'color_call',
                  calleeName: callee,
                  colorHex: swatch,
                  hexLiteralNodeId: singleHex ? argNodes[0].id : null,
                  statementNodeIds: ownedStmtIds,
                  loc: node.loc,
                  start: node.ownedStart ?? node.start,
                  end: hasSameLineComment ? nextNode.end : node.end,
                  title: entry ? labelOf(entry.label, lang) : callee,
                  colour: '#226b72',
                  visibleNames: expr.visibleNames || [],
                  capsules: []
                }));
                i += step;
                continue;
              }
            }

            // 5e. General 1-line p5 / helper call statement
            if (argNodes.every(Boolean)) {
              const p5Entry = expr.p5 ? catalogue.P5_CALLS[callee] : null;
              const friendlyCallLabels = {
                'brush.pick': ['ブラシを選ぶ', 'Pick brush', '选择画笔'],
                'brush.stroke': ['ブラシの色', 'Brush color', '画笔颜色'],
                'brush.strokeWeight': ['ブラシの太さ', 'Brush weight', '画笔粗细'],
                'brush.fill': ['水彩の塗り', 'Watercolor fill', '水彩填充'],
                'brush.bleed': ['水彩のにじみ', 'Watercolor bleed', '水彩晕染'],
                'brush.fillBleed': ['水彩のにじみ', 'Watercolor bleed', '水彩晕染'],
                'brush.noFill': ['ブラシ塗りなし', 'No brush fill', '无画笔填充'],
                'brush.line': ['ブラシで線', 'Brush line', '画笔直线'],
                'brush.circle': ['ブラシで円', 'Brush circle', '画笔圆'],
                'brush.rect': ['ブラシで四角', 'Brush rectangle', '画笔矩形'],
                'brush.hatch': ['ハッチング', 'Brush hatch', '排线阴影'],
                'brush.load': ['ブラシを準備', 'Load brushes', '初始化画笔'],
                'Matter.Engine.update': ['物理を進める', 'Step physics', '推进物理'],
                'Matter.Composite.add': ['物理ワールドへ追加', 'Add to physics', '加入物理世界']
              };
              let callTitle;
              if (p5Entry) {
                callTitle = labelOf(p5Entry.label, lang).replace(/\s+[a-zA-Z][\w.]*$/, '');
              } else if (friendlyCallLabels[callee]) {
                const row = friendlyCallLabels[callee];
                callTitle = pickLang(lang, row[0], row[1], row[2]);
              } else if (callee.endsWith('.push')) {
                callTitle = pickLang(lang, `${callee.slice(0, -5)} に追加`, `Add to ${callee.slice(0, -5)}`, `追加到 ${callee.slice(0, -5)}`);
              } else if (callee.endsWith('.play')) {
                callTitle = pickLang(lang, `${callee.slice(0, -5)} を再生`, `Play ${callee.slice(0, -5)}`, `播放 ${callee.slice(0, -5)}`);
              } else if (callee.endsWith('.stop')) {
                callTitle = pickLang(lang, `${callee.slice(0, -5)} を停止`, `Stop ${callee.slice(0, -5)}`, `停止 ${callee.slice(0, -5)}`);
              } else {
                callTitle = callee;
              }
              const specArgs = p5Entry?.args || [];
              const capsules = argNodes.map((argNode, idx) => ({
                key: 'arg_' + idx,
                label: argNodes.length === 1 && !p5Entry ? '' : argumentLabel(callee, idx),
                raw: argNode.raw,
                role: roleForCallArg(callee, specArgs[idx], idx),
                targetIds: [argNode.id]
              }));
              out.push(register({
                id: node.id,
                primaryNodeId: expr.id,
                callNodeId: expr.id,
                calleeName: callee,
                easyKind: 'call_stmt',
                statementNodeIds: ownedStmtIds,
                loc: node.loc,
                start: node.ownedStart ?? node.start,
                end: hasSameLineComment ? nextNode.end : node.end,
                title: callTitle,
                colour: expr.p5 ? '#226b72' : '#4c6860',
                requirements: expr.requirements || null,
                visibleNames: expr.visibleNames || [],
                capsules
              }));
              i += step;
              continue;
            }
          }

          // 5f. Assignment: x = 10; or x += 1;
          if (expr && expr.kind === 'assignment') {
            const left = byId.get(expr.inputs?.left);
            const right = byId.get(expr.inputs?.right);
            const op = expr.fields?.operator?.value || '=';
            if (left && right && ['identifier', 'member'].includes(left.kind) && ['=', '+=', '-=', '*=', '/='].includes(op)) {
              const targetLabel = left.raw.trim();
              const title = op === '='
                ? pickLang(lang, `${targetLabel} を変える`, `Set ${targetLabel}`, `设 ${targetLabel} 为`)
                : op === '+='
                  ? pickLang(lang, `${targetLabel} を増やす`, `Change ${targetLabel} by +`, `${targetLabel} 增加`)
                  : op === '-='
                    ? pickLang(lang, `${targetLabel} を減らす`, `Change ${targetLabel} by −`, `${targetLabel} 减少`)
                    : `${targetLabel} ${op}`;
              out.push(register({
                id: node.id,
                primaryNodeId: expr.id,
                easyKind: 'var_assign',
                statementNodeIds: ownedStmtIds,
                loc: node.loc,
                start: node.ownedStart ?? node.start,
                end: hasSameLineComment ? nextNode.end : node.end,
                title,
                colour: '#824d6e',
                visibleNames: expr.visibleNames || [],
                capsules: [
                  { key: 'value', label: '', raw: right.raw, role: 'number', targetIds: [right.id] }
                ]
              }));
              i += step;
              continue;
            }
          }

          // 5g. Update: x++; or x--;
          if (expr && expr.kind === 'update') {
            const arg = byId.get(expr.inputs?.argument);
            const op = expr.fields?.operator?.value || '++';
            if (arg && ['identifier', 'member'].includes(arg.kind)) {
              const name = arg.raw.trim();
              out.push(register({
                id: node.id,
                primaryNodeId: expr.id,
                easyKind: 'var_update',
                statementNodeIds: ownedStmtIds,
                loc: node.loc,
                start: node.ownedStart ?? node.start,
                end: hasSameLineComment ? nextNode.end : node.end,
                title: op === '++'
                  ? pickLang(lang, `${name} を 1 増やす`, `Increase ${name} by 1`, `${name} 加 1`)
                  : pickLang(lang, `${name} を 1 減らす`, `Decrease ${name} by 1`, `${name} 减 1`),
                colour: '#824d6e',
                visibleNames: expr.visibleNames || [],
                capsules: []
              }));
              i += step;
              continue;
            }
          }
        }

        // 6. Simple VariableDeclaration: let x = 80; / const speed = 2;
        if (node.kind === 'variable_declaration') {
          const declIds = node.inputs?.declarations || [];
          if (declIds.length === 1) {
            const decl = byId.get(declIds[0]);
            const nameNode = decl && byId.get(decl.inputs?.name);
            const initNode = decl && (decl.inputs?.init ? byId.get(decl.inputs.init) : null);
            if (nameNode && nameNode.kind === 'identifier') {
              const varName = nameNode.fields?.name?.value || 'x';
              const declKind = node.fields?.kind?.value || 'let';
              const paramKind = hasSameLineComment ? parameterKindFromComment(nextNode.fields?.text?.value) : null;
              // Skip raw multi-line template literals (like shader GLSL) -> protect them cleanly!
              if (!initNode || (!initNode.kind.startsWith('raw_') && !/\n/.test(initNode.raw))) {
                const prefix = paramKind
                  ? pickLang(lang, 'パラメータ', 'Param', '参数')
                  : declKind === 'const'
                    ? pickLang(lang, '定数', 'Const', '常量')
                    : pickLang(lang, '変数', 'Var', '变量');
                const capsules = [];
                if (nameNode.renameable) {
                  capsules.push({ key: 'name', label: '', raw: varName, role: 'variable_name', renameNodeId: nameNode.id });
                }
                if (initNode) {
                  const isHex = initNode.kind === 'literal' && initNode.valueType === 'string' &&
                    /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i.test(String(initNode.fields?.value?.value || ''));
                  capsules.push({
                    key: 'init',
                    label: '=',
                    raw: initNode.raw,
                    role: isHex ? 'color_literal' : 'any',
                    hexValue: isHex ? String(initNode.fields.value.value) : null,
                    targetIds: [initNode.id]
                  });
                }
                out.push(register({
                  id: node.id,
                  primaryNodeId: node.id,
                  easyKind: 'var_decl',
                  varName,
                  renameable: !!nameNode.renameable,
                  statementNodeIds: ownedStmtIds,
                  loc: node.loc,
                  start: node.ownedStart ?? node.start,
                  end: hasSameLineComment ? nextNode.end : node.end,
                  title: nameNode.renameable ? prefix : `${prefix} ${varName}`,
                  colour: '#824d6e',
                  visibleNames: node.visibleNames || [],
                  capsules
                }));
                i += step;
                continue;
              }
            }
          }
        }

        // 7. IfStatement
        if (node.kind === 'if') {
          const testNode = byId.get(node.inputs?.test);
          const consNode = byId.get(node.inputs?.consequent);
          const altNode = node.inputs?.alternate ? byId.get(node.inputs.alternate) : null;
          if (testNode && consNode && consNode.kind === 'block' && (!altNode || altNode.kind === 'block')) {
            const consChildren = projectList(consNode.inputs?.body || [], {
              parentId: consNode.id,
              input: 'body',
              headSkip: 0,
              tailSkip: 0
            });
            const bodies = {
              consequent: {
                children: consChildren,
                parentId: consNode.id,
                input: 'body',
                headSkip: 0,
                tailSkip: 0
              }
            };
            if (altNode) {
              bodies.alternate = {
                children: projectList(altNode.inputs?.body || [], {
                  parentId: altNode.id,
                  input: 'body',
                  headSkip: 0,
                  tailSkip: 0
                }),
                parentId: altNode.id,
                input: 'body',
                headSkip: 0,
                tailSkip: 0
              };
            }
            out.push(register({
              id: node.id,
              primaryNodeId: node.id,
              easyKind: 'if_stmt',
              hasElse: !!altNode,
              statementNodeIds: ownedStmtIds,
              loc: node.loc,
              start: node.ownedStart ?? node.start,
              end: hasSameLineComment ? nextNode.end : node.end,
              title: pickLang(lang, 'もし', 'If', '如果'),
              titleSuffix: pickLang(lang, 'なら', 'then', '则'),
              elseLabel: pickLang(lang, 'そうでなければ', 'Else', '否则'),
              colour: '#365f8b',
              visibleNames: node.visibleNames || [],
              capsules: [
                { key: 'test', label: '', raw: testNode.raw, role: 'condition', targetIds: [testNode.id] }
              ],
              bodies
            }));
            i += step;
            continue;
          }
        }

        // 8. Standard count loop: for (let i = 0; i < N; i++) { ... }
        const rep = matchRepeatFor(byId, node);
        if (rep) {
          const bodyChildren = projectList(rep.bodyIds, {
            parentId: rep.bodyBlock.id,
            input: 'body',
            headSkip: 0,
            tailSkip: 0
          });
          out.push(register({
            id: node.id,
            primaryNodeId: node.id,
            easyKind: 'repeat_count',
            varName: rep.varName,
            statementNodeIds: ownedStmtIds,
            loc: node.loc,
            start: node.ownedStart ?? node.start,
            end: hasSameLineComment ? nextNode.end : node.end,
            title: '',
            titleSuffix: pickLang(lang, `回くり返す (${rep.varName})`, `times (${rep.varName})`, `次循环 (${rep.varName})`),
            colour: '#365f8b',
            visibleNames: rep.bodyBlock.visibleNames || node.visibleNames || [],
            capsules: [
              { key: 'count', label: '', raw: rep.countNode.raw, role: 'count', targetIds: [rep.countNode.id] }
            ],
            bodies: {
              body: {
                children: bodyChildren,
                parentId: rep.bodyBlock.id,
                input: 'body',
                headSkip: 0,
                tailSkip: 0
              }
            }
          }));
          i += step;
          continue;
        }

        // 9. ForOfStatement: for (const item of values) { ... }
        if (node.kind === 'for_of') {
          const leftNode = byId.get(node.inputs?.left);
          const rightNode = byId.get(node.inputs?.right);
          const bodyNode = byId.get(node.inputs?.body);
          if (leftNode && rightNode && bodyNode && bodyNode.kind === 'block') {
            let itemVar = leftNode.raw.replace(/^(?:const|let|var)\s+/, '').trim();
            const bodyChildren = projectList(bodyNode.inputs?.body || [], {
              parentId: bodyNode.id,
              input: 'body',
              headSkip: 0,
              tailSkip: 0
            });
            out.push(register({
              id: node.id,
              primaryNodeId: node.id,
              easyKind: 'for_of_stmt',
              statementNodeIds: ownedStmtIds,
              loc: node.loc,
              start: node.ownedStart ?? node.start,
              end: hasSameLineComment ? nextNode.end : node.end,
              title: pickLang(lang, 'リスト', 'Each in', '遍历'),
              titleSuffix: pickLang(lang, `の各要素 (${itemVar})`, `as ${itemVar}`, `每项 (${itemVar})`),
              colour: '#365f8b',
              visibleNames: bodyNode.visibleNames || [],
              capsules: [
                { key: 'list', label: '', raw: rightNode.raw, role: 'any', targetIds: [rightNode.id] }
              ],
              bodies: {
                body: {
                  children: bodyChildren,
                  parentId: bodyNode.id,
                  input: 'body',
                  headSkip: 0,
                  tailSkip: 0
                }
              }
            }));
            i += step;
            continue;
          }
        }

        // 10. Return statement
        if (node.kind === 'return') {
          const argNode = node.inputs?.argument ? byId.get(node.inputs.argument) : null;
          out.push(register({
            id: node.id,
            primaryNodeId: node.id,
            easyKind: 'return_stmt',
            statementNodeIds: ownedStmtIds,
            loc: node.loc,
            start: node.ownedStart ?? node.start,
            end: hasSameLineComment ? nextNode.end : node.end,
            title: pickLang(lang, '値を返す', 'Return', '返回'),
            colour: '#4c6860',
            visibleNames: node.visibleNames || [],
            capsules: argNode ? [{ key: 'arg', label: '', raw: argNode.raw, role: 'any', targetIds: [argNode.id] }] : []
          }));
          i += step;
          continue;
        }

        // 11. Standalone comment (skip attached @rin comments so they travel silently with their owner)
        if (node.kind === 'comment') {
          const isAttachedRin = projection.nodes.some(other =>
            Array.isArray(other.attachedCommentIds) && other.attachedCommentIds.includes(node.id)
          );
          if (isAttachedRin) {
            i += 1;
            continue;
          }
          out.push(register({
            id: node.id,
            primaryNodeId: node.id,
            easyKind: 'comment_stmt',
            commentNodeId: node.id,
            commentValue: String(node.fields?.text?.value || '').trim(),
            statementNodeIds: [node.id],
            loc: node.loc,
            start: node.start,
            end: node.end,
            title: pickLang(lang, 'メモ', 'Note', '备注'),
            colour: '#586473',
            visibleNames: [],
            capsules: []
          }));
          i += 1;
          continue;
        }

        // 12. Everything else -> Protected 1-line "詳細な処理" block
        out.push(register({
          id: node.id,
          primaryNodeId: node.id,
          easyKind: 'detailed_protected',
          summary: shortCodeSummary(node.raw),
          statementNodeIds: ownedStmtIds,
          loc: node.loc,
          start: node.ownedStart ?? node.start,
          end: hasSameLineComment ? nextNode.end : node.end,
          title: pickLang(lang, '詳細な処理', 'Detailed code', '详细代码'),
          colour: '#6d584b',
          visibleNames: node.visibleNames || [],
          capsules: []
        }));
        i += step;
      }
      return out;
    }

    const topItems = projectList(projection.rootIds || [], {
      parentId: null,
      input: 'root',
      headSkip: 0,
      tailSkip: 0
    });

    // Group contiguous top-level non-function items into clean stacks while keeping functions as standalone cards
    const roots = [];
    let currentStack = null;
    for (const item of topItems) {
      if (item.easyKind === 'event_fn' || item.easyKind === 'custom_fn') {
        currentStack = null;
        roots.push({ kind: 'card', key: 'fn_' + (item.fnName || item.id), items: [item] });
      } else {
        if (!currentStack) {
          currentStack = { kind: 'stack', key: 'top_' + item.id, items: [] };
          roots.push(currentStack);
        }
        currentStack.items.push(item);
      }
    }

    return { ok: true, items, topItems, roots };
  }

  function proposeCapsuleEdit(projection, item, capsuleKey, newValue, options = {}) {
    const sourceType = projection?.sourceType || 'script';
    const capsule = (item?.capsules || []).find(c => c.key === capsuleKey);
    if (!capsule) return { ok: false, errorCode: 'unknown_capsule', error: 'Unknown capsule' };

    if (capsule.role === 'variable_name' && capsule.renameNodeId) {
      return core.proposePatch(projection, {
        type: 'renameBinding',
        id: capsule.renameNodeId,
        name: String(newValue || '').trim()
      });
    }

    if (item.easyKind === 'rainbow_style') {
      const check = validateExpression(newValue, sourceType);
      if (!check.ok) return { ok: false, errorCode: check.errorCode, error: 'Invalid expression' };
      const speedCap = item.capsules.find(c => c.key === 'speed');
      const offsetCap = item.capsules.find(c => c.key === 'offset');
      const nextSpeed = capsuleKey === 'speed' ? check.expression : speedCap.raw;
      const nextOffset = capsuleKey === 'offset' ? check.expression : offsetCap.raw;
      const exprText = '"hsl(" + floor((frameCount * (' + nextSpeed.replace(/^\((.*)\)$/, '$1') + ') + (' + nextOffset.replace(/^\((.*)\)$/, '$1') + ')) % 360) + ", 85%, 65%)"';
      // Keep clean form without extra parens when simple
      const cleanPart = s => /^[A-Za-z0-9_$.]+$/.test(s) || /^[A-Za-z0-9_$.]+\s*\*\s*[A-Za-z0-9_$.]+$/.test(s) ? s : '(' + s + ')';
      const cleanExpr = '"hsl(" + floor((frameCount * ' + cleanPart(nextSpeed) + ' + ' + cleanPart(nextOffset) + ') % 360) + ", 85%, 65%)"';
      return core.proposePatch(projection, {
        type: 'setInput',
        id: item.primaryNodeId,
        input: 'args',
        source: [validateExpression(cleanExpr, sourceType).ok ? cleanExpr : exprText]
      });
    }

    const check = validateExpression(newValue, sourceType);
    if (!check.ok) return { ok: false, errorCode: check.errorCode, error: 'Invalid expression' };
    const byId = nodeMap(projection);
    const isPlainNumber = /^-?(?:\d+\.?\d*|\.\d+)$/.test(check.expression) && Number.isFinite(Number(check.expression));
    const ops = (capsule.targetIds || []).map(targetId => {
      const targetNode = byId.get(targetId);
      if (targetNode && targetNode.kind === 'literal' && targetNode.valueType === 'number' && isPlainNumber && Number(check.expression) >= 0) {
        return { type: 'setField', id: targetId, field: 'value', value: Number(check.expression) };
      }
      const parent = targetNode?.parentId ? byId.get(targetNode.parentId) : null;
      if (parent && ['p5_call', 'call', 'new'].includes(parent.kind) && targetNode.parentInput === 'args' && (capsule.targetIds || []).length === 1) {
        const nextArgs = (parent.inputs?.args || []).map(aid => aid === targetId ? check.expression : (byId.get(aid)?.raw || '0'));
        return { type: 'setInput', id: parent.id, input: 'args', source: nextArgs };
      }
      return {
        type: 'replace',
        id: targetId,
        source: check.expression
      };
    });
    return core.proposePatch(projection, ops);
  }

  function proposeTrailColorEdit(projection, item, hex) {
    const rgb = hexToRgb(hex);
    if (!item || item.easyKind !== 'trail_bg') return { ok: false, error: 'Invalid trail block' };
    const ids = item.rgbNodeIds || [];
    if (ids.length === 3) {
      return core.proposePatch(projection, [
        { type: 'replace', id: ids[0], source: String(rgb.r) },
        { type: 'replace', id: ids[1], source: String(rgb.g) },
        { type: 'replace', id: ids[2], source: String(rgb.b) }
      ]);
    }
    if (ids.length === 1) {
      const alphaCap = item.capsules?.[0];
      return core.proposePatch(projection, {
        type: 'setInput',
        id: item.callNodeId,
        input: 'args',
        source: [String(rgb.r), String(rgb.g), String(rgb.b), alphaCap ? alphaCap.raw : '32']
      });
    }
    return { ok: false, error: 'Unsupported trail arguments' };
  }

  // Convert visual insertion index inside an Easy container body into exact core.proposePatch destination
  function resolveBodyInsertion(projection, bodyDescriptor, visualIndex) {
    const listIds = bodyDescriptor.parentId == null
      ? projection.rootIds
      : (projection.nodes.find(n => n.id === bodyDescriptor.parentId)?.inputs?.[bodyDescriptor.input] || []);
    const children = bodyDescriptor.children || [];
    const clamped = Math.max(0, Math.min(visualIndex, children.length));

    if (clamped < children.length) {
      const targetChild = children[clamped];
      const firstId = targetChild.statementNodeIds[0];
      const idx = listIds.indexOf(firstId);
      if (idx >= 0) return { parentId: bodyDescriptor.parentId, input: bodyDescriptor.input, index: idx };
    }
    if (children.length > 0) {
      const lastChild = children[children.length - 1];
      const lastId = lastChild.statementNodeIds[lastChild.statementNodeIds.length - 1];
      const idx = listIds.indexOf(lastId);
      if (idx >= 0) return { parentId: bodyDescriptor.parentId, input: bodyDescriptor.input, index: idx + 1 };
    }
    if (bodyDescriptor.beforeNodeId) {
      const beforeIdx = listIds.indexOf(bodyDescriptor.beforeNodeId);
      if (beforeIdx >= 0) return { parentId: bodyDescriptor.parentId, input: bodyDescriptor.input, index: beforeIdx };
    }
    if (bodyDescriptor.afterNodeId) {
      const afterIdx = listIds.indexOf(bodyDescriptor.afterNodeId);
      if (afterIdx >= 0) return { parentId: bodyDescriptor.parentId, input: bodyDescriptor.input, index: afterIdx + 1 };
    }
    const base = bodyDescriptor.headSkip || 0;
    const maxIdx = Math.max(base, listIds.length - (bodyDescriptor.tailSkip || 0));
    return { parentId: bodyDescriptor.parentId, input: bodyDescriptor.input, index: Math.min(base, maxIdx) };
  }

  return Object.freeze({
    BLEND_OPTIONS,
    validateExpression,
    formatCapsuleDisplay,
    presetsForRole,
    sliderConfigForRole,
    buildEasyGraph,
    proposeCapsuleEdit,
    proposeTrailColorEdit,
    resolveBodyInsertion,
    hexToRgb,
    rgbToHex
  });
}));
