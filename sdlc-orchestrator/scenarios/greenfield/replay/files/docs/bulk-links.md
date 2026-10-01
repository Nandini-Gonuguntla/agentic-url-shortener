# Bulk link creation

`POST /api/v1/links/bulk` creates up to 100 short links in one request. Items are processed
independently: a bad item is reported in its own result and never fails the rest of the batch.

## Request

```json
{
  "items": [
    {"url": "https://example.com/spring", "customAlias": "spring-sale"},
    {"url": "https://example.com/summer"}
  ]
}
```

| Rule | Response |
|---|---|
| `items` missing, empty or more than 100 entries | `400` problem details, nothing created |
| Item invalid (URL, alias, alias taken) | item `REJECTED` with `problemType` and `message` |

## Response (`200 OK`)

```json
{
  "requested": 2,
  "created": 1,
  "failed": 1,
  "results": [
    {"index": 0, "status": "CREATED", "link": {"code": "spring-sale", "shortUrl": "http://localhost:8080/spring-sale", "...": "..."}},
    {"index": 1, "status": "REJECTED", "problemType": "urn:problem:alias-taken", "message": "Alias 'spring-sale' is already in use"}
  ]
}
```

Problem types are the same as for `POST /api/v1/links`: `invalid-url`, `invalid-alias`, `alias-taken`.

## Operational notes

- Each item is inserted in its own transaction; a rejected item never rolls back created ones.
- The whole bulk request consumes one rate-limit token. The 100-item cap bounds the cost; weighting
  tokens by item count is a planned follow-up.
