import { en, fa, type MessageKey } from './messages';

export type Locale = 'fa' | 'en';
export type { MessageKey };

/** Cookie holding the chosen language; read by the server for <html lang dir>. */
export const LOCALE_COOKIE = 'lang';
export const DEFAULT_LOCALE: Locale = 'fa';

export function parseLocale(value: string | undefined | null): Locale {
  return value === 'en' ? 'en' : DEFAULT_LOCALE;
}

export const dirOf = (locale: Locale) => (locale === 'fa' ? 'rtl' : 'ltr');

// Server-side rendering of client components has no <html> to read, so
// I18nProvider records the request's locale here before its subtree renders.
// Pages here fetch their data in the browser, so what SSR renders is mostly
// shells; in the browser the server-stamped <html lang> is the source.
let ssrLocale: Locale = DEFAULT_LOCALE;
export function setSsrLocale(locale: Locale) {
  ssrLocale = locale;
}

/** The active UI language. */
export function getLocale(): Locale {
  if (typeof document !== 'undefined') return parseLocale(document.documentElement.lang);
  return ssrLocale;
}

export function translate(locale: Locale, key: MessageKey, ...args: (string | number)[]): string {
  const template: string = (locale === 'en' ? en : fa)[key] ?? fa[key];
  return args.length ? template.replace(/\{(\d+)\}/g, (_, i) => String(args[Number(i)] ?? '')) : template;
}

/**
 * Translates [key] into the active language. Fine in client components and
 * plain helpers; server components use getServerT() from './server'.
 */
export function t(key: MessageKey, ...args: (string | number)[]): string {
  return translate(getLocale(), key, ...args);
}

/** Persian digits in the Persian UI, Latin digits in the English one. */
export function localizeDigits(value: string | number, locale: Locale = getLocale()): string {
  const s = String(value);
  if (locale === 'en') {
    return s.replace(/[۰-۹]/g, (d) => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(d)))
      .replace(/[٠-٩]/g, (d) => String('٠١٢٣٤٥٦٧٨٩'.indexOf(d)));
  }
  return s.replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[Number(d)]);
}

/** Intl locale: fa-IR gives the Jalali calendar, en-US the Gregorian one. */
export function intlLocale(locale: Locale = getLocale()): string {
  return locale === 'en' ? 'en-US' : 'fa-IR';
}
