// Minimal stand-in for the Django API so e2e needs no backend. It runs as a
// separate process because the business profile is fetched server-side by
// Next, which page.route() cannot intercept.
import http from 'node:http';

const PORT = Number(process.env.MOCK_API_PORT || 4187);
const TOKEN = 'e2e-token';

export const business = {
  id: 7,
  title: 'کلینیک آزمون',
  category: 'DENTIST',
  category_display: 'دندانپزشکی',
  unique_code: 'E2E1',
  bio: 'تیم دندانپزشکی آزمون',
  phone: '09120000000',
  extra_phones: [],
  address: 'تهران، خیابان آزمایش',
  latitude: null,
  longitude: null,
  logo: null,
  default_service_duration: 30,
  work_start_hour: 9,
  work_end_hour: 18,
  closed_weekdays: [],
  allow_anonymous_view: true,
  notice_enabled: false,
  notice_message: '',
  booking_enabled: true,
  deposit_mode: 'REQUIRED',
  deposit_amount: 100000,
  accepted_payment_methods: ['CARD'],
  services: ['جرم‌گیری', 'ترمیم'],
  allow_client_add_service: false,
};

const appointment = {
  id: 55,
  business,
  appointment_date: Date.now() + 3 * 24 * 3600 * 1000,
  status: 'PENDING',
  queue_position: 1,
  estimated_turn_time: null,
  reminder_sms_sent: false,
  reminder_push_sent: false,
  card_number: '6037991234567890',
  card_owner_name: 'صاحب کارت',
};

const ok = (data) => ({ status: 'success', data });

function route(req, url) {
  const p = url.pathname;
  const authed = req.headers.authorization === `Visitor ${TOKEN}`;
  if (p === '/api/client/business/') {
    return [200, ok({ results: [business], count: 1, total_pages: 1 })];
  }
  if (/\/available-slots\/$/.test(p)) {
    const date = url.searchParams.get('date');
    const slots = ['10:00', '10:30', '11:00'].map((time) => ({
      time,
      timestamp: new Date(`${date}T${time}:00`).getTime(),
      available: true,
      status: 'AVAILABLE',
    }));
    return [200, ok({ slots, duration_minutes: 30, date })];
  }
  if (p === '/api/client/auth/otp/send/') return [200, ok({ expires_in: 120 })];
  if (p === '/api/client/auth/otp/verify/') {
    return [200, ok({ is_registered: true, token: TOKEN, visitor: { id: 1, full_name: 'تست', phone_number: '09123456789' } })];
  }
  if (p === '/api/client/appointments/' && req.method === 'POST') {
    return authed ? [200, ok({ id: appointment.id, requires_payment: true })] : [401, { status: 'error', message: 'unauthorized' }];
  }
  if (p === '/api/client/appointments/') {
    return authed ? [200, ok({ results: [appointment] })] : [401, { status: 'error', message: 'unauthorized' }];
  }
  if (p.startsWith('/api/accounting/') && p.endsWith('payment-result')) {
    const okPay = url.searchParams.get('success') === '1';
    return [200, okPay ? 'پرداخت موفق' : 'پرداخت ناموفق', 'text/html'];
  }
  return [404, { status: 'error', message: 'not found' }];
}

http
  .createServer((req, res) => {
    const url = new URL(req.url, `http://127.0.0.1:${PORT}`);
    const cors = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': '*',
      'Access-Control-Allow-Methods': '*',
    };
    if (req.method === 'OPTIONS') {
      res.writeHead(204, cors).end();
      return;
    }
    const [status, body, type] = route(req, url);
    res.writeHead(status, { ...cors, 'Content-Type': type ? `${type}; charset=utf-8` : 'application/json' });
    res.end(typeof body === 'string' ? body : JSON.stringify(body));
  })
  .listen(PORT, '127.0.0.1', () => console.log(`mock api on ${PORT}`));
