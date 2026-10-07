# Tenancy & Identity contract

- Status: Draft
- Updated: 2026-10-07
- Reviewed: not yet
- Roadmap step: Build 1, Tenant foundation (`docs/roadmap.md`)
- Draft spec: `docs/api/drafts/tenancy-identity.yaml`
- Seeded from: live endpoints (`/auth/*`, `/me`, `/me/profile`, `/users`, `/roles`, `/admin/users/*`, `/admin/identity-audit`); frontend manifest 0.4.0 seeds nothing here (its auth paths mirror the live ones)
- API conventions: `architecture/API-CONVENTIONS.md` (Draft)
- Related: ADR-0001, ADR-0008, ADR-0009, ADR-0010, ADR-0014, ADR-0018; register Q-16..Q-31 (all answered); `planning/proposals/settings-and-notifications.md` (Reviewed 2026-10-06; sections A1, B, C, D2); legacy auth decisions D16-D24 (in force)

Rows marked **(Claude)** are Claude's defaults where neither the register nor the settings proposal decided; Gabriel may override any of them when he reviews this contract. Everything else cites Gabriel's answer.

## Scope

Covers: the restaurant as tenant, users, memberships and roles, how each request gets its restaurant, sign-in and tokens, the member's own account, the team screen, the identity audit log, the platform admin's restaurant management, the restaurant's Build 1 settings (name, slug, timezone, currency) and the isolation rules every later contract inherits.

Leaves to other contracts: storefront settings, branding, ordering settings, delivery zones and the PIX key (Storefront, Build 4, Q-30 a); public restaurant pages beyond slug resolution (Storefront); the notification feed (Order Core, Build 3).

## Decisions

| # | Decision | Decided by | Source |
|---|---|---|---|
| D-1 | The tenant is one restaurant location. No organization or chain level this run. | Gabriel | Q-22 a |
| D-2 | A user belongs to at most one restaurant, through a `memberships` row (unique on `user_id`), so several can come later. | Gabriel | Q-16 a |
| D-3 | Roles: `PLATFORM_ADMIN` (a flag on the user, no membership) and the membership roles `OWNER`, `MANAGER`, `STAFF`. Today's `SUPER_ADMIN`, `ADMIN`, `USER` and the `roles` table are removed. | Gabriel | Q-17 a |
| D-4 | The platform admin may read every restaurant's data for support (§5.4), and manages restaurants and their owners. | Gabriel | Q-18 b |
| D-5 | Signup is invite-only. `POST /auth/register` is removed. The platform admin creates a restaurant and invites its owner; owners and managers invite their team. | Gabriel | Q-19 a; proposal B1, B3 |
| D-6 | Restaurant members' requests take the restaurant from the signed-in member's membership, never from the request. Public requests take it from `/public/r/{slug}`. Platform requests name it in the path. | Claude (delegated) / Gabriel | Q-26 a, Q-20 a |
| D-7 | The slug is set by the platform admin at creation, unique, and never changes this run. | Gabriel | Q-31 a |
| D-8 | Isolation is application-level: `restaurant_id` on every restaurant-owned table, a fail-closed tenant context, an ArchUnit rule and a cross-tenant test per endpoint. | Gabriel | Q-25 a |
| D-9 | Every restaurant has an IANA timezone, required at creation, default `America/Sao_Paulo`; there is no deployment-wide fallback. Currency is BRL. | Gabriel | Q-28 a, Q-21 a |
| D-10 | Build 1 auth follow-ups: revoke refresh tokens on deactivation and on role or membership change; drop `refreshToken` from response bodies and the body fallback; check `Origin` unconditionally on cookie-borne calls; the frontend keeps the access token in memory only. | Gabriel | Q-29 a |
| D-11 | Access tokens live 15 minutes (today 24 h). This bounds how long a deactivated, demoted or signed-out member keeps access (proposal B4, C4, C5). The refresh flow already renews them silently. | **Claude** | proposal "the T&I contract sets the access-token lifetime" |
| D-12 | Permission matrix (§5.3): the owner edits everything; the manager manages staff; staff manage nothing; only an owner or the platform admin grants `OWNER`. | Claude (proposal A0, B3) | proposal A0, B3 |
| D-13 | Date and number format follows the viewer's UI language; it is not a restaurant setting. Instants are shown in the restaurant's timezone. | **Claude** | proposal D2 ("the T&I contract decides") |
| D-14 | The owner sees the timezone read-only; the platform admin may correct it. | **Claude** | proposal A1 ("the T&I contract says whether the platform admin can correct it") |
| D-15 | Platform admin support reads use the `X-Support-Restaurant-Id` header on `GET` requests to restaurant paths; any write with it is refused. | **Claude** | Q-18 b; §5.4 |
| D-16 | A restaurant always keeps at least one active owner. | **Claude** | B4 (deactivation) |
| D-17 | No restaurant suspension or deletion this run: there is no billing (ADR-0018). | **Claude** | ADR-0018 |
| D-18 | Profile keeps name, phone, email (read-only) and avatar; department, office, bio, country and timezone are removed. `/me/profile` folds into `/me`. | Gabriel (proposal Q3 yes); folding: Claude | proposal C1 |

