/**
 * جدول داده‌ی سمت کاربر: جستجو، فیلتر، مرتب‌سازی، صفحه‌بندی و انتخاب گروهی
 *
 * const table = Nava.dataTable({
 *   rows, pageSize, searchKeys,
 *   tbody, list, empty, pager, info,           // عناصر DOM
 *   row(item, i), card(item, i),                // تابع ساخت ردیف دسکتاپ / کارت موبایل
 *   onSelect(selectedRows)
 * });
 */
(function (Nava) {
  'use strict';
  const { $$, fa } = Nava.utils;
  const { svg: icon } = Nava.icons;

  function dataTable(o) {
    const state = { query: '', filter: null, sortKey: null, dir: 'asc', page: 1, selected: new Set() };
    let view = [];

    function compute() {
      const q = state.query.trim();
      view = o.rows.filter((r) =>
        (!state.filter || state.filter(r)) &&
        (!q || o.searchKeys.some((k) => String(r[k]).includes(q)))
      );
      if (state.sortKey) {
        const k = state.sortKey, d = state.dir === 'asc' ? 1 : -1;
        view.sort((a, b) => (a[k] > b[k] ? d : a[k] < b[k] ? -d : 0));
      }
    }

    function pager(total) {
      if (!o.pager) return;
      const pages = Math.max(1, Math.ceil(total / o.pageSize));
      const p = state.page;
      const nums = [];
      for (let n = 1; n <= pages; n++) {
        if (n === 1 || n === pages || Math.abs(n - p) <= 1) nums.push(n);
        else if (nums[nums.length - 1] !== '…') nums.push('…');
      }
      o.pager.innerHTML =
        '<button type="button" class="pager-btn" data-page="' + (p - 1) + '"' + (p === 1 ? ' disabled' : '') + ' aria-label="قبلی">' + icon('chevron-right', 'size-4') + '</button>' +
        nums.map((n) => n === '…' ? '<span class="px-1 text-muted-foreground">…</span>' :
          '<button type="button" class="pager-btn" data-page="' + n + '"' + (n === p ? ' aria-current="page"' : '') + '>' + fa(n) + '</button>').join('') +
        '<button type="button" class="pager-btn" data-page="' + (p + 1) + '"' + (p === pages ? ' disabled' : '') + ' aria-label="بعدی">' + icon('chevron-left', 'size-4') + '</button>';
      if (o.info) {
        const from = total ? (p - 1) * o.pageSize + 1 : 0;
        o.info.textContent = 'نمایش ' + fa(from) + ' تا ' + fa(Math.min(total, p * o.pageSize)) + ' از ' + fa(total) + ' مورد';
      }
    }

    function render() {
      compute();
      const pages = Math.max(1, Math.ceil(view.length / o.pageSize));
      state.page = Math.min(state.page, pages);
      const slice = view.slice((state.page - 1) * o.pageSize, state.page * o.pageSize);
      if (o.tbody) o.tbody.innerHTML = slice.map((r, i) => o.row(r, i, state.selected.has(r))).join('');
      if (o.list) o.list.innerHTML = slice.map((r, i) => o.card(r, i)).join('');
      if (o.empty) o.empty.classList.toggle('hidden', view.length > 0);
      pager(view.length);
      syncSelectAll();
    }

    /* انتخاب گروهی */
    function syncSelectAll() {
      if (!o.selectAll) return;
      const ids = $$('[data-select]', o.tbody).map((c) => c.dataset.select);
      const n = ids.filter((id) => state.selected.has(o.rows[id])).length;
      o.selectAll.checked = n > 0 && n === ids.length;
      o.selectAll.indeterminate = n > 0 && n < ids.length;
      o.onSelect && o.onSelect(Array.from(state.selected));
    }
    if (o.tbody) {
      o.tbody.addEventListener('change', (e) => {
        const cb = e.target.closest('[data-select]');
        if (!cb) return;
        const row = o.rows[cb.dataset.select];
        cb.checked ? state.selected.add(row) : state.selected.delete(row);
        cb.closest('tr').classList.toggle('is-selected', cb.checked);
        syncSelectAll();
      });
    }
    if (o.selectAll) {
      o.selectAll.addEventListener('change', () => {
        $$('[data-select]', o.tbody).forEach((cb) => {
          cb.checked = o.selectAll.checked;
          const row = o.rows[cb.dataset.select];
          o.selectAll.checked ? state.selected.add(row) : state.selected.delete(row);
          cb.closest('tr').classList.toggle('is-selected', cb.checked);
        });
        syncSelectAll();
      });
    }

    /* مرتب‌سازی */
    $$('[data-sort]', o.head).forEach((btn) => btn.addEventListener('click', () => {
      const k = btn.dataset.sort;
      state.dir = state.sortKey === k && state.dir === 'asc' ? 'desc' : 'asc';
      state.sortKey = k;
      $$('[data-sort]', o.head).forEach((b) => b.removeAttribute('data-dir'));
      btn.dataset.dir = state.dir;
      render();
    }));

    if (o.pager) o.pager.addEventListener('click', (e) => {
      const b = e.target.closest('[data-page]');
      if (!b || b.disabled) return;
      state.page = +b.dataset.page;
      render();
      (o.scrollTarget || o.pager).scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });

    render();
    return {
      search(q) { state.query = q; state.page = 1; render(); },
      filter(fn) { state.filter = fn; state.page = 1; render(); },
      clearSelection() { state.selected.clear(); render(); },
      remove(rows) { rows.forEach((r) => { const i = o.rows.indexOf(r); if (i > -1) o.rows.splice(i, 1); state.selected.delete(r); }); render(); },
      refresh: render,
      get view() { return view; },
    };
  }

  Nava.dataTable = dataTable;
})(window.Nava);
