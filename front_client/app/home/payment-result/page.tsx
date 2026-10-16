'use client';

import { useEffect, useState, Suspense } from 'react';
import { usePathname, useSearchParams } from 'next/navigation';
import { PANEL_URL, buildAppReturnLink, isWebPanelReturn, tryReturnToApp } from '@/lib/appReturn';
import { t } from '@/lib/i18n';

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface VerifyResult {
  success: boolean;
  message: string;
  data?: Record<string, unknown>;
}

function PaymentResultContent() {
  const searchParams = useSearchParams();
  const trackId = searchParams.get('trackId');
  const success = searchParams.get('success');

  const [result, setResult] = useState<VerifyResult | null>(null);
  const [loading, setLoading] = useState(true);

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
    if (!loading && result) tryReturnToApp(appLink);
  }, [loading, result, appLink]);
  // (tryReturnToApp is a plain navigation, so for the panel it is a normal
  // redirect back; the visible button below stays either way.)

  useEffect(() => {
    if (!trackId) {
      setResult({ success: false, message: t('paymentIdNotFound') });
      setLoading(false);
      return;
    }

    if (success === '0') {
      setResult({ success: false, message: t('paymentWasCancelledOrFailed') });
      setLoading(false);
      return;
    }

    const verify = async () => {
      try {
        const res = await fetch(
          `${BASE_URL}/api/accounting/payment-result?trackId=${trackId}&success=${success}`,
          { method: 'GET' }
        );
        const text = await res.text();
        // backend returns HTML — check if it contains success marker
        const isSuccess = text.includes(t('successful')) && !text.includes(t('failed'));
        setResult({
          success: isSuccess,
          message: isSuccess
            ? t('yourSubscriptionIsNowActive')
            : t('processingPaymentFailedPleaseContact'),
        });
      } catch {
        setResult({ success: false, message: t('couldntReachServer') });
      } finally {
        setLoading(false);
      }
    };

    verify();
  }, [trackId, success]);

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
            {result?.success && <p style={styles.sub}>{t('yourSubscriptionIsActive')}</p>}
            <a href={appLink} style={styles.button}>
              {toPanel ? t('backToPanel') : t('backToApp')}
            </a>
          </>
        )}
      </div>
    </div>
  );
}

export default function PaymentResultPage() {
  return (
    <Suspense fallback={<div style={styles.wrapper}><div style={styles.spinner} /></div>}>
      <PaymentResultContent />
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
    transition: 'opacity 0.2s',
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
