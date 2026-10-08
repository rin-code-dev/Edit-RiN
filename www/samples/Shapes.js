// Palette — balance
// Tap to change the palette.
const palettes = [
  ['#F2EFE7', '#2B2825', '#D67856', '#C8C2B8'],
  ['#1C1B1A', '#F2EFE7', '#D67856', '#302D2B'],
  ['#E6E2DD', '#34302D', '#D67856', '#C8C2B8'],
];
let paletteIndex = 0;
function setup() {
  createCanvas(600, 600);
  pixelDensity(1);
  noLoop();
}
function draw() {
  const colors = palettes[paletteIndex],
    unit = min(width, height);
  background(colors[0]);
  noStroke();
  push();
  translate(width / 2, height / 2);
  // A disc, its cutout, and one counterweight.
  fill(colors[1]);
  circle(unit * 0.055, -unit * 0.025, unit * 0.57);
  fill(colors[0]);
  circle(unit * 0.16, -unit * 0.12, unit * 0.43);
  fill(colors[2]);
  rect(-unit * 0.28, -unit * 0.04, unit * 0.14, unit * 0.36);
  fill(colors[3]);
  circle(unit * 0.29, unit * 0.27, unit * 0.047);
  pop();
}
function changePalette() {
  paletteIndex = (paletteIndex + 1) % palettes.length;
  redraw();
}
function mousePressed() {
  changePalette();
  return false;
}
