# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Enterprise-grade B2B order management backend: Java 17, Spring Boot 3, PostgreSQL (Flyway-migrated), JWT auth. Base package: `com.enterprise.ordersuite`. Built as a modular monolith — modules under `src/main/java/com/enterprise/ordersuite/` (`auth`, `identity`, `orders`, `products`, `profile`, `security`, `storage`, `notifications`, `common`, `api`) are isolated by dependency inversion (see Architecture below), not by physical service boundaries.

## Working in this repository

Project skills live in `.claude/skills/` and are versioned. Invoke them; do not re-derive
the conventions.

| Skill | Invoke before |
|---|---|
| `writing-backend-tests` | writing or changing any test |
| `spring-security-changes` | touching `security/`, `auth/`, any `@PreAuthorize`, JWT or rate limiting |
| `backend-module-development` | adding a module, endpoint, service, entity or DTO |
| `flyway-migrations` | adding a migration or changing an entity's schema |
| `api-contract-sync` | creating or changing any endpoint or payload shape |

Agents in `.claude/agents/`: `spring-security-reviewer` (read-only audit),
`backend-test-writer`, `backend-feature-builder`, `flyway-migration-author`.

A `PreToolUse` hook warns before edits to security-sensitive files. It advises, never blocks.

**Verification:** a full `./gradlew test` (Docker required) before claiming anything works.

**Language:** ask the user questions in the language they are writing in; write all code,
comments and documentation in English.

## Commands

This project uses **Gradle**, not Maven — ignore the Maven instructions in README.md/HELP.md, they're stale.

```bash
# Build
./gradlew build

# Run locally (uses application-local.yml, requires .env — see Environment below)
./gradlew bootRun

# Run all tests (JUnit 5 via Testcontainers — requires Docker running)
./gradlew test

# Run a single test class
./gradlew test --tests "com.enterprise.ordersuite.orders.application.service.OrderServiceTest"

# Run a single test method
./gradlew test --tests "com.enterprise.ordersuite.orders.api.OrderControllerIT.createOrder_shouldReturn201"

# Flyway (migrations live in src/main/resources/db/migration, applied automatically on boot via ddl-auto: validate)
./gradlew flywayMigrate
./gradlew flywayInfo
```

There is no separate lint task configured; rely on compilation and tests.

## Environment

Config is env-var driven (`spring-dotenv` loads `.env` automatically; see `.env.example` for the minimal required set: `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`). `build.gradle` also reads `.env` directly to configure the Flyway Gradle plugin outside of Spring's context. Never hardcode secrets — extend `.env`/`.env.example` and reference via `${VAR}` in `application.yml`, following the existing pattern (see `storage:`, `jwt:`, `app.email:` blocks).

Tests do **not** use `.env`/local Postgres — they spin up real containers via Testcontainers (see Testing below), so integration tests are safe to run without a manually configured DB and never hit production.

## Architecture

**Layering per module**: `api` (controllers + DTOs) → `application` (services, mappers) → `domain` (entities, domain exceptions) → `persistence` (Spring Data repositories). Not every module has all four packages, but new code should follow this shape when adding to a module.

**Module isolation via Dependency Inversion**: `orders` depends on a `ProductService` *interface* it defines itself (`orders.application.service.ProductService`), not on the `products` module directly. The real implementation lives in `products.application.service.ProductService` and implements the orders-module interface. This is the established pattern for cross-module dependencies — when one module needs another's functionality, define the contract in the consuming module and have the providing module implement it, rather than importing across module packages directly.

**Reuse existing infrastructure** rather than re-implementing:
- `identity.application.CurrentUserService` — get the logged-in user (id/email) from the security context.
- `common.util.PagedResult` — pagination wrapper for search/list endpoints.
- `api.errors.ApiErrorResponse` + `GlobalExceptionHandler`/`AuthExceptionHandler` — standard error response shape (`code`, `message`, `timestamp`, optional `errors` list for validation failures).

