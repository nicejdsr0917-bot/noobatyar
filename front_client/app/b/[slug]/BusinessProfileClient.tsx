'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { businessCategoryLabel, type Business } from '@/lib/api';
import BusinessNotice from '@/app/components/BusinessNotice';
import Icon, { type IconName } from '@/app/components/Icon';
import CategoryArt, { CategoryGlyph } from '@/app/components/CategoryArt';
import AppBanner from '@/app/components/AppBanner';
import { toPersianDigits as toFa } from '@/lib/validation';

interface Props {
  business: Business;
  slug: string;
}

export default function BusinessProfileClient({ business, slug }: Props) {
  const router = useRouter();
  const [isNavigating, setIsNavigating] = useState(false);

  // Derived from the logo server-side (Business.theme_color) and already
  // contrast-clamped for white text there, so it can be used raw. Falls back
  // to the brand purple for a business with no logo.
  const heroColor = business.theme_color || '#8b5cf6';
  const bookingEnabled = business.booking_enabled !== false; // default true if undefined

  const logoUrl = business.logo
    ? business.logo.startsWith('http')
      ? business.logo
      : `${process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000'}${business.logo}`
    : null;
  // A logo URL that 404s (media file gone) otherwise renders the browser's
  // broken-image glyph over the hero. Falling back to the category icon is
  // the same thing a business with no logo at all already shows.
  const [logoFailed, setLogoFailed] = useState(false);
  const [bioExpanded, setBioExpanded] = useState(false);

  /**
   * Where the bio goes depends on how many *lines* it is — not how many
   * characters.
   *
   * A short bio is a tagline — it belongs under the name, and reads well
   * there. A long one is prose, and the hero is the wrong place for prose:
   * at 244 characters it pushed the hero to twice its height, buried the
   * call buttons below the fold, and ran straight over the category artwork
   * behind it. Centred long text is also the hardest way to read Persian,
   * since both edges end up ragged.
   *
   * Character count alone gets this wrong in the other direction too: a bio
   * of five short emoji-led lines ("💜رنگ مو\n💚کراتین\n...") is only ~45
   * characters — nowhere near a length-based cutoff — but is still five
   * lines, and the hero's subtitle is a 2-line clamp
   * (-webkit-line-clamp in globals.css). The clamp doesn't know or care
   * *why* a bio is long; a bio the owner wrote as a short list is exactly as
   * unreadable, ellipsised at line 2, as one that is long text. So this
   * counts line breaks first, and only falls back to a character estimate
   * for a single unbroken line long enough to *wrap* past two lines on its
   * own (~300px wide at 13px) — that case has no '\n' to count.
   */
  const bio = business.bio?.trim() ?? '';
  const HERO_TAGLINE_MAX_LINES = 2;
  const HERO_TAGLINE_MAX_CHARS = 90;
  const bioIsProse =
    bio.split('\n').length > HERO_TAGLINE_MAX_LINES || bio.length > HERO_TAGLINE_MAX_CHARS;
  const heroTagline = bioIsProse
    ? ''
    : bio || `ارائه خدمات تخصصی در زمینه ${businessCategoryLabel(business)} توسط کادر مجرب.`;

  // ── Stat values (short, so they fit the compact cards) ──
  // Minute suffix only when non-zero: "۹ تا ۲۱" reads better than "۹:۰۰ تا
  // ۲۱:۰۰" for the (still-typical) whole-hour business, and a business that
  // actually opens on a half hour is exactly when the suffix earns its space.
  const formatWorkTime = (hour: number, minute: number | undefined) =>
    minute ? `${toFa(String(hour).padStart(2, '0'))}:${toFa(String(minute).padStart(2, '0'))}` : toFa(String(hour).padStart(2, '0'));
  const workHours = `${formatWorkTime(business.work_start_hour, business.work_start_minute)} تا ${formatWorkTime(business.work_end_hour, business.work_end_minute)}`;

  const depositText =
    business.deposit_mode === 'NONE'
      ? 'رایگان'
      : business.deposit_amount
        ? `${toFa(business.deposit_amount.toLocaleString('en-US'))} ت`
        : business.deposit_mode === 'MANDATORY'
          ? 'اجباری'
          : 'اختیاری';

  // Payment comes from accepted_payment_methods. The older single-value
  // payment_method is only a fallback: the owner app never sends it, so it is
  // permanently "NONE" and reading it made every business look cash-free.
  const methods = business.accepted_payment_methods?.length
    ? business.accepted_payment_methods
    : business.payment_method && business.payment_method !== 'NONE'
      ? [business.payment_method]
      : [];

  const acceptsOnline = methods.includes('ONLINE') || methods.includes('GATEWAY');
  const acceptsCard = methods.includes('CARD');
  const acceptsCash = methods.includes('CASH');

  const paymentText =
    acceptsOnline && acceptsCard ? 'آنلاین / کارت'
      : acceptsOnline ? 'آنلاین'
        : acceptsCard && acceptsCash ? 'کارت / محل'
          : acceptsCard ? 'کارت به کارت'
            : acceptsCash ? 'در محل'
              : 'ندارد';

  const paymentIcon: IconName = acceptsOnline
    ? 'payments'
    : acceptsCard
      ? 'creditCard'
      : acceptsCash
        ? 'storefront'
        : 'creditCard';

  return (
    // .themed re-points the primary tokens at this business's colour for the
    // whole page — buttons, stat icons, the address pin, and the promo banner
    // — so the header is not the only part that reflects their brand.
    <div
      className="page-content themed"
      style={{ ['--hero-color' as string]: heroColor }}
    >

      {/* ── Hero ── */}
      <div className="biz-hero">
        {/* Category line-art, in place of the decorative blobs this used to
            draw: it says what the business actually does. */}
        <CategoryArt category={business.category} className="biz-hero-art" />
        <div className="biz-hero-avatar">
          {logoUrl && !logoFailed ? (
            // eslint-disable-next-line @next/next/no-img-element -- remote media host, no loader configured
            <img
              src={logoUrl}
              alt={business.title}
              onError={() => setLogoFailed(true)}
              // onError alone misses the common case: the <img> ships in the
              // SSR HTML, so a failing logo has usually already errored by the
              // time React hydrates and attaches the handler. A decoded-but-
              // zero-width image is exactly that already-failed state.
              ref={(node) => {
                if (node?.complete && node.naturalWidth === 0) setLogoFailed(true);
              }}
            />
          ) : (
            <CategoryGlyph category={business.category} size={44} stroke={1.5} />
          )}
        </div>

        <h1 className="biz-hero-title">{business.title}</h1>

        <span className="biz-hero-chip">
          <CategoryGlyph category={business.category} size={14} />
          <span>{businessCategoryLabel(business)}</span>
        </span>

        {heroTagline && <p className="biz-hero-bio">{heroTagline}</p>}

        {business.phone && (
          <div className="biz-hero-actions">
            <a href={`tel:${business.phone}`} className="biz-action call">
              <Icon name="call" size={16} />
              <span dir="ltr">{toFa(business.phone)}</span>
            </a>
            {/* Same call button, repeated per extra number — a customer
                calling the wrong line for a multi-number business is exactly
                what having more than one number is meant to prevent. */}
            {business.extra_phones?.map((extraPhone) => (
              <a key={extraPhone} href={`tel:${extraPhone}`} className="biz-action call">
                <Icon name="call" size={16} />
                <span dir="ltr">{toFa(extraPhone)}</span>
              </a>
            ))}
          </div>
        )}
      </div>

      {/* ── Owner notice — first thing under the hero, before any detail ── */}
      <BusinessNotice business={business} style={{ paddingTop: 16 }} />

      {/* ── Stats strip ── */}
      <div className="section" style={{ paddingTop: 20, paddingBottom: 8 }}>
        <div className="stat-grid">
          <div className="stat-card">
            <div className="ico"><Icon name="schedule" size={20} /></div>
            <div className="k">ساعات کاری</div>
            <div className="v">{workHours}</div>
          </div>
          <div className="stat-card">
            <div className="ico"><Icon name="creditCard" size={20} /></div>
            <div className="k">بیعانه</div>
            <div className="v">{depositText}</div>
          </div>
          <div className="stat-card">
            <div className="ico"><Icon name={paymentIcon} size={20} /></div>
            <div className="k">روش پرداخت</div>
            <div className="v">{paymentText}</div>
          </div>
        </div>
      </div>

      {/* ── About ──
          Sits after the stats, not before them: hours, deposit and payment
          are what a visitor is deciding on, and the description supports
          that decision rather than leading it. */}
      {bioIsProse && (
        <div className="section" style={{ paddingTop: 8, paddingBottom: 8 }}>
          <div className="biz-about">
            <div className="biz-about-head">
              <span className="ico"><Icon name="info" size={17} /></span>
              <h2>درباره‌ی ما</h2>
            </div>
            <p className={bioExpanded ? 'biz-about-text' : 'biz-about-text clamped'}>
              {bio}
            </p>
            <button
              type="button"
              className="biz-about-toggle"
              onClick={() => setBioExpanded((v) => !v)}
              aria-expanded={bioExpanded}
            >
              {bioExpanded ? 'کمتر' : 'بیشتر'}
              <Icon name={bioExpanded ? 'chevronUp' : 'chevronDown'} size={15} />
            </button>
          </div>
        </div>
      )}

      {/* ── Address ── */}
      {business.address && (
        <div className="section" style={{ paddingTop: 8, paddingBottom: 8 }}>
          <a
            className="biz-address"
            href={`https://maps.google.com/?q=${encodeURIComponent(business.address)}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            <div className="pin"><Icon name="locationOn" size={19} /></div>
            <div className="addr-text">
              <div className="addr-k">آدرس</div>
              <div className="addr-v">{business.address}</div>
            </div>
            <span className="chevron"><Icon name="chevronLeft" size={18} /></span>
          </a>
        </div>
      )}

      {/* ── Booking Disabled Banner ──
          Stays down here, next to the CTA it explains, rather than next to the
          notice at the top: the two say different things (one is the owner's
          message, one is the state of the button) and separating them keeps a
          business that is both closed *and* running late from stacking two
          near-identical boxes above the fold. */}
      {!bookingEnabled && (
        <div className="section" style={{ paddingTop: 8, paddingBottom: 8 }}>
          <div style={{
            background: 'var(--color-error-bg)',
            border: '1px solid var(--color-error)',
            borderRadius: 12, padding: '14px 18px',
            fontSize: 13, color: 'var(--color-error)', fontWeight: 600,
            textAlign: 'right', lineHeight: 1.6,
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
            <Icon name="block" size={18} />
            ثبت نوبت در حال حاضر غیرفعال است
          </div>
        </div>
      )}

      {/* ── Noobatyar Promo ──
          Takes this business's colour rather than Noobatyar purple: the page is
          .themed, and see `.themed .app-banner` in globals.css. */}
      <div className="section" style={{ paddingTop: 8, paddingBottom: 8 }}>
        <AppBanner variant="owner" />
      </div>

      {/* ── Fixed Bottom Button ── */}
      <div className="btn-group">
        <button
          className="btn-primary"
          onClick={() => {
            setIsNavigating(true);
            router.push(`/b/${slug}/book`);
          }}
          disabled={!bookingEnabled || isNavigating}
          style={!bookingEnabled ? { background: 'var(--color-faint)', boxShadow: 'none', cursor: 'not-allowed' } : {}}
        >
          {!bookingEnabled
            ? 'ثبت نوبت غیرفعال است'
            : isNavigating
              ? <>در حال بارگذاری<span className="btn-spinner" /></>
              : 'دریافت / مشاهده نوبت'}
        </button>
      </div>

    </div>
  );
}
