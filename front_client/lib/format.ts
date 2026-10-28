
import { intlLocale, localizeDigits, t } from '@/lib/i18n';
/**
 * Shared display formatters.
 *
 * These were duplicated verbatim across the appointment pages; the profile page
 * would have been a third copy. Dates come from Intl: `fa-IR` yields Jalali
 * output in the Persian UI, `en-US` Gregorian in the English one — `moment-jalaali` is in package.json but
 * has never been imported anywhere.
 */

/* Category glyphs now live in app/components/CategoryArt.tsx (CategoryGlyph
   for UI sizes, CategoryArt for the hero mark) — Tabler icons rather than
   emoji, which rendered differently on every Android skin and could not take
   a theme colour. */

export const STATUS_LABELS: Record<string, { label: string; color: string; bg: string }> = {
  LOCKED:               { get label() { return t('awaitingPayment'); }, color: '#b45309', bg: '#fef3c7' },
  PENDING_VERIFICATION: { get label() { return t('awaitingPaymentApproval'); }, color: '#d97706', bg: '#fef3c7' },
  PENDING_APPROVAL:     { get label() { return t('awaitingApproval'); }, color: '#d97706', bg: '#fef3c7' },
  WAITING:              { get label() { return t('inQueue'); },          color: '#047857', bg: '#d1fae5' },
  CONFIRMED:            { get label() { return t('confirmed'); },      color: '#047857', bg: '#d1fae5' },
  IN_PROGRESS:          { get label() { return t('inService'); },   color: '#1d4ed8', bg: '#dbeafe' },
  COMPLETED:            { get label() { return t('done'); },       color: '#374151', bg: '#f3f4f6' },
  CANCELLED:            { get label() { return t('cancelled2'); },         color: '#b91c1c', bg: '#fee2e2' },
  NO_SHOW:              { get label() { return t('noShow'); },           color: '#b91c1c', bg: '#fee2e2' },
};

/** Appointment timestamps arrive from the API as JS milliseconds. */
export function formatDate(ts: number): string {
  const d = new Date(ts);
  return d.toLocaleString(intlLocale(), {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Activity rows carry ISO-8601 strings rather than epoch millis. */
export function formatIsoDate(iso: string): string {
  const ts = Date.parse(iso);
  return Number.isNaN(ts) ? '—' : formatDate(ts);
}

/** Digits in the UI language (name kept for its many call sites). */
export function toPersianNumerals(n: number | string): string {
  return localizeDigits(n);
}
