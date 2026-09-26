# Restaurant-ops migration, Phase 1 — Auth target design

Date: 2026-09-25
Status: approved design, amended 2026-09-25 after a risk review (D22–D24) — plan: `../plans/2026-09-25-restaurant-ops-phase-1-auth.md`

## Problem

`2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` puts auth in Phase 1:
*HttpOnly cookie, rotation with family revocation, new JWT claims, CORS + `Origin`*, built
**backward-compatible** so the frontend can flip on its own schedule. Every integration test
logs in through `/auth/login`, so landing the final auth shape now means every test written in
Phases 2–5 is born in it.

The target is the manifest's `Auth` tag (`docs/contracts/backend-integration-manifest.openapi.yaml`,
0.3.0): `AuthResponse`, `JwtClaims`, `/auth/login`, `/auth/register`, `/auth/refresh`,
`/auth/logout`, and the `dev-cookie-secure` resolution (D12).

## Findings that shaped the decisions

- **Rotation already exists.** `AuthenticationService.refresh` marks the presented token used and
  issues a new one. What is missing is any link between the two: a replayed token is rejected
  with a plain error and nothing else happens, so a thief who refreshes first keeps a live chain.
- **There is no family concept in the schema.** `refresh_tokens` (V5, V7, V20) has no column
  tying a rotated token to its successor. Family revocation needs a migration.
- **`refresh()` is not `@Transactional`.** Marking the old token used and issuing the new one are
  two separate commits, and two concurrent calls with the same token can both pass the
  `isActive` check.
- **An invalid refresh token answers 400**, not the manifest's 401
  (`AuthExceptionHandler.handleInvalidRefreshToken`, asserted in `RefreshTokenFlowIT`).
- **CORS hardcodes `http://localhost:3000`** in `SecurityConfig.corsConfigurationSource`.
  `allowCredentials(true)` is already set.
- **The manifest's cookie path `/api/auth` assumes `SERVER_CONTEXT_PATH=/api`.** The context path
  is env-bound (`application.yml`), so the cookie path must be derived from it, not hardcoded.
- **A password reset does not revoke refresh tokens.** A stolen refresh token keeps working for
  up to 14 days after the victim resets their password.
- **Auth rate limiting has never fired outside the tests** (found in the risk review, confirmed
  2026-09-25). `AuthRateLimitFilter` matches `request.getRequestURI()` against `"/auth/login"`
  and friends. `getRequestURI()` includes the context path, so a real deployment sees
  `/api/auth/login`, matches nothing, and skips every request. MockMvc sends no context path,
  which is why the rate-limit ITs pass. A throwaway MockMvc run with rate limiting forced on,
  seven bad logins each:

  ```
  without context path: [401, 401, 401, 401, 401, 429, 429]
  with /api context path: [401, 401, 401, 401, 401, 401, 401]
  ```

  The running local app (`POST http://localhost:8080/api/auth/login`, seven bad logins) also
  answered 401 seven times. The Phase 1 `Origin` filter has to match the same two paths, so it
  would have inherited the defect.
- **Fixing that turns rate limiting on in production for the first time**, and the limiter keys
  on `getRemoteAddr()`. Its comment says X-Forwarded-For parsing is delegated to Tomcat, but
  `server.forward-headers-strategy` is not configured, so nothing does. Behind a reverse proxy
  every user would share the proxy's IP and one bucket (5 logins per window, site-wide); trusting
  the header unconditionally would let any client forge its IP. Not deployed yet (Gabriel,
  2026-09-25), so this is decided now rather than discovered at launch.
- **Reuse detection has a 7-day horizon.** `RefreshTokenCleanupService` deletes used and revoked
  tokens 7 days after use. A replay after that finds nothing, answers a plain 401 and revokes no
  family, so a thief who rotated first keeps the session if the user returns after a week.

## Scope

