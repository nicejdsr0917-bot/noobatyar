'use client';

import { getVisitorToken } from '@/lib/auth';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getMyAppointments, type Appointment } from '@/lib/api';
import Toolbar from '@/app/components/Toolbar';
import AppointmentCard from '@/app/components/AppointmentCard';
import Icon from '@/app/components/Icon';
import AppBanner from '@/app/components/AppBanner';
import { t } from '@/lib/i18n';

type Tab = 'UPCOMING' | 'PAST' | 'CANCELED';

const TAB_LABELS: Record<Tab, string> = {
  get UPCOMING() { return t('upcoming'); },
  get PAST() { return t('past'); },
  get CANCELED() { return t('cancelled'); },
};

export default function AppointmentsPage() {
  const router = useRouter();
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState<Tab>('UPCOMING');
  // Set when the deposit gateway sends the client back here. Read from the URL
  // client-side rather than via useSearchParams, which would force this whole
  // page behind a Suspense boundary for one optional banner.
  const [paymentResult, setPaymentResult] = useState<'success' | 'failed' | 'conflict' | null>(null);

  useEffect(() => {
    const state = new URLSearchParams(window.location.search).get('payment');
    if (state === 'success' || state === 'failed' || state === 'conflict') {
      setPaymentResult(state);
      // Drop the parameter so a refresh does not replay the banner.
      window.history.replaceState({}, '', '/appointments');
    }
  }, []);

  useEffect(() => {
    const token = getVisitorToken();
    if (!token) {
      router.replace('/auth/login?redirect=/appointments');
      return;
    }
    getMyAppointments(token)
      .then(setAppointments)
      .catch(() => setError(t('couldntLoadAppointments')))
      .finally(() => setLoading(false));
  }, [router]);

  const filtered = appointments.filter((apt) => {
    if (activeTab === 'UPCOMING') return !['COMPLETED', 'CANCELLED', 'NO_SHOW'].includes(apt.status);
    if (activeTab === 'PAST') return apt.status === 'COMPLETED';
    return ['CANCELLED', 'NO_SHOW'].includes(apt.status);
  });

  return (
    <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
      <Toolbar
        title={t('myAppointments')}
        actions={[{ icon: 'person', label: t('myProfile'), onClick: () => router.push('/profile') }]}
      />

      <div style={{ padding: '24px' }}>
        {/* ── Deposit gateway result ── */}
        {paymentResult && (
          <div
            style={{
              marginBottom: 20,
              padding: '14px 16px',
              borderRadius: 14,
              fontSize: 13.5,
              lineHeight: 1.8,
              display: 'flex',
              alignItems: 'center',
              gap: 9,
              textAlign: 'start',
              background:
                paymentResult === 'success'
                  ? 'rgba(34,197,94,0.12)'
                  : paymentResult === 'conflict'
                    ? 'rgba(245,158,11,0.14)'
                    : 'rgba(239,68,68,0.12)',
              color:
                paymentResult === 'success'
                  ? '#16a34a'
                  : paymentResult === 'conflict'
                    ? '#b45309'
                    : '#dc2626',
            }}
          >
            <Icon name={paymentResult === 'success' ? 'checkCircle' : 'error'} size={20} />
            <span>
              {paymentResult === 'success'
                ? t('depositPaidSuccessfullyYourAppointment')
                : paymentResult === 'conflict'
                  ? t('depositPaidButSlotTaken')
                  : t('paymentFailedYourAppointmentHasnt')}
            </span>
          </div>
        )}

        {/* ── Tabs ── */}
        <div
          style={{
            display: 'flex',
            background: 'var(--color-surface-variant)',
            borderRadius: 14,
            padding: 4,
            marginBottom: 24,
          }}
        >
          {(['CANCELED', 'PAST', 'UPCOMING'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              style={{
                flex: 1,
                padding: '10px 0',
                border: 'none',
                background: activeTab === tab ? 'var(--color-surface)' : 'none',
                borderRadius: 10,
                color: activeTab === tab ? 'var(--color-text)' : 'var(--color-muted)',
                fontSize: 12,
                fontWeight: 600,
                fontFamily: 'inherit',
                boxShadow: activeTab === tab ? '0 1px 3px rgba(0,0,0,0.05)' : 'none',
                cursor: 'pointer',
              }}
            >
              {TAB_LABELS[tab]}
            </button>
          ))}
        </div>

        {loading &&
          [1, 2, 3].map((i) => (
            <div key={i} className="skeleton" style={{ height: 120, marginBottom: 16, borderRadius: 16 }} />
          ))}

        {error && (
          <div className="empty-state">
            <span className="empty-state-icon" style={{ color: 'var(--color-error)' }}>
              <Icon name="error" size={26} />
            </span>
            <h3>{error}</h3>
            <button className="btn-primary" style={{ marginTop: 18, height: 48 }} onClick={() => location.reload()}>
              {t('tryAgain')}</button>
          </div>
        )}

        {!loading && !error && filtered.length === 0 && (
          <div className="empty-state">
            <span className="empty-state-icon">
              <Icon name="eventBusy" size={26} />
            </span>
            <h3>{t('noAppointmentsFound')}</h3>
            <p>{t('youHaveNoAppointmentsIn')}</p>
          </div>
        )}

        {filtered.map((appt) => (
          <AppointmentCard key={appt.id} appt={appt} />
        ))}

        {/* ── Noobatyar Promo ──
            After the list, not above it: this is the screen people come back to
            in order to check a booking, and an advert between them and that
            answer is the fastest way to make it resented. Shown once the fetch
            settles so it never appears above a skeleton. */}
        {!loading && !error && <AppBanner variant="owner" style={{ marginTop: 18 }} />}
      </div>
    </div>
  );
}
