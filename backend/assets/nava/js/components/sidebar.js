/**
 * سایدبار: جمع‌شدن در دسکتاپ، کشوی موبایل و نشانگر لغزنده‌ی آیتم فعال
 */
(function (Nava) {
  'use strict';
  const { $, $$, storage } = Nava.utils;
  const root = document.documentElement;
  const nav = $('[data-nav]');
  const indicator = $('[data-nav-indicator]');
  if (!nav) return;

  /* نشانگر لغزنده */
  function moveIndicator(link) {
    if (!link || !indicator) return;
    indicator.style.height = link.offsetHeight + 'px';
    indicator.style.transform = 'translateY(' + (link.getBoundingClientRect().top - nav.getBoundingClientRect().top + nav.scrollTop) + 'px)';
    indicator.style.top = '0';
  }
  const current = () => $('.nav-link[aria-current="page"]', nav);

  $$('.nav-link', nav).forEach((link) => {
    // پیش از رفتن به صفحه‌ی بعد، نشانگر به آیتم جدید می‌لغزد
    link.addEventListener('click', () => {
      $$('.nav-link', nav).forEach((l) => l.removeAttribute('aria-current'));
      link.setAttribute('aria-current', 'page');
      moveIndicator(link);
    });
  });

  /* جمع کردن (دسکتاپ) */
  $$('[data-collapse-toggle]').forEach((btn) =>
    btn.addEventListener('click', () => {
      const collapsed = root.classList.toggle('sidebar-collapsed');
      storage.set('nava-sidebar', collapsed ? 'collapsed' : 'expanded');
      setTimeout(() => { moveIndicator(current()); window.dispatchEvent(new Event('resize')); }, 470);
    })
  );

  /* کشوی موبایل */
  function open() { root.classList.add('sidebar-open'); document.body.style.overflow = 'hidden'; }
  function close() { root.classList.remove('sidebar-open'); document.body.style.overflow = ''; }
  $$('[data-sidebar-open]').forEach((b) => b.addEventListener('click', open));
  $$('[data-sidebar-close]').forEach((b) => b.addEventListener('click', close));
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });

  window.addEventListener('resize', Nava.utils.debounce(() => moveIndicator(current()), 100));
  requestAnimationFrame(() => {
    moveIndicator(current());
    const link = current();
    if (link) link.scrollIntoView({ block: 'nearest' });
  });
  document.fonts && document.fonts.ready.then(() => moveIndicator(current()));
})(window.Nava);
