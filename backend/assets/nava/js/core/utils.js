/**
 * ابزارهای مشترک: قالب‌بندی اعداد/تاریخ فارسی، انتخابگرها و زمان‌بندی
 */
(function (Nava) {
  'use strict';

  const faNumber = new Intl.NumberFormat('fa-IR');
  const faDigits = '۰۱۲۳۴۵۶۷۸۹';

  const utils = {
    $: (sel, root) => (root || document).querySelector(sel),
    $$: (sel, root) => Array.from((root || document).querySelectorAll(sel)),

    /** تبدیل عدد به رقم فارسی با جداکننده‌ی هزارگان */
    fa(n, decimals) {
      if (decimals == null) return faNumber.format(n);
      return new Intl.NumberFormat('fa-IR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals }).format(n);
    },

    /** فقط ارقام لاتین را فارسی می‌کند (برای رشته‌ها) */
    faDigits(str) {
      return String(str).replace(/\d/g, (d) => faDigits[d]);
    },

    /** تاریخ شمسی */
    jalali(date, opts) {
      const d = date || new Date();
      if (opts) return new Intl.DateTimeFormat('fa-IR-u-ca-persian', opts).format(d);
      const parts = {};
      new Intl.DateTimeFormat('fa-IR-u-ca-persian', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
        .formatToParts(d).forEach((p) => (parts[p.type] = p.value));
      return parts.weekday + '، ' + parts.day + ' ' + parts.month + ' ' + parts.year;
    },

    prefersReducedMotion() {
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    },

    clamp: (v, min, max) => Math.min(max, Math.max(min, v)),
    lerp: (a, b, t) => a + (b - a) * t,

    /** منحنی‌های حرکت */
    ease: {
      outExpo: (t) => (t === 1 ? 1 : 1 - Math.pow(2, -10 * t)),
      outCubic: (t) => 1 - Math.pow(1 - t, 3),
      inOutCubic: (t) => (t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2),
      outBack: (t) => { const c1 = 1.4, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); },
    },

    /** انیمیشن عددی با requestAnimationFrame */
    tween({ duration = 1000, easing, onUpdate, onDone }) {
      const ease = easing || utils.ease.outExpo;
      if (utils.prefersReducedMotion()) { onUpdate(1); onDone && onDone(); return () => {}; }
      let raf, start;
      const step = (now) => {
        if (start == null) start = now;
        const t = Math.min(1, (now - start) / duration);
        onUpdate(ease(t));
        if (t < 1) raf = requestAnimationFrame(step);
        else onDone && onDone();
      };
      raf = requestAnimationFrame(step);
      return () => cancelAnimationFrame(raf);
    },

    debounce(fn, wait) {
      let t;
      return function () { clearTimeout(t); t = setTimeout(() => fn.apply(this, arguments), wait); };
    },

    /** خواندن رنگ از توکن CSS، مثل color('primary') */
    color(name, alpha) {
      const v = getComputedStyle(document.documentElement).getPropertyValue('--' + name).trim();
      return alpha == null ? 'hsl(' + v + ')' : 'hsl(' + v + ' / ' + alpha + ')';
    },

    escape(str) {
      return String(str).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    },

    storage: {
      get(key) { try { return localStorage.getItem(key); } catch (e) { return null; } },
      set(key, value) { try { localStorage.setItem(key, value); } catch (e) { /* حالت خصوصی */ } },
    },
  };

  Nava.utils = utils;
})(window.Nava = window.Nava || {});
