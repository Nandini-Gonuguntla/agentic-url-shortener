# Destination safety checks

Short links hide where they lead, so the service refuses destinations commonly used to deceive
the people who click them. Checks run when a link is created; redirects are unaffected.

| Rule | Example refused | Setting |
|---|---|---|
| Blocked domain or any subdomain | `https://login.phishing.test/` | `shortener.safety.blocked-domains` |
| IP address host (IPv4, IPv6, decimal, hex) | `http://203.0.113.7/`, `http://3232235521/` | `shortener.safety.block-ip-literals` |
| Embedded credentials | `https://bank.example@evil.test/` | always on |

Matching uses label boundaries: blocking `phishing.test` blocks `login.phishing.test`, not `notphishing.test`.

## API

Refused destinations return `422 Unprocessable Entity`:

```json
{"type": "urn:problem:unsafe-destination", "status": 422, "detail": "The destination domain is blocked by policy"}
```

Messages name the rule, never the submitted URL, because it may contain credentials.
Rejected URLs are not logged for the same reason.

## Operations

- Update the blocklist through configuration (`SHORTENER_SAFETY_BLOCKEDDOMAINS_0=...` or `application.yml`); no code change needed.
- Existing links are not re-validated.
- Disable quickly by emptying the blocklist and setting `block-ip-literals: false`.
