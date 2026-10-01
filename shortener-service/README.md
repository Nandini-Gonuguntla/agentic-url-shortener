# shortener-service

URL shortener with click analytics and reliability features. Java 21, Spring Boot 3.5, JPA + Flyway, H2.

## Run

```bash
../mvnw spring-boot:run
```

The service listens on `http://localhost:8080`.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/links` | Create a link. Body: `{"url": "...", "customAlias": "optional"}`. Returns `201` with `Location`. |
| `GET` | `/api/v1/links/{code}` | Link metadata. |
| `DELETE` | `/api/v1/links/{code}` | Delete a link and its analytics. |
| `GET` | `/api/v1/links/{code}/stats?days=30` | Total clicks, unique visitors, clicks per day, top referrers. |
| `GET` | `/{code}` | `302` redirect to the target URL. |

Errors use RFC 9457 problem details (`application/problem+json`) with a `type` of `urn:problem:<kind>`.

## Reliability and privacy

- Codes are 7 random Base62 characters from a CSPRNG (not enumerable); collisions are retried.
- Click counters are incremented atomically in SQL, so concurrent redirects never lose counts.
- Analytics are recorded off the redirect path on a bounded queue; when the queue is full events are dropped and counted (`shortener.clicks.dropped`) instead of slowing redirects.
- Redirects are served from a bounded LRU cache with TTL.
- Link creation is rate limited per client address (token bucket, `429` with `Retry-After`).
- Visitors are counted with a salted SHA-256 hash; raw IP addresses are never stored. Only the referrer host is kept.

## Configuration

See `src/main/resources/application.yml` (`shortener.*`). Override `shortener.analytics.visitor-salt` in every real environment.
