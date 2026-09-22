// 墨竹 — 参考写真の「太い淡墨の竹・濃い節・鋭い葉」を描く習作
// Edit:RiN: p5.js 2.3.3 / p5.brush ON / 画面に自動フィット
// 全文を sketch.js に貼り付けて実行。外部素材は使いません。
// 竹と葉の濃淡は独自の筆圧・墨溜まり描画、節と枝の乾筆はp5.brush。
const INK_SEED = 8417;
const INK_SIZE = 1000;
const INK_MARGIN = 0.06; // 画面の短辺に対する余白（片側）
let inkPaper;
let inkNodes = [];
let inkTwigs = [];
let inkFinished = false;
let inkRendered = false;
let inkImage = null;

function setup() {
  pixelDensity(1);
  createCanvas(INK_SIZE, INK_SIZE, WEBGL);
  randomSeed(INK_SEED);
  noiseSeed(INK_SEED);
  brush.seed(INK_SEED);
  brush.noiseSeed(INK_SEED);
  inkPaper = createGraphics(INK_SIZE, INK_SIZE);
  inkPaper.pixelDensity(1);
  // アプリのcanvas用CSSが下描き用キャンバスを表示しないようDOMから外す。
  inkPaper.canvas.remove();
  buildInkPainting(inkPaper.drawingContext);
}

function draw() {
  if (inkRendered) {
    if (!inkImage) {
      // p5.brushの描画確定後、次のフレームで完成画を保存。
      inkImage = get();
      resizeCanvas(max(1, windowWidth), max(1, windowHeight), true);
    }
    background('#f2f0e6');
    const size = min(width, height) * (1 - INK_MARGIN * 2);
    push();
    translate(-width / 2, -height / 2);
    image(inkImage, (width-size)/2, (height-size)/2, size, size);
    pop();
    inkFinished = true;
    noLoop();
    return;
  }
  background('#f2f0e6');
  push();
  translate(-INK_SIZE / 2, -INK_SIZE / 2);
  image(inkPaper, 0, 0);
  brush.noFill();
  brush.noHatch();
  brush.noField();
  // 線の幅・筆圧を変え、均一な輪郭線にしない。
  for (const n of inkNodes) {
    brush.set('charcoal', '#22221e', n.dark ? 2.1 : 1.2);
    brush.spline(n.points, 0.65);
    brush.set('HB', n.dark ? '#1a1b18' : '#77776b', n.dark ? 1.8 : 0.8);
    brush.spline(n.points.map(([x,y,p]) => [x,y+2,p*0.75]), 0.6);
  }
  for (const path of inkTwigs) {
    brush.set('2B', '#20211c', 1.3);
    brush.spline(path, 0.6);
  }
  pop();
  inkRendered = true;
}

function windowResized() {
  if (!inkImage) return;
  resizeCanvas(max(1, windowWidth), max(1, windowHeight), true);
  redraw();
}