## Resources

### Restaurant

| Field | Type | Required | Rule | Source |
|---|---|---|---|---|
| `id` | ULID | yes | server-generated | ADR-0009 |
| `name` | string, 1-80 | yes | owner or platform admin edits | proposal A1 |
| `slug` | string, 3-40 | yes | `^[a-z0-9]+(-[a-z0-9]+)*$`; unique; not in the reserved list (`admin`, `api`, `app`, `auth`, `login`, `platform`, `public`, `r`, `static`, `www`); never changes | Q-31 a; pattern and list: Claude |
| `storefrontUrl` | URL | yes | absolute; built from `APP_PUBLIC_BASE_URL` + `/r/{slug}` | proposal A1 |
| `timezone` | IANA name | yes | validated against the JVM zone list; default `America/Sao_Paulo` | Q-28 a; D-14 |
| `currency` | enum `BRL` | yes | fixed this run | Q-21 a |
| `createdAt` | instant | yes | | |

The slug is suggested from the name (lowercase, accents removed, spaces to hyphens) in the platform admin's form; the admin may edit it before creating. A taken slug is 409 `SLUG_TAKEN`.

### User and membership

| Field | Type | Required | Rule | Source |
|---|---|---|---|---|
| `id` | ULID | yes | the user's id | ADR-0009 |
| `email` | string, ≤254 | yes | unique across the platform, stored lowercase | API conventions §8.4 |
| `firstName`, `lastName` | string, 1-80 | yes | | live shape |
| `phone` | E.164 string | no | `+55` numbers (Q-21 a) | proposal C1 |
| `avatarUrl` | URL | no | | proposal C2 |
| `platformAdmin` | boolean | yes | true only for platform admins; set by seed or migration, never by an endpoint this run | D-3; **Claude** |
| `active` | boolean | yes | false = cannot sign in or refresh | live |
| `membership` | object or null | yes | `{ restaurantId, role, memberSince }`; null for a platform admin | D-2 |
| `invitationPending` | boolean | yes | true until the invited person sets a password | proposal B3 |

A platform admin never has a membership, and a member never has `platformAdmin = true`. The one existing env-seeded root user (`V15__seed_super_admin.sql`) becomes the first platform admin.

### Audit event

| Field | Type | Rule |
|---|---|---|
| `id` | ULID | |
| `restaurantId` | ULID or null | null for platform-level events (restaurant created) |
| `type` | enum | `MEMBER_INVITED`, `INVITE_RESENT`, `ROLE_CHANGED`, `MEMBER_DEACTIVATED`, `MEMBER_REACTIVATED`, `PASSWORD_CHANGED`, `PASSWORD_RESET`, `SIGNED_OUT_OTHER_DEVICES`, `RESTAURANT_CREATED`, `RESTAURANT_UPDATED`; Build 4 adds `PIX_KEY_CHANGED`, `WHATSAPP_NUMBER_CHANGED` |
| `actorUserId`, `actorName` | | who did it |
| `targetUserId`, `targetName` | or null | who it was done to |
| `details` | object | type-specific, no secrets, no tokens (for `ROLE_CHANGED`: `{ from, to }`) |
| `occurredAt` | instant | |

