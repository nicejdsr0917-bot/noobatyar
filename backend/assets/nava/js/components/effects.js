/**
 * افکت‌های تعاملی: نور دنبال‌کننده روی کارت‌ها، موج کلیک، ظاهرشدن هنگام اسکرول، شمارنده و کج‌شدن سه‌بعدی
 */
(function (Nava) {
  'use strict';
  const { $$, tween, fa, prefersReducedMotion } = Nava.utils;

  const effects = {
    /** نور Spotlight روی کارت‌ها — یک شنونده برای کل صفحه */
    spotlight() {
      document.addEventListener('pointermove', (e) => {
        const card = e.target.closest && e.target.closest('.card');
        if (!card) return;
        const r = card.getBoundingClientRect();
        card.style.setProperty('--mx', e.clientX - r.left + 'px');
        card.style.setProperty('--my', e.clientY - r.top + 'px');
      }, { passive: true });
    },

    /** موج کلیک Material */
    ripple() {
      document.addEventListener('pointerdown', (e) => {
        const btn = e.target.closest && e.target.closest('[data-ripple]');
        if (!btn) return;
        const r = btn.getBoundingClientRect();
        const size = Math.max(r.width, r.height);
        const span = document.createElement('span');
        span.className = 'ripple';
        span.style.cssText = 'width:' + size + 'px;height:' + size + 'px;left:' + (e.clientX - r.left - size / 2) + 'px;top:' + (e.clientY - r.top - size / 2) + 'px';
        btn.appendChild(span);
        span.addEventListener('animationend', () => span.remove());
      });
    },

    /** ظاهرشدن عناصر هنگام ورود به دید */
    reveal(root) {
      const els = $$('.reveal:not(.is-visible)', root);
      if (!('IntersectionObserver' in window) || prefersReducedMotion()) {
        els.forEach((el) => el.classList.add('is-visible'));
        return;
      }
      const io = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add('is-visible');
          entry.target.dispatchEvent(new CustomEvent('nava:reveal'));
          io.unobserve(entry.target);
        });
      }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
      els.forEach((el) => io.observe(el));
    },

    /** وقتی عنصر دیده شد، callback اجرا شود (برای شروع انیمیشن نمودارها) */
    onVisible(el, cb) {
      if (!('IntersectionObserver' in window)) return cb();
      const io = new IntersectionObserver((entries) => {
        if (entries[0].isIntersecting) { io.disconnect(); cb(); }
      }, { threshold: 0.2 });
      io.observe(el);
    },

    /** شمارنده‌ی عددی */
    countUp(el, to, opts) {
      const o = Object.assign({ duration: 1800, decimals: 0, prefix: '', suffix: '' }, opts);
      tween({
        duration: o.duration,
        onUpdate: (t) => { el.textContent = o.prefix + fa(to * t, o.decimals) + o.suffix; },
      });
    },

    /** کج‌شدن سه‌بعدی ملایم */
    tilt(el, max) {
      if (prefersReducedMotion() || matchMedia('(hover: none)').matches) return;
      const m = max || 6;
      el.style.transformStyle = 'preserve-3d';
      el.addEventListener('pointermove', (e) => {
        const r = el.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - .5;
        const y = (e.clientY - r.top) / r.height - .5;
        el.style.transform = 'perspective(800px) rotateX(' + (-y * m) + 'deg) rotateY(' + (x * m) + 'deg) translateY(-2px)';
      });
      el.addEventListener('pointerleave', () => { el.style.transform = ''; });
    },
  };

  effects.spotlight();
  effects.ripple();
  Nava.effects = effects;
})(window.Nava);
