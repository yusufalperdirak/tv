(function () {
'use strict';
var $ = function (i) { return document.getElementById(i); };
var N = window.Native || null;              // Android koprusu (tarayicida yok -> demo)
function call(fn) { if (!N) return; try { N[fn].apply(N, [].slice.call(arguments, 1)); } catch (e) {} }
var st = $('st'), SC = 1;

/* ---------- olcekleme ---------- */
function fit() {
  var s = Math.min(innerWidth / 1024, innerHeight / 600); SC = s;
  st.style.transform = 'scale(' + s + ')';
  st.style.left = ((innerWidth - 1024 * s) / 2) + 'px';
  st.style.top = ((innerHeight - 600 * s) / 2) + 'px';
  if (cur === 'nav') map.draw(true);
}

/* ---------- sekmeler ---------- */
var TABX = [0, 327, 653, 980], TABS = ['ana', 'nav', 'arac'], PAGES = ['ana', 'nav', 'arac', 'izin', 'keys'], cur = 'ana';
function frame() {
  var i = TABS.indexOf(cur === 'izin' || cur === 'keys' ? 'arac' : cur), sl = 14, base = 62, s = '';
  var tp = function (a, b) { return 'M' + a + ' ' + base + ' L' + a + ' 10 Q' + a + ' 2 ' + (a + 8) + ' 2 L' + (b - sl - 8) + ' 2 Q' + (b - sl) + ' 2 ' + (b - sl + 4) + ' 8 L' + b + ' ' + base; };
  for (var k = 0; k < 3; k++) s += '<path d="' + tp(TABX[k] + 2, TABX[k + 1] - 2) + '" fill="' + (k === i ? '#FFE53A' : 'none') + '" stroke="#FFE53A" stroke-width="2.4" stroke-linejoin="round"/>';
  s += '<path d="M0 ' + base + ' L' + (TABX[i] + 2) + ' ' + base + ' M' + (TABX[i + 1] - 2) + ' ' + base + ' L980 ' + base + '" stroke="#FFE53A" stroke-width="2.4"/>';
  $('frame').innerHTML = s;
  var tb = document.querySelectorAll('.tb');
  for (var j = 0; j < tb.length; j++) { var on = TABS[j] === TABS[i]; tb[j].classList.toggle('on', on); tb[j].setAttribute('aria-pressed', on); }
}
function show(p) {
  cur = p; frame();
  for (var k = 0; k < PAGES.length; k++) $('p-' + PAGES[k]).classList.toggle('show', PAGES[k] === p);
  if (p === 'nav') map.start(); else map.stop();
  if (p === 'keys' && S.keys.learning) {}
  if (p !== 'keys' && S.keys.learning) call('cancelLearn');
}
var tbs = document.querySelectorAll('.tb');
for (var t = 0; t < tbs.length; t++) (function (b) { b.onclick = function () { if (b.dataset.t === 'arac') showInfo(); show(b.dataset.t); }; })(tbs[t]);
function showInfo() { $('sets').style.display = 'none'; $('info').style.display = ''; }
function showSets() { $('info').style.display = 'none'; $('sets').style.display = ''; }
$('toSet').onclick = showSets;
$('toInfo').onclick = showInfo;
$('toIz').onclick = function () { show('izin'); call('refreshPerms'); };
$('izBack').onclick = function () { show('arac'); showSets(); };
$('toKeys').onclick = function () { show('keys'); };
$('kBack').onclick = function () { show('arac'); showSets(); };
$('sSound').onclick = function () { call('openSys', 'sound'); };
$('sBt').onclick = function () { call('openSys', 'bt'); };
$('kacc').onclick = function () { call('grant', 'acc'); };
window.onBack = function () {
  if (cur === 'izin' || cur === 'keys') { show('arac'); showSets(); return true; }
  if (cur !== 'ana') { show('ana'); return true; }
  return true;
};

/* ---------- durum ---------- */
var S = {
  media: null, loc: null, wx: null, nav: null,
  cp: { found: false, name: '' }, perms: [], keys: { acc: false, learning: null, map: {} }, ver: ''
};
window.onNative = function (d) {
  if (!d) return;
  if (typeof d === 'string') { try { d = JSON.parse(d); } catch (e) { return; } }
  for (var k in d) if (Object.prototype.hasOwnProperty.call(d, k)) S[k] = d[k];
  if ('media' in d) renderMedia();
  if ('loc' in d) { renderLoc(); map.loc(S.loc); }
  if ('wx' in d) renderWx();
  if ('nav' in d) { renderNav(); map.draw(true); }
  if ('cp' in d) renderCp();
  if ('perms' in d) renderPerms();
  if ('keys' in d) renderKeys();
  if ('ver' in d) $('ver').textContent = 'GurbetMap Sport ' + S.ver;
  if ('page' in d && d.page) show(d.page);
};

/* ---------- muzik ---------- */
var PLAY = 'M6 4h4v16H6zM14 4h4v16h-4z', PAUSE = 'M7 4l13 8-13 8z';
function txt(id, v) { var e = $(id); if (e.textContent !== v) e.textContent = v; }
function fmtFreq(f) { if (!f) return ''; var n = parseFloat(String(f).replace(',', '.')); if (isNaN(n)) return String(f); return n.toFixed(1).replace('.', ',') + ' MHz'; }
function renderMedia() {
  var m = S.media || {}, src = m.src || null;
  var srcEl = $('src');
  if (src === 'radio') {
    var bandHtml = 'FM' + (m.preset ? '<span class="pn">' + m.preset + '</span>' : '');
    if (srcEl.innerHTML !== bandHtml) srcEl.innerHTML = bandHtml;
    txt('i1', m.tp ? 'TP' : ''); txt('i2', m.ps ? 'RDS' : ''); txt('i3', '');
    txt('stn', m.ps || fmtFreq(m.freq) || m.title || 'Radyo');
    var sub = [];
    if (m.ps && m.freq) sub.push(fmtFreq(m.freq));
    if (m.rt) sub.push(m.rt); else if (m.title && m.title !== m.ps) sub.push(m.title);
    txt('sub', sub.join(' · '));
  } else if (src) {
    if (srcEl.textContent !== 'BT') srcEl.textContent = 'BT';
    txt('i1', 'BT'); txt('i2', 'AUDIO'); txt('i3', m.device || '');
    txt('stn', m.title || 'Bluetooth');
    txt('sub', [m.artist, m.album].filter(Boolean).join(' · '));
  } else {
    srcEl.textContent = '';
    txt('i1', ''); txt('i2', ''); txt('i3', '');
    txt('stn', 'Ses kaynağı yok');
    txt('sub', 'Radyo veya Bluetooth seçin');
  }
  $('kR').classList.toggle('on', src === 'radio');
  $('kB').classList.toggle('on', !!src && src !== 'radio');
  $('pi').setAttribute('d', m.playing ? PLAY : PAUSE);
  $('pp').setAttribute('aria-label', m.playing ? 'Duraklat' : 'Oynat');
  var showProg = src && src !== 'radio' && m.dur > 0;
  $('prog').style.display = showProg ? 'block' : 'none';
  progBase = { pos: m.pos || 0, dur: m.dur || 0, at: Date.now(), playing: !!m.playing };
  tickProg();
}
var progBase = { pos: 0, dur: 0, at: 0, playing: false };
function tickProg() {
  if (!progBase.dur) return;
  var p = progBase.pos + (progBase.playing ? Date.now() - progBase.at : 0);
  $('pg').style.width = Math.max(0, Math.min(100, p / progBase.dur * 100)) + '%';
}
$('kR').onclick = function () { call('source', 'radio'); };
$('kB').onclick = function () { call('source', 'bt'); };
$('pv').onclick = function () { call('media', 'prev'); };
$('nx').onclick = function () { call('media', 'next'); };
$('pp').onclick = function () { call('media', 'toggle'); };

/* ---------- konum / hava / arac bilgileri ---------- */
var YON = ['Kuzey', 'Kuzeydoğu', 'Doğu', 'Güneydoğu', 'Güney', 'Güneybatı', 'Batı', 'Kuzeybatı'];
function yon(b) { return YON[Math.round(((b % 360) + 360) % 360 / 45) % 8]; }
function setB(id, html) { var e = $(id); if (e.innerHTML !== html) e.innerHTML = html; }
function renderLoc() {
  var l = S.loc;
  if (!l || !l.ok) { setB('v1', '–'); setB('v2', '–'); setB('v4', '–'); setB('v5', l && l.sats ? String(l.sats) : '–'); txt('sv', '–'); txt('sh', 'GPS bekleniyor'); return; }
  var kmh = Math.round((l.speed || 0) * 3.6);
  setB('v1', kmh + '<small>km/s</small>');
  setB('v2', (l.speed || 0) > 0.8 ? yon(l.bearing || 0) : '–');
  setB('v4', l.alt != null ? Math.round(l.alt) + '<small>m</small>' : '–');
  setB('v5', l.sats != null ? String(l.sats) : '–');
  txt('sv', String(kmh)); txt('sh', 'km/s' + ((l.speed || 0) > 0.8 ? ' · ' + yon(l.bearing || 0) : ''));
}
function renderWx() {
  var w = S.wx;
  if (!w || w.t == null) { setB('v3', '–'); txt('btmp', '--°'); return; }
  var tt = Math.round(w.t);
  setB('v3', tt + '°<small>' + (w.txt || '') + '</small>');
  txt('wxl', 'Hava' + (w.city ? ' · ' + w.city : ''));
  setB('v6', w.city || '–');
  txt('btmp', tt + '.0°');
}

/* ---------- navigasyon bilgisi ---------- */
function renderNav() {
  var n = S.nav;
  var on = !!(n && n.on);
  $('ninfo').style.display = on ? '' : 'none';
  $('spd').style.display = on ? 'none' : '';
  if (on) {
    txt('ndist', n.dist || '');
    txt('ntext', n.text || '');
    var d = n.turn === 'left' ? 'M16 20V11a3 3 0 00-3-3H5M9 4L5 8l4 4' : n.turn === 'right' ? 'M8 20V11a3 3 0 013-3h8M15 4l4 4-4 4' : n.turn === 'uturn' ? 'M8 20V9a4 4 0 018 0v3M13 9l3 3 3-3' : 'M12 20V5M6 11l6-6 6 6';
    $('tarrow').setAttribute('d', d);
    txt('nr1', n.eta || ''); txt('nr2', n.app || '');
  } else { txt('nr1', ''); txt('nr2', ''); }
}

/* ---------- CarPlay ---------- */
function renderCp() {
  var c = S.cp || {};
  $('go').disabled = !c.found;
  if (!opening) {
    txt('cph', c.found ? 'iPhone bekleniyor' : 'CP-Link bulunamadı');
    txt('go', c.found ? "CarPlay'i Başlat" : 'Uygulama yüklü değil');
  }
}
var opening = false, si = 0;
setInterval(function () { si = (si + 1) % 3; var d = $('steps').children; for (var k = 0; k < 3; k++) d[k].classList.toggle('on', k === si); }, 1600);
$('go').onclick = function () {
  if (opening || !(S.cp && S.cp.found)) return;
  opening = true; txt('go', 'Açılıyor…'); call('carplay');
  setTimeout(function () { opening = false; renderCp(); }, 2500);
};

/* ---------- izinler ---------- */
var IZD = {
  loc: ['Konum (GPS)', 'Harita, hız ve hava durumu için', 'M12 21s-7-6.3-7-12a7 7 0 0114 0c0 5.7-7 12-7 12zM12 11.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z'],
  bt: ['Yakındaki cihazlar', 'iPhone ve Bluetooth müzik bağlantısı', 'M7 7l10 10-5 5V2l5 5L7 17'],
  notif: ['Bildirim erişimi', 'Şarkı bilgisi, müzik kontrolü ve CP-Link tarifleri', 'M6 8a6 6 0 0112 0c0 7 3 9 3 9H3s3-2 3-9M10 21h4'],
  acc: ['Erişilebilirlik', 'Direksiyon ve radyo tuşlarını yakalamak için', 'M12 4a2 2 0 100 .1M5 8h14M12 8v6M9 21l3-7 3 7'],
  overlay: ['Diğer uygulamaların üzerinde', 'Radyo açılınca ana ekrana geri dönebilmek için', 'M4 5h12v10H4zM8 9h12v10H8z'],
  battery: ['Arka planda çalışma', 'Pil kısıtlamasını kaldırır, bağlantılar kesilmez', 'M3 7h16v10H3zM21 10v4M6 10h6v4H6z'],
  home: ['Varsayılan ana ekran', 'Kontak açılınca GurbetMap açılsın', 'M3 11l9-7 9 7M5 10v10h14V10']
};
var CHECK = '<span class="ok"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12l5 5 9-10"/></svg>Verildi</span>';
function renderPerms() {
  var P = S.perms || [], g = 0, h = '', seg = '';
  for (var i = 0; i < P.length; i++) {
    var p = P[i], d = IZD[p.id]; if (!d) continue;
    if (p.ok) g++;
    seg += '<i class="' + (p.ok ? 'f' : '') + '"></i>';
    h += '<div class="iz"><svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="' + d[2] + '"/></svg><span class="n">' + d[0] + '</span><span class="w">' + d[1] + '</span>' + (p.ok ? CHECK : '<button class="key on" data-p="' + p.id + '">İzin ver</button>') + '</div>';
  }
  if ($('izl').innerHTML !== h) {
    $('izl').innerHTML = h;
    var bs = $('izl').querySelectorAll('button[data-p]');
    for (var j = 0; j < bs.length; j++) (function (b) { b.onclick = function () { call('grant', b.dataset.p); }; })(bs[j]);
  }
  $('segs').innerHTML = seg;
  txt('izt', g + '/' + P.length + ' verildi');
  txt('izs', g + '/' + P.length + ' ›');
}

/* ---------- direksiyon tuslari ---------- */
var KA = [['next', 'Sonraki'], ['prev', 'Önceki'], ['play', 'Oynat / Duraklat'], ['volup', 'Ses +'], ['voldown', 'Ses −'], ['source', 'Radyo ↔ Bluetooth'], ['carplay', 'CarPlay'], ['home', 'Ana ekran']];
function renderKeys() {
  var K = S.keys || {}, M = K.map || {}, h = '', n = 0;
  for (var i = 0; i < KA.length; i++) {
    var a = KA[i][0], k = M[a], learning = K.learning === a;
    if (k) n++;
    h += '<div class="iz"><span class="n">' + KA[i][1] + '</span><span class="w' + (learning ? ' blink' : '') + '">' + (learning ? 'Tuşa basın…' : (k || 'Atanmadı')) + '</span><span class="kbtns">' +
      (learning ? '<button class="key" data-c="1">Vazgeç</button>' : '<button class="key on" data-l="' + a + '">Öğret</button>' + (k ? '<button class="key" data-u="' + a + '">Sil</button>' : '')) + '</span></div>';
  }
  if ($('kl').innerHTML !== h) {
    $('kl').innerHTML = h;
    var b = $('kl').querySelectorAll('button');
    for (var j = 0; j < b.length; j++) (function (x) {
      x.onclick = function () {
        if (x.dataset.l) call('learn', x.dataset.l);
        else if (x.dataset.u) call('unlearn', x.dataset.u);
        else call('cancelLearn');
      };
    })(b[j]);
  }
  $('kwarn').style.display = K.acc ? 'none' : 'flex';
  $('kl').style.top = K.acc ? '66px' : '118px';
  txt('kst', n + '/' + KA.length + ' atandı');
  txt('kss', n ? n + ' tuş ›' : 'Öğret ›');
}

/* ---------- saat ---------- */
var DAYS = ['Pazar', 'Pazartesi', 'Salı', 'Çarşamba', 'Perşembe', 'Cuma', 'Cumartesi'], MON = ['Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran', 'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'];
var lastMin = -1;
function tick() {
  var d = new Date(), m = d.getMinutes();
  if (m !== lastMin) {
    lastMin = m;
    $('clk').innerHTML = d.getHours() + '<i>:</i>' + ('0' + m).slice(-2);
    txt('date', DAYS[d.getDay()] + ', ' + d.getDate() + ' ' + MON[d.getMonth()]);
  }
  tickProg();
}

/* ---------- harita: sonsuz sehir dokusu, GPS ile akar ---------- */
var map = (function () {
  var w = document.querySelector('.mapw'), cv = w.querySelector('canvas'), an = w.querySelector('.anc'), pk = document.querySelector('.puck');
  var K = 1.1, STEP = 150, TILE = 1200, Y = '255,229,58';
  var wx = 600, wy = 600, b = 0, tb = 0, v = 0, last = 0, timer = null, tile = null, dirty = true;
  function seeded(s) { return function () { s = (s * 9301 + 49297) % 233280; return s / 233280; }; }
  function makeTile() {
    var c = document.createElement('canvas'); c.width = TILE; c.height = TILE;
    var g = c.getContext('2d'), r = seeded(11), n = TILE / STEP;
    for (var i = 0; i < n; i++) for (var j = 0; j < n; j++) {
      var x0 = i * STEP + 9, y0 = j * STEP + 9, s = STEP - 18;
      if (r() < .08) { g.fillStyle = 'rgba(' + Y + ',.05)'; g.fillRect(x0, y0, s, s); g.strokeStyle = 'rgba(' + Y + ',.18)'; g.setLineDash([6, 6]); g.lineWidth = 1.5; g.strokeRect(x0, y0, s, s); g.setLineDash([]); continue; }
      var nx = r() < .5 ? 2 : 3, ny = r() < .5 ? 2 : 3, cw = s / nx, ch = s / ny;
      for (var a = 0; a < nx; a++) for (var q = 0; q < ny; q++) {
        if (r() < .18) continue;
        var pd = 4 + r() * 4;
        g.fillStyle = 'rgba(60,20,4,.35)'; g.fillRect(x0 + a * cw + pd, y0 + q * ch + pd, cw - 2 * pd, ch - 2 * pd);
        g.strokeStyle = 'rgba(' + Y + ',.22)'; g.lineWidth = 1.3; g.strokeRect(x0 + a * cw + pd, y0 + q * ch + pd, cw - 2 * pd, ch - 2 * pd);
      }
    }
    for (var k = 0; k < n; k++) {
      var major = k % 4 === 0;
      g.strokeStyle = 'rgba(' + Y + ',' + (major ? .6 : .3) + ')'; g.lineWidth = major ? 7 : 3;
      g.beginPath(); g.moveTo(k * STEP, 0); g.lineTo(k * STEP, TILE); g.stroke();
      g.beginPath(); g.moveTo(0, k * STEP); g.lineTo(TILE, k * STEP); g.stroke();
    }
    return c;
  }
  function draw(force) {
    if (!w.offsetParent) return;
    if (!force && !dirty) return;
    dirty = false;
    var W = cv.offsetWidth, H = cv.offsetHeight; if (!W || !H) return;
    if (cv.width !== W || cv.height !== H) { cv.width = W; cv.height = H; }
    if (!tile) tile = makeTile();
    var g = cv.getContext('2d'), px = W / 2, py = H * .70;
    g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, W, H);
    g.save(); g.translate(px, py); g.scale(K, K); g.rotate(-b * Math.PI / 180); g.translate(-wx, -wy);
    var R = Math.hypot(W, H) / K * .62, x0 = Math.floor((wx - R) / TILE) * TILE, y0 = Math.floor((wy - R) / TILE) * TILE;
    for (var x = x0; x < wx + R; x += TILE) for (var y = y0; y < wy + R; y += TILE) g.drawImage(tile, x, y);
    g.restore();
    var n = S.nav;
    if (n && n.on) {
      var m = Math.max(40, Math.min(420, (n.meters || 300) / 2)) * K, side = n.turn === 'left' ? -1 : n.turn === 'right' ? 1 : 0;
      g.save(); g.lineJoin = 'round'; g.lineCap = 'round'; g.shadowColor = 'rgba(255,229,58,.8)'; g.shadowBlur = 18; g.strokeStyle = '#FFE53A'; g.lineWidth = 16;
      g.beginPath(); g.moveTo(px, py); g.lineTo(px, py - m); if (side) g.lineTo(px + side * 260 * K, py - m); g.stroke(); g.restore();
    }
    an.style.left = px + 'px'; an.style.top = py + 'px';
    var r = an.getBoundingClientRect(), sr = st.getBoundingClientRect();
    pk.style.left = ((r.left - sr.left) / SC) + 'px'; pk.style.top = ((r.top - sr.top) / SC) + 'px';
  }
  function step() {
    var now = Date.now(), dt = Math.min(.5, (now - last) / 1000); last = now;
    var db = ((tb - b + 540) % 360) - 180; b = (b + db * Math.min(1, dt * 4) + 360) % 360;
    if (v > .3) { var rad = b * Math.PI / 180; wx += Math.sin(rad) * v * 2 * dt; wy -= Math.cos(rad) * v * 2 * dt; wx = ((wx % TILE) + TILE) % TILE + TILE; wy = ((wy % TILE) + TILE) % TILE + TILE; }
    if (v > .3 || Math.abs(db) > .5) { dirty = true; draw(); }
  }
  return {
    draw: draw,
    loc: function (l) { if (!l || !l.ok) { v = 0; return; } v = l.speed || 0; if (v > .8 && l.bearing != null) tb = l.bearing; },
    start: function () { dirty = true; last = Date.now(); setTimeout(function () { draw(true); }, 0); if (!timer) timer = setInterval(step, 100); },
    stop: function () { if (timer) { clearInterval(timer); timer = null; } }
  };
})();

/* ---------- baslat ---------- */
addEventListener('resize', fit);
frame(); fit(); renderMedia(); renderLoc(); renderWx(); renderNav(); renderCp(); renderKeys(); tick();
setInterval(tick, 1000);
if (N) { call('ready'); }
else {
  // Tarayicida onizleme: ornek veriler
  window.onNative({
    media: { src: 'radio', freq: 98.4, ps: 'ANTENNA 5', rt: 'Şimdi: Gece yolu', playing: true, preset: 3, tp: true },
    loc: { ok: true, speed: 13.9, bearing: 30, alt: 245, sats: 11 },
    wx: { t: 8, txt: 'bulutlu', city: 'Skopje' },
    nav: { on: false },
    cp: { found: true, name: 'CP-Link' },
    perms: [{ id: 'loc', ok: true }, { id: 'bt', ok: true }, { id: 'notif', ok: false }, { id: 'acc', ok: false }, { id: 'overlay', ok: true }, { id: 'battery', ok: true }, { id: 'home', ok: true }],
    keys: { acc: false, learning: null, map: { next: 'Tuş 87' } }, ver: '11.0 (önizleme)'
  });
  $('kR').onclick = function () { window.onNative({ media: { src: 'radio', freq: 98.4, ps: 'ANTENNA 5', rt: 'Şimdi: Gece yolu', playing: true, preset: 3, tp: true } }); };
  $('kB').onclick = function () { window.onNative({ media: { src: 'bt', title: 'Şarkı adı', artist: 'Sanatçı', album: 'Albüm', playing: true, pos: 60000, dur: 215000, device: 'iPhone' } }); };
  $('pp').onclick = function () { var m = S.media || {}; m.playing = !m.playing; window.onNative({ media: m }); };
}
})();
