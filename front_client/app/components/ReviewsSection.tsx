'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import {
  deleteMyReview, getMyReview, getReviewSummary, getReviews, reportReview, saveMyReview,
  type MyReview, type RatingSummary, type ReportReason, type Review, type ReviewPage,
} from '@/lib/api';
import { toPersianDigits as toFa } from '@/lib/validation';
import { useVisitorToken } from '@/lib/visitorToken';
import { t } from '@/lib/i18n';

export function Stars({ value }: { value: number }) {
  return (
    <span className="stars" role="img" aria-label={t('stars', toFa(String(value)))}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= Math.round(value) ? 'on' : ''}>★</span>
      ))}
    </span>
  );
}

const REASONS: [ReportReason, Parameters<typeof t>[0]][] = [
  ['SPAM', 'reasonSpam'], ['INAPPROPRIATE', 'reasonInappropriate'],
  ['MISLEADING', 'reasonMisleading'], ['IMPERSONATION', 'reasonImpersonation'],
  ['ILLEGAL', 'reasonIllegal'], ['OTHER', 'reasonOther'],
];

interface Props {
  businessId: number;
  summary: RatingSummary | null;
  initialPage: ReviewPage | null;
  loginHref: string;
}

export default function ReviewsSection({ businessId, summary: initialSummary, initialPage, loginHref }: Props) {
  const token = useVisitorToken();
  const [summary, setSummary] = useState(initialSummary);
  const [reviews, setReviews] = useState<Review[]>(initialPage?.results ?? []);
  const [next, setNext] = useState<number | null>(initialPage?.next ?? null);
  const [loadedMine, setMine] = useState<MyReview | null>(null);
  // Signed out means no review of one's own, whatever was loaded before.
  const mine = token ? loadedMine : null;
  const [editing, setEditing] = useState(false);
  const [rating, setRating] = useState(0);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [reporting, setReporting] = useState<number | null>(null);
  const [reason, setReason] = useState<ReportReason>('SPAM');
  const [detail, setDetail] = useState('');

  useEffect(() => {
    if (!token) return;
    let stale = false;
    getMyReview(businessId, token).then((r) => {
      if (stale) return;
      setMine(r);
      if (r) { setRating(r.rating); setText(r.text); }
    }).catch(() => {});
    return () => { stale = true; };
  }, [token, businessId]);

  // Opened from the "how was your visit?" push (…?review=1): bring the form into view.
  const sectionRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (new URLSearchParams(window.location.search).get('review') !== '1') return;
    const id = window.setTimeout(() => sectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 400);
    return () => window.clearTimeout(id);
  }, []);

  const refresh = useCallback(async () => {
    try {
      const page = await getReviews(businessId, 1);
      setReviews(page.results); setNext(page.next);
      setSummary(await getReviewSummary(businessId));
    } catch { /* keep what is shown */ }
  }, [businessId]);

  const run = async (fn: () => Promise<void>, okText: string) => {
    setBusy(true); setMsg(null);
    try { await fn(); setMsg({ ok: true, text: okText }); }
    catch (e) {
      const status = (e as { status?: number }).status;
      setMsg({ ok: false, text: status === 403 ? t('reviewEditWindowOver') : (e as Error).message });
    } finally { setBusy(false); }
  };

  const submit = () => run(async () => {
    const saved = await saveMyReview(businessId, { rating, text: text.trim() }, token!, !!mine);
    setMine(saved); setEditing(false); await refresh();
  }, t('reviewSaved'));

  const remove = () => run(async () => {
    await deleteMyReview(businessId, token!);
    setMine(null); setRating(0); setText(''); setEditing(false); await refresh();
  }, t('reviewDeleted'));

  const loadMore = async () => {
    if (!next) return;
    setBusy(true);
    try {
      const page = await getReviews(businessId, next);
      setReviews((r) => [...r, ...page.results]); setNext(page.next);
    } catch { /* leave the button for a retry */ } finally { setBusy(false); }
  };

  const sendReport = (id: number) => run(async () => {
    await reportReview(id, reason, detail.trim(), token ?? undefined);
    setReporting(null); setDetail('');
  }, t('reportSent'));

  const showForm = !!token && (!mine || editing);

  return (
    <section ref={sectionRef} className="section" style={{ paddingTop: 8, paddingBottom: 8 }} data-testid="reviews-section">
      <h2 className="section-title">{t('reviews')}</h2>

      {summary && summary.count > 0 ? (
        <div className="rating-summary" data-testid="rating-summary">
          <div className="rating-avg">{toFa(summary.avg.toFixed(1))}</div>
          <div>
            <Stars value={summary.avg} />
            <div className="rating-count">{t('reviewsCount', toFa(String(summary.count)))}</div>
          </div>
          <div className="rating-hist">
            {[5, 4, 3, 2, 1].map((n) => {
              const c = summary.histogram?.[String(n)] ?? 0;
              return (
                <div key={n} className="hist-row">
                  <span>{toFa(String(n))}</span>
                  <span className="hist-bar"><i style={{ width: `${(c / summary.count) * 100}%` }} /></span>
                </div>
              );
            })}
          </div>
        </div>
      ) : (
        <p className="rating-count">{t('noReviewsYet')}</p>
      )}

      {!token && <Link href={loginHref} className="btn-secondary review-cta">{t('signInToReview')}</Link>}
      {token && mine && !editing && (
        <div className="review-mine">
          <Stars value={mine.rating} />
          {mine.text && <p>{mine.text}</p>}
          <button type="button" className="btn-secondary" onClick={() => setEditing(true)}>{t('editReview')}</button>
          <button type="button" className="btn-secondary" onClick={remove} disabled={busy}>{t('deleteReview')}</button>
        </div>
      )}
      {showForm && (
        <div className="review-form" data-testid="review-form">
          <div role="radiogroup" aria-label={t('yourRating')} className="star-input">
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} type="button" role="radio" aria-checked={rating === n}
                aria-label={t('stars', toFa(String(n)))} className={n <= rating ? 'on' : ''}
                onClick={() => setRating(n)}>★</button>
            ))}
          </div>
          <textarea value={text} maxLength={1000} rows={3} placeholder={t('yourReviewText')}
            onChange={(e) => setText(e.target.value)} aria-label={t('yourReviewText')} />
          <div className="review-actions">
            <button type="button" className="btn-primary" disabled={busy || rating < 1} onClick={submit}>
              {t('submitReview')}
            </button>
            {editing && <button type="button" className="btn-secondary" onClick={() => setEditing(false)}>{t('cancel2')}</button>}
          </div>
        </div>
      )}
      {msg && <p role="status" className={msg.ok ? 'review-msg ok' : 'review-msg err'}>{msg.text}</p>}

      <ul className="review-list">
        {reviews.map((r) => (
          <li key={r.id} className="review-item" data-testid="review-item">
            <div className="review-head">
              {r.visitor_avatar ? (
                // eslint-disable-next-line @next/next/no-img-element -- remote media, sized by CSS
                <img className="review-avatar" src={r.visitor_avatar} alt="" loading="lazy" />
              ) : (
                <span className="review-avatar empty" aria-hidden="true">{r.visitor_name?.trim().charAt(0)}</span>
              )}
              <strong>{r.visitor_name}</strong>
              <Stars value={r.rating} />
              <time dateTime={r.created_at}>{new Date(r.created_at).toLocaleDateString('fa-IR')}</time>
            </div>
            {r.text && <p>{r.text}</p>}
            <button type="button" className="review-report" onClick={() => setReporting(reporting === r.id ? null : r.id)}>
              {t('reportReview')}
            </button>
            {reporting === r.id && (
              <div className="report-box">
                <strong>{t('reportReviewTitle')}</strong>
                <select value={reason} onChange={(e) => setReason(e.target.value as ReportReason)} aria-label={t('reportReviewTitle')}>
                  {REASONS.map(([v, k]) => <option key={v} value={v}>{t(k)}</option>)}
                </select>
                <textarea rows={2} value={detail} maxLength={500} placeholder={t('reportDetail')}
                  onChange={(e) => setDetail(e.target.value)} />
                <div className="review-actions">
                  <button type="button" className="btn-primary" disabled={busy} onClick={() => sendReport(r.id)}>{t('submitReview')}</button>
                  <button type="button" className="btn-secondary" onClick={() => setReporting(null)}>{t('cancel2')}</button>
                </div>
              </div>
            )}
          </li>
        ))}
      </ul>
      {next && <button type="button" className="btn-secondary" onClick={loadMore} disabled={busy}>{t('loadMoreReviews')}</button>}
    </section>
  );
}
