# Restaurant-ops migration, Phase 0 — Foundation design

Date: 2026-09-24
Status: approved (design); implementation plan to follow

## Problem

`2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` approved a seven-phase
migration from the B2B order suite to a restaurant operations system, and made Phase 0 the
foundation: *resolve `id-type`, `created-at-format`, `category-identity` and
`dev-cookie-secure`; land the contract snapshot*. Those four are `x-open-decisions` entries in
the frontend's backend-integration manifest. Every later phase's schema depends on them, so
deciding them late means reworking all of them.

One further item joins Phase 0 by the user's decision on 2026-09-24. The security audit
recorded in `../plans/2026-09-20-test-failures-and-audit-followups.md` left one finding
unfixed: `OrderService.updateOrder` accepts an item replacement on an order in **any** status,
so `PUT {"status":"DELIVERED","items":[]}` on a delivered order wipes its lines and rewrites
`totalAmount` to 0, with no `OrderHistory` row. Stock is already protected; the order record is
not. The fix has to name a set of statuses, and Phase 0 is where the status model is being
settled, so it lands here rather than waiting for Phase 4's enum rename.

## Findings that shaped the decisions

Established by reading both repositories on 2026-09-24, and worth recording because two of them
contradict what the manifest's own `x-open-decisions` text assumes.

1. **The snapshot is already clean.** `docs/contracts/backend-integration-manifest.openapi.yaml`
   is byte-identical to the canonical copy at
   `enterprise-order-suite-frontend/order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml`,
   both v0.2.0. Phase 0's "land the contract snapshot" item is therefore already satisfied; what
   remains is the decisions and the patch that records them.
2. **`id-type` is barely open.** The frontend's types already split by backing, not by
   preference: everything wired to the live backend (`src/types/administration.ts`,
   `src/types/profile.ts`) uses `id: number`, and only the mock-backed types
   (`menu.ts`, `orders.ts`, `tables.ts`, `settings.ts`) use `id: string`. The string ids are an
   artifact of the mock generator's `DEL-2026-8123` format, not a frontend requirement.
3. **`created-at-format` has teeth.** `src/features/home/services/home.service.ts` compares
   `createdAt` with `===`, `>` and `.startsWith("YYYY-MM")`, and
   `src/features/analytics/services/analytics.service.ts` keys a `Map` on the raw value. A full
   ISO datetime does not throw there — it silently yields zero.
4. **A status-only update omits `items` entirely.** Every existing `PUT /orders/{id}` in
   `OrderControllerIT` that changes only status builds an `OrderUpdateRequest` with no `items`
   at all, and `OrderUpdateRequest.items` carries no `@NotNull`. Rejecting a *present* `items`
   payload on a closed order therefore breaks nothing in the current suite.
5. **Error codes already disagree.** The backend emits `SCREAMING_SNAKE`
   (`INVALID_STATUS_TRANSITION`, `GlobalExceptionHandler:60`) and the frontend consumes that
   from live endpoints today. The manifest's newer, mock-derived codes are camelCase
   (`categoryNameRequired`, `categoryNameTaken`, `categoryInUse`).

## Scope

Phase 0 settles decisions; it does not implement the resources those decisions describe.
Implementing them now would mean implementing them twice, because each belongs to a phase that
does not exist yet.

| Deliverable | In Phase 0 |
|---|---|
| The five decisions, recorded with rationale | yes |
| Canonical manifest patched to 0.3.0; snapshot re-copied | yes |
| Item-edit boundary enforced in `OrderService`, with tests | yes |
| Refresh-cookie / CORS configuration | no — Phase 1 |
| `MenuCategory` table, `MenuItem` | no — Phase 2 |
| `RestaurantSettings`, `DeliveryZones`, `Tables` | no — Phase 3 |
| `businessDate` field, `OrderStatus` rename, anonymous customer | no — Phase 4 |

## Decisions taken

Recorded so later phases do not relitigate them. Numbering continues from the parent design's
D1–D8.

| # | Decision | Lands in |
|---|---|---|
| D9 | `id-type`: the backend keeps `int64`; the frontend's four mock-backed types become `number` | 2–4 |
| D10 | `created-at-format`: `createdAt` is a UTC instant; the server stamps a `businessDate` at creation | 4 |
| D10a | The restaurant's timezone is admin-editable in `RestaurantSettings`; config is only its fallback | 3 |
| D11 | `category-identity`: normalized table with an FK, name-based wire contract | 2 |
| D12 | `dev-cookie-secure`: env-bound cookie and CORS properties, production-safe defaults | 1 |
| D13 | Item edits are accepted only while an order is open | **0** |
| D14 | Error codes are `SCREAMING_SNAKE` everywhere; the manifest's three camelCase codes are corrected | 2 |

