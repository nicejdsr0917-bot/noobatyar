'use client';

import { t } from '@/lib/i18n';
import { changeLocale, useLocale } from '@/lib/i18n/client';

/**
 * Floating fa/en switch, sitting beside the theme toggle. Shows the language it
 * switches *to*, in that language ("EN" / "فا"); the full name is in the label.
 */
export default function LanguageToggle() {
  const locale = useLocale();
  return (
    <button
      type="button"
      className="lang-toggle"
      onClick={() => changeLocale(locale === 'fa' ? 'en' : 'fa')}
      aria-label={t('languageSwitchLabel')}
      title={t('languageSwitchLabel')}
      lang={locale === 'fa' ? 'en' : 'fa'}
    >
      {t('languageSwitch')}
    </button>
  );
}
