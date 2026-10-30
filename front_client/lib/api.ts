import { clearVisitorToken } from '@/lib/auth';

import { getLocale, t } from '@/lib/i18n';
// API client for Noobatyar backend
const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

// Resolves a (usually relative) media path like "/media/business_logos/x.jpg"
// into an absolute URL against the API host. Returns null when there is no path.
export function mediaUrl(path: string | null | undefined): string | null {
  if (!path) return null;
  return path.startsWith('http') ? path : `${BASE_URL}${path}`;
}

export interface Business {
  id: number;
  title: string;
  category: string;
  /** Persian label as the server rendered it. Preferred over the local map so
   *  a category added after this build shipped still reads correctly. */
  category_display?: string;
  /** Hex colour derived from the logo server-side, already contrast-checked
   *  against white text. Empty/absent for a business with no logo. */
  theme_color?: string;
  unique_code: string;
  bio?: string | null;
  phone: string | null;
  /** Additional contact numbers beyond `phone`. Absent/empty on an older
   *  server, meaning "no extra numbers". */
  extra_phones?: string[];
  address: string | null;
  /** The owner's map pin. Null when none is set, and — like `address` —
   *  masked to null for a guest when allow_anonymous_view is off. Absent on
   *  an older server. */
  latitude?: number | null;
  longitude?: number | null;
  /** The owner has no active subscription. When the admin's contact policy
   *  is on, the server then sends phone/extra_phones/address/location as
   *  null (withheld, not hidden) — this flag is how a client tells that
   *  apart from the owner's own allow_anonymous_view switch, which nulls the
   *  same fields. Absent on an older server. */
  is_expired?: boolean;
  /** Whether the business has a map pin at all — never where it is. Lets a
   *  client tell a withheld pin (null coordinates, is_expired) from one that
   *  was never set. Absent on an older server. */
  has_location?: boolean;
  /** Rating summary; absent on an older server. */
  rating?: { avg: number; count: number; histogram: Record<string, number> };
  logo: string | null;
  default_service_duration: number;
  work_start_hour: number;
  work_end_hour: number;
  /** Minute component alongside work_start_hour/work_end_hour, e.g. 19:30 is
   *  work_end_hour=19, work_end_minute=30. Absent/undefined on an older
   *  server, meaning :00 — the same as an explicit 0. */
  work_start_minute?: number;
  work_end_minute?: number;
  /** Weekly recurring days off, as JS's `Date.getDay()` ints (Sun=0..Sat=6) —
   *  NOT the backend's own Mon=0..Sun=6 convention. Converted once at the API
   *  boundary (see `closedWeekdaysJs` in the booking page) so every caller in
   *  this app can compare directly against `Date.getDay()` without repeating
   *  the +1 mod 7 shift themselves. Absent/undefined on an older server,
   *  meaning "open every day". */
  closed_weekdays?: number[];
  allow_anonymous_view: boolean;
  /** Owner-posted advisory (closure, emergency, running late) is live.
   *  Independent of `booking_enabled`: a business can post a notice and still
   *  take bookings. */
  notice_enabled: boolean;
  /** The advisory text, max 300 chars. The backend sends an empty string
   *  whenever `notice_enabled` is false, so stale text never reaches us.
   *  Still nullable here to tolerate older payloads. */
  notice_message: string | null;
  booking_enabled: boolean;
  deposit_mode?: string;
  deposit_amount?: number;
  accepted_payment_methods?: string[];
  max_appointments_per_hour?: number | null;
  payment_method?: string;
  payment_link?: string;
  /** True when the business has a Zibal merchant, so checkout can redirect to a
   *  real gateway instead of the manual link + tracking-number flow. */
  online_gateway_enabled?: boolean;
  /** The services this business offers, defined by the owner in the app. Shown
   *  as chips at booking time so "what are you coming for?" is an answer the
   *  owner can plan a slot length around instead of free text. Optional here to
   *  tolerate older payloads. */
  services?: string[];
  /** Whether the client may add a service that isn't on `services`. Off unless
   *  the owner turned it on in their business settings — the backend rejects
   *  off-menu names either way, so this only decides whether we offer the box. */
  allow_client_add_service?: boolean;
}

export interface TimeSlot {
  time: string;
  timestamp: number;
  available: boolean;
  status: 'AVAILABLE' | 'BOOKED' | 'PAST';
}

