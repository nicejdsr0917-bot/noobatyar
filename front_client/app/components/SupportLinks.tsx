'use client';

/**
 * The same channels the owner app lists under "درباره ما", so a customer who
 * hits a dead end has the identical set of ways to reach us. Kept in one place
 * because these live in three surfaces now — change them here, not inline.
 */

import Icon, { type IconName } from './Icon';
import { t } from '@/lib/i18n';

export interface SupportChannel {
  key: string;
  label: string;
  href: string;
  /**
   * Material icon standing in for the channel. None of these messengers ship a
   * Material brand glyph, so each takes the nearest generic one; `fg` carries
   * the brand colour so the row still reads as five distinct destinations.
   */
  icon: IconName;
  tint: string;
  fg: string;
}

export const SUPPORT_CHANNELS: SupportChannel[] = [
  { key: 'bale', get label() { return t('bale'); }, href: 'https://ble.ir/noobatyar', icon: 'forum', tint: '#e0f2fe', fg: '#0369a1' },
  { key: 'eitaa', get label() { return t('eitaa'); }, href: 'https://eitaa.com/noobatyar', icon: 'send', tint: '#fef3c7', fg: '#b45309' },
  { key: 'rubika', get label() { return t('rubika'); }, href: 'https://rubika.ir/noobatyar', icon: 'sms', tint: '#ede9fe', fg: '#6d28d9' },
  // Telegram's mark is a paper plane, so it gets 'send'.
  { key: 'telegram', get label() { return t('telegram'); }, href: 'https://t.me/noobatyar', icon: 'send', tint: '#e0f2fe', fg: '#0284c7' },
  { key: 'instagram', get label() { return t('instagram'); }, href: 'https://instagram.com/javiddev', icon: 'photoCamera', tint: '#fce7f3', fg: '#be185d' },
  { key: 'website', get label() { return t('website'); }, href: 'https://noobatyar.ir', icon: 'language', tint: '#dcfce7', fg: '#15803d' },
];

export default function SupportLinks() {
  return (
    <div
      style={{
        // A fixed grid rather than a wrapping flex row, so no item is ever
        // stranded alone on a second line. Six channels → two even rows of 3.
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: 7,
        width: '100%',
        maxWidth: 340,
      }}
    >
      {SUPPORT_CHANNELS.map((c) => (
        <a
          key={c.key}
          href={c.href}
          target="_blank"
          rel="noopener noreferrer"
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 6,
            minWidth: 0,
            padding: '10px 2px',
            borderRadius: 14,
            background: 'var(--color-surface)',
            border: '1.5px solid var(--color-border)',
            textDecoration: 'none',
            color: 'var(--color-text)',
          }}
        >
          <span
            style={{
              width: 36,
              height: 36,
              borderRadius: 11,
              display: 'grid',
              placeItems: 'center',
              background: c.tint,
              color: c.fg,
            }}
          >
            <Icon name={c.icon} size={18} />
          </span>
          <span
            style={{
              fontSize: 10,
              fontWeight: 600,
              whiteSpace: 'nowrap',
              maxWidth: '100%',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {c.label}
          </span>
        </a>
      ))}
    </div>
  );
}
