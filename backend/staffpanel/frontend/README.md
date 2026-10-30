# Nava build sources (staff panel)

`main.css` and `tailwind.config.js` come from the «نوا» admin template
(Tailwind 3.4.19). The built file `assets/nava/css/app.css` is committed, so
nothing here runs at deploy or request time — no Node, no CDN.

Rebuild after changing classes in `templates/staffpanel/` or `assets/nava/js/`:

```bash
cd backend
npx tailwindcss@3.4.19 -c staffpanel/frontend/tailwind.config.js \
  -i staffpanel/frontend/main.css -o assets/nava/css/app.css --minify
```

Vendored JS (`assets/nava/js/`) is Nava's own, unchanged, except:
- `components/layout.js` and `data/mock.js` are not vendored: the shell (sidebar,
  header, palette markup) is rendered by `templates/staffpanel/base.html`.
- `components/command-palette.js` also queries `/…/panel/search/` (businesses,
  customers, phone numbers) — marked `Noobatyar:` in the file.
- `panel.js` is ours: page wiring (toasts from Django messages, server tables,
  reject-reason modal, charts from `json_script` data).
