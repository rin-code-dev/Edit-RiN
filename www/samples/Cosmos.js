// Cosmic Nebula & Starfield (GLSL Showcase)
// @rin number warpSpeed "Warp Speed" 0.0 3.0 1.0 0.1
// @rin number starDensity "Starfield" 0.5 2.5 1.2 0.1
// @rin color nebula "Nebula Dust" #6C00FF
// @rin color core "Core Singularity" #00F0FF

let cosmosShader;

function setup() {
  createCanvas(400, 400, WEBGL);
  noStroke();

  if (typeof rinShaders !== 'undefined' && rinShaders['cosmos.vert'] && rinShaders['cosmos.frag']) {
    cosmosShader = createShader(rinShaders['cosmos.vert'], rinShaders['cosmos.frag']);
  } else {
    cosmosShader = loadShader('cosmos.vert', 'cosmos.frag');
  }
}

function hexToRgb01(hex, fallback) {
  if (typeof hex !== 'string') return fallback;
  const c = hex.replace('#', '');
  const n = parseInt(c.length === 3 ? c.split('').map(x => x + x).join('') : c, 16);
  if (isNaN(n)) return fallback;
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

function draw() {
  shader(cosmosShader);

  const p = (typeof rinParams !== 'undefined') ? rinParams : {};
  const warp = Number(p.warpSpeed ?? 1.0);
  const stars = Number(p.starDensity ?? 1.2);
  const [nebR, nebG, nebB] = hexToRgb01(p.nebula, [0.42, 0.0, 1.0]);
  const [coreR, coreG, coreB] = hexToRgb01(p.core, [0.0, 0.94, 1.0]);

  cosmosShader.setUniform('u_resolution', [width, height]);
  cosmosShader.setUniform('u_time', millis() * 0.001);
  cosmosShader.setUniform('u_mouse', [mouseX, mouseY]);
  cosmosShader.setUniform('u_warp', warp);
  cosmosShader.setUniform('u_stars', stars);
  cosmosShader.setUniform('u_nebulaColor', [nebR, nebG, nebB]);
  cosmosShader.setUniform('u_coreColor', [coreR, coreG, coreB]);

  // p5.js WebGL標準の全画面プレーン描画
  plane(width, height);
}