function buildInkPainting(c) {
  // 微細な紙目。輪郭の上にも同じ紙目が残るよう最後にも施す。
  c.fillStyle = '#f2f0e6'; c.fillRect(0,0,1000,1000);
  const cloud = c.createRadialGradient(280,410,40,600,480,760);
  cloud.addColorStop(0,'rgba(255,255,252,.4)');
  cloud.addColorStop(1,'rgba(194,180,144,.16)');
  c.fillStyle=cloud;c.fillRect(0,0,1000,1000);

  // 後ろの薄い竹。前後で墨の濃度・節間を変える。
  bambooStem(c, [[508,1060],[479,654],[493,352],[485,-60]], 66, .19, false);
  bambooStem(c, [[246,552],[204,176],[189,-110]], 35, .07, false);

  // 奥の葉は淡く、大きく。上半分に重なりを集める。
  const paleLeaves = [
    [516,345,205,230,24,.23], [484,203,255,52,24,.18],
    [541,313,760,156,25,.22], [649,177,998,56,30,.20],
    [705,95,973,170,24,.22], [468,460,230,555,26,.22],
    [702,537,883,634,25,.21], [701,558,948,612,19,.20],
    [324,280,138,450,28,.24], [255,108,15,95,23,.25],
    [315,134,505,233,21,.22], [825,177,1042,298,36,.22],
    [746,99,976,-26,28,.27], [213,149,-45,214,22,.21]
  ];
  paleLeaves.forEach(a=>inkLeaf(c,...a));

  bambooStem(c, [[770,1080],[741,927],[708,480],[756,52],[777,-155]], 110, .86, true);
  bambooStem(c, [[608,1048],[551,642],[508,214],[505,-147]], 83, .70, true);

  // 枝は葉の中心へ集める。葉を等間隔に並べない。
  twig(c, [[546,649],[497,547],[416,423],[346,319],[279,223],[217,113]], 7.5);
  twig(c, [[697,938],[650,818],[622,690],[594,576],[566,480]], 6.5);
  twig(c, [[761,489],[761,384],[778,265],[770,150],[808,61]], 7.2);
  twig(c, [[416,423],[363,397],[319,382],[288,359]], 3.6);
  twig(c, [[346,319],[338,245],[322,185],[291,122]], 3.1);
  twig(c, [[594,576],[628,511],[656,464]], 3.1);
  twig(c, [[778,265],[833,211],[868,189]], 3.6);
  twig(c, [[508,214],[555,140],[575,59],[593,-21]], 3.4);

  // 一筆の押し→腹→抜き。左右非対称の葉と鋭い先端。
  const leaves = [
    [246,126,74,38,22,.59], [233,137,-26,201,32,.83],
    [259,171,54,304,32,.74], [281,228,26,373,28,.96],
    [304,248,66,455,36,.57], [325,240,441,409,35,.96],
    [306,167,464,293,24,.63], [315,116,445,223,21,.81],
    [285,113,227,24,19,.92], [215,103,147,-43,20,.65],
    [253,74,267,131,12,.89], [374,389,101,454,30,.69],
    [346,391,67,552,24,.54], [440,437,576,358,29,.95],
    [414,430,224,530,17,.53], [584,426,713,489,27,.96],
    [579,429,609,351,16,.84], [594,555,624,479,15,.87],
    [615,614,636,540,11,.88], [762,356,849,264,21,.75],
    [779,213,894,337,37,.95], [816,203,1021,297,35,.67],
    [842,192,1052,197,24,.62], [773,186,827,109,17,.91],
    [783,105,647,195,27,.79], [776,82,610,119,25,.59],
    [836,57,978,117,30,.73], [869,40,1005,76,19,.89],
    [792,77,853,-40,21,.65], [563,103,572,29,12,.78]
  ];
  leaves.forEach(a=>inkLeaf(c,...a));

  // 小さい芽・枝先の墨点。
  for (const [x,y,dx,dy] of [[273,208,-14,-33],[352,353,-17,-24],
    [380,379,9,-32],[628,710,-14,-23],[635,734,12,-25],
    [608,623,-11,-26],[782,292,9,-30],[768,128,-11,-19],
    [809,92,8,-22],[547,163,12,-27],[230,105,-8,-19]]) {
    inkLeaf(c,x,y,x+dx,y+dy,5,.88);
  }
  // 濡れた紙へ墨が広がる淡い縁。乾筆の芯は残す。
  const wet = document.createElement('canvas');
  wet.width=1000; wet.height=1000;
  wet.getContext('2d').drawImage(c.canvas,0,0);
  c.save();c.globalCompositeOperation='multiply';c.globalAlpha=.28;
  c.filter='blur(2.5px)';c.drawImage(wet,0,0);c.restore();
  paperGrain(c);
}

