'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  cancelAppointment,
  getAppointment,
  payAppointment,
  payAppointmentWithReceipt,
  startOnlineDeposit,
  type Appointment,
  type PaymentMethod,
} from '@/lib/api';
import { bankLogoSrc, detectBank } from '@/lib/bankLogos';
import {
  digitsOnly,
  formatCardNumber,
  isValidCardChecksum,
  validatePaymentRef,
  REF_MAX_LENGTH,
} from '@/lib/validation';
import Toolbar from '@/app/components/Toolbar';
import TextField from '@/app/components/TextField';
import ReceiptUpload from '@/app/components/ReceiptUpload';
import Icon from '@/app/components/Icon';
import { intlLocale, t } from '@/lib/i18n';

export default function CheckoutPage({ params }: { params: Promise<{ slug: string; id: string }> }) {
  const router = useRouter();
  const [slug, setSlug] = useState('');
  const [id, setId] = useState(0);
  const [appointment, setAppointment] = useState<Appointment | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [paymentRef, setPaymentRef] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState('');
  // Set by a failed submit so every field stops hiding its error behind
  // "not blurred yet" (see TextField).
  const [submitAttempted, setSubmitAttempted] = useState(false);
  // Null until the user picks one: the effective method falls back to the first
  // method this business actually offers, rather than assuming CARD.
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethod | null>(null);
  const [toast, setToast] = useState('');
  const [copied, setCopied] = useState(false);
  const [redirecting, setRedirecting] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  const biz = appointment?.business;
  const accepted = biz?.accepted_payment_methods;
  const depositMode = biz?.deposit_mode;
  // A mandatory deposit cannot be settled in person, so cash is not offered.
  // An optional deposit always allows paying at the venue instead.
  // Online is payable either through a real Zibal gateway or, for businesses
  // without a merchant account, the older static payment link. Requiring the
  // link alone would hide the option from every gateway-configured business.
  const canPayOnline =
    !!accepted?.includes('ONLINE') && (!!biz?.online_gateway_enabled || !!biz?.payment_link);
  const canPayCard = !accepted || accepted.includes('CARD');
  const canPayCash =
    depositMode !== 'MANDATORY' && (!!accepted?.includes('CASH') || depositMode === 'OPTIONAL');

  const availableMethods: PaymentMethod[] = [
    ...(canPayOnline ? (['ONLINE'] as const) : []),
    ...(canPayCard ? (['CARD'] as const) : []),
    ...(canPayCash ? (['CASH'] as const) : []),
  ];
  const method: PaymentMethod =
    selectedMethod && availableMethods.includes(selectedMethod)
      ? selectedMethod
      : availableMethods[0] ?? 'CARD';

  // Proof is required for card/online, and either half of it satisfies the
  // backend — so the reference is only mandatory when no receipt was attached.
  const refRequired = method !== 'CASH' && !file;
  const refError = validatePaymentRef(paymentRef, { required: refRequired });

  const rawCard = appointment?.card_number ?? '';
  const cardDigits = digitsOnly(rawCard);
  const cardLooksWrong = cardDigits.length > 0 && !isValidCardChecksum(cardDigits);
  const cardBank = detectBank(cardDigits);

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(''), 3000);
  };

  const copyCard = async () => {
    if (!cardDigits) return;
    try {
      await navigator.clipboard.writeText(cardDigits);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      showToast(t('copyingIsntAvailable'));
    }
  };

  useEffect(() => {
    params.then((p) => {
      setSlug(p.slug);
      setId(parseInt(p.id, 10));
    });
  }, [params]);

  useEffect(() => {
    if (!id) return;
    const token = localStorage.getItem('visitor_token');
    if (!token) {
      router.push(`/auth/login?redirect=/b/${slug}/checkout/${id}`);
      return;
    }

    getAppointment(id, token)
      .then((apt) => {
        if (apt.status !== 'LOCKED') {
          // Already paid or otherwise moved on.
          router.replace('/appointments');
        } else {
          setAppointment(apt);
        }
      })
      .catch(() => setError(t('appointmentNotFoundOrYou')))
      .finally(() => setLoading(false));
  }, [id, slug, router]);

  /**
   * Hands the client to Zibal. The booking is settled by the gateway callback
   * on the server, so there is nothing to submit from this page afterwards —
   * the client returns straight to /appointments.
   */
  const handleGatewayRedirect = async () => {
    const token = localStorage.getItem('visitor_token');
    if (!token) return;

    setRedirecting(true);
    try {
      const { payment_url } = await startOnlineDeposit(id, token);
      window.location.href = payment_url;
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : t('couldntConnectToPaymentGateway'));
      setRedirecting(false);
    }
  };

  /**
   * The toolbar's × — an explicit way out of the payment step, in place of
   * the arrow-back this screen used to inherit from Toolbar's default.
   *
   * That default called router.back(), plain browser history navigation.
   * The bug this replaces: this appointment already exists as LOCKED before
   * this page even renders (the previous, time-picker page creates it), and
   * `router.replace` on the success/checkout pages only swaps the *current*
   * history entry — it never removes the entries around it. So a tab that
   * has ever completed a booking before carries THAT attempt's checkout and
   * /booking-success pages in its back/forward list too. Pressing back twice
   * from here can walk past this attempt's own time-picker page and land on
   * one of those old, unrelated screens — which is how a customer backing
   * out of an unfinished payment saw a stale "نوبت شما تایید شد" from a
   * previous booking and reasonably read it as this one succeeding.
   *
   * So this button does not navigate through history at all: it releases
   * the lock this attempt is holding (cancelAppointment — LOCKED is in the
   * backend's CANCELLABLE_STATUSES, so the slot is freed for other
   * customers) and replaces straight to this business's own page, a fixed,
   * unambiguous destination.
   */
  const handleCancel = async () => {
    // Guards against racing an in-flight payment submission or gateway
    // redirect — cancelling and paying at nearly the same moment must not
    // both land on the server.
    if (cancelling || submitting || redirecting) return;
    setCancelling(true);
    const token = localStorage.getItem('visitor_token');
    if (token && id) {
      try {
        await cancelAppointment(id, token);
      } catch {
        // Best-effort: the customer must still be able to leave even if the
        // release call fails on a network hiccup — an appointment stuck
        // LOCKED past its own slot time ages out on the backend regardless.
      }
    }
    router.replace(slug ? `/b/${slug}` : '/');
  };

  const handleSubmit = async () => {
    setSubmitAttempted(true);

    // Card and online transfers must carry proof; paying in person carries none.
    if (method !== 'CASH' && !file && !paymentRef.trim()) {
      showToast(t('enterTrackingNumberOrUpload'));
      return;
    }
    if (method !== 'CASH' && refError) {
      showToast(refError);
      return;
    }

    const token = localStorage.getItem('visitor_token');
    if (!token) return;

    setSubmitting(true);
    try {
      if (method !== 'CASH' && file) {
        const formData = new FormData();
        formData.append('method', method);
        formData.append('payment_receipt', file);
        if (paymentRef.trim()) {
          formData.append('payment_reference', digitsOnly(paymentRef));
        }
        await payAppointmentWithReceipt(id, formData, token);
      } else {
        await payAppointment(
          id,
          method === 'CASH' ? '' : digitsOnly(paymentRef),
          token,
          method,
        );
      }
      // The animated confirmation screen replaces the old toast-then-list hop,
      // and `replace` keeps the settled checkout out of the back stack.
      router.replace(`/booking-success?id=${id}&kind=${method === 'CASH' ? 'pending' : 'paid'}`);
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : t('couldntRecordPayment'));
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
        <Toolbar title={t('paymentAndConfirmation')} backIcon="close" onBack={handleCancel} />
        <div style={{ padding: 24 }}>
          <div className="skeleton" style={{ height: 52, borderRadius: 14, marginBottom: 16 }} />
          <div className="skeleton" style={{ height: 180, borderRadius: 16, marginBottom: 16 }} />
          <div className="skeleton" style={{ height: 52, borderRadius: 12 }} />
        </div>
      </div>
    );
  }

  if (error || !appointment) {
    return (
      <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
        <Toolbar title={t('paymentAndConfirmation')} backIcon="close" onBack={handleCancel} />
        <div style={{ padding: 24 }}>
          <div className="empty-state">
            <span className="empty-state-icon" style={{ color: 'var(--color-error)' }}>
              <Icon name="error" size={26} />
            </span>
            <h3>{error}</h3>
            <button className="btn-primary" style={{ marginTop: 18, height: 48 }} onClick={handleCancel}>
              {t('back')}</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
      <Toolbar title={t('paymentAndConfirmation')} backIcon="close" onBack={handleCancel} />

      <div style={{ padding: '24px' }}>
        {/* ── Method tabs ── (only the methods this business actually offers) */}
        {availableMethods.length > 1 && (
          <div
            style={{
              display: 'flex',
              background: 'var(--color-surface-variant)',
              borderRadius: 14,
              padding: 4,
              marginBottom: 16,
            }}
          >
            {availableMethods.map((m) => {
              const isActive = method === m;
              const label =
                m === 'ONLINE' ? t('onlineGateway') : m === 'CARD' ? t('cardTransfer') : t('payOnSite');
              const icon = m === 'ONLINE' ? 'payments' : m === 'CARD' ? 'creditCard' : 'storefront';
              return (
                <button
                  key={m}
                  onClick={() => setSelectedMethod(m)}
                  style={{
                    flex: 1,
                    padding: '11px 0',
                    border: 'none',
                    background: isActive ? 'var(--color-surface)' : 'none',
                    borderRadius: 10,
                    color: isActive ? 'var(--color-text)' : 'var(--color-muted)',
                    fontSize: 12.5,
                    fontWeight: 600,
                    fontFamily: 'inherit',
                    boxShadow: isActive ? '0 1px 3px rgba(0,0,0,0.05)' : 'none',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 5,
                  }}
                >
                  <Icon name={icon} size={15} />
                  {label}
                </button>
              );
            })}
          </div>
        )}

        {depositMode === 'OPTIONAL' && (
          <div
            style={{
              display: 'flex',
              gap: 9,
              background: 'var(--color-primary-tint)',
              color: 'var(--color-text)',
              borderRadius: 12,
              padding: '12px 14px',
              marginBottom: 16,
              fontSize: 12.5,
              textAlign: 'start',
              lineHeight: 1.8,
            }}
          >
            <Icon name="info" size={18} color="var(--color-primary)" style={{ marginTop: 2 }} />
            <span>
              {t('depositForThisBusinessIs')}<b>{t('optional')}</b> {t('youCanPayDepositOr')}</span>
          </div>
        )}

        {/* ── Card transfer ── */}
        {method === 'CARD' && (
          <>
            <div
              style={{
                background: 'var(--color-surface)',
                borderRadius: 16,
                border: '1px solid var(--color-border)',
                padding: 20,
                marginBottom: 20,
              }}
            >
              <h2
                style={{
                  fontSize: 13.5,
                  fontWeight: 700,
                  textAlign: 'start',
                  marginBottom: 16,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 7,
                }}
              >
                <Icon name="creditCard" size={17} color="var(--color-primary)" />
                {t('transferToCardNumberBelow')}</h2>

              {/* Grouped 4-by-4 so it can be read off the screen and typed into
                  a banking app without losing your place. */}
              <div className="card-number-row">
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                  {cardBank && (
                    // eslint-disable-next-line @next/next/no-img-element -- local /public asset, no loader needed
                    <img
                      src={bankLogoSrc(cardBank.key)}
                      alt={cardBank.name}
                      title={cardBank.name}
                      className="card-bank-logo"
                    />
                  )}
                  <span className="card-number" dir="ltr">
                    {cardDigits ? formatCardNumber(cardDigits) : t('noCardNumberOnFile')}
                  </span>
                </div>
                <button
                  onClick={copyCard}
                  disabled={!cardDigits}
                  className="card-copy"
                  style={{ color: copied ? 'var(--color-success-text)' : 'var(--color-primary)' }}
                >
                  <Icon name={copied ? 'check' : 'copy'} size={15} />
                  {copied ? t('copied') : t('copy')}
                </button>
              </div>

              {cardLooksWrong && (
                <p className="field-error" style={{ marginTop: 2, marginBottom: 10 }}>
                  <Icon name="warning" size={14} />
                  <span>{t('cardNumberOnFileDoesnt')}</span>
                </p>
              )}

              <div
                style={{
                  textAlign: 'start',
                  fontSize: 12.5,
                  color: 'var(--color-muted)',
                  marginBottom: 14,
                }}
              >
                {t('name')}{appointment?.card_owner_name || t('businessOwner')}
              </div>

              {biz?.deposit_amount ? (
                <div style={{ textAlign: 'start', fontSize: 15, fontWeight: 700, color: 'var(--color-text)' }}>
                  {t('depositAmount')}{biz.deposit_amount.toLocaleString(intlLocale())} {t('toman')}{depositMode === 'OPTIONAL' && (
                    <span style={{ fontSize: 12, fontWeight: 500, color: 'var(--color-muted)' }}> {t('optional2')}</span>
                  )}
                </div>
              ) : (
                <div style={{ textAlign: 'start', fontSize: 12.5, color: 'var(--color-muted)' }}>
                  {t('agreeOnAmountWithBusiness')}</div>
              )}
            </div>

            {/* ── Proof of payment ── */}
            <div className="section-head" style={{ marginBottom: 10 }}>
              <Icon name="receipt" size={17} color="var(--color-primary)" />
              <h2>{t('paymentReceipt')}</h2>
            </div>

            <TextField
              label={t('trackingNumber')}
              value={paymentRef}
              onChange={setPaymentRef}
              transform={(raw) => digitsOnly(raw).slice(0, REF_MAX_LENGTH)}
              validate={(v) => validatePaymentRef(v, { required: refRequired })}
              showError={submitAttempted}
              type="tel"
              inputMode="numeric"
              icon="payments"
              dir="ltr"
              placeholder={t('t12345678')}
              hint={
                file
                  ? t('receiptUploadedTrackingNumberIs')
                  : t('transactionTrackingNumberOrUpload')
              }
              required={refRequired}
            />

            <ReceiptUpload file={file} onChange={setFile} onError={showToast} />
          </>
        )}

        {method === 'ONLINE' && (
          <div style={{ textAlign: 'center', padding: '28px 0', color: 'var(--color-muted)', fontSize: 14 }}>
            {biz?.online_gateway_enabled ? (
              /* Real Zibal gateway: the bank confirms the payment and the
                 booking is settled by the callback, so there is no tracking
                 number for the client to copy back. */
              <>
                <span className="empty-state-icon" style={{ margin: '0 auto 14px' }}>
                  <Icon name="payments" size={26} />
                </span>
                <p style={{ marginBottom: 20 }}>{t('youllBeSentToBank')}</p>
                <button
                  className="btn-primary"
                  onClick={handleGatewayRedirect}
                  disabled={redirecting}
                  style={{ height: 52 }}
                >
                  {redirecting ? (
                    <>
                      <span className="btn-spinner" /> {t('redirecting2')}</>
                  ) : (
                    t('payWithBankGateway')
                  )}
                </button>
                <p style={{ marginTop: 16, fontSize: 12, color: 'var(--color-faint)' }}>
                  {t('afterSuccessfulPaymentYourAppointment')}</p>
              </>
            ) : biz?.payment_link ? (
              <>
                <p style={{ marginBottom: 16 }}>
                  {t('toPayDepositOnlinePlease')}</p>
                <a
                  href={biz.payment_link.startsWith('http') ? biz.payment_link : `https://${biz.payment_link}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                    color: 'var(--color-primary)',
                    fontWeight: 700,
                    textDecoration: 'none',
                    fontSize: 15,
                  }}
                >
                  {t('goToPaymentGateway')}<Icon name="chevronLeft" size={17} />
                </a>
                <p style={{ margin: '24px 0 8px', fontSize: 12, color: 'var(--color-faint)' }}>
                  {t('afterPayingEnterTrackingNumber')}</p>

                <div style={{ textAlign: 'start' }}>
                  <TextField
                    value={paymentRef}
                    onChange={setPaymentRef}
                    transform={(raw) => digitsOnly(raw).slice(0, REF_MAX_LENGTH)}
                    validate={(v) => validatePaymentRef(v, { required: refRequired })}
                    showError={submitAttempted}
                    type="tel"
                    inputMode="numeric"
                    icon="payments"
                    dir="ltr"
                    placeholder={t('paymentGatewayTrackingNumber')}
                    required={refRequired}
                  />
                </div>
              </>
            ) : (
              t('connectingToPaymentGatewayIs')
            )}
          </div>
        )}

        {method === 'CASH' && (
          <div style={{ textAlign: 'center', padding: '28px 0' }}>
            <span className="empty-state-icon" style={{ margin: '0 auto 14px' }}>
              <Icon name="storefront" size={26} />
            </span>
            <p style={{ color: 'var(--color-muted)', fontSize: 13.5, lineHeight: 1.9 }}>
              {t('youChoseToPayOn')}<br />
              {t('yourAppointmentIsSentTo')}</p>
          </div>
        )}
      </div>

      {toast && <div className="toast">{toast}</div>}

      {/* ── Fixed bottom button ──
           Hidden for the real gateway: that flow finishes at the bank and is
           settled by the server callback, so a "submit" here would have nothing
           to send and would only invite a double payment. */}
      {!(method === 'ONLINE' && biz?.online_gateway_enabled) && (
        <div className="btn-group">
          <button className="btn-primary" onClick={handleSubmit} disabled={submitting}>
            {submitting ? (
              <>
                <span className="btn-spinner" /> {t('submitting')}</>
            ) : method === 'CASH' ? (
              t('bookAppointmentPayOnSite')
            ) : (
              t('confirmAppointment')
            )}
          </button>
          {method === 'CARD' && biz?.deposit_amount ? (
            <p
              style={{
                textAlign: 'center',
                fontSize: 11,
                color: 'var(--color-faint)',
                marginTop: 8,
              }}
            >
              {t('amount')}{biz.deposit_amount.toLocaleString(intlLocale())} {t('toman')}</p>
          ) : null}
        </div>
      )}
    </div>
  );
}
