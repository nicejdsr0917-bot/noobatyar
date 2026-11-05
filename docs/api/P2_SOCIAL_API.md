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

Public list row (first name and profile photo only; never any contact detail):
```json
{"id":7,"rating":5,"text":"عالی بود","created_at":"2026-10-30T09:00:00Z","visitor_name":"علی","visitor_phone":"","visitor_avatar":"https://api.noobatyar.ir/media/visitor_avatars/v7.jpg"}
```
`visitor_phone` is always `""` (kept only for older app builds; it used to carry a masked number). `visitor_avatar` is an absolute URL or `null`.
The owner list (`/api/reviews/owner/`) keeps the masked number the owner always saw.
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


## 7. Following business pages

Any signed-in customer may follow any number of businesses. The count is public. Only the owner sees who follows, and only first name and photo (never the phone):

- **GET /api/business/<id>/followers/?page=&page_size=** (owner JWT, 404 for others') — newest first, `{count, total_pages, current_page, next, previous, results: [{name, avatar, created_at}]}`.
- Push: on a new follow the owner receives `دنبال‌کننده جدید` (data `type=NEW_FOLLOWER`, `business_id`). One push per customer and business ever (outbox dedupe `follow_push:<business>:<visitor>`), so unfollow/follow again does not repeat it.
- Promo SMS audience `followers` (`/api/business/<id>/promo-sms/`): following counts as consent; `marketing_opt_out` and the owner's archive exclude. A follower who is not already in the owner's customer list is addressed by first name, shows with an empty `phone` in the campaign recipients, and is logged in SmsLog without the visitor link, so the owner never sees their number.

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/client/businesses/<business_id>/follow/` | Visitor | follow; idempotent; 404 for a business the public cannot see |
| DELETE | `/api/client/businesses/<business_id>/follow/` | Visitor | unfollow; idempotent |
| GET | `/api/client/businesses/following/` | Visitor | businesses the visitor follows, newest first, paginated (public business rows, each with `"is_following": true`) |

Follow/unfollow response: `{"id":3,"is_following":true,"followers_count":12}`.

Business payloads (public list/detail, client list/detail, owner `/api/business/…`) gain `followers_count` (int). The client detail adds `is_following` (bool) only when the request carries a visitor token; it is never in the shared cached copy.

## 8. Customer bio

- **GET /api/client/auth/me/** — `data` gains `bio` (string, may be empty).
- **PATCH /api/client/auth/me/** — `full_name` and `bio` are both optional now (at least one). `bio`: up to 150 characters after trimming (runs of spaces collapsed, at most one blank line); `""` clears it. Over 150 -> 400.
- The bio is shown only to the customer themself; no public endpoint returns it.

## 9. Client app version check

`POST /api/version/check/` without `app` normally means the owner app. A `version_code` in the client-only range (1789813665..1790244241, which the owner app never shipped) is treated as `CLIENT`, because client builds up to client-v1.2.0 send no `app`. Client builds from now on send `"app": "CLIENT"`.

## 10. Business stories (24 hours, photo only)

No admin approval. A story is served until `expires_at` (created + 24h); expired rows are deleted when the owner posts the next one. At most 10 active per business.

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/owner/stories/?business_id=` | Owner JWT | active stories, oldest first |
| POST | `/api/owner/stories/` | Owner JWT | multipart: `business_id`, `image` (any ratio; server scales to fit 1080×1920, JPEG), `caption` (≤100), `button` (`""` or `discount`), `discount_type` (`percent` 1..100 or `amount` toman ≥1000), `discount_value` |
| DELETE | `/api/owner/stories/<id>/` | Owner JWT | 404 for others' |
| GET | `/api/client/stories/active/` | none | `{business_ids: [...]}` — draw the purple ring around these |
| GET | `/api/client/businesses/<id>/stories/` | optional customer token | `{business_id, title, stories: [{id, image, caption, button, discount_type, discount_value, created_at, expires_at}], is_following?}` — `is_following` only with a customer token |

The discount is shown on the story only; it is not applied to bookings or deposits.

Push: the first story of a day enqueues one push to every follower (`send_to_visitor`, title = business title, body = caption, data `type=NEW_STORY`, `business_id`); dedupe `story_push:<business>:<YYYYMMDD>`.

