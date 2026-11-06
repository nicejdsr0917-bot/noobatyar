# P2 Showcase API — portfolio, certificates, banners, logo rule

All responses use the standard envelope:
`{"status": "success"|"error", "code": <http>, "message": "...", "data": ...}`.
Owner endpoints need `Authorization: Bearer <JWT>` and only work on the caller's own businesses
(someone else's business or item gives **404**, no token gives **401**).

## 1. Portfolio (owner) — `/api/owner/showcase/portfolio/`

Image rules (server-enforced, JPEG output): target **1080x1080**; ratio 1:1 (±10%); shortest side
**>= 600 px** else 400; larger images are downscaled; max upload 8 MB; a 360px-wide thumbnail is generated.
Max **30** items per business (400 when exceeded). Every new item / changed image / changed caption
goes to **PENDING** and appears publicly only after admin approval (Bale card or admin queue).
Changing only `order` does not re-queue.

| Method | Path | Body | Notes |
|---|---|---|---|
| GET | `/api/owner/showcase/portfolio/?business_id=ID` | — | all own items (every status), ordered |
| POST | same | multipart: `business_id`, `image` (file, required), `caption` (<=300, optional), `order` (optional, default = last+1) | 201 |
| PATCH (or PUT) | `/api/owner/showcase/portfolio/<id>/` | multipart/JSON: any of `caption`, `order`, `image` | 200 |
| DELETE | `/api/owner/showcase/portfolio/<id>/` | — | 200 |
| POST | `/api/owner/showcase/portfolio/reorder/` | JSON `{"business_id": 1, "ids": [3,1,2]}` — must list **every** item exactly once | returns the new list |

Item:
```json
{"id": 7, "image": "https://.../media/portfolio/ab.jpg", "thumbnail": "https://.../portfolio/thumbs/ab_t.jpg",
 "caption": "رنگ و مش", "order": 0, "status": "PENDING", "reject_reason": "",
 "created_at": "...", "updated_at": "..."}
```
`status`: `PENDING` | `APPROVED` | `REJECTED` (`reject_reason` set when rejected; show it to the owner).

## 2. Certificates (owner) — `/api/owner/showcase/certificates/`

Same endpoints/behaviour as portfolio (`.../certificates/`, `.../certificates/<id>/`, `.../certificates/reorder/`).
Image: target **1080x1350** portrait (ratio 4:5 ±10%), width >= 600, height >= 750.
Fields: `title` (required on create, <=150), `issuer` (<=150), `year` (1300–2100, nullable), `order`, `image`.
Changing `title/issuer/year/image` re-queues review.
```json
{"id": 3, "image": "...", "thumbnail": "...", "title": "دیپلم", "issuer": "نظام پزشکی", "year": 1402,
 "order": 0, "status": "APPROVED", "reject_reason": "", "created_at": "...", "updated_at": "..."}
```

## 3. Public showcase (client/web) — `GET /api/client/businesses/<id>/showcase/`

No auth. 404 if the business is not publicly visible. Only **APPROVED** items, ordered by `order`.
Cached with the public business stamp (invalidated on any showcase/business change).
Show the Noobatyar logo watermark at display time (client side).
```json
{"status":"success","code":200,"message":null,"data":{
  "portfolio":[{"id":7,"image":"...","thumbnail":"...","caption":"...","order":0}],
  "certificates":[{"id":3,"image":"...","thumbnail":"...","title":"...","issuer":"...","year":1402,"order":0}]}}
```

## 4. Banners (owner app) — `GET /api/owner/banners/?business_id=ID`

Auth required; the business must belong to the caller (404 otherwise, 400 if `business_id` missing/invalid).
Returns the **active** banners (is_active, within starts/ends) for that business's category — both
category-specific banners and its category-group banners — ordered by `order`. All owners get them
(no plan filter). Every banner opens the single shared `link_url` (may be `""`).
Banner images are exactly 1200x400 (admin rejects any other size).
```json
{"status":"success","code":200,"message":null,"data":{
  "banners":[{"id":1,"image":"https://.../media/banners/x.png","order":0}],
  "link_url":"https://noobatyar.ir/plans"}}
```

## 5. Logo required on create (owner app, new version)

`POST /api/business/` (multipart) — add form field **`logo_required=1`** (or header `X-Logo-Required: 1`)
to enforce: without a `logo` file the response is **400** `"لوگوی کسب‌وکار الزامی است"`.
Old app versions send neither, so they keep working. Updates (`PUT /api/business/<id>/`) are never enforced.

## Admin / Bale (staff only, for reference)
New items appear in admin → «نمونه‌کار و مدارک» and as Bale cards (photo + ✅ تایید / ❌ رد with canned reasons).
Marketing leads (admin → «بازاریابی») have no public API.
