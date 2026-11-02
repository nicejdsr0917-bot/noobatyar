'use client';

import { Suspense, useEffect, useState } from 'react';
import { usePathname, useSearchParams } from 'next/navigation';
import { PANEL_URL, buildAppReturnLink, isWebPanelReturn, tryReturnToApp } from '@/lib/appReturn';
import { getPaymentResult, type PaymentResultKind } from '@/lib/api';
import { t } from '@/lib/i18n';

interface VerifyResult {
  success: boolean;
  message: string;
  /** Sub-line under the message, only on success. */
  sub?: string;
}

function PaymentResultContent({ kind }: { kind: PaymentResultKind }) {
  const searchParams = useSearchParams();
  const trackId = searchParams.get('trackId');
  const success = searchParams.get('success');

  // Outcomes known without asking the server are derived, not set in an effect.
  const early: VerifyResult | null = !trackId
    ? { success: false, message: t('paymentIdNotFound') }
    : success === '0'
      ? { success: false, message: t('paymentWasCancelledOrFailed') }
      : null;

  const [fetched, setFetched] = useState<{ key: string; result: VerifyResult } | null>(null);
  const key = `${trackId}|${success}`;
  const result = early ?? (fetched?.key === key ? fetched.result : null);
  const loading = !result;

  // A payer from the web panel has no app to jump into: send them back to the
  // panel. Everyone else gets the noobatyar:// link of the native apps.
  const toPanel = isWebPanelReturn(usePathname());
  const appLink = toPanel
    ? PANEL_URL
    : buildAppReturnLink({
        success: result?.success ?? false,
        txn: trackId,
      });

  // Once the outcome is known, hand the user straight back to the app. The
  // button below stays regardless: browsers often block a custom-scheme jump
  // that no tap asked for.
  useEffect(() => {
    if (result) tryReturnToApp(appLink);
  }, [result, appLink]);

  useEffect(() => {
    if (early || !trackId) return;
    let cancelled = false;
    getPaymentResult(kind, trackId, success)
      .then(({ success: ok, packName }) => {
        const addon = kind === 'addon';
        const next: VerifyResult = ok
          ? {
              success: true,
              message: addon
                ? t('packIsNowActive', packName ?? t('addOnPack'))
                : t('yourSubscriptionIsNowActive'),
            }
          : { success: false, message: t('processingPaymentFailedPleaseContact') };
        if (!cancelled) setFetched({ key, result: next });
      })
      .catch(() => {
        if (!cancelled) setFetched({ key, result: { success: false, message: t('couldntReachServer') } });
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kind, trackId, success]);

  return (
    <div style={styles.wrapper}>
      <div style={styles.card}>
        {loading ? (
          <>
            <div style={styles.spinner} />
            <p style={styles.loadingText}>{t('checkingPayment')}</p>
          </>
        ) : (
          <>
            <div style={{ ...styles.icon, color: result?.success ? '#22c55e' : '#ef4444' }}>
              {result?.success ? '✓' : '✗'}
            </div>
            <h1 style={{ ...styles.title, color: result?.success ? '#22c55e' : '#ef4444' }}>
              {result?.success ? t('paymentSuccessful') : t('paymentFailed')}
            </h1>
            <p style={styles.message}>{result?.message}</p>
            {result?.success && (
              <p style={styles.sub}>
                {kind === 'addon' ? t('addOnPackActivated') : t('yourSubscriptionIsActive')}
              </p>
            )}
            <a href={appLink} style={styles.button}>
              {toPanel ? t('backToPanel') : t('backToApp')}
            </a>
          </>
        )}
      </div>
    </div>
  );
}

export default function PaymentResult({ kind }: { kind: PaymentResultKind }) {
  return (
    <Suspense fallback={<div style={styles.wrapper}><div style={styles.spinner} /></div>}>
      <PaymentResultContent kind={kind} />
    </Suspense>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrapper: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
    fontFamily: 'Vazirmatn, Tahoma, Arial, sans-serif',
    direction: 'rtl',
  },
  card: {
    background: 'rgba(255,255,255,0.05)',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: '24px',
    padding: '48px 40px',
    maxWidth: '420px',
    width: '90%',
    textAlign: 'center',
    boxShadow: '0 25px 50px rgba(0,0,0,0.4)',
  },
  icon: {
    fontSize: '64px',
    fontWeight: 'bold',
    marginBottom: '16px',
    display: 'block',
  },
  title: {
    fontSize: '24px',
    fontWeight: '700',
    marginBottom: '12px',
  },
  message: {
    color: '#94a3b8',
    fontSize: '16px',
    lineHeight: '1.6',
    marginBottom: '12px',
  },
  sub: {
    color: '#64748b',
    fontSize: '14px',
    marginBottom: '24px',
  },
  button: {
    display: 'inline-block',
    marginTop: '24px',
    padding: '12px 32px',
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff',
    borderRadius: '12px',
    textDecoration: 'none',
    fontWeight: '600',
    fontSize: '15px',
  },
  spinner: {
    width: '48px',
    height: '48px',
    border: '4px solid rgba(255,255,255,0.1)',
    borderTop: '4px solid #6366f1',
    borderRadius: '50%',
    animation: 'spin 0.8s linear infinite',
    margin: '0 auto 16px',
  },
  loadingText: {
    color: '#94a3b8',
    fontSize: '16px',
  },
};
