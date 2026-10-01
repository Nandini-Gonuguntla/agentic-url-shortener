# Changelog

## Unreleased

- Added `POST /api/v1/links/bulk` to create up to 100 links in one request with per-item results (see `docs/bulk-links.md`).

## 1.0.0

- Initial release: create, resolve and delete short links; click analytics (totals, unique visitors, per-day, top referrers); per-client rate limiting on link creation; LRU cache on the redirect path.