export interface Appointment {
  id: number;
  business: Business;
  appointment_date: number;
  status: string;
  queue_position: number;
  estimated_turn_time: number | null;
  /** Whether this appointment's reminder was actually delivered on each
   *  channel — independent booleans, since a business can have one, both,
   *  or neither enabled (see backend/appointment/client_serializers.py). */
  reminder_sms_sent: boolean;
  reminder_push_sent: boolean;
  /** Card-to-card deposit destination. Only present (non-null) on the signed-in
   *  visitor's own LOCKED appointment — never on the public business payload. */
  card_number?: string | null;
  card_owner_name?: string | null;
}

/** Thrown when the API rejects the stored token (HTTP 401). */
export class UnauthorizedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'UnauthorizedError';
  }
}

/**
 * A failed API call that still carries its HTTP status. Callers need the status
 * to tell "this resource is not served to the public" (404) apart from "the
 * network or the server is having a bad day", which look identical once the
 * response has been flattened to a message string.
 */
export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * True when the API answered 404. The public surface returns 404 for a business
 * it does not serve, without saying why — so treat it as "not available", never
 * as a bug to report.
 */
export function isNotFound(err: unknown): boolean {
  return err instanceof ApiError && err.status === 404;
}

/** True when the outgoing request carried an Authorization header. */
function hasAuthHeader(headers: HeadersInit | undefined): boolean {
  if (!headers) return false;
  if (headers instanceof Headers) return headers.has('Authorization');
  if (Array.isArray(headers)) return headers.some(([k]) => k.toLowerCase() === 'authorization');
  return Object.keys(headers).some((k) => k.toLowerCase() === 'authorization');
}

/**
 * The stored token is gone or rejected. Clear it and bounce the user to the
 * login screen, preserving where they were so they land back after signing in.
 */
export function forceReLogin(): void {
  if (typeof window === 'undefined') return;
  clearVisitorToken();
  // Avoid a redirect loop if we're already on the login screen.
  if (window.location.pathname.startsWith('/auth/login')) return;
  const target = encodeURIComponent(window.location.pathname + window.location.search);
  window.location.href = `/auth/login?redirect=${target}`;
}

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  // Destructure headers separately so we can merge them with Content-Type
  // without `...options` overwriting the headers key entirely.
  const { headers: extraHeaders, ...restOptions } = options ?? {};
  // A FormData body needs the browser to set its own multipart boundary, so
  // the JSON Content-Type must not be forced onto it.
  const isForm = typeof FormData !== 'undefined' && restOptions.body instanceof FormData;
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: {
      ...(isForm ? {} : { 'Content-Type': 'application/json' }),
      ...extraHeaders,
    },
    ...restOptions,
  });

  // A rejected credential means the token expired or was revoked: drop it and
  // send the user back to login. (Public endpoints — e.g. the OTP flow — never
  // carry an Authorization header, so they won't trigger this.)
  //
  // 403 counts too, not just 401. DRF returns 403 for an authentication failure
  // unless an authenticator advertises a WWW-Authenticate challenge, and every
  // endpoint on this surface is scoped to the caller's own visitor record — so
  // there is no "signed in but forbidden" state a visitor can legitimately hit.
  // Treating 403 as recoverable is what keeps a stale token from becoming a dead
  // end with no way to sign out.
  if (res.status === 401 || (res.status === 403 && hasAuthHeader(extraHeaders))) {
    if (hasAuthHeader(extraHeaders)) {
      forceReLogin();
    } else if (typeof window !== 'undefined') {
      clearVisitorToken();
    }
    throw new UnauthorizedError(t('yourSessionHasExpiredPlease2'));
  }

  // An error response is not guaranteed to be JSON — a 404 from the proxy or a
  // 500 from the WSGI layer arrives as HTML. Parsing that used to throw a raw
  // SyntaxError, which then surfaced to the user as an English parser message.
  let json: { status?: string; message?: string; data?: unknown } | null = null;
  try {
    json = await res.json();
  } catch {
    json = null;
  }

  if (!res.ok || json?.status === 'error') {
    // Prefer the server's own Persian message — for a refused booking that
    // message is the whole point ("این کسب‌وکار در حال حاضر نوبت نمی‌پذیرد");
    // replacing it with a generic string would leave the user with no idea
    // what happened.
    throw new ApiError(json?.message || t('couldntReachServer2'), res.status);
  }
  return json?.data as T;
}

