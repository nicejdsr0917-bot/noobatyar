'use client';

import Icon from '@/app/components/Icon';
import type { Business } from '@/lib/api';
import { t } from '@/lib/i18n';

/**
 * Stand-in for a phone number or address the server withheld because the
 * business's subscription has lapsed (admin policy, `Business.is_expired`).
 *
 * The real value never reached the browser — the API sends null — so what
 * is blurred here is placeholder text of the right shape, not the data
 * itself; there is nothing to un-blur from devtools.
 */

const PLACEHOLDER = {
  get phone() { return t('t02112345678'); },
  get address() { return t('no12SampleAlleyValiasr'); },
} as const;

const unavailable = () => t('thisBusinesssContactDetailsArent');

/**
 * Whether `field` is missing because of the subscription policy. A null
 * field on a business that is *not* expired was withheld by the owner's own
 * "show contact info to guests" switch (or was never filled in), and gets
 * no placeholder — that one stays simply absent, as before.
 *
 * The map pin is optional, so for it the server's `has_location` says
 * whether there is anything to stand in for.
 */
export function isWithheld(biz: Business, field: 'phone' | 'address' | 'location'): boolean {
  if (biz.is_expired !== true) return false;
  if (field === 'location') {
    return biz.has_location === true && (biz.latitude == null || biz.longitude == null);
  }
  return !biz[field];
}

/**
 * A map-shaped placeholder for a withheld pin: blocks, streets, a park and a
 * river drawn here, then blurred. Deliberately not real tiles, not even of
 * some default spot — no network, and nothing on it is a real place.
 */
export function LockedMap() {
  return (
    <div className="biz-map locked-map" role="img" aria-label={unavailable()} title={unavailable()}>
      <svg className="locked-map-art" viewBox="0 0 400 190" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
        <rect width="400" height="190" fill="#f2efe9" />
        <path d="M-10 150 C 80 120, 140 175, 230 140 S 360 110, 420 135" stroke="#aad3df" strokeWidth="16" fill="none" />
        <rect x="250" y="20" width="70" height="50" rx="4" fill="#cdebb0" />
        <rect x="30" y="18" width="60" height="38" rx="3" fill="#e0dbd2" />
        <rect x="110" y="18" width="70" height="38" rx="3" fill="#e0dbd2" />
        <rect x="30" y="74" width="60" height="40" rx="3" fill="#e0dbd2" />
        <rect x="110" y="74" width="70" height="40" rx="3" fill="#e0dbd2" />
        <rect x="200" y="84" width="46" height="34" rx="3" fill="#e0dbd2" />
        <rect x="330" y="80" width="56" height="34" rx="3" fill="#e0dbd2" />
        <path d="M0 64 H400 M100 0 V190 M190 0 V190 M330 0 V100" stroke="#fff" strokeWidth="9" />
        <path d="M0 64 H400" stroke="#fcd6a4" strokeWidth="6" />
        <path d="M240 0 L 300 190" stroke="#f7fabf" strokeWidth="7" />
        <path d="M200 70 m-9 0 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0" fill="var(--color-primary)" />
      </svg>
      <span className="locked-map-badge">
        <Icon name="lock" size={15} />
        {t('locationOnMap2')}</span>
    </div>
  );
}

export default function LockedContact({ kind }: { kind: keyof typeof PLACEHOLDER }) {
  return (
    <span className="contact-locked" role="img" aria-label={unavailable()} title={unavailable()}>
      <span className="contact-locked-text" aria-hidden="true">
        {PLACEHOLDER[kind]}
      </span>
      <Icon name="lock" size={14} className="contact-locked-icon" />
    </span>
  );
}
