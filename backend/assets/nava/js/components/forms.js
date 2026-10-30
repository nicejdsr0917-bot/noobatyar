/**
 * ابزارهای فرم: نمایش/مخفی رمز، سنجش قدرت رمز، اعتبارسنجی و ورودی کد یک‌بارمصرف
 */
(function (Nava) {
  'use strict';
  const { $, $$ } = Nava.utils;

  /* نمایش / مخفی کردن رمز */
  $$('[data-password-toggle]').forEach((btn) => {
    const input = btn.parentElement.querySelector('input');
    btn.addEventListener('click', () => {
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      btn.innerHTML = Nava.icons.svg(show ? 'eye-off' : 'eye', 'size-4');
      btn.setAttribute('aria-label', show ? 'مخفی کردن رمز' : 'نمایش رمز');
    });
  });

  /* قدرت رمز عبور */
  const LEVELS = [
    { label: 'خیلی ضعیف', color: 'bg-danger' },
    { label: 'ضعیف', color: 'bg-danger' },
    { label: 'متوسط', color: 'bg-warning' },
    { label: 'خوب', color: 'bg-foreground/70' },
    { label: 'قوی', color: 'bg-foreground' },
  ];
  function score(pw) {
    let s = 0;
    if (pw.length >= 8) s++;
    if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++;
    if (/\d/.test(pw)) s++;
    if (/[^A-Za-z0-9]/.test(pw) || pw.length >= 12) s++;
    return pw ? s : -1;
  }
  $$('[data-strength]').forEach((meter) => {
    const input = document.getElementById(meter.dataset.strength);
    const bars = $$('span', meter);
    const label = $('[data-strength-label]', meter.parentElement);
    input.addEventListener('input', () => {
      const s = score(input.value);
      bars.forEach((b, i) => {
        b.className = i <= s - 1 || (s === 0 && i === 0) ? LEVELS[Math.max(0, s)].color + ' on' : '';
      });
      if (label) label.textContent = s < 0 ? '' : LEVELS[Math.max(0, s)].label;
    });
  });

  /* اعتبارسنجی */
  const RULES = {
    required: (v) => v.trim().length > 0,
    email: (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim()),
    phone: (v) => /^(09|۰۹)[0-9۰-۹]{9}$/.test(v.trim()),
    min8: (v) => v.length >= 8,
    match: (v, input) => v === document.getElementById(input.dataset.match).value,
    checked: (v, input) => input.checked,
  };
  function validateField(input) {
    const field = input.closest('.field');
    const rules = (input.dataset.validate || '').split(' ').filter(Boolean);
    const ok = rules.every((r) => RULES[r](input.value, input));
    if (field) {
      field.classList.remove('has-error');
      if (!ok) { void field.offsetWidth; field.classList.add('has-error'); }
    }
    input.setAttribute('aria-invalid', String(!ok));
    return ok;
  }
  function validate(form) {
    const inputs = $$('[data-validate]', form);
    const results = inputs.map(validateField);
    const firstBad = inputs[results.indexOf(false)];
    if (firstBad) firstBad.focus();
    return results.every(Boolean);
  }
  $$('[data-validate]').forEach((input) =>
    input.addEventListener(input.type === 'checkbox' ? 'change' : 'blur', () => input.value && validateField(input))
  );

  /* حالت بارگذاری دکمه‌ی ارسال */
  function loading(btn, on) {
    if (on) {
      btn.dataset.label = btn.innerHTML;
      btn.innerHTML = '<span class="spinner"></span>' + (btn.dataset.loadingText || 'لطفاً صبر کنید…');
      btn.disabled = true;
    } else {
      btn.innerHTML = btn.dataset.label;
      btn.disabled = false;
    }
  }

  /* ورودی کد یک‌بارمصرف */
  function otp(root, onComplete) {
    const cells = $$('input', root);
    const value = () => cells.map((c) => c.value).join('');
    cells.forEach((c, i) => {
      c.addEventListener('input', () => {
        c.value = c.value.replace(/[^0-9۰-۹]/g, '').slice(-1);
        c.classList.toggle('filled', !!c.value);
        if (c.value && cells[i + 1]) cells[i + 1].focus();
        if (value().length === cells.length) onComplete(value());
      });
      c.addEventListener('keydown', (e) => {
        if (e.key === 'Backspace' && !c.value && cells[i - 1]) cells[i - 1].focus();
      });
      c.addEventListener('paste', (e) => {
        e.preventDefault();
        const digits = (e.clipboardData.getData('text') || '').replace(/[^0-9۰-۹]/g, '').split('');
        cells.forEach((cell, j) => { cell.value = digits[j] || ''; cell.classList.toggle('filled', !!cell.value); });
        (cells[digits.length] || cells[cells.length - 1]).focus();
        if (value().length === cells.length) onComplete(value());
      });
    });
    return { reset() { cells.forEach((c) => { c.value = ''; c.classList.remove('filled'); }); cells[0].focus(); } };
  }

  Nava.forms = { validate, validateField, loading, otp };
})(window.Nava);