// ── Auth: OTP Flow ──────────────────────────────────────────────────────────
// These hit the client-only auth surface (/api/client/auth/...), which only
// ever creates/returns a Visitor — never a full owner-app `User` account. See
// backend/visitor/client_auth_views.py.
export async function sendOtp(phone: string): Promise<{ expires_in: number }> {
  return apiFetch('/api/client/auth/otp/send/', {
    method: 'POST',
    body: JSON.stringify({ phone }),
  });
}

export interface Visitor {
  id: number;
  full_name: string;
  phone_number: string;
}

export interface OtpVerifyResult {
  is_registered: boolean;
  // If registered:
  visitor?: Visitor;
  token?: string;
  // If new visitor:
  register_token?: string;
  expires_in?: number;
}

export async function verifyOtp(phone: string, code: string): Promise<OtpVerifyResult> {
  return apiFetch<OtpVerifyResult>('/api/client/auth/otp/verify/', {
    method: 'POST',
    body: JSON.stringify({ phone, code }),
  });
}

export async function completeRegister(
  phone: string,
  register_token: string,
  name: string
): Promise<{ visitor: Visitor; token: string }> {
  return apiFetch('/api/client/auth/register/', {
    method: 'POST',
    body: JSON.stringify({ phone, register_token, name }),
  });
}

// Business
export async function getBusinessByCode(code: string): Promise<Business> {
  const data = await apiFetch<{
    results: Business[];
    count: number;
  }>(`/api/client/business/?search=${encodeURIComponent(code)}`);
  // Matched case-insensitively, like the server-side `unique_code__iexact`
  // lookup that produced these results: codes can be set by hand in the admin
  // and may contain lowercase letters, so a URL typed in another casing must
  // still land on the business the API already found.
  const biz = data.results?.find(
    (b) => b.unique_code.toLowerCase() === code.toLowerCase()
  );
  // The public listing only contains businesses the API is willing to serve, so
  // "absent from the results" is the same condition as a 404 on the detail
  // endpoint — report it as one, so callers get the not-available screen rather
  // than a load-failure screen.
  if (!biz) throw new ApiError(t('businessNotFound'), 404);
  return biz;
}

export async function getBusinessById(id: number): Promise<Business> {
  return apiFetch<Business>(`/api/client/business/${id}/`);
}

/**
 * Every business the public listing is willing to serve, walked page by page.
 *
 * Only sitemap.xml needs this. The API caps page_size at 100, so a crawl of the
 * whole directory is a handful of requests, and `maxPages` stops it from
 * turning into an unbounded loop if the server ever reports a total_pages it
 * cannot actually deliver. Failures are swallowed per page rather than thrown:
 * a sitemap that is short by one page is a far better outcome for a crawler
 * than a 500, which Google treats as "this site has no sitemap".
 */
export async function listAllPublicBusinesses(maxPages = 50): Promise<Business[]> {
  const out: Business[] = [];
  let page = 1;
  let totalPages = 1;

  while (page <= totalPages && page <= maxPages) {
    try {
      const data = await apiFetch<{
        results: Business[];
        total_pages: number;
      }>(`/api/client/business/?page=${page}&page_size=100`);
      out.push(...(data.results ?? []));
      totalPages = data.total_pages ?? 1;
    } catch {
      break;
    }
    page += 1;
  }
  return out;
}

export async function listBusinesses(search = '', category = '', page = 1) {
  const params = new URLSearchParams({ page: String(page), page_size: '10' });
  if (search) params.set('search', search);
  if (category) params.set('category', category);
  return apiFetch<{ results: Business[]; count: number; total_pages: number }>(
    `/api/client/business/?${params}`
  );
}

// Available Slots
export async function getAvailableSlots(businessId: number, date: string): Promise<{
  slots: TimeSlot[];
  duration_minutes: number;
  date: string;
  /** True when this date falls on one of the business's weekly days off —
   *  distinct from an empty `slots` array meaning "fully booked", so the UI
   *  can tell a customer *why* nothing is bookable. Absent on an older
   *  backend, in which case the empty-slots case is the only signal. */
  closed?: boolean;
}> {
  return apiFetch(`/api/client/appointments/${businessId}/available-slots/?date=${date}`);
}