### D9 — `id-type`: int64, and the frontend adapts

New resources (`MenuItem`, `Table`, `DeliveryZone`) expose the numeric primary key they already
have in `BaseEntity`. `Order` exposes both its numeric `id` and its existing unique
`orderNumber` string, which is what the mock's `DEL-2026-8123` was really serving: a
human-readable key for display and for the `/track-order` lookup.

Rejected: exposing string ids everywhere. It would leave the frontend with two id conventions
permanently, because the live `/users` and `/me/profile` endpoints would keep returning numbers.
Rejected: opaque public ids (UUID or slug). The enumeration concern is real once `/public/*`
exists, but Phase 5's defence is phone-scoping, not id opacity, and a second identifier on every
resource is a cost without a matching benefit today.

**Consequence for the frontend:** `src/types/{menu,orders,tables,settings}.ts` change `id:
string` to `id: number`. This is mechanical and has to happen when those services come off
mocks regardless.

### D10 — `created-at-format`: instant plus a server-derived business date

`Order.createdAt` becomes a UTC instant. Alongside it the server returns `businessDate`, the
`YYYY-MM-DD` the order belongs to, computed in the restaurant's timezone.

The instant is what the kitchen display, prep-time measurement and same-day ordering need, and
it cannot be recovered later if the API only ever sends a date. The derived field is what keeps
Home and Analytics correct: they switch their comparisons from `createdAt` to `businessDate` and
are otherwise unchanged. Deriving the date on the server rather than in the browser is the point
— a browser in a different timezone from the restaurant would otherwise attribute late-evening
orders to the wrong day, silently.

**The timezone is a setting the admin owns, not a deployment detail.** It belongs in
`RestaurantSettings` next to `brandColor` and `whatsappNumber`, and is editable from the back
office — the restaurant may not be where the server is, and the business may open somewhere
else later. Phase 3 builds that field; `RestaurantSettings.timezone` is therefore an additive
change to this phase's manifest patch, not a Phase 3 discovery.

Because `RestaurantSettings` does not exist until Phase 3 while `businessDate` is not returned
until Phase 4, the configuration property `restaurant.timezone` exists as the **fallback only**,
resolved in this order: the settings row, then the property, then `ZoneId.systemDefault()`.

Defaulting to the system zone rather than to a hardcoded `America/Sao_Paulo` keeps a
single-country assumption out of the code, and is right on a developer machine. It is *not*
right on a typical container or VPS, which reports UTC — so the resolved zone and **where it came
from** are logged at startup, following the precedent set by the `SUPER_ADMIN_EMAIL` warning in
the previous phase. A default that is wrong is tolerable; one that is wrong and silent is not.

Keeping the zone in a settings row rather than in configuration is also what makes a future
multi-tenant move cheap: the field moves to the tenant, and nothing else changes. That move is
not designed for here — the parent design's model is one restaurant.

**An order's business date is stamped when the order is placed.** `business_date` is a stored,
indexed column, computed once at creation from the timezone then in force, and never recomputed.
Changing the setting affects orders placed afterwards and leaves earlier ones alone, so a day
whose totals were already reported does not move. This is how the order already treats every
other value that can change underneath it — `OrderLine.unitPrice` and `Order.deliveryZone` are
both captured at order time. Deriving the date on each read would have been simpler, but it
re-dates an order placed near midnight whenever the setting changes, retroactively.

No phase computes the business date client-side.

### D11 — `category-identity`: id in the database, name on the wire

`menu_categories(id, name UNIQUE)` with `menu_items.category_id` as a foreign key. The API is
exactly what the manifest already specifies: the category **name** in `MenuItem.category`,
`/menu-categories/{name}` as the path, name in and name out.

This is the manifest's own suggestion ("consider adding a stable id later") taken now instead of
as a later migration. A rename becomes a single-row `UPDATE` that every item follows for free,
rather than a bulk rewrite inside a transaction, and the `409` in-use case becomes a
foreign-key constraint rather than a scan.