## Operations

All paths are under `/api` (API conventions §2). "Membership" means the restaurant comes from the caller's membership (D-6).

| Method | Path | Caller | Restaurant resolved from | Idempotent | Draft state |
|---|---|---|---|---|---|
| POST | `/auth/login` | public | n/a | no | draft (reshaped: no body `refreshToken`) |
| POST | `/auth/refresh` | public (cookie) | n/a | no (rotates) | draft (reshaped) |
| POST | `/auth/logout` | public (cookie) | n/a | yes | draft (reshaped) |
| POST | `/auth/forgot-password` | public | n/a | yes (always 204) | draft (live shape kept) |
| POST | `/auth/reset-password` | public | n/a | no | draft (also completes an invite) |
| ~~POST~~ | ~~`/auth/register`~~ | | | | removed (D-5) |
| GET | `/me` | any signed-in user | membership | yes | draft (adds restaurant and role) |
| PATCH | `/me` | any signed-in user | membership | yes | draft (name, phone) |
| POST | `/me/avatar` | any signed-in user | n/a (user prefix) | yes | draft (moved from `/me/profile/avatar`) |
| DELETE | `/me/avatar` | any signed-in user | n/a | yes | draft |
| POST | `/me/password` | any signed-in user | n/a | no | draft (new, proposal C4) |
| POST | `/me/sign-out-other-devices` | any signed-in user | n/a | yes | draft (new, proposal C5) |
| GET | `/restaurant` | OWNER, MANAGER, STAFF | membership | yes | draft (new) |
| PATCH | `/restaurant` | OWNER | membership | yes | draft (new; `name` only in Build 1) |
| GET | `/team/members` | OWNER, MANAGER | membership | yes | draft (replaces `/users`) |
| GET | `/team/members/{memberId}` | OWNER, MANAGER | membership | yes | draft (replaces `/users/{id}`) |
| POST | `/team/members` | OWNER, MANAGER | membership | no (`EMAIL_TAKEN`) | draft (replaces `POST /admin/users`) |
| PATCH | `/team/members/{memberId}` | OWNER, MANAGER (§5.3) | membership | yes | draft (name; role change) |
| POST | `/team/members/{memberId}/deactivate` | OWNER, MANAGER (§5.3) | membership | yes | draft (replaces `/admin/users/{id}/deactivate`) |
| POST | `/team/members/{memberId}/reactivate` | OWNER, MANAGER (§5.3) | membership | yes | draft |
| POST | `/team/members/{memberId}/resend-invite` | OWNER, MANAGER (§5.3) | membership | yes | draft (replaces `/admin/users/{id}/password-setup`) |
| GET | `/audit-events` | OWNER, MANAGER | membership | yes | draft (replaces `/admin/identity-audit`) |
| GET | `/platform/restaurants` | PLATFORM_ADMIN | n/a | yes | draft (new) |
| POST | `/platform/restaurants` | PLATFORM_ADMIN | n/a | no (`SLUG_TAKEN`, `EMAIL_TAKEN`) | draft (new; creates restaurant and invites owner) |
| GET | `/platform/restaurants/{restaurantId}` | PLATFORM_ADMIN | path | yes | draft (new) |
| PATCH | `/platform/restaurants/{restaurantId}` | PLATFORM_ADMIN | path | yes | draft (new; name, timezone) |
| GET | `/platform/restaurants/{restaurantId}/members` | PLATFORM_ADMIN | path | yes | draft (new) |
| POST | `/platform/restaurants/{restaurantId}/members` | PLATFORM_ADMIN | path | no | draft (new; invite an owner, or any role) |
| GET | `/public/r/{slug}` | public | slug | yes | draft (minimal; the Storefront contract extends it) |

Removed with no replacement: `GET /roles` (three fixed roles, proposal B6), `GET /admin/users/{id}/status` (folded into the member resource), `PATCH /admin/users/{id}/role` (folded into `PATCH /team/members/{memberId}`).