// Appointments
export async function bookAppointment(
  businessId: number,
  appointmentDate: number,
  serviceDuration?: number,
  description?: string,
  token?: string,
  selectedServices?: string[]
) {
  return apiFetch<{ id: number; requires_payment?: boolean }>('/api/client/appointments/', {
    method: 'POST',
    headers: token ? { Authorization: `Visitor ${token}` } : {},
    body: JSON.stringify({
      business_id: businessId,
      appointment_date: appointmentDate,
      // Explicit so the owner's per-channel stats never depend on the server default.
      source: 'WEB',
      ...(serviceDuration && { service_duration: serviceDuration }),
      ...(description && { description }),
      ...(selectedServices?.length && { selected_services: selectedServices }),
    }),
  });
}

/** One row of the visitor's own activity log. */
export interface ActivityEntry {
  id: number;
  action: string;
  action_label: string;
  actor_type: 'VISITOR' | 'OWNER' | 'SYSTEM';
  actor_label: string;
  business: number | null;
  business_title: string | null;
  appointment: number | null;
  detail: Record<string, unknown>;
  created_at: string;
}

/** The signed-in visitor's own identity. */
export async function getMe(token: string): Promise<Visitor> {
  return apiFetch<Visitor>('/api/client/auth/me/', {
    headers: { Authorization: `Visitor ${token}` },
  });
}

/** Register (or refresh) this browser's FCM token against the signed-in visitor. */
export async function registerDeviceToken(deviceToken: string, visitorToken: string): Promise<void> {
  await apiFetch('/api/client/auth/devices/register/', {
    method: 'POST',
    headers: { Authorization: `Visitor ${visitorToken}` },
    body: JSON.stringify({ token: deviceToken }),
  });
}

export async function getMyActivity(token: string): Promise<ActivityEntry[]> {
  // Paginated like the appointments endpoint; tolerate a bare array too.
  const data = await apiFetch<ActivityEntry[] | { results: ActivityEntry[] }>(
    '/api/client/activity/',
    { headers: { Authorization: `Visitor ${token}` } }
  );
  return Array.isArray(data) ? data : data.results ?? [];
}

export async function getMyAppointments(token: string): Promise<Appointment[]> {
  // The endpoint is paginated ({ results: [...] }); tolerate a bare array too.
  const data = await apiFetch<Appointment[] | { results: Appointment[] }>(
    '/api/client/appointments/',
    { headers: { Authorization: `Visitor ${token}` } }
  );
  return Array.isArray(data) ? data : data.results ?? [];
}

export async function getAppointment(id: number, token: string): Promise<Appointment> {
  const all = await getMyAppointments(token);
  const apt = all.find((a) => a.id === id);
  if (!apt) throw new Error(t('appointmentNotFound'));
  return apt;
}

export type PaymentMethod = 'CARD' | 'ONLINE' | 'CASH';

export async function payAppointment(
  id: number,
  paymentReference: string,
  token: string,
  paymentMethod: PaymentMethod = 'CARD',
) {
  return apiFetch<{ id: number }>(`/api/client/appointments/${id}/pay/`, {
    method: 'POST',
    headers: { Authorization: `Visitor ${token}` },
    body: JSON.stringify({ payment_reference: paymentReference, method: paymentMethod }),
  });
}

/**
 * Opens a Zibal payment for the deposit and returns the gateway URL to send the
 * client to. Only works when the business configured a Zibal merchant id;
 * businesses that only set a static payment_link keep the manual flow instead.
 */
export async function startOnlineDeposit(id: number, token: string) {
  return apiFetch<{ payment_url: string; track_id: string }>(
    `/api/client/appointments/${id}/pay/online/`,
    {
      method: 'POST',
      headers: { Authorization: `Visitor ${token}` },
    },
  );
}

export async function cancelAppointment(id: number, token: string) {
  return apiFetch<{ id: number }>(`/api/client/appointments/${id}/cancel/`, {
    method: 'POST',
    headers: { Authorization: `Visitor ${token}` },
  });
}

export async function payAppointmentWithReceipt(id: number, formData: FormData, token: string) {
  return apiFetch(`/api/client/appointments/${id}/pay/`, {
    method: 'POST',
    headers: { Authorization: `Visitor ${token}` },
    body: formData,
  });
}

export type PaymentResultKind = 'subscription' | 'addon';

/**
 * Outcome of a Zibal gateway return. The backend answers with an HTML page,
 * not JSON, so this reads the text and looks for its success marker.
 */