**`MenuCategory` keeps a name-only wire shape.** Its id is an implementation detail; exposing it
would contradict the path contract and serve no consumer — the frontend's menu service maps
categories to a plain string list. D9's "numeric ids" applies to resources the client addresses
by id, which categories are not.

### D12 — `dev-cookie-secure`: env-bound, production-safe by default

Phase 1 moves the refresh token into an HttpOnly cookie. Its attributes and the CORS origin
become configuration, bound from the environment like every other setting in this repository
(`jwt:`, `storage:`, `app.email:`):

```
security.refresh-cookie.secure     ${REFRESH_COOKIE_SECURE:true}
security.refresh-cookie.same-site  ${REFRESH_COOKIE_SAME_SITE:Lax}
security.cors.allowed-origins      ${CORS_ALLOWED_ORIGINS:http://localhost:3000}
```

The defaults are the production-safe ones, so a missing configuration breaks local development
loudly instead of shipping an insecure cookie silently. Local configuration opts out of `Secure`
explicitly, which is a visible, reviewable act.

Rejected: deriving the flag from the active Spring profile. It couples a security property to
profile selection, so any environment booting the local profile for an unrelated reason
downgrades the cookie without saying so. Rejected: requiring HTTPS in development via mkcert —
real setup friction for every clone, to solve what a flag solves.

`SecurityConfig.corsConfigurationSource` currently hardcodes `http://localhost:3000` and does not
set `allowCredentials`. Both change in Phase 1, not here.

### D13 — the item-edit boundary (implemented in this phase)

`OrderService` already has one predicate, `holdsSettleableStock`, deciding whether item movements
touch stock: true for `PENDING` and `PROCESSING`, false for `SHIPPED`, `DELIVERED` and
`CANCELLED`. That predicate becomes the single notion of *the order is still open*, and gains a
second consequence: an item payload submitted against an order that is not open is **rejected**
rather than silently applied.

Reusing the stock boundary rather than inventing a stricter `PENDING`-only one keeps a single
concept in the service instead of two similar-looking predicates that can drift apart. It also
maps cleanly across Phase 4's rename — `New` and `Preparing` stay open, `Ready`, `Completed` and
`Cancelled` do not — and it matches a kitchen, where an order being prepared can still have a
drink added to it.

Rejected: allowing edits everywhere but writing an audit row. It leaves a delivered order's
total rewritable to zero, which is the actual complaint, and expands `OrderHistory` beyond status
transitions.

### D14 — one error-code convention

The backend keeps `SCREAMING_SNAKE`, because that is what it already emits and what the frontend
already maps from live endpoints. The manifest's three camelCase category codes are corrected to
`CATEGORY_NAME_REQUIRED`, `CATEGORY_NAME_TAKEN` and `CATEGORY_IN_USE` as part of this phase's
patch, so Phase 2 implements one convention rather than two.

## Contract work

The manifest is canonical in the frontend repository; `docs/contracts/` holds a snapshot. The
`api-contract-sync` skill requires patching the canonical copy rather than editing the snapshot,
following the manifest's own `x-maintenance` rules: patch only what the decisions touch, leave
every other line byte-identical, bump `info.version`, add an `x-changelog` entry at the top.

Edits to the canonical file:

- `x-open-decisions`: flip `id-type`, `category-identity`, `created-at-format` and
  `dev-cookie-secure` to `status: decided-2026-09-24`, each carrying its resolution. Per the
  maintenance rules a superseded decision is never deleted, only flipped and reflected in the
  affected schema in the same change.
- `MenuItem.id`, `Table.id`, `DeliveryZone.id`, `Order.id`: `type: string` becomes
  `type: integer, format: int64`.
- `Order.orderNumber`: added, `type: string`, described as the human-readable display and
  tracking key.
- `Order.createdAt`: `type: string, format: date-time`, its open-decision prose replaced by the
  resolution.
- `Order.businessDate`: added, `type: string, format: date`, carrying the note that Home and
  Analytics compare **this** field, not `createdAt`, and that it is fixed at creation.
- `RestaurantSettings.timezone`: added, `type: string`, an IANA zone identifier, admin-editable.
  Described as the zone each order's `businessDate` is stamped in, so a reader understands that
  editing it changes future orders only.
- `MenuCategory`: description records that a stable id backs the name internally while the wire
  contract stays name-based.
