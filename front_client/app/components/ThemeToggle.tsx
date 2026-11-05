'use client';

import { useEffect, useState } from 'react';
import Icon from './Icon';
import { t } from '@/lib/i18n';

/**
 * Floating light/dark theme toggle, mirroring the owner app's theme modes.
 * Defaults to light (layout's themeScript); once the user toggles, the choice is
 * remembered in localStorage and applied via [data-theme] on <html>.
 */
export default function ThemeToggle() {
  const [theme, setTheme] = useState<'light' | 'dark' | null>(null);

  useEffect(() => {
    const stored = localStorage.getItem('theme');
    // localStorage only exists after hydration, so this must run in an effect.
    /* eslint-disable react-hooks/set-state-in-effect */
    setTheme(stored === 'dark' ? 'dark' : 'light');
    /* eslint-enable react-hooks/set-state-in-effect */
  }, []);

  const toggle = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    localStorage.setItem('theme', next);
    document.documentElement.setAttribute('data-theme', next);
  };

  // Avoid a hydration mismatch: render nothing until we know the theme.
  if (theme === null) return null;

  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={toggle}
      aria-label={theme === 'dark' ? t('switchToLightTheme') : t('switchToDarkTheme')}
      title={theme === 'dark' ? t('lightMode') : t('darkMode')}
    >
      <Icon name={theme === 'dark' ? 'lightMode' : 'darkMode'} size={19} />
    </button>
  );
}
