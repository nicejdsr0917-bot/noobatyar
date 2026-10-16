'use client';

import { createContext, useContext } from 'react';
import { LOCALE_COOKIE, setSsrLocale, type Locale } from './index';

const LocaleContext = createContext<Locale>('fa');

export function I18nProvider({ locale, children }: { locale: Locale; children: React.ReactNode }) {
  if (typeof window === 'undefined') setSsrLocale(locale);
  return <LocaleContext.Provider value={locale}>{children}</LocaleContext.Provider>;
}

export const useLocale = () => useContext(LocaleContext);

/**
 * Persists the choice in a cookie (so the server renders the right language,
 * direction and font on the next request) and reloads to apply it everywhere.
 */
export function changeLocale(locale: Locale) {
  document.cookie = `${LOCALE_COOKIE}=${locale}; path=/; max-age=31536000; samesite=lax`;
  try { localStorage.setItem(LOCALE_COOKIE, locale); } catch {}
  window.location.reload();
}
