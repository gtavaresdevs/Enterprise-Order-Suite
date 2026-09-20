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

Logging precedent: `orders/api/OrderController.java:38-39`.

## Money is derived by the server, never accepted from the client

Never persist a client-supplied total. Compute it from the line items plus any
server-resolved fee. Precedent: `OrderService.calculateTotalAmount` (line 200).

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

Invoke the `api-contract-sync` skill. The endpoint shape is owned by the frontend's manifest,
not by this repository.

## Conventions

- **Indentation is 4 spaces** in `src/main`. (`src/test` is 2.)
- **Validation** with `@Valid` on request bodies; failures surface through
  `GlobalExceptionHandler` as the `errors` list on `ApiErrorResponse`.
- An entity change and its Flyway migration ship in the **same commit** — see the
  `flyway-migrations` skill. `ddl-auto: validate` means a mismatch stops the application
  from booting.

## Product direction — target, not yet built

This backend is documented as a B2B order suite. **It is becoming a restaurant operations
system**, and the frontend is already running that model on mock data.

Name new modules for the restaurant domain — `menu`, `tables`, `settings` — rather than
extending `products`/`orders` semantics that are scheduled for replacement. `Product` becomes
`MenuItem`; orders come to belong to anonymous customers rather than `User` accounts.

Check `docs/contracts/backend-integration-manifest.openapi.yaml` and
`docs/superpowers/specs/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md`
before extending the legacy shape. Code written against the old model during the migration
is born obsolete.

## Scope discipline

Build what was asked. No speculative flexibility, no abstraction for a second caller that
does not exist, no error handling for cases that cannot occur. Validate at real boundaries —
user input and external calls — and trust internal guarantees elsewhere.