| In Phase 1 | |
|---|---|
| Refresh cookie issued on login, register and refresh; cleared on logout | yes |
| Refresh and logout read the cookie, falling back to the body | yes |
| Token families, reuse detection revoking the family | yes |
| Row lock + transaction on rotation | yes |
| `Origin` check on cookie-borne requests | yes |
| CORS origins from configuration | yes |
| `firstName`, `lastName`, `email` JWT claims | yes |
| Invalid refresh → 401 | yes |
| Password reset revokes all of the user's refresh tokens | yes (user's decision, 2026-09-25) |
| Rate limiting matches paths under the context path (D22) | yes — first task, own commit |
| Client-IP trust made explicit and env-bound (D23) | yes |
| Used/revoked tokens retained until their own expiry (D24) | yes |
| Dropping `refreshToken` from the response body | **no — Phase 6**, coordinated with the frontend |
| Making the `Origin` check unconditional | **no — Phase 6**, once the body path is gone |

## Decisions taken

### D16 — token families, revoked as a unit on reuse

`refresh_tokens` gains `family_id UUID NOT NULL`, indexed. Login and register start a new
family; every rotation copies the family onto the successor. Presenting a token that is **used
or revoked** (reuse) revokes every non-revoked token in its family, then answers 401.
Presenting a token that is unknown or merely expired answers 401 and revokes nothing — there is
no family to act on, or no evidence of theft.

Rejected: revoking **all** of the user's tokens on reuse. Simpler (no column), but it goes beyond
the contract and logs the user out of unrelated devices on every incident.

Existing rows receive one fresh family each (`gen_random_uuid()`, built into PostgreSQL 13+;
tests run 16), so tokens live at deploy time keep working and behave as single-member families.

### D17 — strict reuse detection, no grace window

The first presentation of a token rotates it; any later presentation is reuse, even one
millisecond later. Two tabs refreshing at boot with the same cookie therefore revoke the
family, and the user logs in again.

Rejected: a grace window (~10 s) during which reuse answers 401 without revoking. It adds state
and edge cases, and it is a window in which a replayed token goes unpunished. The race is the
frontend's to prevent — single-flight the refresh across tabs (a Web Lock or a
`BroadcastChannel`). That is a frontend follow-up, not a backend accommodation.

Rotation runs in one transaction and loads the token with `PESSIMISTIC_WRITE`
(`SELECT … FOR UPDATE`), so two concurrent presentations serialize: the first rotates, the
second sees `used_at` set and is treated as reuse — consistent with D17 rather than a double
issue.

### D18 — token source: cookie first, body as fallback

`/auth/refresh` and `/auth/logout` read the `refreshToken` cookie; if absent, the body's
`refreshToken`; if both are absent, refresh answers 401 and logout answers 200 (idempotent).
The request body becomes optional on both endpoints. `Content-Type: application/json` remains
required by the existing `consumes` mapping (415 otherwise), which the manifest asks for as part
of the CSRF defense.

When both are present the cookie wins: it is the target source, and preferring it means a
stale `localStorage` value in a half-migrated frontend cannot override a fresh cookie.

### D19 — `Origin` validated only for cookie-borne requests

A request to `/auth/refresh` or `/auth/logout` that **carries the refresh cookie** must have an
`Origin` header equal to one of `security.cors.allowed-origins`. A missing or unlisted `Origin`
answers **403** `ORIGIN_NOT_ALLOWED` before any token is read or touched.

Requests without the cookie skip the check. CSRF works by making the browser attach a
credential the attacker cannot read; a token in the body is one the caller already had to read,
so the attack does not apply. This keeps curl, Swagger and every existing IT working unchanged.
In the target state (Phase 6) every such request carries the cookie, so the check becomes
universal without further change — it is then exactly the manifest's "validate the Origin
header".

The check is a servlet filter keyed on the cookie's presence, registered in `SecurityConfig`
ahead of `JwtAuthenticationFilter`. It is a CSRF defense, not authorization, so it does not
conflict with the rule that authorization lives only in `@PreAuthorize`.

Rejected: checking `Origin` on every call to the two endpoints. Stricter in name only, and it
breaks non-browser callers and every refresh IT.

### D20 — invalid refresh answers 401

Unknown, expired, used, revoked, or absent refresh token → **401** `INVALID_REFRESH_TOKEN`,
matching the manifest. The code is unchanged; only the status moves from 400. The frontend
already codes against the manifest; its interceptor must not retry a failed `/auth/refresh`
(frontend follow-up).

