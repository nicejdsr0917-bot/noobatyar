
import { localizeDigits, t } from '@/lib/i18n';
/**
 * Field validators + input normalizers shared by every form in the client app.
 *
 * Two rules hold everywhere:
 *
 *  1. **Persian/Arabic digits are accepted as input, Latin digits are stored.**
 *     Iranian keyboards emit ۰-۹ (and Arabic ٠-٩ on some Android IMEs). Every
 *     validator normalizes before testing, so a phone typed as ۰۹۱۲… passes,
 *     and the value handed to the API is always ASCII.
 *
 *  2. **Validators return `null` when valid**, or an error string in the UI language. That
 *     lets call sites write `const err = validatePhone(v)` and treat the result
 *     as both the boolean and the message.
 */

const PERSIAN_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const ARABIC_DIGITS = '٠١٢٣٤٥٦٧٨٩';

/** Fold Persian/Arabic digits to ASCII, leaving everything else untouched. */
export function toLatinDigits(input: string): string {
  return input.replace(/[۰-۹٠-٩]/g, (d) => {
    const p = PERSIAN_DIGITS.indexOf(d);
    if (p > -1) return String(p);
    return String(ARABIC_DIGITS.indexOf(d));
  });
}

/** Digits for display in the UI language: Persian in fa, Latin in en. */
export function toPersianDigits(input: string | number): string {
  return localizeDigits(input);
}

/** Keep only digits (after folding Persian/Arabic ones). */
export function digitsOnly(input: string): string {
  return toLatinDigits(input).replace(/\D/g, '');
}

/* ── Phone ─────────────────────────────────────────────────────── */

export const PHONE_MAX_LENGTH = 11;

export function normalizePhone(input: string): string {
  let d = digitsOnly(input);
  // Tolerate the shapes people actually paste: +98912…, 0098912…, 912…
  if (d.startsWith('0098')) d = d.slice(4);
  else if (d.startsWith('98') && d.length > 10) d = d.slice(2);
  if (d.length === 10 && d.startsWith('9')) d = `0${d}`;
  return d.slice(0, PHONE_MAX_LENGTH);
}

export function validatePhone(input: string): string | null {
  const d = normalizePhone(input);
  if (!d) return t('enterYourMobileNumber2');
  if (!d.startsWith('09')) return t('mobileNumberMustStartWith');
  if (d.length !== 11) return t('mobileNumberMustBe11');
  return null;
}

/* ── Person name ───────────────────────────────────────────────── */

export const NAME_MAX_LENGTH = 50;

export function validateName(input: string): string | null {
  const v = input.trim();
  if (!v) return t('enterYourName');
  if (v.length < 3) return t('nameMustBeAtLeast');
  if (v.length > NAME_MAX_LENGTH) return t('nameCantBeLongerThan', toPersianDigits(NAME_MAX_LENGTH));
  // Persian/Arabic letters, Latin letters, spaces, ZWNJ and hyphens. Digits and
  // punctuation are rejected: this feeds the name the business sees on a booking.
  if (!/^[؀-ۿ‌ a-zA-Z\-']+$/.test(v)) return t('nameCanOnlyContainLetters');
  return null;
}

/* ── Bank card ─────────────────────────────────────────────────── */

export const CARD_DIGITS = 16;

/** "6037991234567890" → "6037 9912 3456 7890" (groups of 4, for display). */
export function formatCardNumber(input: string): string {
  const d = digitsOnly(input).slice(0, CARD_DIGITS);
  return d.replace(/(\d{4})(?=\d)/g, '$1 ').trim();
}

/**
 * Luhn checksum — the same check the bank runs. Catches a mistyped digit in a
 * card the *owner* entered, which is worth surfacing before a customer wires
 * money into the void.
 */
export function isValidCardChecksum(input: string): boolean {
  const d = digitsOnly(input);
  if (d.length !== CARD_DIGITS) return false;
  let sum = 0;
  for (let i = 0; i < CARD_DIGITS; i++) {
    let n = Number(d[i]);
    // Double every second digit counting from the left on a 16-digit PAN.
    if (i % 2 === 0) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
  }
  return sum % 10 === 0;
}

/* ── Payment tracking reference ────────────────────────────────── */

export const REF_MIN_LENGTH = 4;
export const REF_MAX_LENGTH = 30;

/**
 * Bank tracking numbers ("شماره پیگیری") are digit strings of wildly varying
 * length depending on the issuing bank, so the only safe checks are "digits"
 * and "a plausible length".
 */
export function validatePaymentRef(input: string, { required }: { required: boolean }): string | null {
  const raw = input.trim();
  if (!raw) return required ? t('enterTrackingNumber') : null;
  const d = digitsOnly(raw);
  if (d.length !== raw.replace(/\s/g, '').length) return t('trackingNumberCanOnlyContain');
  if (d.length < REF_MIN_LENGTH) return t('trackingNumberMustBeAt', toPersianDigits(REF_MIN_LENGTH));
  if (d.length > REF_MAX_LENGTH) return t('trackingNumberCantBeLonger', toPersianDigits(REF_MAX_LENGTH));
  return null;
}

/* ── OTP ───────────────────────────────────────────────────────── */

export const OTP_LENGTH = 6;

export function validateOtp(code: string): string | null {
  const d = digitsOnly(code);
  if (d.length !== OTP_LENGTH) return t('enterFullDigitCode', toPersianDigits(OTP_LENGTH));
  return null;
}

/* ── Free text ─────────────────────────────────────────────────── */

export function validateFreeText(
  input: string,
  { max, label, required = false }: { max: number; label: string; required?: boolean },
): string | null {
  const v = input.trim();
  if (!v) return required ? t('enter', label) : null;
  if (v.length > max) return t('cantBeLongerThanCharacters', label, toPersianDigits(max));
  return null;
}
