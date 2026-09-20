# Claude tooling + restaurant-ops migration — design

Date: 2026-09-20
Status: approved (design); implementation plan to follow

## Problem

Two problems, one root cause.

**1. No guardrails.** Work on this backend is driven by Claude Code, but the repository ships
no project-specific skills, agents or hooks. Every session re-derives the conventions from
scratch, and drift follows. Concrete evidence found while writing this design:

- Authorization is implemented three different ways for the same job — `@PreAuthorize` with an
  ownership helper on the service (`OrderService:79/118/167`), `@PostAuthorize` on the
  controller (`OrderController:46`), and hand-written `if` blocks inside controller method
  bodies (`OrderController:92`, `OrderController:112`). `CLAUDE.md` already declared the first
  one canonical; the drift happened regardless, because nothing enforced it.
- That hand-written style produced a real defect. `isAdmin()` in both `OrderService:209` and
  `OrderController:124` reads the raw authority list, which the role hierarchy never touches —
  `RoleHierarchy` is applied by `methodSecurityExpressionHandler`, so it only affects
  `@PreAuthorize`. A `SUPER_ADMIN` therefore fails `equals("ROLE_ADMIN")` and is silently
  demoted: on `GET /orders` and `GET /orders/search` they see only their own orders.
- `JwtAuthenticationFilter:66-75` fabricates `SCOPE_order:*` authorities from the user's role.
  They look like OAuth2 scopes but are not — they are not claims and carry no independent
  meaning. `SCOPE_order:write` is granted to every authenticated user, so
  `hasRole('ADMIN') or hasAuthority('SCOPE_order:write')` never denies anyone.

**2. The backend and the frontend are building different products.** The frontend's
`2026-09-14-backend-integration-manifest.openapi.yaml` (v0.2.0) describes a **restaurant
operations** system. This backend is a generic **B2B order suite**. The frontend is running on
mock data for Menu, Tables, Orders and the public storefront, waiting on a backend that does
not exist yet.

## Scope

This design covers the tooling (built now) and the migration plan (written now, executed
later). It does **not** cover executing the migration.

| Deliverable | Built in this pass |
|---|---|
| 5 project skills, 4 agents, 1 hook, `security-guidance` plugin | yes |
| Structural fix of the `orders` authorization drift | yes |
| Contract snapshot, `CLAUDE.md` update, `.gitignore` change | yes |
| Restaurant-ops migration | plan document only |

## Decisions taken

Recorded so later sessions do not relitigate them.

| # | Decision | Rationale |
|---|---|---|
| D1 | `.claude/` is versioned; only `settings.local.json` stays ignored | Skills are how the repo is worked on — they belong to the repo, not to one machine |
| D2 | Authorization lives in `@PreAuthorize`, never in a method body | Hand-written checks bypass the role hierarchy; `isAdmin()` is the proof |
| D3 | Indentation: 4 spaces in `src/main`, 2 in `src/test` | Formalizes the de-facto split (main 82:33, test 33:4) — zero reformatting |
| D4 | Skills teach the **target** model, marking legacy explicitly | Otherwise new code is born obsolete during the migration |
| D5 | Verification is a full `./gradlew test` before any completion claim | Testcontainers; Docker required |
| D6 | Questions are asked in the user's language; code and docs are always English | Existing repo convention |
| D7 | The `/orders` permission matrix is frozen; only the `isAdmin()` defect changes | The frontend contract must not shift under a refactor |
| D8 | Auth migrates early, backward-compatible | See "Roadmap ordering" |

## Part 1 — Tooling

### Skills (`.claude/skills/`)

Each is one technique, `description` starting with "Use when", written in English.

**`writing-backend-tests`** — `*Test` is Mockito-only, no Spring context; `*IT` uses the
`@IntegrationTest` composite with MockMvc and a **real login against `/auth/login`**, not
`@WithMockUser`. Naming `method_scenario_expectedOutcome`; AssertJ with `.as(...)` descriptions;
unique emails via `UUID`; `MutableClock` for anything time-dependent; 2-space indentation.
Mandatory coverage rules: every resource-scoped endpoint gets a denied-access test, and every
`/public/*` endpoint gets a leak test.

**`spring-security-changes`** — a map of what exists (filter chain order, role hierarchy, rate
limiting, JWT issuance) plus the obligation to ask detailed questions *before* changing
anything. Encodes D2 with the `isAdmin()` defect as the worked example. Covers the target
surface: HttpOnly refresh cookie, rotation with family revocation on reuse, `Origin` validation
as the CSRF defense, and the `/public/*` threat model.

**`backend-module-development`** — layering `api → application → domain → persistence`;
cross-module dependency inversion (the interface is declared by the *consumer*); `PagedResult`;
`ApiErrorResponse`; `CurrentUserService`; 4-space indentation. Adds the rule that the server
derives every monetary value and never trusts a client-supplied total.

**`flyway-migrations`** — `V<N>__name.sql` numbering without collision, JPA entity and schema
kept in sync (`ddl-auto: validate` means divergence stops boot), never edit an applied
migration, and the enum-rename-without-data-loss pattern that phase 4 needs.

**`api-contract-sync`** — check the manifest before adding or changing an endpoint; when the
backend must diverge, report it back to the canonical copy following the manifest's own
`x-maintenance` rules (patch never rewrite, bump `info.version`, add an `x-changelog` entry).

### Agents (`.claude/agents/`)

All four carry the same standing instruction: **evolve from what exists; improving the
structure is allowed, breaking what works is not.**