export async function getPaymentResult(
  kind: PaymentResultKind,
  trackId: string,
  success: string | null,
): Promise<{ success: boolean; packName: string | null }> {
  const path = kind === 'addon' ? '/api/accounting/addons/payment-result' : '/api/accounting/payment-result';
  const res = await fetch(
    `${BASE_URL}${path}?trackId=${trackId}&success=${success}`,
    { method: 'GET' },
  );
  const text = await res.text();
  const isSuccess = text.includes(t('successful')) && !text.includes(t('failed'));
  const packMatch = text.match(/«([^»]+)»/);
  return { success: isSuccess, packName: packMatch ? packMatch[1] : null };
}

// Helpers
export function businessUrl(code: string) {
  return `/b/Noobatyar-${code}`;
}

export function extractCode(slug: string): string {
  return slug.replace(/^Noobatyar-/i, '');
}

/**
 * Persian label for a business category.
 *
 * Mirrors `Business.CATEGORY_CHOICES` in backend/business/models.py, which is
 * the source of truth. An unknown key falls through to the raw value rather
 * than "سایر": a category this build predates should still read as *something*
 * specific, and showing the code makes the drift obvious instead of quietly
 * mislabelling every new business as "other".
 */
export function businessCategoryLabel(
  biz: { category: string; category_display?: string },
): string {
  // category_display comes from the server in Persian; use our own label in English.
  if (getLocale() === 'en') return categoryLabel(biz.category);
  return biz.category_display?.trim() || categoryLabel(biz.category);
}

export function categoryLabel(category: string): string {
  const map: Record<string, string> = {
    DOCTOR: t('doctorsClinics'),
    GENERAL_PRACTITIONER: t('generalPractitioner'),
    INTERNAL_MEDICINE: t('internalMedicine'),
    CARDIOLOGY: t('cardiology'),
    DERMATOLOGY: t('dermatologyHairBeautyDoctor'),
    ORTHOPEDICS: t('orthopedics'),
    PEDIATRICS: t('pediatrics'),
    GYNECOLOGY: t('obstetricsGynecology'),
    ENT: t('ent'),
    OPHTHALMOLOGY: t('ophthalmology'),
    NEUROLOGY: t('neurology'),
    UROLOGY: t('urology'),
    ENDOCRINOLOGY: t('endocrinologyDiabetes'),
    GASTROENTEROLOGY: t('gastroenterologyHepatology'),
    ONCOLOGY: t('oncology'),
    SURGERY: t('surgery'),
    PSYCHIATRY: t('psychiatry'),
    INFERTILITY: t('fertilityIvf'),
    DENTIST: t('dentistry'),
    ORTHODONTICS: t('orthodontics'),
    DENTAL_IMPLANT: t('implantsOralSurgery'),
    PEDIATRIC_DENTISTRY: t('pediatricDentistry'),
    PSYCHOLOGY: t('psychologyCounseling'),
    PHYSIOTHERAPY: t('physiotherapyRehabilitation'),
    NUTRITION: t('nutritionDietTherapy'),
    LABORATORY: t('laboratory'),
    RADIOLOGY: t('radiologyUltrasound'),
    OPTOMETRY: t('optometryEyewear'),
    SPEECH_THERAPY: t('speechTherapy'),
    OCCUPATIONAL_THERAPY: t('occupationalTherapy'),
    MIDWIFERY: t('midwiferyWomensHealth'),
    NURSING: t('nursingHomeCare'),
    PHARMACY: t('pharmacy'),
    VETERINARY: t('veterinary'),
    BEAUTY_SALON: t('hairBeautySalons'),
    WOMENS_SALON: t('womensBeautySalon'),
    BARBERSHOP: t('barbershop'),
    HAIR_SALON: t('hairSalonColorKeratin'),
    HAIR_TRANSPLANT: t('hairEyebrowTransplant'),
    MAKEUP: t('makeupArtist'),
    BRIDAL: t('bridalEventServices'),
    NAIL_SALON: t('nailSalon'),
    EYEBROW_LASH: t('browsLashesMicroblading'),
    SKIN_LASER: t('skinLaser'),
    TATTOO: t('tattooMicropigmentation'),
    MASSAGE_SPA: t('massageSpa'),
    SLIMMING: t('weightLossFitness'),
    CONSULTANT: t('consulting'),
    LAWYER: t('legalServicesAdvice'),
    ACCOUNTING: t('accountingTax'),
    REAL_ESTATE: t('realEstate'),
    INSURANCE: t('insurance'),
    IMMIGRATION: t('immigrationVisas'),
    TUTORING: t('tutoringPrivateLessons'),
    LANGUAGE_SCHOOL: t('languageSchool'),
    MUSIC_SCHOOL: t('musicLessons'),
    GYM: t('gymPersonalTrainer'),
    YOGA_PILATES: t('yogaPilates'),
    DRIVING_SCHOOL: t('drivingSchool'),
    AUTO_SERVICE: t('carRepairServices'),
    CAR_WASH: t('carWashDetailing'),
    HOME_SERVICE: t('homeServicesRepairs'),
    DEVICE_REPAIR: t('phoneElectronicsRepair'),
    TAILORING: t('tailoringFashionDesign'),
    PHOTOGRAPHY: t('photographyStudio'),
    EVENT_SERVICES: t('eventVenuesServices'),
    PET_GROOMING: t('petGroomingCare'),
    OTHER: t('other'),
  };
  return map[category] || category;
}