### D21 — password reset revokes every refresh token the user holds

A successful `PasswordResetService.resetPassword` revokes all of the user's non-revoked refresh
tokens in the same transaction. A reset is the user's statement that their credentials may be
compromised; leaving refresh tokens alive defeats it.

### D22 — filters match the path within the application

Every filter that matches on a path uses the request URI minus the context path (one helper,
`RequestPaths.withinApplication`), never the raw `getRequestURI()`. `AuthRateLimitFilter` is
corrected to it, and `RefreshOriginFilter` (D19) is born using it. Regression tests run with
`contextPath("/api")`, because a filter that passes every context-free test while switching
off in production is exactly the defect found.

The raw URI is not normalized, so matching on it relies on Spring Security's
`StrictHttpFirewall` (it runs before the filter chain and rejects `;`, `//`, encoded slashes and
dot segments) and on Spring MVC 6 not matching trailing slashes. Both are framework defaults;
nothing here disables them.

This lands as its own commit, first, because it is a production security fix independent of
the cookie work (Gabriel, 2026-09-25).

Rejected: fixing only the Origin filter and deferring the rate limiter — it leaves a known
brute-force exposure on `/auth/login` and `/auth/forgot-password` in place.

### D23 — client-IP trust is explicit, env-bound and off by default

`server.forward-headers-strategy: ${SERVER_FORWARD_HEADERS_STRATEGY:none}`. With `none` the
client IP is the socket address — correct locally and when exposed directly, and the only safe
default, because honoring `X-Forwarded-For` from anyone lets a client forge its IP and walk
around every rate limit. A deployment behind a reverse proxy or load balancer sets `native`:
Tomcat's `RemoteIpValve` then takes the client IP from `X-Forwarded-For` only when the
connection comes from a trusted proxy (`server.tomcat.remoteip.internal-proxies`, private
ranges by default; a proxy on a public address has to be added there).

Rejected: leaving it unset. Spring Boot then enables forward headers on some detected cloud
platforms and not others — a security property decided by where the jar happens to run, the
same reasoning D12 used to reject profile-derived cookie flags.

Not tested automatically: MockMvc does not run Tomcat valves, so an IT would pass whatever the
setting. Verified by hand instead (see Verification).

### D24 — used and revoked tokens are kept until their own expiry

Cleanup deletes only tokens past `expires_at`; the separate 7-day purge of used and revoked
tokens goes. Reuse is then detected for a token's whole 14-day life. `refresh()` checks
used/revoked **before** expired, so replaying a used token that has also expired still revokes
its family. Cost: one extra row per rotation, gone at most 14 days later.

Rejected: keeping 7 days and documenting the blind spot.

### Unchanged decisions this phase implements

- **D12** — `security.refresh-cookie.secure ${REFRESH_COOKIE_SECURE:true}`,
  `security.refresh-cookie.same-site ${REFRESH_COOKIE_SAME_SITE:Lax}`,
  `security.cors.allowed-origins ${CORS_ALLOWED_ORIGINS:http://localhost:3000}`
  (comma-separated). Production-safe defaults; local development sets
  `REFRESH_COOKIE_SECURE=false` explicitly.
- **D14** — the new error code is `SCREAMING_SNAKE`: `ORIGIN_NOT_ALLOWED`.

## Contract work

Nothing to report back. Every behavior above is either the manifest's target or invisible to
the wire:

- The cookie attributes, the 401/403 responses, rotation, family revocation and the three claims
  are the manifest's target text.
- D18's body fallback and D19's conditional check are the backward-compatible interim the
  migration design already approved; the manifest documents both the live and target shapes.
- D21 has no wire shape.

The snapshot is not edited. The `Auth` paths stay `x-status: target-change` until Phase 6 removes
the body token; flipping them to `live` belongs to that phase.

**Frontend follow-ups to hand over** (in the frontend repo, not here): send
`credentials: 'include'` on `/auth/*`; single-flight `/auth/refresh` across tabs (D17); never
retry a 401 from `/auth/refresh` itself (D20); read the new claims instead of calling
`/me/profile` on first render.

## Implementation

### Schema — `V21__Refresh_Token_Families.sql`

