/**
 * تغییر تم روشن/تیره با انیمیشن دایره‌ای (View Transitions API)
 */
(function (Nava) {
  'use strict';
  const { $$, storage, prefersReducedMotion } = Nava.utils;
  const root = document.documentElement;

  function apply(dark) {
    root.classList.toggle('dark', dark);
    storage.set('nava-theme', dark ? 'dark' : 'light');
    document.dispatchEvent(new CustomEvent('nava:themechange', { detail: { dark } }));
  }

  function toggle(event) {
    const dark = !root.classList.contains('dark');
    if (!document.startViewTransition || prefersReducedMotion()) return apply(dark);

    const x = event ? event.clientX : innerWidth / 2;
    const y = event ? event.clientY : 0;
    const r = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));

    document.startViewTransition(() => apply(dark)).ready.then(() => {
      root.animate(
        { clipPath: ['circle(0px at ' + x + 'px ' + y + 'px)', 'circle(' + r + 'px at ' + x + 'px ' + y + 'px)'] },
        { duration: 650, easing: 'cubic-bezier(.65,0,.35,1)', pseudoElement: '::view-transition-new(root)' }
      );
    });
  }

  /** تنظیم مستقیم تم: 'light' | 'dark' | 'system' */
  function set(mode) {
    const dark = mode === 'system' ? matchMedia('(prefers-color-scheme: dark)').matches : mode === 'dark';
    if (dark !== root.classList.contains('dark')) toggle();
    if (mode === 'system') { try { localStorage.removeItem('nava-theme'); } catch (e) { /* حالت خصوصی */ } }
  }
  function mode() {
    const t = storage.get('nava-theme');
    return t === 'dark' || t === 'light' ? t : 'system';
  }

  $$('[data-theme-toggle]').forEach((btn) => btn.addEventListener('click', toggle));
  Nava.theme = { toggle, set, mode };
})(window.Nava);