`{memberId}` is the member's user id.

## Lifecycle and rules

### Restaurant creation (proposal B1)
1. The platform admin sends name, slug, timezone and the owner's email, first and last name.
2. In one transaction: the restaurant row, a user with no password, an `OWNER` membership, a password-setup token, and a `RESTAURANT_CREATED` plus a `MEMBER_INVITED` audit event.
3. After commit, the password-setup email is sent (today's email). The owner sets a password through `/auth/reset-password` and lands in their restaurant.

### Invites (proposal B3)
1. An invite creates the user (no password, `invitationPending = true`) and the membership with the chosen role, then sends the password-setup email after commit.
2. An email that already exists on the platform is 409 `EMAIL_TAKEN`, also if it belongs to another restaurant (one restaurant per user, Q-16 a). The response does not say which restaurant.
3. Resending an invite issues a new token and invalidates the old one. Invite tokens expire after 7 days **(Claude)**; reset tokens keep today's lifetime.

### Membership changes
1. Role change, deactivation and reactivation follow the matrix in §5.3.
2. Role change and deactivation revoke every refresh-token family of the target (Q-29 a). Their current access token works until it expires (at most 15 minutes, D-11); the screen says so (proposal Rule 8).
3. Nobody changes their own role or deactivates themselves: 409 `SELF_ACTION_NOT_ALLOWED`.
4. Demoting or deactivating the last active owner: 409 `LAST_OWNER` (D-16).
5. A deactivated user's login answers 401 `INVALID_CREDENTIALS`, like a wrong password (no account enumeration).
6. Members are never hard-deleted: their id stays referenced by orders and audit events.

### Own account (proposal C)
1. `PATCH /me` edits `firstName`, `lastName`, `phone`. Email is read-only.
2. `POST /me/password` takes `currentPassword` and `newPassword`. Wrong current password: 400 `INVALID_CURRENT_PASSWORD`; reused password: 409 `PASSWORD_REUSE_ERROR` (live code, today 400; becomes 409 per API conventions §10.3); the same password rules as reset. On success every other refresh-token family of the user is revoked; the current session continues.
3. `POST /me/sign-out-other-devices` revokes every refresh-token family of the user except the one in the request's cookie (proposal C5).

### Tokens
1. Access token: JWT, 15 minutes (D-11). Claims: `sub` (user ULID), `rid` (restaurant ULID, absent for a platform admin), `role` (`OWNER`, `MANAGER`, `STAFF` or `PLATFORM_ADMIN`). Display names leave the token; the client reads them from `GET /me` **(Claude)**.
2. Refresh token: today's HttpOnly cookie with family rotation and reuse detection (D16-D24 kept), never in a body (D-10).
3. The claims are written from the membership when the token is issued. A membership change revokes the refresh tokens, so the next access token carries the new role.

## Tenancy and security

### 5.1 Tenant context
1. A filter after JWT authentication sets a request-scoped `TenantContext { restaurantId, userId, role }` from the token. For a public request the slug resolver sets `restaurantId` from `/public/r/{slug}`.
2. Restaurant-scoped code reads the restaurant only from `TenantContext`. When it is empty, the call fails closed with an exception mapped to 500 and logged as a bug; it never falls back to "all restaurants" (ADR-0001).
3. Every repository method on a restaurant-owned table takes the restaurant id as a parameter or uses a query that filters by it. An ArchUnit test fails the build when a repository of a restaurant-owned entity has a finder without a `restaurantId` parameter (except `findById`-style methods that are not exposed; the rule's exact form is fixed in Build 1).
4. `@Async` work, schedulers and after-commit listeners copy `TenantContext` and the log MDC through a `TaskDecorator`.

### 5.2 What is restaurant-scoped
`restaurant_id` (`char(26)`, not null, foreign key, indexed first in every composite index) is on every restaurant-owned table: memberships, audit events, and later menu, orders, settings and delivery zones. Also scoped: object-storage keys (`restaurants/{restaurantId}/...`), rate-limit keys for staff endpoints, and the log MDC (`restaurantId`, `userId`, next to today's `requestId`) (Q-25 a). Users and refresh tokens are platform-level rows; a user's restaurant is found through the membership.

### 5.3 Roles and permission matrix

| Action | OWNER | MANAGER | STAFF | PLATFORM_ADMIN |
|---|---|---|---|---|
| Read restaurant (name, slug, link, timezone, currency) | yes | yes | yes | yes (platform path or support read) |
| Edit restaurant name | yes | no | no | yes |
| Correct timezone | no | no | no | yes |
| List team, read audit log | yes | yes | no | yes |
| Invite / change role / deactivate / reactivate / resend invite: STAFF | yes | yes | no | yes |
| Same for MANAGER | yes | no | no | yes |
| Same for OWNER (grant, demote, deactivate) | yes | no | no | yes |
| Own account (`/me/*`) | yes | yes | yes | yes |
| Create restaurants, list all restaurants | no | no | no | yes |

A manager acting on a manager or owner, or granting either role, gets 403 `FORBIDDEN`. Build 4 adds settings rows (proposal A0: the manager edits ordering settings and delivery zones; the PIX key and WhatsApp number are owner-only).

### 5.4 Platform admin support reads (Q-18 b)
1. The platform admin has no membership, so restaurant paths have no restaurant for them by default: they get 403 `FORBIDDEN`.
2. On a `GET` to a restaurant path, the platform admin may send `X-Support-Restaurant-Id: <restaurantId>`; the tenant context then uses that restaurant, read-only (D-15).
3. The header on any other method, or from any other role, is 403 `FORBIDDEN`. The header is never read for non-platform-admin tokens, so it cannot become a cross-tenant hole.
4. Each support read is logged with the MDC (`userId`, `restaurantId`, `support=true`). Q-18 b chose no audit trail, so nothing is written to the audit log.
5. Writes for a restaurant go through `/platform/*` paths only.
6. In the draft spec, `x-roles: PLATFORM_ADMIN_SUPPORT_READ` marks the `GET` operations that accept the header.

### 5.5 Public endpoints
1. `/public/r/{slug}` resolves the slug to a restaurant; an unknown slug is 404 `RESTAURANT_NOT_FOUND`.
2. `/public/*` never reuses a staff query and never returns staff-only fields (LR-4, ADR-0000). Each `/public/*` endpoint has a leak test.

### 5.6 Required tests (inherited by every contract)
1. Cross-tenant test per restaurant endpoint: a member of R2 reads, edits or transitions an R1 resource by id and gets 404; lists never contain R1 rows.
2. Role test per endpoint: each role outside `x-roles` gets 403.
3. Support-read test: the platform admin's `GET` with the header works, a write with it is 403, and a member's request with it is 403.
4. Leak test per `/public/*` endpoint (LR-4).

## Errors

| Code (SCREAMING_SNAKE) | HTTP | When |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | wrong email or password, or inactive user (live) |
| `INVALID_REFRESH_TOKEN` | 401 | missing, expired, revoked or reused refresh token (live, D20) |
| `INVALID_RESET_TOKEN` | 400 | expired or used reset or invite token (live) |
| `INVALID_CURRENT_PASSWORD` | 400 | `POST /me/password` with a wrong current password (new) |
| `PASSWORD_REUSE_ERROR` | 409 | the new password was used before (live, status changes from 400) |
| `EMAIL_TAKEN` | 409 | invite or restaurant creation with an existing email |
| `SLUG_TAKEN` | 409 | the slug exists |
| `SLUG_RESERVED` | 400 | the slug is in the reserved list (validation) |
| `LAST_OWNER` | 409 | demoting or deactivating the last active owner |
| `SELF_ACTION_NOT_ALLOWED` | 409 | changing one's own role or deactivating oneself |
| `INVITATION_NOT_PENDING` | 409 | resending an invite to a member who already set a password |
| `MEMBER_NOT_FOUND` | 404 | unknown member, or a member of another restaurant |
| `RESTAURANT_NOT_FOUND` | 404 | unknown restaurant id (platform) or slug (public) |
| `INVALID_AVATAR` | 400 | avatar not an image or unreadable (live) |
| `FORBIDDEN` | 403 | role not allowed; support header misuse |

Plus the generic codes of API conventions §10.

## Offline and retries

Not applicable: this area creates no orders (ADR-0004). Retried invites fail with `EMAIL_TAKEN`; retried restaurant creation fails with `SLUG_TAKEN` or `EMAIL_TAKEN`.

## Schema notes for Build 1

Build 1 is the single pre-launch re-baseline (ADR-0009): it rewrites the identity tables with ULID keys rather than migrating legacy rows (ADR-0008).

- `restaurants (id char(26) pk, name, slug unique, timezone, currency, created_at, updated_at)`.
- `users (id char(26) pk, email unique, first_name, last_name, phone, password null until set, platform_admin bool, active bool, avatar_key, created_at, updated_at)`; `user_profiles` and `roles` are dropped.
- `memberships (id char(26) pk, restaurant_id fk, user_id fk unique, role varchar check in (OWNER, MANAGER, STAFF), created_at)`.
- `identity_audit_events` gains `restaurant_id` (nullable for platform events), `target_user_id`, `details jsonb`.
- Refresh tokens, password history and reset tokens keep their shape with `char(26)` user ids; invite tokens reuse the reset-token table with a `purpose` column **(Claude)**.
- Seed (dev and tests): the env-seeded platform admin; restaurants R1 and R2, each with an owner, a manager and a staff member (proposal acceptance scenario).

## Acceptance criteria

1. `POST /auth/register` returns 404; the frontend `/register` route is gone.
2. The platform admin creates R1 with an owner; the owner sets a password from the email and `GET /me` shows R1 and `OWNER`.
3. The owner invites a manager and a staff member; a manager inviting an owner or a manager gets 403.
4. Demoting or deactivating R1's only owner gets 409 `LAST_OWNER`; deactivating oneself gets 409 `SELF_ACTION_NOT_ALLOWED`.
5. After a member is deactivated, their next refresh gets 401 and their access ends within 15 minutes.
6. After `POST /me/password`, another browser's refresh gets 401 and the current session continues; the same after `POST /me/sign-out-other-devices`.
7. R2's owner never sees R1's members, audit events or restaurant (cross-tenant tests, §5.6), for every endpoint in this contract.
8. The platform admin reads R1's team with `X-Support-Restaurant-Id`; the same header on a `PATCH` gets 403; a staff token with the header gets 403.
9. `GET /public/r/{unknown}` gets 404 `RESTAURANT_NOT_FOUND`.
10. Logs carry `requestId`, `restaurantId` and `userId` on every request line of a signed-in call.
11. No auth response body contains `refreshToken`; `/auth/refresh` and `/auth/logout` reject a foreign `Origin` with 403 `ORIGIN_NOT_ALLOWED`.
12. The ArchUnit tenant rule runs in `./gradlew test` and fails on a restaurant-owned repository finder without a restaurant id.
13. `docs/api/openapi.yaml` and its drift test exist and cover every operation above (ADR-0010; MP §6 Build 1).

## Graduation log

| Date | Operations | Commit |
|---|---|---|

## Open questions

None in the register. Gabriel may override any **(Claude)** row (D-11, D-13 to D-17, the slug pattern and reserved list, invite expiry, the token claims) when he reviews this contract.

## Sources

- Register `planning/open-questions.md` Q-16..Q-31 (answers 2026-10-01; Q-30 2026-10-06).
- `planning/proposals/settings-and-notifications.md` A0, A1, B1-B6, C1-C5, D2 (Reviewed 2026-10-06).
- Live code: `identity/api/*Controller.java`, `auth/controllers/AuthenticationController.java`, `profile/api/controller/ProfileController.java`, `db/migration/V2__create_users_table.sql`, `V3__insert_roles.sql`, `V15__seed_super_admin.sql`, `application.yml` (`JWT_EXPIRATION_MS` default 86400000).
- BE `docs/superpowers/specs/2026-09-25-restaurant-ops-phase-1-auth-design.md` D16-D24 and "Out of scope, carried forward".
