# Changelog

## Unreleased

- Added destination safety checks on link creation: blocked domains (with subdomains), IP-address hosts and embedded credentials are refused with `422 urn:problem:unsafe-destination` (see `docs/destination-safety.md`).
- Configuration: new `shortener.safety.blocked-domains` and `shortener.safety.block-ip-literals`.

## 1.0.0

- Initial release: create, resolve and delete short links; click analytics (totals, unique visitors, per-day, top referrers); per-client rate limiting on link creation; LRU cache on the redirect path.
