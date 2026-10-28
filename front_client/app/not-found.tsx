import Link from 'next/link';
import SupportLinks from '@/app/components/SupportLinks';
import { getServerT } from '@/lib/i18n/server';

/** Catch-all 404 for any route that isn't a business code. */
export default async function NotFound() {
  const t = await getServerT();
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
        {/* eslint-disable-next-line @next/next/no-img-element -- local static brand mark */}
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
          {t('pageNotFound')}</h1>

        <p
          style={{
            fontSize: 13,
            lineHeight: 2,
            color: 'var(--color-muted)',
            maxWidth: 300,
            marginBottom: 26,
          }}
        >
          {t('addressYouEnteredDoesntExist')}</p>

        <p
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: 'var(--color-text)',
            marginBottom: 12,
          }}
        >
          {t('needHelp')}</p>

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
