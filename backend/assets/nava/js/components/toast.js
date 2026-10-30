/**
 * اعلان موقت (Toast)
 * Nava.toast('پیام', { type: 'success' | 'info' | 'error' })
 */
(function (Nava) {
  'use strict';
  const { $, $$, escape } = Nava.utils;
  const host = $('[data-toasts]');
  const TYPES = {
    success: { icon: 'check', cls: 'bg-primary/10 text-primary' },
    info: { icon: 'bell', cls: 'bg-info/10 text-info' },
    error: { icon: 'x', cls: 'bg-danger/10 text-danger' },
  };

  function toast(message, opts) {
    const o = Object.assign({ type: 'success', duration: 3200 }, opts);
    const t = TYPES[o.type] || TYPES.success;
    const el = document.createElement('div');
    el.className = 'toast';
    el.setAttribute('role', 'status');
    el.innerHTML =
      '<span class="grid size-8 shrink-0 place-items-center rounded-full ' + t.cls + '">' + Nava.icons.svg(t.icon, 'size-4') + '</span>' +
      '<div class="flex-1 pt-1.5"><p class="font-medium">' + escape(message) + '</p></div>' +
      '<button type="button" class="btn-icon size-7" aria-label="بستن">' + Nava.icons.svg('x', 'size-4') + '</button>';
    const remove = () => { el.classList.add('is-leaving'); el.addEventListener('animationend', () => el.remove(), { once: true }); };
    el.querySelector('button').addEventListener('click', remove);
    host.appendChild(el);
    setTimeout(remove, o.duration);
  }

  $$('[data-toast]').forEach((b) => b.addEventListener('click', () => toast(b.dataset.toast)));
  Nava.toast = toast;
})(window.Nava);
