# P2 social API (additive)

All responses use the standard envelope `{"status","message","data"}` unless noted. Nothing existing was removed or renamed; only keys were added.
Visitor auth: header `Authorization: Visitor <token>`. Owner auth: `Authorization: Bearer <jwt>`.
Pagination envelope: `{count,total_pages,current_page,next,previous,results}` (next/previous are page numbers). Params `page`, `page_size` (max 100).

## 1. Appointment source

Values: `WEB`, `APP`, `OWNER`, `UNKNOWN` (rows created before this feature).

- **POST /api/client/appointments/** (Visitor) — new optional body field `source`: `"WEB"` (default) or `"APP"`. Any other value (including `OWNER`) -> 400. The mobile client app must send `"source":"APP"`.
- **POST /api/appointment/** (owner) — always stored as `OWNER`; response `data` gains `source`.
- Owner appointment serializers (`AppointmentSerializer`, query/list rows from `/api/appointment/query/`) gain `source`.
- **GET /api/appointment/stats/?business_id=&date_from=&date_to=** (owner) — `data` gains
  `"source_counts": {"WEB":3,"APP":12,"OWNER":5,"UNKNOWN":0}` for the requested period (all four keys always present).

## 2. Reviews

Rules: any signed-in customer (Visitor) may review a business once; published immediately; PUT allowed within 7 days of creation; rating 1-5; text up to 1000 chars (optional).

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/client/reviews/business/<business_id>/` | none | public list, newest first, hidden excluded, paginated |
| GET | `/api/client/reviews/business/<business_id>/summary/` | none | rating summary |
| GET | `/api/client/reviews/business/<business_id>/mine/` | Visitor | own review or 404 |
| POST | `/api/client/reviews/business/<business_id>/mine/` | Visitor | create; 201; 409 if already reviewed |
| PUT | `/api/client/reviews/business/<business_id>/mine/` | Visitor | edit within 7 days (403 after); 404 if none |
| DELETE | `/api/client/reviews/business/<business_id>/mine/` | Visitor | delete own |
| POST | `/api/client/reviews/<review_id>/report/` | none or Visitor | report a review |
| GET | `/api/reviews/owner/?business_id=` | Owner JWT | read-only list of the owner's own business (404 for others'), includes hidden |

Write requests (POST/PUT/DELETE on `mine`) are throttled per visitor (20/hour) -> 429.

Create/edit body: `{"rating": 5, "text": "عالی بود"}`.
Own review: `{"id":7,"business_id":3,"rating":5,"text":"...","created_at":"...","updated_at":"..."}`.

Public list row (name is first name only, phone masked):
```json
{"id":7,"rating":5,"text":"عالی بود","created_at":"2026-10-30T09:00:00Z","visitor_name":"علی","visitor_phone":"0912***4567"}
```
Owner list rows add `"is_hidden": false`; `data` also has `"summary"`.

Summary (also returned as the new `rating` key in public business **list rows and detail** — `/api/client/business(es)/` and `/<id>/`):
```json
{"avg":4.5,"count":2,"histogram":{"1":0,"2":0,"3":0,"4":1,"5":1}}
```
`avg` is 0 when `count` is 0. Hidden reviews are excluded.

Errors: 400 invalid rating/text, 401 no/invalid Visitor token, 404 business not public / review missing, 409 duplicate.

Push: the business owner receives an FCM push (title `نظر جدید`, data `type=NEW_REVIEW`, `business_id`, `review_id`).

### Report a review
`POST /api/client/reviews/<review_id>/report/` body `{"reason":"SPAM|INAPPROPRIATE|MISLEADING|IMPERSONATION|ILLEGAL|OTHER","detail":"..."}` -> 201. Creates a `ContentReport` (admin queue) with `review` set. Throttled per IP like the business report (5/hour).

## 3. Business report (existing, verified)
`POST /api/client/business/<id>/report/` (also `/api/client/businesses/<id>/report/`), optional Visitor token or `reporter_phone`; same reasons. Admin lists and closes reports under ContentReport (statuses NEW / REVIEWING / ACTIONED / DISMISSED). Covered by tests in `business/tests.py`.

## 4. Ranking
Business gets `rank_score` / `rank_updated_at` (not exposed in payloads). Public list without `lat/lng` is now ordered by `is_claimed desc, rank_score desc, created_at desc`; geo sorts are unchanged. Nightly command: `python manage.py compute_rank_scores` (cron line in `backend/DEPLOYMENT.md` section 8).

## 5. Public site services (AllowAny, GET only, cached 10 min, 60 req/min/IP, `Access-Control-Allow-Origin: *`)

- **GET /api/public/site/active-customers/** — approved, claimed businesses whose owner has a live paid subscription, max 50, best rank first.
- **GET /api/public/site/newest-businesses/** — newest approved claimed businesses, max 12.

Plain JSON (not enveloped):
```json
{"count":1,"results":[{"name":"سالن","category":"BEAUTY_SALON","category_display":"آرایشگاه","city":"تهران","slug":"AB12CD","logo":"https://host/media/business_logos/x.png","url":"https://noobatyar.ir/b/AB12CD"}]}
```
`slug` is the business `unique_code` (there is no separate slug field). `logo` is null when absent.

Add `?format=html` for a ready fragment (all values escaped):
`<ul class="nby-businesses"><li class="nby-business"><a href="..."><img ...><span class="nby-name">..</span><span class="nby-meta">category - city</span></a></li></ul>`
Setting `SITE_PUBLIC_URL` (default `https://noobatyar.ir`) controls the link base.

## 6. First free slot (client app list card)
Public business **list rows and detail** (`/api/client/business(es)/`, `/<id>/`) gain
`"first_free_slot": <epoch ms> | null` — the start of the earliest bookable slot in the next 7 local
days, using the same grid/occupancy rules as `available-slots`. `null` when the business is not
claimed, has booking off, or has nothing free. Added on top of the cached payload with its own 30 s
cache per business, dropped whenever one of its appointments changes. Old clients ignore it.