// ── Showcase (portfolio / certificates) ─────────────────────────────────────
export interface PortfolioItem {
  id: number;
  image: string;
  thumbnail: string;
  caption: string;
  order: number;
}
export interface CertificateItem {
  id: number;
  image: string;
  thumbnail: string;
  title: string;
  issuer: string;
  year: number | null;
  order: number;
}
export interface Showcase {
  portfolio: PortfolioItem[];
  certificates: CertificateItem[];
}

export async function getShowcase(businessId: number): Promise<Showcase> {
  return apiFetch<Showcase>(`/api/client/businesses/${businessId}/showcase/`);
}

// ── Reviews ─────────────────────────────────────────────────────────────────
export interface RatingSummary {
  avg: number;
  count: number;
  histogram: Record<string, number>;
}
export interface Review {
  id: number;
  rating: number;
  text: string;
  created_at: string;
  visitor_name: string;
  visitor_phone: string;
}
export interface MyReview {
  id: number;
  business_id: number;
  rating: number;
  text: string;
  created_at: string;
  updated_at: string;
}
export interface ReviewPage {
  count: number;
  total_pages: number;
  current_page: number;
  next: number | null;
  previous: number | null;
  results: Review[];
}

export async function getReviewSummary(businessId: number): Promise<RatingSummary> {
  return apiFetch<RatingSummary>(`/api/client/reviews/business/${businessId}/summary/`);
}

export async function getReviews(businessId: number, page = 1, pageSize = 10): Promise<ReviewPage> {
  return apiFetch<ReviewPage>(
    `/api/client/reviews/business/${businessId}/?page=${page}&page_size=${pageSize}`
  );
}

const visitorAuth = (token: string) => ({ Authorization: `Visitor ${token}` });

/** The caller's own review, or null when they have not written one (404). */
export async function getMyReview(businessId: number, token: string): Promise<MyReview | null> {
  try {
    return await apiFetch<MyReview>(`/api/client/reviews/business/${businessId}/mine/`, {
      headers: visitorAuth(token),
    });
  } catch (err) {
    if (isNotFound(err)) return null;
    throw err;
  }
}

export async function saveMyReview(
  businessId: number,
  review: { rating: number; text: string },
  token: string,
  isEdit: boolean
): Promise<MyReview> {
  return apiFetch<MyReview>(`/api/client/reviews/business/${businessId}/mine/`, {
    method: isEdit ? 'PUT' : 'POST',
    headers: visitorAuth(token),
    body: JSON.stringify(review),
  });
}

export async function deleteMyReview(businessId: number, token: string): Promise<void> {
  await apiFetch(`/api/client/reviews/business/${businessId}/mine/`, {
    method: 'DELETE',
    headers: visitorAuth(token),
  });
}

export type ReportReason =
  | 'SPAM'
  | 'INAPPROPRIATE'
  | 'MISLEADING'
  | 'IMPERSONATION'
  | 'ILLEGAL'
  | 'OTHER';

export async function reportReview(
  reviewId: number,
  reason: ReportReason,
  detail: string,
  token?: string
): Promise<void> {
  await apiFetch(`/api/client/reviews/${reviewId}/report/`, {
    method: 'POST',
    headers: token ? visitorAuth(token) : {},
    body: JSON.stringify({ reason, detail }),
  });
}