function bambooStem(c, joints, width, opacity, dark) {
  for (let k=0;k<joints.length-1;k++) {
    const a=joints[k], b=joints[k+1];
    const dx=b[0]-a[0],dy=b[1]-a[1],len=Math.hypot(dx,dy);
    const nx=-dy/len,ny=dx/len;
    const point=(t,u)=>[a[0]+dx*t+nx*u,a[1]+dy*t+ny*u];
    c.save();
    const path=new Path2D();
    for(let side=0;side<2;side++) for(let j=0;j<=60;j++) {
      const t=side ? 1-j/60 : j/60;
      const waist=1-.12*Math.sin(Math.PI*t);
      const u=(side?1:-1)*width*.5*waist+(noise(t*11,k*7+side*3)-.5)*13; 
      const p=point(.004+t*.993,u);
      if(!side&&!j)path.moveTo(...p);else path.lineTo(...p);
    }
    path.closePath();c.clip(path);
    const left=point(.5,-width/2),right=point(.5,width/2);
    const g=c.createLinearGradient(...left,...right);
    // 左に墨溜まり、中央に水分、右は淡い墨。黒い輪郭で囲わない。
    for(const [t,v] of [[0,.85],[.10,.91],[.29,.56],[.55,.20],[.80,.15],[1,.24]])
      g.addColorStop(t,`rgba(24,26,23,${v*opacity})`);
    c.fillStyle=g;c.fillRect(0,0,1000,1000);
    for(let j=0;j<24;j++) {
      const u=random(-width*.52,width*.52);
      const p=point(0,u),q=point(1,u+random(-5,5));
      c.strokeStyle=`rgba(29,30,26,${random(.012,.065)*opacity})`;
      c.lineWidth=random(1,6);c.beginPath();c.moveTo(...p);
      c.bezierCurveTo(p[0]+random(-8,8),lerp(p[1],q[1],.3),q[0]+random(-8,8),lerp(p[1],q[1],.7),...q);c.stroke();
    }
    // 节の下のにじみを縦に落とす。
    const base=point(.07,-width*.2);
    const pool=c.createRadialGradient(...base,1,...base,width*.85);
    pool.addColorStop(0,`rgba(23,25,21,${opacity*.48})`);
    pool.addColorStop(1,'rgba(23,25,21,0)');
    c.fillStyle=pool;c.fillRect(0,0,1000,1000);c.restore();
    const node=[];
    for(let j=0;j<=10;j++) {
      const u=lerp(-width*.55,width*.55,j/10);
      const p=point(.007+Math.sin(j/10*Math.PI)*.023,u);
      p[1]+=(noise(j*.8,k*11)-.5)*3;
      node.push([...p, .35+Math.sin(j/10*Math.PI)*.9]);
    }
    // 水分のある濃い節を土台にし、p5.brushで紙目を加える。
    c.strokeStyle=`rgba(12,15,11,${opacity*.95})`;c.lineWidth=dark?8:2;
    c.beginPath();node.forEach((p,i)=>i?c.lineTo(p[0],p[1]):c.moveTo(p[0],p[1]));c.stroke();
    // 節の左端に筆を置いた墨溜まりと、割れた毛先。
    if(dark) {
      c.fillStyle='rgba(12,16,11,.75)';
      c.beginPath();c.ellipse(node[0][0]+2,node[0][1]+3,5,9,-.3,0,Math.PI*2);c.fill();
      for(let m=0;m<14;m++) {
        c.strokeStyle=`rgba(239,236,219,${random(.15,.65)})`;c.lineWidth=random(.4,1.2);
        c.beginPath();
        for(let j=1;j<9;j++) {
          const xx=node[j][0], yy=node[j][1]+random(-3,3);
          if(j===1)c.moveTo(xx,yy);else c.lineTo(xx,yy);
        }c.stroke();
      }
    }
    inkNodes.push({points:node,dark});
  }
}

