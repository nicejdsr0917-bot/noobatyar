# CI

`.github/workflows/ci.yml` runs on every push and pull request (all branches).

| Job | What |
|---|---|
| backend | Python 3.11, Postgres 16 service (`POSTGRES_*` env makes settings use it), `makemigrations --check --dry-run`, `manage.py test --parallel 4` (`DEBUG=True`, cache falls back to LocMem) |
| web | Node 20, `npm ci`, `tsc --noEmit` in `front_client` |
| kmp | JDK 17, Gradle cache; one variant per app: owner `:composeApp:testLocalSiteDebugUnitTest`, client `:composeApp:testDebugUnitTest` (client has no flavors) |

`android-release.yml` (tags `v*.*.*`) is unchanged and builds the owner app only.

## Two apps, one version table
`versions.AppVersion.app` is `OWNER` (default, all existing rows) or `CLIENT`.
`POST /api/version/check/` and `GET /api/version/changelog/` take an optional `app`
(`OWNER`/`CLIENT`); without it behaviour is identical to before. `force_update` is per app.
`version_code` is unique per (app, code), not globally.

## Client release tags (not built yet)
Convention: owner releases keep `vX.Y.Z`; client releases will use `client-vX.Y.Z`.
A future client workflow should trigger on `tags: ["client-v*"]`. Note that the
existing `v*.*.*` pattern does not match `client-v1.0.0`, so the two never collide.
