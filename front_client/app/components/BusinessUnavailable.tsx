'use client';

import Link from 'next/link';
import SupportLinks from '@/app/components/SupportLinks';
import { t } from '@/lib/i18n';

/**
 * The single "this booking link doesn't lead anywhere right now" screen.
 *
 * A customer reaches it from a stale QR code, a mistyped link, or a business
 * the public API no longer serves. The wording is deliberately the same for
 * every one of those causes: the API never tells the client *why* a business
 * is missing, and neither does this page.
 *
 * Rendered both by `app/b/[slug]/not-found.tsx` (server-side lookup failed)
 * and by the booking page when a request 404s mid-session — so keep it free of
 * hooks and server-only imports; it is bundled into a Client Component too.
 */
export default function BusinessUnavailable() {
  return (
    <div className="page-content">
      <div
        style={{
          minHeight: '100dvh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '40px 24px',
          textAlign: 'center',
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element -- brand mark is a
            local static SVG; next/image adds no value and needs explicit sizing. */}
        <img
          src="/icons/icon-192.png"
          alt={t('noobatyar')}
          width={84}
          height={84}
          style={{ borderRadius: 22, marginBottom: 22 }}
        />

        <h1
          style={{
            fontSize: 19,
            fontWeight: 700,
            color: 'var(--color-text)',
            marginBottom: 10,
          }}
        >
          {t('thisBusinessIsntAvailable')}</h1>

        <p
          style={{
            fontSize: 13,
            lineHeight: 2,
            color: 'var(--color-muted)',
            maxWidth: 300,
            marginBottom: 26,
          }}
        >
          {t('thisLinkIsntValidOr')}<br />
          {t('checkAddressAgainOrAsk')}</p>

        <p
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: 'var(--color-text)',
            marginBottom: 12,
          }}
        >
          {t('thinkSomethingsWrong')}</p>

        <SupportLinks />

        <Link
          href="/"
          style={{
            marginTop: 26,
            fontSize: 13,
            color: 'var(--color-primary)',
            textDecoration: 'none',
            fontWeight: 600,
          }}
        >
          {t('backToNoobatyar')}</Link>
      </div>
    </div>
  );
}
