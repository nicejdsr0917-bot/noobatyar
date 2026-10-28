'use client';

import { createContext, use, useContext } from 'react';
import { DEFAULT_LOCALE, LOCALE_COOKIE, setSsrLocaleReader, type Locale } from './index';

const LocaleContext = createContext<Locale>(DEFAULT_LOCALE);

// On the server, t() takes the locale of the request being rendered from the
// nearest I18nProvider. `use` may be called from plain helpers as long as it
// happens during a render; outside one (module scope) it throws, and getLocale
// falls back to the default.
if (typeof window === 'undefined') {
  setSsrLocaleReader(() => {
    try {
      // The rule guards against swallowing a suspending promise; reading a
      // context never suspends, so the catch only sees calls outside a render.
      // eslint-disable-next-line react-hooks/rules-of-hooks
      return use(LocaleContext);
    } catch {
      return undefined;
    }
  });
}

export function I18nProvider({ locale, children }: { locale: Locale; children: React.ReactNode }) {
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
