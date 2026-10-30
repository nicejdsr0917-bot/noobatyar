/**
 * پالت فرمان (⌘K / Ctrl+K) — جستجو و پیمایش با کیبورد
 */
(function (Nava) {
  'use strict';
  const { $, $$, escape } = Nava.utils;
  const modal = $('[data-cmdk]');
  if (!modal) return;
  const input = $('[data-cmdk-input]', modal);
  const list = $('[data-cmdk-list]', modal);

  // Noobatyar: page list comes from the server-rendered menu (Nava.layout.pages,
  // already filtered by permission); actions are the panel's own.
  const ITEMS = Nava.layout.pages
    .map((p) => ({ group: 'صفحات', label: p.label, icon: p.icon, href: p.href }))
    .concat(Nava.layout.actions || [])
    .concat([
      { group: 'عملیات', label: 'تغییر تم روشن / تیره', icon: 'moon', action: () => Nava.theme.toggle() },
    ]);

  // Noobatyar: live results (businesses, customers, phone numbers) from the
  // panel's search endpoint, merged under the static items.
  let remote = [];
  let seq = 0;
  const fetchRemote = Nava.utils.debounce((q) => {
    const url = Nava.layout.searchUrl;
    const mine = ++seq;
    if (!url || q.length < 2) { remote = []; render(); return; }
    fetch(url + '?q=' + encodeURIComponent(q), { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
      .then((r) => (r.ok ? r.json() : { results: [] }))
      .then((d) => { if (mine === seq) { remote = d.results || []; render(); } })
      .catch(() => {});
  }, 220);

  let filtered = ITEMS;
  let active = 0;
  let lastFocus = null;

  function render() {
    const q = input.value.trim();
    filtered = ITEMS.filter((i) => i.label.includes(q) || i.group.includes(q)).concat(remote);
    active = Math.min(active, Math.max(0, filtered.length - 1));
    if (!filtered.length) {
      list.innerHTML = '<li class="py-10 text-center text-sm text-muted-foreground">نتیجه‌ای یافت نشد.</li>';
      return;
    }
    let html = '';
    let group = '';
    filtered.forEach((item, i) => {
      if (item.group !== group) {
        group = item.group;
        html += '<li class="px-2 pb-1 pt-3 text-[11px] font-semibold text-muted-foreground first:pt-1" role="presentation">' + escape(group) + '</li>';
      }
      const on = i === active;
      html +=
        '<li role="option" aria-selected="' + on + '" data-index="' + i + '" class="flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors ' + (on ? 'bg-primary text-primary-foreground' : 'hover:bg-muted') + '">' +
        Nava.icons.svg(item.icon, 'size-4 ' + (on ? '' : 'text-muted-foreground')) +
        '<span class="flex-1 truncate">' + escape(item.label) + (item.hint ? '<span class="ms-2 text-[11px] ' + (on ? 'opacity-70' : 'text-muted-foreground') + '">' + escape(item.hint) + '</span>' : '') + '</span>' +
        (on ? Nava.icons.svg('corner-left', 'size-4 opacity-70') : '') +
        '</li>';
    });
    list.innerHTML = html;
    const el = $('[aria-selected="true"]', list);
    el && el.scrollIntoView({ block: 'nearest' });
  }

  function open() {
    lastFocus = document.activeElement;
    modal.classList.remove('hidden');
    input.value = '';
    remote = [];
    active = 0;
    render();
    requestAnimationFrame(() => input.focus());
  }
  function close() {
    modal.classList.add('hidden');
    lastFocus && lastFocus.focus();
  }
  function run(i) {
    const item = filtered[i];
    if (!item) return;
    close();
    if (item.action) item.action();
    else location.href = item.href;
  }

  input.addEventListener('input', () => { active = 0; render(); fetchRemote(input.value.trim()); });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); active = (active + 1) % filtered.length; render(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); active = (active - 1 + filtered.length) % filtered.length; render(); }
    else if (e.key === 'Enter') { e.preventDefault(); run(active); }
  });
  list.addEventListener('click', (e) => {
    const li = e.target.closest('[data-index]');
    li && run(+li.dataset.index);
  });
  list.addEventListener('mousemove', (e) => {
    const li = e.target.closest('[data-index]');
    if (li && +li.dataset.index !== active) { active = +li.dataset.index; render(); }
  });

  $$('[data-cmdk-open]').forEach((b) => b.addEventListener('click', open));
  $$('[data-cmdk-close]', modal).forEach((b) => b.addEventListener('click', close));
  document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); modal.classList.contains('hidden') ? open() : close(); }
    else if (e.key === 'Escape' && !modal.classList.contains('hidden')) close();
  });
})(window.Nava);