**Authorization model**: Spring Security method security (`@PreAuthorize`) is the primary enforcement point, not just URL-level rules in `SecurityConfig`. Role hierarchy: `ROLE_SUPER_ADMIN > ROLE_ADMIN > ROLE_USER` (defined in `SecurityConfig.roleHierarchy()`). Resource-ownership checks (e.g. a user can only touch their own orders) are implemented as helper methods on the service itself referenced from the SpEL expression — see `OrderService.isOrderOwner` used via `@PreAuthorize("hasRole('ADMIN') or @orderService.isOrderOwner(#id, principal.id)")`. Follow this pattern for any new resource-scoped endpoint rather than filtering in the service body. Multi-tenant list/search endpoints (e.g. `OrderService.searchOrders`/`getAllOrders`) additionally force-filter by the current user's ID for non-admins at the query level.

Authorization is **only** expressed in `@PreAuthorize`. A manual check inside a method body
bypasses the role hierarchy — `RoleHierarchy` is applied by `methodSecurityExpressionHandler`,
so it affects annotations and not a raw `getAuthorities()` call. `OrderController` currently
violates this and is scheduled for correction; do not copy it.

**Order domain**: `Order` has a state machine enforced on the entity itself (`Order.transitionTo`, throws `InvalidStatusTransitionException` for illegal transitions) — every status change must go through it, and `OrderService.updateOrder` records an `OrderHistory` row (previous status, new status, who, when) on every transition and triggers a notification. Creating/cancelling an order also mutates `Product` stock via `ProductService.decrementStock`/`incrementStock` — stock and order state are meant to stay consistent, so don't bypass `OrderService` to touch stock directly.

**Auth**: JWT-based, stateless (`SessionCreationPolicy.STATELESS`), with refresh tokens (`RefreshToken`, `RefreshTokenService`, `RefreshTokenCleanupScheduler`), password reset flow with history tracking (`PasswordHistory` prevents password reuse), and per-endpoint rate limiting (`AuthRateLimitFilter` + `RateLimiter` implementations, configurable/toggleable via `security.rate-limit.*` properties, backed by `InMemoryBucketedSlidingWindowRateLimiter` or a `NoOpRateLimiter` when disabled).

**Storage**: `storage.ObjectStorageService` abstracts S3-compatible object storage (AWS S3 in prod, MinIO in tests/local — see `ObjectStorageConfig`), currently used for profile avatars (`profile.application.service.AvatarImageProcessor`/`AvatarValidator`).

## Product direction — read before adding features

This backend is documented above as a B2B order suite. **It is becoming a restaurant
operations system.** The frontend already runs that model on mock data and is waiting on it.

- Contract snapshot: `docs/contracts/backend-integration-manifest.openapi.yaml`
  (canonical copy lives in the frontend repo — see `docs/contracts/README.md`)
- Design and migration phases:
  `docs/superpowers/specs/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md`

What this changes, in short: `OrderStatus` is renamed to `New/Preparing/Ready/Completed/
Cancelled`; orders belong to **anonymous customers** (name + phone) rather than to a `User`,
so `isOrderOwner` disappears and the back office becomes staff-only; `Product` grows into
`MenuItem`; `Tables`, `MenuCategories`, `DeliveryZones` and `RestaurantSettings` are new; and
an unauthenticated `/public/*` surface appears.

Do not write new code in the legacy shape without checking the contract first.

## Testing

Integration tests use the `@IntegrationTest` composite annotation (`support.IntegrationTest`), which wires up `@SpringBootTest` plus real **Testcontainers** for Postgres (`support.PostgresTestContainerConfig`, via `@ServiceConnection` — no manual datasource wiring needed) and MinIO (`support.MinioTestContainerConfig`) for storage tests. This guarantees tests never touch a real/prod database or bucket. Prefer `@IntegrationTest` over hand-rolling `@SpringBootTest` for anything hitting the DB, security layer, or storage.

Naming convention: `*Test` for plain unit tests (Mockito-style, no Spring context), `*IT` for integration tests that need the full context/containers.

`support.MutableClock` is used where tests need to control `Instant.now()`/`Clock` behavior (e.g. rate limit windows, token expiry) — inject `Clock` rather than calling `Instant.now()` directly in new code that needs to be tested this way (existing services already follow this).
