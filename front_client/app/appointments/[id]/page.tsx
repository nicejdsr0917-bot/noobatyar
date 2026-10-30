'use client';

import { googleMapsSearchUrl } from '@/lib/maps';
import { getVisitorToken } from '@/lib/auth';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getAppointment, cancelAppointment, businessCategoryLabel, type Appointment } from '@/lib/api';
import { STATUS_LABELS, formatDate } from '@/lib/format';
import Toolbar from '@/app/components/Toolbar';
import Icon from '@/app/components/Icon';
import LockedContact, { isWithheld } from '@/app/components/LockedContact';
import { BusinessAvatar } from '@/app/components/AppointmentCard';
import { t } from '@/lib/i18n';

// Anything still ahead of the appointment time can be cancelled by the client.
const CANCELLABLE = ['LOCKED', 'PENDING_APPROVAL', 'PENDING_VERIFICATION', 'WAITING', 'CONFIRMED'];


export default function AppointmentDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const router = useRouter();
  // Captured once at mount: "is it still ahead of us" does not need a live clock.
  const [now] = useState(() => Date.now());
  const [appointment, setAppointment] = useState<Appointment | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [toast, setToast] = useState('');

  const handleCancel = async () => {
    if (!appointment) return;
    const token = getVisitorToken();
    if (!token) return;

    setCancelling(true);
    try {
      await cancelAppointment(appointment.id, token);
      setAppointment({ ...appointment, status: 'CANCELLED' });
      setConfirmCancel(false);
      setToast(t('yourAppointmentWasCancelled'));
      setTimeout(() => setToast(''), 3000);
    } catch (err: unknown) {
      setToast(err instanceof Error ? err.message : t('couldntCancelAppointment'));
      setTimeout(() => setToast(''), 3000);
    } finally {
      setCancelling(false);
    }
  };

  useEffect(() => {
    params.then((p) => {
      const aptId = parseInt(p.id, 10);
      const token = getVisitorToken();
      if (!token) {
        router.replace(`/auth/login?redirect=/appointments/${aptId}`);
        return;
      }
      getAppointment(aptId, token)
        .then(setAppointment)
        .catch(() => setError(t('appointmentNotFoundOrYou')))
        .finally(() => setLoading(false));
    });
  }, [params, router]);

  if (loading) {
    return (
      <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
        <Toolbar title={t('appointmentDetails')} actions={[{ icon: 'calendar', label: t('myAppointments'), onClick: () => router.push('/appointments') }]} />
        <div style={{ padding: 24 }}>
          <div className="skeleton" style={{ width: 72, height: 72, borderRadius: '50%', margin: '8px auto 16px' }} />
          <div className="skeleton" style={{ height: 20, width: 160, borderRadius: 8, margin: '0 auto 24px' }} />
          <div className="skeleton" style={{ height: 150, borderRadius: 16, marginBottom: 16 }} />
          <div className="skeleton" style={{ height: 110, borderRadius: 16 }} />
        </div>
      </div>
    );
  }

  if (error || !appointment) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <span className="empty-state-icon" style={{ margin: '0 auto 12px', color: 'var(--color-error)' }}>
          <Icon name="error" size={26} />
        </span>
        <p style={{ color: 'var(--color-muted)' }}>{error}</p>
        <button className="btn-primary" style={{ marginTop: 20, width: 'auto', padding: '0 32px' }} onClick={() => router.back()}>{t('back')}</button>
      </div>
    );
  }

  const st = STATUS_LABELS[appointment.status] || { label: appointment.status, color: 'var(--color-muted)', bg: '#f3f4f6' };
  const biz = appointment.business;

  return (
    <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>

      <Toolbar title={t('appointmentDetails')} actions={[{ icon: 'calendar', label: t('myAppointments'), onClick: () => router.push('/appointments') }]} />

      <div style={{ padding: '24px 24px' }}>

        {/* ── Header ── */}
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          {/* The business's own logo, as on the booking pages; falls back to
              the category glyph when there is none (or it fails to load). */}
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 12 }}>
            <BusinessAvatar logo={biz.logo} category={biz.category} size={76} />
          </div>
          <h2 style={{ fontSize: 18, fontWeight: 700, color: 'var(--color-text)' }}>{biz.title}</h2>
          <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4, marginBottom: 14 }}>
            {businessCategoryLabel(biz)}
          </div>
          <div style={{ display: 'inline-block', background: st.bg, color: st.color, padding: '6px 16px', borderRadius: 20, fontSize: 13, fontWeight: 700 }}>
            {st.label}
          </div>
          {appointment.status === 'WAITING' && (
            <div style={{ marginTop: 12, fontSize: 13, color: 'var(--color-success-text)', fontWeight: 600 }}>
              {appointment.queue_position === 0 ? t('itsYourTurn') : t('peopleAheadOfYou', appointment.queue_position)}
            </div>
          )}
        </div>

        {/* ── Appointment Info ── */}
        <div style={{
          background: 'var(--color-surface)', borderRadius: 16, border: '1px solid var(--color-border)',
          padding: 24, marginBottom: 16,
        }}>
          <h2 style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 12 }}>{t('appointmentInfo')}</h2>
          <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--color-text)', direction: 'rtl' }}>
            {formatDate(appointment.appointment_date)}
          </div>

          <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px dashed var(--color-border)', display: 'flex', justifyContent: 'space-between' }}>
            <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>{t('trackingCode')}</div>
            <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--color-text)', fontFamily: 'monospace' }}>#{appointment.id}</div>
          </div>

          {(appointment.reminder_sms_sent || appointment.reminder_push_sent) && (
            <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px dashed var(--color-border)' }}>
              <div style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 8 }}>{t('reminder')}</div>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {appointment.reminder_push_sent && (
                  <span className="reminder-chip" style={{ fontSize: 11, padding: '4px 10px' }}>
                    <Icon name="notifications" size={13} />
                    {t('notificationSent')}</span>
                )}
                {appointment.reminder_sms_sent && (
                  <span className="reminder-chip" style={{ fontSize: 11, padding: '4px 10px' }}>
                    <Icon name="sms" size={13} />
                    {t('smsSent')}</span>
                )}
              </div>
            </div>
          )}
        </div>

        {/* ── Contact ── */}
        <div style={{
          background: 'var(--color-surface)', borderRadius: 16, border: '1px solid var(--color-border)',
          padding: 24, marginBottom: 24,
        }}>
          <h2 style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 12 }}>{t('contactDetails')}</h2>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
            <div style={{ fontSize: 12, color: 'var(--color-muted)', flexShrink: 0 }}>{t('address')}</div>
            {biz.address ? (
              <a
                href={googleMapsSearchUrl(biz.address)}
                target="_blank" rel="noopener noreferrer"
                style={{ fontSize: 13, color: 'var(--color-primary)', lineHeight: 1.7, textAlign: 'end', textDecoration: 'none', fontWeight: 600 }}
              >
                {biz.address}
              </a>
            ) : isWithheld(biz, 'address') ? (
              <div style={{ fontSize: 13 }}><LockedContact kind="address" /></div>
            ) : (
              <div style={{ fontSize: 13, color: 'var(--color-text)' }}>{t('notProvided')}</div>
            )}
          </div>

          <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px dashed var(--color-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>{t('phone')}</div>
            {biz.phone ? (
              <a href={`tel:${biz.phone}`} dir="ltr" style={{ fontSize: 14, color: 'var(--color-primary)', textDecoration: 'none', fontWeight: 700 }}>
                {biz.phone}
              </a>
            ) : isWithheld(biz, 'phone') ? (
              <div style={{ fontSize: 14 }}><LockedContact kind="phone" /></div>
            ) : (
              <div style={{ fontSize: 13, color: 'var(--color-text)' }}>{t('notProvided')}</div>
            )}
          </div>
        </div>

        {/* ── Actions ── */}
        {appointment.status === 'LOCKED' && (
          <button
            className="btn-primary"
            onClick={() => router.push(`/b/Noobatyar-${biz.unique_code}/checkout/${appointment.id}`)}
            style={{ marginBottom: 12 }}
          >
            {t('completePayment')}</button>
        )}

        <button
          onClick={() => router.push(`/b/Noobatyar-${biz.unique_code}`)}
          style={{
            width: '100%', height: 52, borderRadius: 14, border: '1px solid var(--color-border)',
            background: 'var(--color-surface)', color: 'var(--color-muted)', fontSize: 14, fontWeight: 600,
            cursor: 'pointer', fontFamily: 'inherit',
          }}
        >
          {t('viewBusinessProfile')}</button>

        {/* ── Cancel (only while the appointment is still ahead of us) ── */}
        {CANCELLABLE.includes(appointment.status) && appointment.appointment_date > now && (
          confirmCancel ? (
            <div style={{ marginTop: 12, padding: 16, borderRadius: 14, border: '1px solid var(--color-border)', background: 'var(--color-surface)' }}>
              <div style={{ fontSize: 14, color: 'var(--color-text)', textAlign: 'center', marginBottom: 14, lineHeight: 1.8 }}>
                {t('areYouSureYouWant')}</div>
              <div style={{ display: 'flex', gap: 10 }}>
                <button
                  onClick={handleCancel}
                  disabled={cancelling}
                  style={{ flex: 1, height: 46, borderRadius: 12, border: 'none', background: '#b91c1c', color: '#fff', fontSize: 14, fontWeight: 700, cursor: 'pointer', fontFamily: 'inherit' }}
                >
                  {cancelling ? t('cancelling') : t('yesCancel')}
                </button>
                <button
                  onClick={() => setConfirmCancel(false)}
                  disabled={cancelling}
                  style={{ flex: 1, height: 46, borderRadius: 12, border: '1px solid var(--color-border)', background: 'var(--color-surface)', color: 'var(--color-muted)', fontSize: 14, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit' }}
                >
                  {t('cancel')}</button>
              </div>
            </div>
          ) : (
            <button
              onClick={() => setConfirmCancel(true)}
              style={{
                width: '100%', height: 52, marginTop: 12, borderRadius: 14,
                border: '1px solid #fecaca', background: 'var(--color-surface)', color: '#b91c1c',
                fontSize: 14, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit',
              }}
            >
              {t('cancelAppointment')}</button>
          )
        )}

      </div>

      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}
