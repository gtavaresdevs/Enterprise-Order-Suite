---
name: spring-security-changes
description: Use before changing anything under security/ or auth/, any SecurityConfig, JwtAuthenticationFilter, rate limiter, or any @PreAuthorize expression - requires asking the user detailed questions first and forbids authorization logic inside method bodies.
---

# Changing security in this backend

Security changes here are not ordinary refactors. A permission that silently widens looks
exactly like one that works. This skill holds the map of what exists, the rules the code is
supposed to follow, and the questions you must ask before touching any of it.

## Ask first, then implement

Before changing authentication, authorization, token handling or rate limiting, **ask the
user and wait for answers.** Do not infer intent from the surrounding code — the surrounding
code is what produced the current defects. At minimum, cover:

- Who is allowed to call this, by role — and who must be **denied**?
- Is the resource owned by someone? If so, what identifies the owner?
- Does this change an **existing** permission? Is it a widening, a narrowing, or a defect
  fix — and has the frontend been told?
- What should failure look like: 401, 403, or 404 to avoid confirming the resource exists?
- Does it need rate limiting?

Ask in the language the user is writing in. Implement in English.

## The map

- **Filter chain** (`security/config/SecurityConfig.java:164-181`): `RequestIdFilter` →
  `AuthRateLimitFilter` → `JwtAuthenticationFilter`, all registered before
  `UsernamePasswordAuthenticationFilter`. Sessions are `STATELESS`. CSRF is disabled because
  authentication is a bearer header — that reasoning stops holding the moment a cookie
  appears (see Target below).
- **Public paths** (`SecurityConfig.java:169`): `/error`, `/auth/**`, `/actuator/health/**`,
  Swagger (`/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-ui.html`), `/webjars/**`,
  `/logo.png`. `/actuator/info`, `/actuator/metrics/**`, `/admin/users/**` and
  `/admin/identity-audit/**` require `SUPER_ADMIN`; `/admin/**` and `/roles` require `ADMIN`.
  Everything else is `authenticated()`.
- **Role hierarchy** (`SecurityConfig.java:53-55`): `ROLE_SUPER_ADMIN > ROLE_ADMIN >
  ROLE_USER`, wired into `methodSecurityExpressionHandler` (line 58). **This is the single
  most important fact in this file** — see the worked example below.
- **JWT** (`security/jwt/`): `JwtService` issues and validates; `JwtAuthenticationFilter`
  maps roles onto `ROLE_*` authorities.
- **Rate limiting** (`security/ratelimit/`, `security/web/AuthRateLimitFilter.java`):
  per-endpoint buckets from `RateLimitProperties`, toggled by `security.rate-limit.enabled`,
  falling back to `NoOpRateLimiter` when off. Tests disable it in
  `src/test/resources/application-test.yml`.
- **Error shape**: `api/errors/ApiErrorResponse` — `code`, `message`, `timestamp`, optional
  `errors`.

## Hard rules

### 1. Authorization lives in `@PreAuthorize`. Never in a method body.

`RoleHierarchy` is applied by `methodSecurityExpressionHandler`, so it affects
**annotations only**. `getAuthorities()` returns the raw, un-expanded list. This is a live
defect at the time of writing:

```java
// WRONG - OrderService.java:209 and OrderController.java:124
// getAuthorities() returns the RAW list. RoleHierarchy is applied by
// methodSecurityExpressionHandler, so it affects @PreAuthorize ONLY.
// A SUPER_ADMIN does not match this, and is silently demoted.
private boolean isAdmin() {
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
}
```

The consequence: on `GET /orders` and `GET /orders/search` a `SUPER_ADMIN` sees only their
own orders. Nothing throws. Nothing logs. The endpoint just quietly returns less than it
should. **Note that a hand-written check is a finding even when it currently returns the
right answer** — it is one role addition away from being wrong.

### 2. No `@PostAuthorize` for ownership.

Filtering after the fact means the work already happened and the row was already loaded.
Express the rule in `@PreAuthorize`, or push it into the query.

### 3. Never invent authorities that are not claims.

`JwtAuthenticationFilter:66-75` fabricates `SCOPE_order:read`/`:write`/`:delete` from the
user's role. They look like OAuth2 scopes but are not — they are not claims, they carry no
independent meaning, and `SCOPE_order:write` is granted to **every** authenticated user. So
`hasRole('ADMIN') or hasAuthority('SCOPE_order:write')` denies nobody while reading like a
restriction. Do not add more of these.

### 4. Multi-tenant list endpoints force-filter in the query.

Non-admin scoping belongs in the repository call, never in a post-filter loop over rows that
have already been fetched.

### 5. Secrets come from `.env`.

`${VAR}` in `application.yml`, backed by `.env`, with `.env.example` extended in the same
change. Never hardcode a key, password or token.

### 6. Every security change ships with both paths tested.

Allowed **and** denied. Plus a `SUPER_ADMIN` case whenever role handling is involved — see
the `writing-backend-tests` skill.

## Target model — not yet built

Mark anything you write against this section as target state. Source:
`docs/contracts/backend-integration-manifest.openapi.yaml`.

- **Refresh token moves to an HttpOnly cookie**, `Path=/api/auth`, `SameSite=Lax`, with
  `Secure` configurable so local HTTP dev still works.
- **Refresh rotates on every call.** Reuse of an already-rotated token must revoke the
  **entire token family**, not just the replayed token.
- `/auth/refresh` and `/auth/logout` become the only cookie-dependent endpoints, and
  therefore the **only CSRF surface**. The defence is `Origin` validation plus requiring
  `Content-Type: application/json`. Everything else authenticates by header and is unaffected.
- The access token gains `firstName`, `lastName`, `email` claims. **A JWT payload is base64,
  not encrypted** — display data only. Never a phone number, address, or anything else
  sensitive.
- **`/public/*` is unauthenticated and must never reuse an admin-scoped query.** Specific
  obligations from the manifest: filter `available == true` server-side so 86'd items do not
  leak; expose a single-table lookup rather than the full roster; treat `customerPhone` as a
  lookup credential without enabling enumeration; derive `total`, `etaMinutes` and
  `paymentStatus` server-side and force `channel=Online` rather than trusting the client.

## Red flags — stop

- You are about to write `if (isAdmin())` or any permission check inside a method body.
- You are adding an authority that is not a JWT claim.
- You are changing a permission without knowing whether the frontend depends on it.
- You are about to say a security change works without having run `./gradlew test`.