- `Error.code`: the three known values re-cased per D14.
- `info.version` to **0.3.0**, with an `x-changelog` entry at the top carrying an explicit
  `breaking:` line for the id-type change. This follows the file's own precedent — 0.2.0 recorded
  a breaking auth change under a minor bump.

The snapshot in `docs/contracts/` is then re-copied whole and its version row updated, per
`docs/contracts/README.md`.

**Delivery.** The canonical file is edited in place in the frontend repository but **not
committed there** — that repository is public, is on branch `Claude-Assisted-Development`, and
carries unrelated uncommitted work. The owner reviews and commits it. The backend repository
commits its own snapshot and this spec.

## Implementation — the only code change

In `orders`:

- `holdsSettleableStock` is renamed to express the single concept it now carries, with its
  comment covering both consequences: stock movement and editability.
- `updateOrder` rejects the request when `request.getItems() != null` and the order is not open.
  The `items == null` path is untouched, so status-only updates on a closed order keep working —
  finding 4 above is what makes this safe.
- New `OrderNotEditableException` in `orders.domain.exception`, alongside
  `InvalidStatusTransitionException`.

In `api.errors`:

- `GlobalExceptionHandler` maps it to **409 Conflict**, code `ORDER_NOT_EDITABLE`. 409 rather
  than 400 because the payload is well-formed; it is the order's state that refuses it. The
  handler follows the shape of `handleInvalidStatusTransition` exactly, and the existing advice
  ordering (`AuthExceptionHandler` `@Order(1)` with no catch-all, `GlobalExceptionHandler`
  `@Order(2)` owning the fallback) is not touched.

No migration, no entity change, no endpoint or request-shape change. The response gains no field;
one previously-accepted request now returns 409.

## Testing

Tests are written before the fix and must fail before it, per the repository's convention.
`writing-backend-tests` governs their shape.

`OrderServiceTest` (Mockito, no Spring context):

- replacing items throws `OrderNotEditableException` on `SHIPPED`, on `DELIVERED` and on
  `CANCELLED`, and no stock call is made
- replacing items still succeeds on `PENDING` and on `PROCESSING`
- a status-only update on a `DELIVERED` order is unaffected

`OrderControllerIT` (`@IntegrationTest`, real login):

- `PUT {"status":"DELIVERED","items":[]}` on a delivered order returns 409 **and** the order's
  `items` and `totalAmount` are unchanged when re-read. Asserting the body, not only the status,
  is deliberate: the silent zeroing of `totalAmount` is the defect, and a status-only assertion
  would not have caught it.
- the error body carries `code: ORDER_NOT_EDITABLE`
- a status-only `PUT` on a delivered order still returns 200, proving the narrowing did not
  overreach

Existing `OrderControllerIT` item-replacement tests all operate on `PENDING` orders and must pass
unchanged; that is the evidence the boundary was placed where intended.

## Verification

A full `./gradlew test` (Docker required), expected green at 213 plus the new cases. Per D5 of
the parent design, no completion is claimed on a partial run.

## Risks

- **The frontend is not yet aware of the 409.** Nothing in the frontend edits a delivered
  order's items today, so no screen breaks, but the manifest patch is what tells it. Recorded in
  the changelog rather than left to discovery.
- **The fallback timezone follows the machine, which is UTC in most deployments.** Until an admin
  sets the zone in `RestaurantSettings`, `businessDate` can be off by a day at the edges for a
  restaurant whose server is elsewhere. The startup log naming the resolved zone and its source
  is the mitigation; setting the zone is a first-run step, like `SUPER_ADMIN_EMAIL`.
- **Orders placed before the zone is corrected keep their stamped date.** That is the intended
  behaviour — dates do not move retroactively — but it means fixing the setting does not fix
  already-filed orders. Worth knowing during the first days of a deployment, when the volume of
  wrongly-stamped orders is small and a manual correction is still cheap.
- **0.3.0 carries a breaking id-type change under a minor bump.** Defensible by the file's own
  precedent, and the affected consumers are all still mock-backed, but it means the version
  number alone does not warn a reader. The `breaking:` line in the changelog entry does.

## Out of scope, carried forward

- `payment-sequencing` and `settings-images` stay open in the manifest. Both depend on external
  choices (a payment processor; an upload endpoint) and neither blocks a schema, exactly as the
  parent design recorded.
- Rotating the seeded super admin password was waived by the user on 2026-09-24 as a
  local-development credential. It must be revisited before any deployment that uses a real one.