```sql
ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;
UPDATE refresh_tokens SET family_id = gen_random_uuid();
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
```

`RefreshToken` gains `@Column(name = "family_id", nullable = false) UUID familyId`.

### Tokens — `RefreshTokenService` / `RefreshTokenRepository`

- `issueFor(User)` starts a new family; `rotate(RefreshToken)` issues the successor in the same
  family and marks the predecessor used.
- Repository: a `PESSIMISTIC_WRITE` lookup by hash; bulk `revokeFamily(familyId, now)` and
  `revokeAllForUser(userId, now)` updates touching only rows with `revoked_at IS NULL`.
- `IssuedRefreshToken` already carries `expiresAt`; the cookie's `Max-Age` is derived from the
  same 14-day TTL.

### Flow — `AuthenticationService`

- `refresh(String rawToken)` becomes `@Transactional`: locked lookup → absent/unknown/expired →
  401; used or revoked → revoke family, 401; inactive user → revoke family, 401 (today it revokes
  the single token); otherwise rotate. The revocation must commit even though the call ends in
  an exception, so the method is `@Transactional(noRollbackFor = InvalidRefreshTokenException.class)`.
- `logout(String rawToken)` revokes the token's family; no-op when absent or unknown.
- `register`/`authenticate` unchanged apart from the family.

### Web — cookie, controller, filter, CORS

- `RefreshCookieProperties` (`security.refresh-cookie.*`) and `CorsProperties`
  (`security.cors.allowed-origins`), bound with `@ConfigurationProperties`.
- A `RefreshCookieFactory` builds the cookie with `ResponseCookie`: name `refreshToken`,
  `HttpOnly`, `Secure` and `SameSite` from properties, `Path = <context-path>/auth`,
  `Max-Age` = refresh TTL; and the matching clearing cookie (`Max-Age=0`, same attributes).
- `AuthenticationController` reads `@CookieValue(required = false)` and an optional body,
  resolves the source per D18, and adds `Set-Cookie` on login, register, refresh and logout.
  The response body is unchanged (`accessToken` + `refreshToken`) until Phase 6.