| Agent | Tools | Role |
|---|---|---|
| `spring-security-reviewer` | read-only | Audits authz, JWT, rate limiting and `/public/*` leakage. Reports, never edits. |
| `backend-test-writer` | read/write + Bash | Writes `*Test`/`*IT` to the house pattern and runs the suite. |
| `backend-feature-builder` | read/write + Bash | Implements a module or endpoint end to end, invoking the module, security and contract skills. |
| `flyway-migration-author` | read/write + Bash | Writes migrations and keeps JPA and schema in sync. |

### Hook + plugin

A `PreToolUse` hook on Edit/Write matching `security/**`, `auth/**`, `SecurityConfig`,
`JwtAuthenticationFilter`, `SecurityFilterChain` or `*RateLimit*` injects a reminder to invoke
`spring-security-changes` and ask before changing. The official **`security-guidance`** plugin
is installed alongside it — it is hook-based, so it fires whether or not a skill was invoked.

## Part 2 — `orders` structural fix

Removed: `@PostAuthorize`, the hand-written `if` checks, and the fabricated `SCOPE_order:*`
authorities. Fixed: `isAdmin()` now resolves through the role hierarchy in both files.

The permission matrix is **frozen** (D7). Note the third column — freezing requires an explicit
`hasRole('ADMIN')` on `deleteOrder`, because deleting the scope would otherwise widen it:

| Endpoint | Effective today | After |
|---|---|---|
| `POST /orders` | any authenticated | `isAuthenticated()` — unchanged |
| `GET /orders/{id}` | owner or ADMIN | unchanged |
| `PUT /orders/{id}` | owner or ADMIN | unchanged |
| `DELETE /orders/{id}` | ADMIN only (via the scope) | `hasRole('ADMIN')` — unchanged behavior, now stated |
| `GET /orders`, `/orders/search` | ADMIN all, USER own | unchanged, **except** SUPER_ADMIN now correctly sees all |

The SUPER_ADMIN correction is the single intentional behavior change, and it is a defect fix.
Tests freeze the whole matrix so the migration cannot shift it by accident.

## Part 3 — Restaurant-ops migration plan

### The gap

| | Backend today | Manifest target |
|---|---|---|
| `OrderStatus` | `PENDING/PROCESSING/SHIPPED/DELIVERED/CANCELLED` | `New/Preparing/Ready/Completed/Cancelled` — breaking rename, new state machine |
| Order owner | `customerId` → `User` (FK, non-null) | anonymous `customerName` + `customerPhone`; customers never have accounts |
| Catalog | `Product`, `sku` required | `MenuItem`: category, image, available (86'd), sizes, addons — no `sku` |
| New resources | — | `Tables`, `MenuCategories`, `DeliveryZones`, `RestaurantSettings` |
| Public surface | none | `/public/menu-items`, `/public/tables/{id}`, `/public/restaurant-settings`, `POST /public/orders`, `GET /public/orders/{id}?phone=` |
| Auth | refresh token in the body, stored in `localStorage` | refresh in an HttpOnly cookie, rotation with family revocation, extra JWT claims, CORS with credentials |
| Totals | client sends `totalAmount` | server derives total, ETA and payment status; forces `channel=Online` on the public endpoint |

The consequence that outlives everything else: **`isOrderOwner` ceases to exist.** In the target
model the back office is staff-only (one restaurant, all staff see all orders) and customer
access runs through the phone-scoped public lookup.

### Roadmap ordering

The ordering criterion is **minimizing rework**, and one fact decides it: every `*IT` logs in
for real against `/auth/login`. If the auth contract changes after the feature phases, dozens of
integration tests get rewritten. So auth moves early — and is built **backward-compatible**
(the cookie is issued *and* the body field is kept; refresh accepts either source), which
removes the need for a synchronized flip with the frontend.

| Phase | Content | Why here |
|---|---|---|
| 0 | Foundation: resolve `id-type`, `created-at-format`, `category-identity`, `dev-cookie-secure`; land the contract snapshot | Every later schema depends on these; deciding late means reworking all of them |
| 1 | Auth target, backward-compatible: HttpOnly cookie, rotation with family revocation, new JWT claims, CORS + `Origin` | Every test written afterwards is born in the final shape; backward compatibility removes the coordinated flip |
| 2 | Catalog: `MenuCategories` + `MenuItem` grown out of `products` | Most isolated slice; unblocks the frontend Menu screen and is the first real exercise of the new skills |
| 3 | `Tables`, `DeliveryZones`, `RestaurantSettings` | Simple CRUDs, mutually independent — parallelizable across subagents |
| 4 | Orders restaurant model: enum rename, new state machine, anonymous customer, channel/fulfillment/payment, server-derived totals | Largest phase; depends on 2 and 3 |
| 5 | `/public/*` surface: rate limiting, phone-as-credential without enumeration, no 86'd items or table roster leakage | Built last, against a model that has stopped moving |
| 6 | Cleanup: drop `refreshToken` from the response body, remove the remaining legacy | Coordinated with the frontend, once it has flipped |

### Open decisions inherited from the manifest

`id-type` (int64 vs string), `category-identity` (name vs id), `created-at-format` (date-only vs
datetime + timezone), `payment-sequencing` (processor TBD), `settings-images` (upload endpoint
needed), `dev-cookie-secure` (Secure flag in local HTTP). Phase 0 resolves the four that block
schemas; the other two are external dependencies.

## Contract ownership

The manifest is canonical in the **frontend** repository. `docs/contracts/` holds a snapshot,
headed with its source path, version and the re-sync rule. When the backend must diverge, the
`api-contract-sync` skill requires reporting it back rather than editing the snapshot silently.

## Testing

Unit tests for the `isAdmin()` hierarchy fix; integration tests freezing the full `/orders`
permission matrix, including a SUPER_ADMIN case that fails against today's code. Skills and
agents are verified by using them — phase 2 is the first real exercise. Every completion claim
requires a full `./gradlew test`.
