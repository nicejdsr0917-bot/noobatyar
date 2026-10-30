/**
 * منوهای شناور (اعلان‌ها، پروفایل) — یکی در هر لحظه باز است
 */
(function (Nava) {
  'use strict';
  const { $, $$ } = Nava.utils;
  const all = $$('[data-popover]');

  function setOpen(wrap, open) {
    $('[data-popover-panel]', wrap).dataset.open = String(open);
    $('[data-popover-trigger]', wrap).setAttribute('aria-expanded', String(open));
  }

  all.forEach((wrap) => {
    $('[data-popover-trigger]', wrap).addEventListener('click', (e) => {
      e.stopPropagation();
      const isOpen = $('[data-popover-panel]', wrap).dataset.open === 'true';
      all.forEach((w) => setOpen(w, false));
      setOpen(wrap, !isOpen);
    });
    $('[data-popover-panel]', wrap).addEventListener('click', (e) => e.stopPropagation());
  });

  const closeAll = () => all.forEach((w) => setOpen(w, false));
  document.addEventListener('click', closeAll);
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeAll(); });
})(window.Nava);
