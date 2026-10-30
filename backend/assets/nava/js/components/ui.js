/**
 * کامپوننت‌های رابط کاربری عمومی:
 * - tabs      : تب‌های زیرخط‌دار با نشانگر لغزنده و پنل‌ها (data-tabs)
 * - segmented : کنترل بخش‌بندی‌شده (data-segmented)
 * - overlay   : مودال و کشو (data-modal / data-drawer)
 * - chips     : فیلترهای تراشه‌ای تک‌انتخابی (data-chips)
 */
(function (Nava) {
  'use strict';
  const { $, $$, debounce } = Nava.utils;

  /* ---------- تب‌ها ---------- */
  function tabs(root, onChange) {
    const btns = $$('[role="tab"]', root);
    let ink = $('.tab-ink', root);
    if (!ink) { ink = document.createElement('span'); ink.className = 'tab-ink'; root.appendChild(ink); }

    const move = (btn) => {
      ink.style.width = btn.offsetWidth + 'px';
      ink.style.insetInlineStart = (root.getBoundingClientRect().right - btn.getBoundingClientRect().right + root.scrollLeft * -1) + 'px';
    };
    const select = (btn, silent) => {
      btns.forEach((b) => {
        const on = b === btn;
        b.setAttribute('aria-selected', String(on));
        const panel = b.dataset.tab && document.getElementById(b.dataset.tab);
        if (panel) panel.hidden = !on;
      });
      move(btn);
      if (!silent && onChange) onChange(btn.dataset.value || btn.dataset.tab, btn);
    };
    btns.forEach((b) => b.addEventListener('click', () => select(b)));

    // تب اولیه از هش آدرس (مثلاً settings.html#billing)
    const fromHash = location.hash && btns.find((b) => b.dataset.tab === location.hash.slice(1));
    const initial = fromHash || btns.find((b) => b.getAttribute('aria-selected') === 'true') || btns[0];
    select(initial, !fromHash);
    const sync = () => move($('[aria-selected="true"]', root));
    window.addEventListener('resize', debounce(sync, 100));
    document.fonts && document.fonts.ready.then(sync);
    return { select: (value) => select(btns.find((b) => (b.dataset.value || b.dataset.tab) === value)) };
  }

  /* ---------- کنترل بخش‌بندی‌شده ---------- */
  function segmented(el, onChange) {
    const thumb = $('.seg-thumb', el);
    const btns = $$('button', el);
    const move = (btn) => {
      thumb.style.width = btn.offsetWidth + 'px';
      thumb.style.insetInlineStart = (el.getBoundingClientRect().right - btn.getBoundingClientRect().right) + 'px';
    };
    btns.forEach((btn) => btn.addEventListener('click', () => {
      if (btn.getAttribute('aria-selected') === 'true') return;
      btns.forEach((b) => b.setAttribute('aria-selected', String(b === btn)));
      move(btn);
      onChange && onChange(btn.dataset.value);
    }));
    const sync = () => move($('[aria-selected="true"]', el));
    requestAnimationFrame(sync);
    window.addEventListener('resize', debounce(sync, 100));
    document.fonts && document.fonts.ready.then(sync);
  }

  /* ---------- مودال و کشو ---------- */
  const stack = [];
  function overlay(id) {
    const root = document.getElementById(id);
    if (!root) return null;
    let lastFocus = null;
    const api = {
      root,
      open() {
        lastFocus = document.activeElement;
        root.dataset.open = 'true';
        root.removeAttribute('inert');
        document.body.style.overflow = 'hidden';
        stack.push(api);
        const f = $('[data-autofocus]', root) || (root.classList.contains('drawer-root') ? $('button[data-close]', root) : $('input:not([type=file]), select, textarea', root));
        setTimeout(() => f && f.focus(), 120);
        root.dispatchEvent(new CustomEvent('nava:open'));
      },
      close() {
        root.dataset.open = 'false';
        root.setAttribute('inert', '');
        stack.splice(stack.indexOf(api), 1);
        if (!stack.length) document.body.style.overflow = '';
        lastFocus && lastFocus.focus();
      },
    };
    root.dataset.open = 'false';
    root.setAttribute('inert', '');
    $$('[data-close]', root).forEach((b) => b.addEventListener('click', api.close));
    $$('[data-open="' + id + '"]').forEach((b) => b.addEventListener('click', (e) => { e.preventDefault(); api.open(); }));
    return api;
  }
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && stack.length) stack[stack.length - 1].close(); });

  /* ---------- تراشه‌ها ---------- */
  function chips(root, onChange) {
    const items = $$('.chip', root);
    items.forEach((c) => c.addEventListener('click', () => {
      items.forEach((x) => x.setAttribute('aria-pressed', String(x === c)));
      onChange(c.dataset.value);
    }));
    return {
      set(value) {
        const c = items.find((x) => x.dataset.value === value);
        c && c.click();
      },
    };
  }

  /* ---------- کپی در کلیپ‌بورد ---------- */
  $$('[data-copy]').forEach((b) => b.addEventListener('click', () => {
    const done = () => Nava.toast('کپی شد');
    navigator.clipboard ? navigator.clipboard.writeText(b.dataset.copy).then(done, done) : done();
  }));

  /* ---------- قطعات نمایشی پرتکرار ---------- */
  const { fa, escape } = Nava.utils;
  const render = {
    /** آواتار حرف اول با رنگ ثابت برای هر نام */
    avatar(name, size) {
      const n = (name.charCodeAt(0) + name.length) % 5 + 1;
      return '<span class="avatar ' + (size || 'size-9 text-sm') + '" style="background:hsl(var(--chart-' + n + ') / .14);color:hsl(var(--chart-' + n + '))">' + escape(name.trim()[0]) + '</span>';
    },
    money(n, unit) {
      return fa(n) + ' <span class="text-[11px] font-normal text-muted-foreground">' + (unit || 'تومان') + '</span>';
    },
    /** کارت آمار کوچک؛ عدد پس از دیده‌شدن شمرده می‌شود */
    stats(root, items) {
      root.innerHTML = items.map((s, i) =>
        '<article class="card card-hover reveal flex items-center gap-4 p-4 sm:p-5" style="--delay:' + i * 70 + 'ms">' +
          '<span class="grid size-11 shrink-0 place-items-center rounded-xl ' + Nava.data.tone[s.tone || 'primary'] + '">' + Nava.icons.svg(s.icon, 'size-5') + '</span>' +
          '<div class="min-w-0"><p class="truncate text-xs text-muted-foreground">' + escape(s.label) + '</p>' +
          '<p class="mt-0.5 text-xl font-extrabold tabular" data-to="' + s.value + '" data-dec="' + (s.decimals || 0) + '" data-suffix="' + (s.suffix || '') + '">۰</p></div>' +
        '</article>'
      ).join('');
      $$('[data-to]', root).forEach((el) => Nava.effects.onVisible(el, () =>
        Nava.effects.countUp(el, +el.dataset.to, { decimals: +el.dataset.dec, suffix: el.dataset.suffix, duration: 1400 })
      ));
    },
  };

  /** دانلود فایل CSV (با BOM برای نمایش درست فارسی در اکسل) */
  function downloadCSV(filename, rows) {
    const csv = rows.map((r) => r.map((c) => '"' + String(c).replace(/"/g, '""') + '"').join(',')).join('\n');
    const url = URL.createObjectURL(new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' }));
    const a = Object.assign(document.createElement('a'), { href: url, download: filename });
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  Nava.ui = { tabs, segmented, overlay, chips, render, downloadCSV };
})(window.Nava);
