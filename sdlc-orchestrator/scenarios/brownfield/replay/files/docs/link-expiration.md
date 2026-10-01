# Link expiration

Links can be created with a time to live. Once expired, the short URL stops redirecting.

## Creating an expiring link

```http
POST /api/v1/links
Content-Type: application/json

{"url": "https://example.com/flash-sale", "expiresInSeconds": 3600}
```

`expiresInSeconds` is optional, must be positive and at most 315360000 (10 years). Out-of-range
values are rejected with `400 urn:problem:invalid-request`. The response and
`GET /api/v1/links/{code}` include `expiresAt` (ISO-8601 UTC), or `null` for links that never expire.

## Redirect behaviour

| State | `GET /{code}` |
|---|---|
| Not expired, or no expiry | `302` to the target URL |
| `now >= expiresAt` | `410 Gone`, `urn:problem:link-expired` |
| Unknown code | `404`, `urn:problem:link-not-found` |

Expiry is enforced on every redirect, including links served from the redirect cache.
Metadata and stats remain available after expiry.

## Operations

- Schema: `V2__add_link_expiry.sql` adds a nullable `expires_at` column. Existing links keep `NULL` and never expire.
- Rollback: revert the application; the previous release ignores the extra column, so no down-migration is needed.
