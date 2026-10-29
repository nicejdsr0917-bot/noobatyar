/**
 * The app's icon set: Tabler, inlined as React components.
 *
 * Deliberately NOT an icon font from a CDN: this app is a PWA with an offline
 * fallback (public/offline.html) served to users in Iran, where
 * fonts.googleapis.com is slow or blocked outright. A font that fails to load
 * renders every glyph as a tofu box. These compile into the bundle, have no
 * network dependency, inherit `currentColor`, and scale with font size.
 *
 * Previously these were Material Symbols paths copied in by hand. Two problems
 * with that: the copies were solid/filled while CategoryArt.tsx — the other
 * place this app draws icons — had already moved to Tabler's outline set, so
 * the two never matched on a page showing both; and a hand-maintained path
 * list drifts in weight and metrics as entries get added one at a time. Tabler
 * draws one grid at one stroke width, which is what makes a set look designed
 * rather than assembled.
 *
 * The `<Icon name="…">` API is unchanged, so every call site keeps working.
 * Add new entries to ICONS below rather than hand-rolling an SVG at a call
 * site. For per-business-category marks use CategoryGlyph/CategoryArt instead
 * — that mapping lives in CategoryArt.tsx and is not duplicated here.
 */
import {
  IconAlertCircle,
  IconAlertTriangle,
  IconArrowRight,
  IconBan,
  IconBell,
  IconBuildingStore,
  IconCalendar,
  IconCalendarX,
  IconCamera,
  IconCheck,
  IconChevronDown,
  IconChevronLeft,
  IconChevronRight,
  IconChevronUp,
  IconCircleCheck,
  IconClock,
  IconCoins,
  IconCopy,
  IconCreditCard,
  IconCrop,
  IconDeviceMobile,
  IconInfoCircle,
  IconLanguage,
  IconLock,
  IconLogout,
  IconMap2,
  IconMapPin,
  IconMessage,
  IconMessages,
  IconMoon,
  IconPhone,
  IconPhoto,
  IconPlus,
  IconReceipt,
  IconRefresh,
  IconRoute,
  IconSend,
  IconSun,
  IconTool,
  IconUser,
  IconWallet,
  IconX,
} from '@tabler/icons-react';
import { createElement, type ComponentType } from 'react';

type TablerIcon = ComponentType<{
  size?: number | string;
  stroke?: number;
  color?: string;
  className?: string;
  style?: React.CSSProperties;
  title?: string;
  'aria-hidden'?: boolean;
}>;

export type IconName = keyof typeof ICONS;

const ICONS = {
  // ── Navigation ──
  // In an RTL layout "back" points right, which is why this is an arrow-right.
  back: IconArrowRight,
  close: IconX,
  chevronLeft: IconChevronLeft,
  chevronRight: IconChevronRight,
  // Vertical pair for expand/collapse — the horizontal ones mean
  // "navigate sideways" in an RTL layout and read wrong on a toggle.
  chevronDown: IconChevronDown,
  chevronUp: IconChevronUp,
  add: IconPlus,
  refresh: IconRefresh,

  // ── Account ──
  person: IconUser,
  logout: IconLogout,
  smartphone: IconDeviceMobile,
  lock: IconLock,

  // ── Time ──
  calendar: IconCalendar,
  schedule: IconClock,
  eventBusy: IconCalendarX,

  // ── Status ──
  check: IconCheck,
  checkCircle: IconCircleCheck,
  error: IconAlertCircle,
  info: IconInfoCircle,
  warning: IconAlertTriangle,
  block: IconBan,

  // ── Messaging ──
  notifications: IconBell,
  sms: IconMessage,
  call: IconPhone,
  forum: IconMessages,
  send: IconSend,

  // ── Media ──
  photoCamera: IconCamera,
  image: IconPhoto,
  crop: IconCrop,
  copy: IconCopy,
  language: IconLanguage,

  // ── Money ──
  creditCard: IconCreditCard,
  payments: IconWallet,
  // The deposit, kept apart from creditCard/payments: on the profile it sits
  // beside the payment-method card, and one glyph on both read as one fact.
  deposit: IconCoins,
  receipt: IconReceipt,

  // ── Places ──
  // The per-trade icons that used to sit here (stethoscope, scissors,
  // barbell, paw…) are gone with CATEGORY_ICON: they served that map alone,
  // and leaving a half-set behind invites reaching for them instead of
  // CategoryGlyph, which is what re-creates the mismatch just removed.
  storefront: IconBuildingStore,
  locationOn: IconMapPin,
  map: IconMap2,
  directions: IconRoute,
  build: IconTool,

  // ── Theme ──
  darkMode: IconMoon,
  lightMode: IconSun,
} satisfies Record<string, TablerIcon>;

/* The business-category → icon map that used to live here is gone.
   CategoryArt.tsx already owned a per-category Tabler mapping for the big
   decorative mark, and this second, cruder map disagreed with it: it sent
   fourteen medical specialities — dentistry, cardiology, ophthalmology,
   neurology and the rest — to one generic clipboard glyph, so a dentist's
   chip showed a clipboard while the artwork behind it on the same page
   showed a tooth. Small category icons now come from CategoryGlyph in
   CategoryArt.tsx, the single source for both sizes. */

interface IconProps {
  name: IconName;
  /** Pixel size; defaults to 1em so icons track the surrounding text. */
  size?: number | string;
  color?: string;
  /**
   * Line weight. Tabler's own default is 2, drawn for a 24px box; well below
   * that it reads heavy and well above it reads thin, so callers rendering at
   * the extremes can compensate.
   */
  stroke?: number;
  className?: string;
  style?: React.CSSProperties;
  /** Supply when the icon is the only content of a control. */
  title?: string;
}

/** Glyphs drawn for RTL that flip in the LTR UI (see .icon-dir in globals.css). */
const DIRECTIONAL: ReadonlySet<IconName> = new Set<IconName>(['back', 'chevronLeft', 'chevronRight']);

export default function Icon({
  name,
  size = '1em',
  color,
  stroke = 2,
  className,
  style,
  title,
}: IconProps) {
  // createElement rather than <Component />: assigning the looked-up component
  // to a capitalized local trips react-hooks' "component created during render"
  // rule, which cannot tell a table lookup from a freshly defined component.
  // Same reasoning as CategoryArt.tsx.
  return createElement(ICONS[name], {
    size,
    stroke,
    color: color ?? 'currentColor',
    className: DIRECTIONAL.has(name) ? [className, 'icon-dir'].filter(Boolean).join(' ') : className,
    // Tabler sets neither of these; the hand-rolled SVG this replaced did, and
    // the layouts here rely on both — icons sit in flex rows that would
    // otherwise squash them, and inline display adds descender space beneath.
    style: { flexShrink: 0, display: 'block', ...style },
    title,
    ...(title ? {} : { 'aria-hidden': true }),
  });
}
