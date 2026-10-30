/**
 * موتور نمودار SVG سبک و بدون وابستگی
 * - areaChart  : نمودار ناحیه‌ای چندسری با مورف بین داده‌ها و تولتیپ
 * - barChart   : نمودار میله‌ای با رشد مرحله‌ای
 * - donutChart : نمودار دونات تعاملی
 * - gauge      : نشانگر نیم‌دایره‌ای پیشرفت
 * - sparkline  : نمودار کوچک داخل کارت‌ها
 * همه‌ی رنگ‌ها از توکن‌های CSS خوانده می‌شوند تا با تغییر تم هماهنگ باشند.
 */
(function (Nava) {
  'use strict';
  const { fa, tween, escape, clamp, lerp, ease } = Nava.utils;
  const NS = 'http://www.w3.org/2000/svg';
  let uid = 0;

  /* ---------- کمکی‌ها ---------- */
  function h(tag, attrs, parent) {
    const el = document.createElementNS(NS, tag);
    for (const k in attrs) el.setAttribute(k, attrs[k]);
    parent && parent.appendChild(el);
    return el;
  }
  const hsl = (token, a) => (a == null ? 'hsl(var(--' + token + '))' : 'hsl(var(--' + token + ') / ' + a + ')');

  /** مسیر نرم (Catmull-Rom → Bézier) با محدودکردن نقاط کنترل */
  function smoothPath(pts, minY, maxY) {
    if (!pts.length) return '';
    let d = 'M' + pts[0][0] + ',' + pts[0][1];
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || p2;
      const c1x = p1[0] + (p2[0] - p0[0]) / 6, c1y = clamp(p1[1] + (p2[1] - p0[1]) / 6, minY, maxY);
      const c2x = p2[0] - (p3[0] - p1[0]) / 6, c2y = clamp(p2[1] - (p3[1] - p1[1]) / 6, minY, maxY);
      d += 'C' + c1x + ',' + c1y + ' ' + c2x + ',' + c2y + ' ' + p2[0] + ',' + p2[1];
    }
    return d;
  }

  /** نمونه‌برداری مجدد آرایه به طول n (برای مورف بین بازه‌های با طول متفاوت) */
  function resample(arr, n) {
    if (arr.length === n) return arr.slice();
    return Array.from({ length: n }, (_, i) => {
      const pos = (i / (n - 1 || 1)) * (arr.length - 1);
      const lo = Math.floor(pos), hi = Math.min(arr.length - 1, lo + 1);
      return lerp(arr[lo], arr[hi], pos - lo);
    });
  }

  function niceMax(v) {
    const p = Math.pow(10, Math.floor(Math.log10(v || 1)));
    return Math.ceil(v / p / 2) * p * 2 || 1;
  }

  function observeSize(el, cb) {
    let w = 0, ht = 0;
    const ro = new ResizeObserver(() => {
      if (el.clientWidth !== w || el.clientHeight !== ht) { w = el.clientWidth; ht = el.clientHeight; cb(w, ht); }
    });
    ro.observe(el);
  }

  function tooltip(host) {
    const tip = document.createElement('div');
    tip.className = 'chart-tooltip';
    tip.style.opacity = '0';
    host.appendChild(tip);
    return {
      show(html, x, y) {
        tip.innerHTML = html;
        const maxX = host.clientWidth - tip.offsetWidth;
        const left = clamp(x - tip.offsetWidth / 2, 0, maxX);
        tip.style.left = left + 'px';
        tip.style.top = Math.max(0, y - tip.offsetHeight - 14) + 'px';
        tip.style.opacity = '1';
        tip.style.transform = 'translateY(0)';
      },
      hide() { tip.style.opacity = '0'; tip.style.transform = 'translateY(4px)'; },
    };
  }

  /* =====================================================================
     نمودار ناحیه‌ای
     ===================================================================== */
  function areaChart(host, config) {
    const id = 'ac' + (++uid);
    const pad = { t: 12, r: 44, b: 30, l: 6 };
    let data = config;
    let shown = config.series.map((s) => s.values.map(() => 0)); // مقادیر در حال نمایش
    let W = 0, H = 0, max = 1;
    let hoverIndex = -1;

    const svg = h('svg', { class: 'absolute inset-0 h-full w-full overflow-visible', style: 'direction:ltr' }, host);
    const defs = h('defs', {}, svg);
    const clip = h('clipPath', { id: id + 'clip' }, defs);
    const clipRect = h('rect', { x: 0, y: -20, width: 0, height: 0 }, clip);
    const gGrid = h('g', {}, svg);
    const gY = h('g', { class: 'fill-muted-foreground text-[11px]' }, svg);
    const gX = h('g', { class: 'fill-muted-foreground text-[11px]' }, svg);
    const gSeries = h('g', { 'clip-path': 'url(#' + id + 'clip)' }, svg);
    const cursor = h('line', { class: 'stroke-muted-foreground/40', 'stroke-dasharray': '4 4', opacity: 0, style: 'transition:opacity .2s' }, svg);
    const gDots = h('g', { opacity: 0, style: 'transition:opacity .2s' }, svg);
    const overlay = h('rect', { fill: 'transparent', style: 'cursor:crosshair' }, svg);
    const tip = tooltip(host);

    const layers = data.series.map((s, i) => {
      const grad = h('linearGradient', { id: id + 'g' + i, x1: 0, y1: 0, x2: 0, y2: 1 }, defs);
      h('stop', { offset: '0%', style: 'stop-color:' + hsl(s.color) + ';stop-opacity:' + (i === 0 ? .32 : .16) }, grad);
      h('stop', { offset: '100%', style: 'stop-color:' + hsl(s.color) + ';stop-opacity:0' }, grad);
      return {
        area: h('path', { fill: 'url(#' + id + 'g' + i + ')' }, gSeries),
        line: h('path', { fill: 'none', stroke: hsl(s.color), 'stroke-width': i === 0 ? 2.5 : 2, 'stroke-linecap': 'round', 'stroke-dasharray': i === 0 ? '' : '6 5' }, gSeries),
        dot: h('circle', { r: 5, fill: hsl('card'), stroke: hsl(s.color), 'stroke-width': 2.5 }, gDots),
      };
    });

    const xAt = (i, n) => W - pad.r - (i * (W - pad.r - pad.l)) / Math.max(1, n - 1); // راست به چپ
    const yAt = (v) => pad.t + (1 - v / max) * (H - pad.t - pad.b);

    function drawAxes() {
      gGrid.innerHTML = ''; gY.innerHTML = ''; gX.innerHTML = '';
      for (let i = 0; i <= 4; i++) {
        const v = (max / 4) * i, y = yAt(v);
        h('line', { x1: pad.l, x2: W - pad.r, y1: y, y2: y, class: 'stroke-border', 'stroke-dasharray': i ? '3 5' : '' }, gGrid);
        const t = h('text', { x: W - pad.r + 10, y: y + 4, 'text-anchor': 'start' }, gY);
        t.textContent = fa(Math.round(v));
      }
      const n = data.labels.length;
      const step = Math.ceil(n / Math.max(2, Math.floor(W / 64)));
      data.labels.forEach((label, i) => {
        if (i % step && i !== n - 1) return;
        const t = h('text', { x: xAt(i, n), y: H - 8, 'text-anchor': 'middle' }, gX);
        t.textContent = label;
      });
    }

    function drawSeries() {
      const n = data.labels.length;
      layers.forEach((L, si) => {
        const pts = shown[si].map((v, i) => [xAt(i, n), yAt(v)]);
        const line = smoothPath(pts, pad.t, H - pad.b);
        L.line.setAttribute('d', line);
        L.area.setAttribute('d', line + 'L' + pts[pts.length - 1][0] + ',' + (H - pad.b) + 'L' + pts[0][0] + ',' + (H - pad.b) + 'Z');
      });
      if (hoverIndex > -1) positionHover(hoverIndex);
    }

    function layout(w, ht) {
      W = w; H = ht;
      clipRect.setAttribute('height', H + 40);
      if (clipRect.dataset.done) { clipRect.setAttribute('width', W); clipRect.setAttribute('x', 0); }
      overlay.setAttribute('x', pad.l); overlay.setAttribute('y', 0);
      overlay.setAttribute('width', Math.max(0, W - pad.r - pad.l)); overlay.setAttribute('height', H - pad.b);
      cursor.setAttribute('y1', pad.t); cursor.setAttribute('y2', H - pad.b);
      drawAxes(); drawSeries();
    }

    function computeMax() {
      max = niceMax(Math.max.apply(null, data.series.flatMap((s) => s.values)) * 1.1);
    }

    function positionHover(i) {
      const n = data.labels.length, x = xAt(i, n);
      cursor.setAttribute('x1', x); cursor.setAttribute('x2', x);
      layers.forEach((L, si) => { L.dot.setAttribute('cx', x); L.dot.setAttribute('cy', yAt(shown[si][i])); });
      const rows = data.series.map((s, si) =>
        '<div class="mt-1 flex items-center justify-between gap-4"><span class="flex items-center gap-1.5 text-muted-foreground"><span class="size-2 rounded-full" style="background:' + hsl(s.color) + '"></span>' + escape(s.name) + '</span><b class="tabular">' + fa(Math.round(data.series[si].values[i])) + '</b></div>'
      ).join('');
      tip.show('<p class="font-semibold">' + escape(data.labels[i]) + '</p>' + rows, x, yAt(Math.max.apply(null, shown.map((s) => s[i]))));
    }

    overlay.addEventListener('pointermove', (e) => {
      const r = svg.getBoundingClientRect();
      const x = e.clientX - r.left, n = data.labels.length;
      const i = clamp(Math.round(((W - pad.r - x) / (W - pad.r - pad.l)) * (n - 1)), 0, n - 1);
      if (i === hoverIndex) return;
      hoverIndex = i;
      cursor.setAttribute('opacity', 1); gDots.setAttribute('opacity', 1);
      positionHover(i);
    });
    overlay.addEventListener('pointerleave', () => {
      hoverIndex = -1;
      cursor.setAttribute('opacity', 0); gDots.setAttribute('opacity', 0); tip.hide();
    });

    computeMax();
    observeSize(host, layout);

    return {
      /** انیمیشن ورود: رسم از راست به چپ + رشد مقادیر */
      play() {
        const target = data.series.map((s) => s.values);
        tween({
          duration: 1600, easing: ease.outExpo,
          onUpdate: (t) => {
            clipRect.setAttribute('x', W * (1 - t)); clipRect.setAttribute('width', W * t);
            shown = target.map((vals) => vals.map((v) => v * ease.outCubic(Math.min(1, t * 1.15))));
            drawSeries();
          },
          onDone: () => { clipRect.dataset.done = '1'; },
        });
      },
      /** مورف نرم به داده‌ی جدید */
      update(next) {
        const from = shown.map((vals) => resample(vals, next.labels.length));
        const oldMax = max;
        data = next; computeMax();
        const newMax = max;
        tween({
          duration: 900, easing: ease.inOutCubic,
          onUpdate: (t) => {
            max = lerp(oldMax, newMax, t);
            shown = next.series.map((s, si) => s.values.map((v, i) => lerp(from[si][i], v, t)));
            drawAxes(); drawSeries();
          },
        });
      },
    };
  }

  /* =====================================================================
     نمودار میله‌ای
     ===================================================================== */
  function barChart(host, { labels, values, unit }) {
    const svg = h('svg', { class: 'absolute inset-0 h-full w-full overflow-visible', style: 'direction:ltr' }, host);
    const tip = tooltip(host);
    const pad = { t: 10, b: 28 };
    const maxV = Math.max.apply(null, values);
    const peak = values.indexOf(maxV);
    let bars = [];

    function layout(W, H) {
      svg.innerHTML = '';
      const n = values.length, slot = W / n, bw = Math.min(36, slot * .55);
      bars = values.map((v, i) => {
        const x = W - slot * (i + .5); // راست به چپ
        const bh = (v / maxV) * (H - pad.t - pad.b);
        const g = h('g', { style: 'cursor:pointer' }, svg);
        h('rect', { x: x - bw / 2, y: pad.t, width: bw, height: H - pad.t - pad.b, rx: 8, class: 'fill-muted/60' }, g);
        const bar = h('rect', {
          x: x - bw / 2, y: H - pad.b - bh, width: bw, height: bh, rx: 8,
          class: i === peak ? 'fill-primary' : 'fill-primary/30 transition-colors',
          style: 'transform-box:fill-box;transform-origin:bottom;transform:scaleY(0);transition:transform .9s cubic-bezier(.34,1.3,.64,1) ' + (i * 70) + 'ms, fill .2s',
        }, g);
        const t = h('text', { x, y: H - 8, 'text-anchor': 'middle', class: 'fill-muted-foreground text-[11px]' }, g);
        t.textContent = labels[i];
        g.addEventListener('pointerenter', () => {
          if (i !== peak) bar.setAttribute('class', 'fill-primary/70');
          tip.show('<p class="font-semibold">' + escape(labels[i]) + '</p><p class="mt-0.5 text-muted-foreground"><b class="text-foreground tabular">' + fa(v) + '</b> ' + escape(unit) + '</p>', x, H - pad.b - bh);
        });
        g.addEventListener('pointerleave', () => { if (i !== peak) bar.setAttribute('class', 'fill-primary/30'); tip.hide(); });
        return bar;
      });
      if (svg.dataset.played) bars.forEach((b) => (b.style.transform = 'scaleY(1)'));
    }
    observeSize(host, layout);

    return {
      play() {
        svg.dataset.played = '1';
        requestAnimationFrame(() => bars.forEach((b) => (b.style.transform = 'scaleY(1)')));
      },
    };
  }

  /* =====================================================================
     نمودار دونات
     ===================================================================== */
  function donutChart(host, { segments, centerLabel, onHover }) {
    const size = 200, r = 78, sw = 22, C = 2 * Math.PI * r, gap = 6;
    const total = segments.reduce((a, s) => a + s.value, 0);
    const svg = h('svg', { viewBox: '0 0 ' + size + ' ' + size, class: 'h-full w-full -rotate-90 overflow-visible' }, host);
    h('circle', { cx: 100, cy: 100, r, fill: 'none', 'stroke-width': sw, class: 'stroke-muted' }, svg);

    const center = document.createElement('div');
    center.className = 'pointer-events-none absolute inset-0 grid place-content-center text-center';
    center.innerHTML = '<p class="text-2xl font-extrabold tabular" data-v>۰</p><p class="text-xs text-muted-foreground" data-l></p>';
    host.appendChild(center);
    const cv = center.querySelector('[data-v]'), cl = center.querySelector('[data-l]');
    const setCenter = (v, l) => { cv.textContent = v; cl.textContent = l; };
    setCenter(fa(total), centerLabel);

    let offset = 0;
    const arcs = segments.map((s, i) => {
      const len = (s.value / total) * C;
      const arc = h('circle', {
        cx: 100, cy: 100, r, fill: 'none', stroke: hsl(s.color), 'stroke-width': sw, 'stroke-linecap': 'round',
        'stroke-dasharray': '0 ' + C, 'stroke-dashoffset': -offset,
        style: 'cursor:pointer;transition:stroke-width .3s cubic-bezier(.34,1.56,.64,1), opacity .3s',
      }, svg);
      const seg = { arc, len: Math.max(0, len - gap), start: offset };
      offset += len;
      const enter = () => highlight(i);
      arc.addEventListener('pointerenter', enter);
      arc.addEventListener('pointerleave', () => highlight(-1));
      return seg;
    });

    function highlight(i) {
      arcs.forEach((a, j) => {
        a.arc.setAttribute('stroke-width', j === i ? sw + 8 : sw);
        a.arc.style.opacity = i === -1 || j === i ? '1' : '.35';
      });
      if (i === -1) setCenter(fa(total), centerLabel);
      else setCenter(fa(Math.round((segments[i].value / total) * 100)) + '٪', segments[i].label);
      onHover && onHover(i);
    }

    return {
      highlight,
      play() {
        tween({
          duration: 1500, easing: ease.outExpo,
          onUpdate: (t) => {
            arcs.forEach((a) => {
              a.arc.setAttribute('stroke-dasharray', a.len * t + ' ' + C);
              a.arc.setAttribute('stroke-dashoffset', -a.start * t);
            });
            setCenter(fa(Math.round(total * t)), centerLabel);
          },
        });
      },
    };
  }

  /* =====================================================================
     نشانگر نیم‌دایره‌ای
     ===================================================================== */
  function gauge(host, { value, label }) {
    const id = 'gg' + (++uid);
    const r = 80, L = Math.PI * r;
    const svg = h('svg', { viewBox: '0 0 200 120', class: 'w-full overflow-visible' }, host);
    const defs = h('defs', {}, svg);
    const grad = h('linearGradient', { id, x1: 1, y1: 0, x2: 0, y2: 0 }, defs);
    h('stop', { offset: '0%', style: 'stop-color:' + hsl('primary') }, grad);
    h('stop', { offset: '55%', style: 'stop-color:' + hsl('chart-5') }, grad);
    h('stop', { offset: '100%', style: 'stop-color:' + hsl('accent') }, grad);
    const d = 'M180,105 A80,80 0 0 0 20,105'; // از راست به چپ
    h('path', { d, fill: 'none', 'stroke-width': 16, 'stroke-linecap': 'round', class: 'stroke-muted' }, svg);
    for (let i = 0; i <= 10; i++) {
      const a = Math.PI * (i / 10);
      const x1 = 100 + Math.cos(a) * 96, y1 = 105 - Math.sin(a) * 96;
      const x2 = 100 + Math.cos(a) * 102, y2 = 105 - Math.sin(a) * 102;
      h('line', { x1, y1, x2, y2, 'stroke-width': 1.5, 'stroke-linecap': 'round', class: 'stroke-border' }, svg);
    }
    const arc = h('path', { d, fill: 'none', stroke: 'url(#' + id + ')', 'stroke-width': 16, 'stroke-linecap': 'round', 'stroke-dasharray': L, 'stroke-dashoffset': L }, svg);
    const knob = h('circle', { r: 7, cx: 180, cy: 105, fill: hsl('card'), stroke: hsl('accent'), 'stroke-width': 3, style: 'filter:drop-shadow(0 2px 4px rgb(0 0 0 / .2))' }, svg);
    const txt = h('text', { x: 100, y: 92, 'text-anchor': 'middle', class: 'fill-foreground text-[30px] font-extrabold' }, svg);
    const sub = h('text', { x: 100, y: 112, 'text-anchor': 'middle', class: 'fill-muted-foreground text-[11px]' }, svg);
    txt.textContent = '۰٪'; sub.textContent = label;

    return {
      play() {
        tween({
          duration: 2000, easing: ease.outExpo,
          onUpdate: (t) => {
            const p = (value / 100) * t;
            arc.setAttribute('stroke-dashoffset', L * (1 - p));
            const a = Math.PI * p; // زاویه از سمت راست
            knob.setAttribute('cx', 100 + Math.cos(a) * r);
            knob.setAttribute('cy', 105 - Math.sin(a) * r);
            txt.textContent = fa(Math.round(value * t)) + '٪';
          },
        });
      },
    };
  }

  /* =====================================================================
     اسپارک‌لاین
     ===================================================================== */
  function sparkline(host, { values, color }) {
    const id = 'sp' + (++uid);
    const W = 120, H = 40, min = Math.min.apply(null, values), max = Math.max.apply(null, values);
    const pts = values.map((v, i) => [W - (i * W) / (values.length - 1), H - 4 - ((v - min) / (max - min || 1)) * (H - 8)]);
    const line = smoothPath(pts, 0, H);
    const svg = h('svg', { viewBox: '0 0 ' + W + ' ' + H, preserveAspectRatio: 'none', class: 'h-full w-full overflow-visible' }, host);
    const grad = h('linearGradient', { id, x1: 0, y1: 0, x2: 0, y2: 1 }, h('defs', {}, svg));
    h('stop', { offset: '0%', style: 'stop-color:' + hsl(color) + ';stop-opacity:.3' }, grad);
    h('stop', { offset: '100%', style: 'stop-color:' + hsl(color) + ';stop-opacity:0' }, grad);
    const area = h('path', { d: line + 'L0,' + H + 'L' + W + ',' + H + 'Z', fill: 'url(#' + id + ')', style: 'opacity:0;transition:opacity 1s ease .6s' }, svg);
    const path = h('path', { d: line, fill: 'none', stroke: hsl(color), 'stroke-width': 2, 'stroke-linecap': 'round' }, svg);
    const len = path.getTotalLength ? path.getTotalLength() : 300;
    path.style.strokeDasharray = len;
    path.style.strokeDashoffset = len;
    return {
      play() {
        path.style.transition = 'stroke-dashoffset 1.6s cubic-bezier(.16,1,.3,1)';
        requestAnimationFrame(() => { path.style.strokeDashoffset = 0; area.style.opacity = 1; });
      },
    };
  }

  Nava.charts = { areaChart, barChart, donutChart, gauge, sparkline };
})(window.Nava);
