/**
 * نوبت‌یار — اتصال صفحه‌های پنل کارکنان به کامپوننت‌های نوا
 *
 * صفحه‌ها در جنگو رندر می‌شوند؛ این فایل فقط رفتار را اضافه می‌کند:
 * - پیام‌های جنگو ← Nava.toast
 * - جدول‌های سمت سرور ← Nava.dataTable (فیلتر زنده، مرتب‌سازی و انتخاب گروهی در همین صفحه)
 * - مودال دلیل رد/مخفی‌سازی (Nava.ui.overlay)
 * - نمودارها از داده‌ی json_script (Nava.charts)
 */
(function (Nava) {
  'use strict';
  const { $, $$, debounce } = Nava.utils;
  const readJSON = (id) => { const el = document.getElementById(id); return el ? JSON.parse(el.textContent) : null; };

  /* ---------- پیام‌ها و ظاهرشدن ---------- */
  (readJSON('nava-messages') || []).forEach((m, i) => setTimeout(() => Nava.toast(m.text, { type: m.type, duration: 4500 }), 250 + i * 150));
  Nava.effects.reveal();

  const queue = $('[data-queue-list]');
  if (queue) {
    const any = queue.children.length > 0;
    $('[data-queue-dot]').hidden = !any;
    $('[data-queue-empty]').classList.toggle('hidden', any);
  }

  /* ---------- تب‌ها و کنترل‌های بخش‌بندی‌شده ---------- */
  $$('[data-tabs]').forEach((el) => Nava.ui.tabs(el));

  /* ---------- شمارنده‌ها ---------- */
  $$('[data-to]').forEach((el) => Nava.effects.onVisible(el, () =>
    Nava.effects.countUp(el, +el.dataset.to, { decimals: +(el.dataset.dec || 0), suffix: el.dataset.suffix || '', duration: 1200 })
  ));

  /* ---------- جدول‌های سمت سرور ---------- */
  $$('[data-server-table]').forEach((card) => {
    const tbody = $('tbody', card);
    if (!tbody) return;
    const trs = $$('tr', tbody);
    const rows = trs.map((tr, i) => {
      const r = { _tr: tr, q: (tr.dataset.q || tr.textContent).replace(/\s+/g, ' ') };
      Object.keys(tr.dataset).forEach((k) => {
        if (k.indexOf('sort') === 0 && k.length > 4) {
          const v = tr.dataset[k];
          r[k.slice(4).toLowerCase()] = v !== '' && !isNaN(v) ? +v : v;
        }
      });
      const cb = $('[data-select]', tr);
      if (cb) { cb.dataset.select = String(i); r.id = cb.value; }
      return r;
    });
    const bulk = $('[data-bulk]', card);
    const table = Nava.dataTable({
      rows,
      pageSize: Math.max(rows.length, 1),
      searchKeys: ['q'],
      tbody,
      head: $('thead', card),
      empty: $('[data-empty]', card),
      selectAll: $('[data-select-all]', card),
      row: (r, i, selected) => {
        const cb = $('[data-select]', r._tr);
        if (cb) cb.toggleAttribute('checked', !!selected); // outerHTML keeps attributes, not .checked
        r._tr.classList.toggle('is-selected', !!selected);
        return r._tr.outerHTML;
      },
      onSelect(selected) {
        if (!bulk) return;
        bulk.hidden = selected.length === 0;
        $$('[data-bulk-count]', bulk).forEach((el) => (el.textContent = Nava.utils.fa(selected.length)));
        $$('input[name="ids"]', bulk).forEach((el) => (el.value = selected.map((r) => r.id).join(',')));
      },
    });
    // rows are re-rendered from outerHTML, so their icons are already SVG and
    // listeners live on tbody (dataTable) or document (below).
    const live = $('[data-live-filter]', card) || $('[data-live-filter]');
    if (live) live.addEventListener('input', debounce(() => table.search(live.value.trim()), 120));
    card._table = table;
  });

  /* ---------- مودال دلیل (رد، مخفی‌سازی، بستن گزارش) ---------- */
  const reason = Nava.ui.overlay('reason-modal');
  if (reason) {
    const form = $('form', reason.root);
    document.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-reason]');
      if (!btn) return;
      e.preventDefault();
      form.action = btn.dataset.action;
      $('[data-reason-title]', reason.root).textContent = btn.dataset.title || 'دلیل';
      $('[data-reason-hint]', reason.root).textContent = btn.dataset.hint || '';
      $('[data-reason-submit]', reason.root).textContent = btn.dataset.submit || 'ثبت';
      $$('[data-reason-extra]', form).forEach((el) => el.remove());
      Object.keys(btn.dataset).forEach((k) => {
        if (k.indexOf('field') !== 0 || k.length <= 5) return;
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = k.slice(5).toLowerCase();
        input.value = btn.dataset[k];
        input.setAttribute('data-reason-extra', '');
        form.appendChild(input);
      });
      // Bulk buttons carry the current selection along.
      const bulk = btn.closest('[data-bulk]');
      const ids = bulk && $('input[name="ids"]', bulk);
      if (ids) {
        const input = Object.assign(document.createElement('input'), { type: 'hidden', name: 'ids', value: ids.value });
        input.setAttribute('data-reason-extra', '');
        form.appendChild(input);
      }
      const note = $('textarea', form);
      note.required = btn.dataset.optional === undefined;
      note.value = '';
      reason.open();
    });
  }

  /* ---------- تأیید پیش از ارسال ---------- */
  document.addEventListener('submit', (e) => {
    const msg = e.target.dataset.confirm;
    if (msg && !window.confirm(msg)) e.preventDefault();
    const btn = e.submitter;
    if (!e.defaultPrevented && btn) { btn.disabled = true; btn.insertAdjacentHTML('afterbegin', '<span class="spinner"></span>'); }
  });

  /* ---------- پیش‌نمایش و بررسی اندازه‌ی بنر ---------- */
  $$('[data-banner-input]').forEach((input) => {
    const preview = $('[data-banner-preview]');
    const w = +input.dataset.width, h = +input.dataset.height;
    input.addEventListener('change', () => {
      const file = input.files && input.files[0];
      if (!file) return;
      const url = URL.createObjectURL(file);
      const img = new Image();
      img.onload = () => {
        const good = img.naturalWidth === w && img.naturalHeight === h;
        if (!good) {
          Nava.toast('ابعاد عکس ' + Nava.utils.faDigits(img.naturalWidth + '×' + img.naturalHeight) + ' است؛ باید دقیقاً ' + Nava.utils.faDigits(w + '×' + h) + ' باشد.', { type: 'error', duration: 5000 });
          input.value = '';
        }
        if (preview) { preview.src = good ? url : ''; preview.hidden = !good; }
      };
      img.src = url;
    });
  });

  /* ---------- نمودارها ---------- */
  $$('[data-area-chart]').forEach((host) => {
    const cfg = readJSON(host.dataset.areaChart);
    if (!cfg) return;
    const chart = Nava.charts.areaChart(host, cfg.month || cfg);
    Nava.effects.onVisible(host, () => chart.play());
    const seg = host.dataset.segmented && document.querySelector('[data-segmented="' + host.dataset.segmented + '"]');
    if (seg) Nava.ui.segmented(seg, (key) => cfg[key] && chart.update(cfg[key]));
  });
  $$('[data-bar-chart]').forEach((host) => {
    const cfg = readJSON(host.dataset.barChart);
    if (!cfg) return;
    const chart = Nava.charts.barChart(host, cfg);
    Nava.effects.onVisible(host, () => chart.play());
  });
  $$('[data-donut]').forEach((host) => {
    const cfg = readJSON(host.dataset.donut);
    if (!cfg || !cfg.segments.some((s) => s.value > 0)) return;
    const chart = Nava.charts.donutChart(host, cfg);
    Nava.effects.onVisible(host, () => chart.play());
  });
  $$('[data-spark]').forEach((host) => {
    const values = host.dataset.spark.split(',').map(Number);
    if (values.length < 2) return;
    const s = Nava.charts.sparkline(host, { values, color: host.dataset.color || 'chart-2' });
    Nava.effects.onVisible(host, () => s.play());
  });
})(window.Nava);