- `RefreshOriginFilter` implements D19, emitting `ApiErrorResponse` for the 403.
- `SecurityConfig.corsConfigurationSource` reads `CorsProperties`.
- `.env.example` gains `REFRESH_COOKIE_SECURE`, `REFRESH_COOKIE_SAME_SITE`,
  `CORS_ALLOWED_ORIGINS` (noting that Swagger UI used in a browser sends the cookie from the
  API's own origin, so a developer who uses it adds `http://localhost:8080` locally); the three keys are bound in `application.yml`. The test profile keeps
  the production default `secure: true` (MockMvc does not enforce it), so tests assert what ships.

### Rate limiting and client IP — `RequestPaths`, `AuthRateLimitFilter`, `application.yml`

- `security.web.RequestPaths.withinApplication(HttpServletRequest)` returns
  `getRequestURI()` minus `getContextPath()`. `AuthRateLimitFilter.shouldNotFilter` and
  `doFilterInternal` use it; `RefreshOriginFilter` uses it.
- `server.forward-headers-strategy` bound per D23; `SERVER_FORWARD_HEADERS_STRATEGY` documented
  in the env template; the misleading comment in `AuthRateLimitFilter` corrected.
- `RefreshTokenCleanupService.cleanupNow()` calls only `deleteExpired`; the repository's
  `deleteUsedOrRevokedBefore` and `CleanupResult.usedRevokedDeleted` are removed (D24).

### Claims — `JwtService`

`generateToken` adds `firstName`, `lastName`, `email` alongside `userId` and `roles`. Display
data only; the manifest already states they are not re-issued on profile edits.

## Testing

Per the `writing-backend-tests` skill: `*Test` for units, `*IT` for the full context, allowed
**and** denied paths.

Unit:
- token-source resolution: cookie only, body only, both (cookie wins), neither;
- `RefreshOriginFilter`: cookie + allowed origin passes; cookie + foreign origin → 403; cookie +
  no origin → 403; no cookie + any origin passes;
- `RefreshCookieFactory`: attributes follow properties; path follows the context path; clearing
  cookie mirrors the issuing one;
- `JwtService`: the three claims are present.

Unit (added by D22–D24):
- `RequestPaths`: `/api` context path stripped; empty context path unchanged;
- cleanup calls only `deleteExpired`.

Integration (`@IntegrationTest`, real login):
- login rate limiting fires with `contextPath("/api")` and without it (D22 — the first run
  fails, reproducing the finding);
- a used token whose own expiry is still ahead survives cleanup (D24);
- login and register set the cookie with `HttpOnly`, `SameSite=Lax`, `Path`, `Max-Age`, `Secure`;
- refresh via cookie rotates and sets a new cookie; refresh via body still works (backward
  compatibility);
- **reuse**: rotate A→B, present A → 401, then B → 401 (family revoked); a token from a second
  login stays valid;
- concurrent presentation of one token yields exactly one success;
- logout via cookie clears it and revokes the family; logout with nothing → 200;
- cookie + foreign or missing `Origin` → 403 `ORIGIN_NOT_ALLOWED`, and the token is **not**
  consumed;
- password reset revokes every refresh token of that user and no one else's;
- invalid refresh → 401 (`RefreshTokenFlowIT`'s two 400 assertions change to 401);
- V21 on a database with pre-existing rows: each gets a distinct family, all still usable.

The rest of the suite passes as written — that is the backward-compatibility proof.

## Verification

Full `./gradlew test` green, then a `spring-security-reviewer` audit of the diff before the
phase is called done. Manual, against the restarted local app:
- log in from the running frontend origin and confirm the cookie in devtools (HttpOnly,
  `Path=/api/auth`) with `REFRESH_COOKIE_SECURE=false` locally;
- seven bad logins to `http://localhost:8080/api/auth/login` answer 429 from the sixth on
  (requires `RATE_LIMIT_ENABLED` not set to `false` locally) — the D22 fix in the real
  container, which no MockMvc test can show;
- the same seven, each with a different forged `X-Forwarded-For`, still hit 429 on the sixth —
  D23's default ignores the header.

## Risks

- **A frontend still on the body path and a cookie at the same time.** Once login sets the
  cookie, the browser sends it on `/auth/refresh` if the frontend uses `credentials: 'include'`.
  Cookie wins (D18), and it is the fresher token, so the pair stays consistent.
- **Two tabs at boot log the user out (D17).** Accepted; mitigated by the frontend's
  single-flight follow-up.
- **`SameSite=Lax` assumes front and API share a site.** True for `localhost:3000`/`:8080`. A
  production split across registrable domains needs `SameSite=None; Secure` plus an anti-CSRF
  token — the manifest's `dev-cookie-secure` text already records this; out of scope here.
- **Rollback of the reuse branch.** If the family revocation rolls back with the 401, reuse
  detection silently does nothing. The integration test that presents B after A's reuse is the
  guard against exactly this.

- **A lost refresh response logs the user out (D17, accepted).** If the server rotates and the
  response never arrives (network drop, timeout), the client retries with the old token, which
  is reuse, and the family is revoked. That is the known price of strict rotation without a
  grace window; the user logs in again.
- **Launch gate, not a deploy-order problem.** The frontend is not live (Gabriel, 2026-09-25),
  so strict reuse and the 400→401 change affect no real user today. The four frontend
  follow-ups (see "Contract work") must land before any launch: without single-flight, two tabs
  refreshing at once log the user out; without the no-retry rule, a 401 from `/auth/refresh`
  can loop.
- **Rate limits start firing locally.** After D22, rapid manual logins against the local app get
  429s where they never did — expected; `RATE_LIMIT_ENABLED=false` is the local escape hatch.
- **The rate limiter is in-memory, per instance.** Correct for the single instance that exists;
  horizontal scaling would divide the effective limit by the instance count. Out of scope until
  there is more than one instance.

## Out of scope, carried forward

- Phase 6: drop `refreshToken` from `AuthResponse`, remove the body fallback, make the `Origin`
  check universal, flip the `Auth` paths to `live`.
- Revoking refresh tokens on user deactivation or role change (refresh already rejects inactive
  users; role changes take effect at the next access token).
