# Backend architecture audit (2026-10-29, Opus, read-only)
Baseline: 733 tests OK on sqlite with --parallel 4 (6 skipped). Worktree has no .venv → use ../Noobatyar-automation/backend/.venv/bin/python.

## A. Tenant isolation / correctness (first)
- A1 HIGH (verified): owner takeover of client account. visitor/views.py:166-168 POST attach sets visitor.user=request.user when user_id NULL (every self-registered client); get_owned_visitor then allows PUT with phone_number change (VisitorSerializer allows it) → owner re-numbers, logs in via api/client/auth/otp/verify (client_auth_views.py:68), sees/cancels person's appointments everywhere. POST response also leaks global full_name for any phone. Fix: never assign user on attach of existing row; phone_number read-only in PUT when visitor has appointments at a business not owned by this owner, or VisitorDeviceToken/VisitorActivity (client usage). tests_cross_business.py:125 only covers rename.
- A2 MED: client booking skips rules (appointment/client_views.py:107-206, services/booking.py:83): no booking_enabled, is_claimed, past date, closed_weekdays, working hours, slot alignment; service_duration unbounded/0/negative (client_serializers.py:111). Add validate_client_booking guard in services.
- A3 MED: day boundaries: UTC-date in appointment_query_view._utc_day_start(:20), appointment_stats_view.py:73-90, service_breakdown_view.py:110-125; client_serializers.get_queue_position(:81-82) replace(hour=0) on UTC. range_visitors_view._parse_ms(:74) already Tehran. Check AppointmentApiService.kt:37-39 first.
- A4 MED: N+1 in client appointment list (ClientAppointmentSerializer nests ClientBusinessSerializer: insurances, get_active_subscription, can_book_appointment, queue-position COUNT per row); selectors.appointment_list_for_visitor lacks prefetch/attach_owner_state.
- A5 LOW-MED: core/segments.py:66,273-297 low-wallet segment reads Redis _wallet_key; source of truth CreditBalance → SQL subquery.
- A6 LOW-MED: api/views.py:159-201 password register without OTP → trial farming; User+Subscription not atomic; exists→create race 500.
- A7 LOW: business/views.py:66-67 int(page) 500; ClientBusinessDetailView (client_views.py:368) masks every exception as 404.

## B. Refactor packages (no model/response changes)
- R1 common/ (responses, pagination, jalali, phone, timeutils, errors.extract_error) + re-export shims; break cycles api↔accounting, business↔visitor, accounting→core (reports.py:45 project_tz); stop importing api.views._extract_error, usage._wallet_key cross-app.
- R2 (after R1) common/timeutils.py + common/params.py; epoch-ms parse ×7, local midnight ×2, _positive_int ×2, business_id parse ×5. Keep semantics; A3 separate commit.
- R3 (after R2) appointment/selectors_stats.py from stats views.
- R4 appointment/slots.py build_day_slots/day_bounds (available_slots_view.py:76-147 vs public_slots_view.py:98-150 differ: 23:59:59 vs exclusive, blocking_q(now) vs ()).
- R5 services/client_booking.py (client_book, client_submit_payment, client_cancel, client_deposit_returned), side effects on_commit; client_views.py:181-205,232-268,276-281,379-382,417-431; receipt saved before conditional update orphans files; fold A2. Tests patch appointment.client_views._fire_* (tests_client_concurrency.py:61-262).
- R6 (with/after R5) core/outbox/handlers.py:14,20,26 must not import views / private bale._send.
- R7 accounting/sms_billing.send_metered_sms; dedupe notifications._send_metered_sms(:185-235), send_appointment_reminders._send_one(:326-383), promo_sms.py:284-300; reminders logic → appointment/services/reminders.py.
- R8 integrations/myket.py (accounting/payment/myket.py:61-103); zibal_deposit.py:70-80 use integrations.zibal.request_payment; drop accounting/payment/zibal_client.py shim (4 importers).
- R9 business/geo.py + selectors.public_business_search (client_views.py:146-281).
- R10 (after R1) common/pagination.page_dict; business/views.py:85-93, client_views.py:266-279, visitor/views.py:394-402; fix A7.
- R11 business/public_policy.py (serializers.py:80-185) + shared mixin for Public/ClientBusinessSerializer (:554,613,647,674); fix A4 prefetch + query-count test.
- R12 visitor/services.py (attach/update_by_owner/archive from visitor/views.py:142-345); accounting.services.grant_trial atomic (api/views.py:186-192); fold A1, A6.
- R13 business/admin.py → package; Business.CATEGORY_GROUPS → business/categories.py (makemigrations --check empty); bale/views.py:106-353 → bale/handlers.py; Business.save _logo_changed extra SELECT (core.py:709) skip when update_fields excludes logo.
- R14 appointment/constants.py for status literals (no migration).
- R15 common/testing.py factories; tests_*.py → tests/ packages; remove root scripts/db bak; dead code. Last.

## Order
Security first: A1→R12, A2→R5, A6→R12. Parallel: R1, R8, R9, R13. Then parallel: R2→R3, R4, R7, R11, R10. R5→R6 sequential. R12 not with R10 (visitor/views.py). R14, R15 last. Every package: makemigrations --check + full suite; payment/race tests on Postgres :55432.

## Note: A3 intentionally deferred (R1-R4 refactor, branch w2/core)

The UTC-date day reading (`common.timeutils.utc_date_tehran_midnight`, formerly
`_utc_day_start` and the inline copies in stats / service-breakdown) was kept
exactly as is. The owner app now mirrors it (UTC calendar date of the epoch-ms,
then midnight Asia/Tehran), so "fixing" the server alone would shift every
range an old or new client asks for. `local_date_midnight` (formerly
`range_visitors_view._parse_ms`) keeps the Tehran-date reading. Both are named
for what they do and documented in `common/timeutils.py`; unifying them needs a
coordinated client + server change.