function twig(c, points, weight) {
  for(let i=0;i<points.length-1;i++) {
    const a=points[i],b=points[i+1];
    c.strokeStyle='rgba(15,19,14,.87)';c.lineWidth=weight*(1-i/points.length*.75);
    c.beginPath();c.moveTo(...a);c.quadraticCurveTo((a[0]+b[0])/2+random(-4,4),(a[1]+b[1])/2,...b);c.stroke();
  }
  inkTwigs.push(points.map((p,i)=>[...p,1-i/points.length*.88]));
}

function inkLeaf(c,x,y,tx,ty,w,opacity) {
  const dx=tx-x,dy=ty-y,len=Math.hypot(dx,dy),nx=-dy/len,ny=dx/len;
  const bend=random(-.09,.09)*len;
  const outline=(expand=0)=>{
    const p=new Path2D();
    for(let side=0;side<2;side++)for(let j=0;j<=42;j++) {
      const t=side?1-j/42:j/42;
      // 腹が根元寄りにあり、先端へ長く細く抜ける。
      const belly=Math.pow(Math.sin(Math.PI*Math.pow(t,.65)),1.8);
      const edge=(noise(t*43,x*.03+side*4)-.5)*4.5 + (noise(t*147,y*.03)-.5)*1.8;
      const u=bend*Math.sin(t*Math.PI)+(side?-1:1)*(w*(side?.40:.60)*belly+expand*Math.sin(t*Math.PI))+edge*Math.sin(t*Math.PI);
      const px=x+dx*t+nx*u,py=y+dy*t+ny*u;
      if(!side&&!j)p.moveTo(px,py);else p.lineTo(px,py);
    }p.closePath();return p;
  };
  // 薄い毛羽だけを外周へ。全体をぼかして葉先を失わない。
  c.fillStyle=`rgba(28,30,23,${opacity*.08})`;c.fill(outline(3.5));
  const path=outline();c.save();c.clip(path);
  const g=c.createLinearGradient(x,y,tx,ty);
  g.addColorStop(0,`rgba(8,12,8,${min(1,opacity*1.14)})`);
  g.addColorStop(.18,`rgba(8,11,8,${min(1,opacity*1.2)})`);
  g.addColorStop(.56,`rgba(18,22,15,${min(1,opacity*1.12)})`);
  g.addColorStop(1,`rgba(46,47,34,${opacity*.35})`);
  c.fillStyle=g;c.fillRect(0,0,1000,1000);
  // 筆毛が残す細い濃淡。均一なベタ塗りを避ける。
  for(let j=0;j<16;j++) {
    const off=random(-w*.48,w*.48);
    c.strokeStyle=j%3?`rgba(17,20,14,${opacity*.055})`:`rgba(244,241,226,${random(.04,.13)})`;
    c.lineWidth=random(.35,1);
    c.beginPath();c.moveTo(x+nx*off,y+ny*off);
    c.quadraticCurveTo(x+dx*.42+nx*(off+bend),y+dy*.42+ny*(off+bend),tx,ty);c.stroke();
  }
  c.restore();
}

function paperGrain(c) {
  const img=c.getImageData(0,0,1000,1000),d=img.data;
  // 低周波は水分の偏り、高周波は紙の繊維が吸う墨の粒子。
  for(let y=0;y<1000;y++)for(let x=0;x<1000;x++) {
    const i=(y*1000+x)*4;
    const coarse=noise(x*.018,y*.018);
    const medium=noise(x*.11+21,y*.11);
    const fine=noise(x*.79,y*.71);
    const pigment=1-d[i]/245;
    const modulation=(coarse-.46)*70+(medium-.5)*46;
    const dry=max(0,fine-.48)*260*max(0,medium-.32);
    const grain=random(-4,4);
    for(let k=0;k<3;k++) d[i+k]=constrain(d[i+k]+pigment*(modulation+dry)+grain,0,255);
  }
  c.putImageData(img,0,0);
}
