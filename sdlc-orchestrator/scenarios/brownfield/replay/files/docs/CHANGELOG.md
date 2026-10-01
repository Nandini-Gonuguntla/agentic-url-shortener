# Changelog

## Unreleased

- Added optional link expiration: `expiresInSeconds` on create, `expiresAt` in metadata, and `410 Gone` (`urn:problem:link-expired`) for expired links (see `docs/link-expiration.md`).
- Schema: `V2__add_link_expiry.sql` adds nullable `link.expires_at`.

## 1.0.0

- Initial release: create, resolve and delete short links; click analytics (totals, unique visitors, per-day, top referrers); per-client rate limiting on link creation; LRU cache on the redirect path.
