---
name: backend-module-development
description: Use when adding or changing a module, endpoint, service, entity or DTO in this backend - covers the api/application/domain/persistence layering, cross-module dependency inversion, pagination, the error response shape, and server-side derivation of monetary values.
---

# Building a module in this backend

A modular monolith. The modules are isolated by dependency inversion, not by physical
boundaries — which means nothing stops you importing across them except knowing not to.

## Layering

```
api          controllers + DTOs
  ↓
application  services, mappers
  ↓
domain       entities, domain exceptions
  ↓
persistence  Spring Data repositories
```

Not every existing module has all four packages. New code follows the shape anyway.

## Cross-module dependencies: the consumer owns the interface

When one module needs another's functionality, **the consuming module declares the
interface** and the providing module implements it. Never import across module packages
directly.

The worked example is in the tree already: `orders` needs product data, so
`orders.application.service.ProductService` is an interface **owned by `orders`**, and
`products.application.service.ProductService` implements it. `orders` compiles without
knowing `products` exists.

Getting this backwards is the easy mistake — it looks like unnecessary ceremony right up
until two modules need to move independently.

## Reuse, do not re-implement

| Need | Use |
|---|---|
| The logged-in user's id or email | `identity.application.CurrentUserService` |
| Any list or search response | `common.util.PagedResult` |
| Any error response | `api.errors.ApiErrorResponse` + `GlobalExceptionHandler` / `AuthExceptionHandler` |
| Request correlation in logs | `MDC.get("requestId")`, populated by `security/web/RequestIdFilter` |

Logging precedent: `orders/api/OrderController.java:39-40`.

## Money is derived by the server, never accepted from the client

Never persist a client-supplied total. Compute it from the line items plus any
server-resolved fee. Precedent: `OrderService.calculateTotalAmount`.

The target model makes this sharper: `total` includes a delivery fee the server must resolve
from an **active** zone. A client that sends its own total is either out of date or lying,
and you cannot tell which from the request.

**Price snapshots.** Line items capture the item name and unit price *at order time*. A
later catalogue edit must never rewrite the value of a historical order.

## Authorization

Out of scope here — the controller declares the coarse permission, the service declares
resource ownership, and **neither puts a permission check in a method body**. The rules,
and the defect that proves why, are in the `spring-security-changes` skill. Invoke it rather
than reasoning from what the surrounding code does.

## Before you add or change an endpoint

**This repository owns the API contract** (ADR-0010). Never invoke the retired
`api-contract-sync` skill, and never take a shape from the frozen frontend manifest or the
`docs/contracts/` snapshot.

- **Design first in `docs/api/drafts/`.** A new request or response shape is drafted there as
  OpenAPI next to its prose contract doc, and is implemented only once that contract doc is
  Reviewed. The rules are in `docs/api/drafts/README.md`.
- **The committed `docs/api/openapi.yaml` must match the code.** It arrives in Build 1 with a
  drift test that runs inside `./gradlew test`. From then on, regenerate and commit it in the
  same commit as the endpoint change, and never hand-edit it to make the drift test pass.
  Until then it does not exist: do not create it.

## Conventions

- **Indentation is 4 spaces** in `src/main`. (`src/test` is 2.)
- **Validation** with `@Valid` on request bodies; failures surface through
  `GlobalExceptionHandler` as the `errors` list on `ApiErrorResponse`.
- An entity change and its Flyway migration ship in the **same commit** — see the
  `flyway-migrations` skill. `ddl-auto: validate` means a mismatch stops the application
  from booting.

## Product direction — target, not yet built

The target is a multi-tenant restaurant operations SaaS with one Order Core and one Menu
(ADR-0001, ADR-0002), built in the order Tenant foundation → Menu → Order Core → Storefront ↔
Order Core (ADR-0007) from the contracts in `docs/`. Start at `docs/README.md`.

The legacy `orders`/`products` shape is replaced, not extended (ADR-0008). Neither
`docs/contracts/` nor the 2026-09-20 migration design describes the target. No feature code
for the new architecture until the readiness gate in `docs/roadmap.md` passes.

## New-architecture rules

Only what is Accepted so far:

- **New tables get a ULID primary key** (ADR-0009). Never an `IDENTITY`, `SERIAL`/`BIGSERIAL`
  or sequence-backed key; the application generates the id, and foreign keys reference
  ULIDs. The column type and wire format are open (API conventions): do not pick one.
- **Restaurant-owned rows carry the restaurant id**, and every read and write of them is
  scoped to the caller's restaurant. A missing restaurant context fails closed (ADR-0001).
- **The scoping mechanism comes from the Tenancy & Identity contract**, which is not written
  yet. Do not invent one: no tenant-resolution scheme, no row-level security, and no copy of
  the legacy per-user order ownership.

## Scope discipline

Build what was asked. No speculative flexibility, no abstraction for a second caller that
does not exist, no error handling for cases that cannot occur. Validate at real boundaries —
user input and external calls — and trust internal guarantees elsewhere.
