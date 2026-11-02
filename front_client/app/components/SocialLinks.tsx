'use client';

/**
 * A business's social pages under its hero: one brand-coloured tile per
 * network the owner linked, popping in one after another. The server stores
 * bare handles (backend/business/social_links.py), so the URL is built here
 * and can only ever point at the network the key names.
 */

import type { Business, SocialKind } from '@/lib/api';
import { t } from '@/lib/i18n';
import { toPersianDigits as toFa } from '@/lib/validation';

interface Network {
  kind: SocialKind;
  label: () => string;
  icon: string;
  bg: string;
  pad: number;
  href: (v: string) => string;
  show: (v: string) => string;
}

const NETWORKS: Network[] = [
  {
    kind: 'instagram', label: () => t('instagram'), icon: '/icons/social/instagram.svg', pad: 9,
    bg: 'linear-gradient(45deg,#feda75,#fa7e1e 30%,#d62976 60%,#962fbf 80%,#4f5bd5)',
    href: (v) => `https://instagram.com/${encodeURIComponent(v)}`, show: (v) => `@${v}`,
  },
  {
    kind: 'telegram', label: () => t('telegram'), icon: '/icons/social/telegram.svg', pad: 9,
    bg: 'linear-gradient(135deg,#37aee2,#1e96c8)',
    href: (v) => `https://t.me/${encodeURIComponent(v)}`, show: (v) => `@${v}`,
  },
  {
    kind: 'whatsapp', label: () => t('whatsapp'), icon: '/icons/social/whatsapp.svg', pad: 9,
    bg: '#25d366',
    // 09121234567 -> 989121234567
    href: (v) => `https://wa.me/98${v.replace(/^0/, '')}`, show: (v) => toFa(v),
  },
  {
    kind: 'eitaa', label: () => t('eitaa'), icon: '/icons/social/eitaa.png', pad: 0, bg: '#f47435',
    href: (v) => `https://eitaa.com/${encodeURIComponent(v)}`, show: (v) => `@${v}`,
  },
  {
    kind: 'bale', label: () => t('bale'), icon: '/icons/social/bale.png', pad: 0, bg: '#00b894',
    href: (v) => `https://ble.ir/${encodeURIComponent(v)}`, show: (v) => `@${v}`,
  },
  {
    kind: 'rubika', label: () => t('rubika'), icon: '/icons/social/rubika.png', pad: 4, bg: '#fff',
    href: (v) => `https://rubika.ir/${encodeURIComponent(v)}`, show: (v) => `@${v}`,
  },
  {
    kind: 'website', label: () => t('website'), icon: '/icons/social/website.svg', pad: 9,
    bg: 'linear-gradient(135deg,#a855f7,#6d28d9)',
    href: (v) => (/^https?:\/\//.test(v) ? v : `https://${v}`),
    show: (v) => v.replace(/^https?:\/\/(www\.)?/, '').replace(/\/$/, ''),
  },
];

export default function SocialLinks({ business }: { business: Business }) {
  const links = business.social_links ?? {};
  const items = NETWORKS.filter((n) => links[n.kind]);
  if (items.length === 0) return null;
  return (
    <div className="section social-section">
      <div className="social-title">{t('socialPages', business.title)}</div>
      <div className="social-grid">
        {items.map((n, i) => {
          const v = links[n.kind]!;
          return (
            <a
              key={n.kind}
              href={n.href(v)}
              target="_blank"
              rel="noopener noreferrer nofollow"
              className="social-tile"
              style={{ animationDelay: `${i * 60}ms` }}
              aria-label={`${n.label()} ${n.show(v)}`}
            >
              <span className="social-logo" style={{ background: n.bg, padding: n.pad }}>
                {/* eslint-disable-next-line @next/next/no-img-element -- tiny static brand marks */}
                <img src={n.icon} alt="" />
              </span>
              <span className="social-text">
                <span className="social-name">{n.label()}</span>
                <span className="social-handle" dir="ltr">{n.show(v)}</span>
              </span>
            </a>
          );
        })}
      </div>
    </div>
  );
}
