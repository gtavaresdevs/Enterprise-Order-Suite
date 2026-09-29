## Start here (shared by backend and frontend; keep identical in both repos)

- **Docs live in the backend repo** `gtavaresdevs/enterprise-order-suite`, folder `docs/`, branch `feature/ai-agent`. Read `docs/README.md` first: it says what phase we are in and what to read for each kind of task. Frontend sessions clone the backend repo read-only to read them.
- **The backend owns the docs and the API contract** and keeps them current (ADR-0010, ADR-0011). The frontend reads them and may add an annotation only when necessary and only after Gabriel has agreed to it.
- **Decisions are ADRs** in `docs/adr/`. Check them before proposing anything that contradicts one; raise the conflict instead of working around it. Never apply a legacy decision from an old spec without checking `docs/adr/0000-legacy-decisions-triage.md`.
- **Open questions** live in `docs/planning/open-questions.md` (ids `Q-NN`, never renumbered). Never record an answer Gabriel did not give.
- **Current phase:** planning, documentation and Claude readiness (`docs/roadmap.md`). No feature code for the new architecture until the readiness gate in `docs/roadmap.md` passes.
- **Git:** work only on the working branch (backend `feature/ai-agent`, frontend `Claude-Assisted-Development`); never read or base work on `main`. Commit straight to the working branch with an explicit pathspec (`git commit -m "..." -- <files>`) and push. Never merge (no `git merge`, no PR merges, nothing into `main`), never `git stash`, never `git add -A`, never force-push (ADR-0013). Only the main agent commits; subagents never write git state.
- **Architecture in one paragraph:** one shared multi-tenant SaaS; every restaurant-owned row is scoped to its restaurant and fails closed without one (ADR-0001). One operational core (one Order model with channel + source, one Menu) that every interface calls through application services; no business rules in channel adapters (ADR-0002). The Restaurant Edge is optional and not built this run (ADR-0003); offline support means orders (ADR-0004). Payments are record-only: the app never processes or queues a payment and never reports an unconfirmed external operation as successful (ADR-0005). There is no production data yet, so schema and API may be reshaped (ADR-0008); ids are ULIDs (ADR-0009).
- **Language:** code, comments and docs in English; ask Gabriel questions in the language he writes in.

## This repo (backend)

Java 17, Spring Boot 3, PostgreSQL (Flyway), JWT auth. Base package `com.enterprise.ordersuite`. A modular monolith: modules under `src/main/java/com/enterprise/ordersuite/` (`auth`, `identity`, `orders`, `products`, `profile`, `security`, `storage`, `notifications`, `common`, `config`, `api`) are isolated by dependency inversion, not by service boundaries.

It is becoming the restaurant-ops multi-tenant SaaS (ADR-0001, ADR-0002). The current code still has the legacy B2B shape: one tenant, `/orders` and `/products`, orders owned by a `User`, `Long` ids. That shape is replaced, not evolved (ADR-0008); do not extend it for new-architecture work.

**Direction:** start at `docs/README.md` and the ADRs in `docs/adr/`; build order is Tenant foundation, Menu, Order Core, Storefront to Order Core (ADR-0007). The old direction (single restaurant, frontend-owned contract, anonymous customers only, the `docs/contracts/` snapshot, the 2026-09-20 migration design) is superseded or open: see the banners on those files and ADR-0000.

**Kept legacy rules** (MASTER-PLAN D-16): authorization only in `@PreAuthorize` (D2); full `./gradlew test` before "done" (D5); `SCREAMING_SNAKE` error codes (D14); every persisted timestamp is an `Instant` on `timestamptz` (D15); auth D16-D24; the server derives every money value; order lines snapshot name and price; cancel is a status change.

## Skills, agents, hooks

Project skills live in `.claude/skills/` and are versioned. Invoke them; do not re-derive the conventions. Where a skill differs from an ADR, the ADR wins.

| Skill | Invoke before | Status |
|---|---|---|
| `writing-backend-tests` | writing or changing any test | S3 update pending (tenancy, ULIDs) |
| `spring-security-changes` | touching `security/`, `auth/`, any `@PreAuthorize`, JWT or rate limiting | current |
| `backend-module-development` | adding a module, endpoint, service, entity or DTO | S3 update pending (tenancy, ULIDs) |
| `flyway-migrations` | adding a migration or changing an entity's schema | S3 update pending (tenancy, ULIDs) |

`api-contract-sync` is retired and removed from this table (ADR-0010): never invoke it; the backend owns the contract. S3 deletes it or leaves it deprecated.

`backend-module-development`, `flyway-migrations` and `writing-backend-tests` are updated in S3 for tenant scoping and ULIDs (ADR-0001, ADR-0009). Until then the ADRs win where they differ.

Agents in `.claude/agents/`: `spring-security-reviewer` (read-only audit), `backend-test-writer`, `backend-feature-builder`, `flyway-migration-author`. `backend-feature-builder` step 1 (`api-contract-sync`, frontend manifest) is retired (ADR-0010).

`.claude/settings.json` registers a `PreToolUse` hook that warns before edits to security-sensitive files. It advises, never blocks. It runs `pwsh`, so it does not run on Linux cloud sessions; S3 ports or removes it (ADR-0015).

**Verification:** a full `./gradlew test` (Docker required) before claiming anything works.

**Git (backend):** working branch `feature/ai-agent`. Update with `git pull --ff-only` (a plain `git pull` can create a merge commit). Parallel worktree results land by `git cherry-pick`, never a merge. `permissions.deny` rules for the git rules land in S3 (ADR-0013).

## Commands

Gradle, not Maven: ignore the Maven instructions in `README.md`.

```bash
./gradlew build
./gradlew bootRun      # profile local (optional, gitignored application-local.yml); needs .env (see Environment)
./gradlew test         # JUnit 5 + Testcontainers; Docker must be running
./gradlew test --tests "com.enterprise.ordersuite.orders.application.service.OrderServiceTest"
./gradlew test --tests "com.enterprise.ordersuite.orders.api.OrderControllerIT.createOrder_asRegularUser_returns201"
./gradlew flywayMigrate   # migrations: src/main/resources/db/migration (also applied on boot)
./gradlew flywayInfo
```

No lint task; rely on compilation and tests.

## Environment

Config is env-var driven: `spring-dotenv` loads `.env`; `.env.example` lists the minimum (`DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`). `build.gradle` reads `.env` directly for the Flyway Gradle plugin. Never hardcode secrets: extend `.env`/`.env.example` and reference them as `${VAR}` in `application.yml`, following the `storage:`, `jwt:` and `app.email:` blocks. JPA runs with `ddl-auto: validate`.

Tests never use `.env` or a local Postgres: they start real containers through Testcontainers, so they are safe to run anywhere.

## Code conventions

**Layering per module:** `api` (controllers, DTOs) → `application` (services, mappers) → `domain` (entities, domain exceptions) → `persistence` (Spring Data repositories). Not every module has all four; new code follows this shape.

**Cross-module dependency inversion:** the consuming module defines the interface it needs and the providing module implements it. Example: `orders.application.service.ProductService` is an interface owned by `orders`, implemented by `products.application.service.ProductService`. Never import across module packages directly.

**Reuse, do not re-implement:**
- `identity.application.CurrentUserService`: the logged-in user (id, email) from the security context.
- `common.util.PagedResult`: pagination wrapper for list and search endpoints.
- `api.errors.ApiErrorResponse` with `GlobalExceptionHandler` / `AuthExceptionHandler`: the error shape (`code`, `message`, `timestamp`, optional `errors` for validation).

**Authorization:** only in `@PreAuthorize` (D2), never in a method body. Role hierarchy `ROLE_SUPER_ADMIN > ROLE_ADMIN > ROLE_USER` (`SecurityConfig.roleHierarchy()`) is applied by `methodSecurityExpressionHandler`, so it affects annotations and not a raw `getAuthorities()` call. Resource checks are helper beans referenced from SpEL, for example `@PreAuthorize("hasRole('ADMIN') or @orderService.isOrderOwner(#id, principal.id)")`. Code that must know a role for query filtering resolves it through `RoleHierarchy` (`OrderService.isAdmin`).
- Legacy: `isOrderOwner` and the per-user filtering in `OrderService.searchOrders` / `getAllOrders` are B2B behavior (ADR-0000 row D2). "Tenant" now means restaurant (ADR-0001); the scoping mechanism comes from the Tenancy & Identity contract. Do not copy per-user ownership into new-architecture endpoints.

**Legacy order domain:** `Order.transitionTo` enforces the status state machine (`InvalidStatusTransitionException`); every change goes through it. `OrderService.updateOrder` writes an `OrderHistory` row per transition and triggers a notification. Create and cancel change `Product` stock through `ProductService.decrementStock` / `incrementStock`; do not touch stock outside `OrderService`. The stock policy for the new Menu is open (ADR-0000 FS-08, Q-42).

**Auth:** stateless JWT access token (`SessionCreationPolicy.STATELESS`). The refresh token is an HttpOnly cookie on `<context-path>/auth`, rotated per use; reusing a rotated token revokes its whole family (V21). The body `refreshToken` is a backward-compatible fallback (`RefreshTokenSource`) until the auth cleanup in Tenant foundation. `RefreshOriginFilter` checks `Origin` on `/auth/refresh` and `/auth/logout`. `RefreshTokenCleanupScheduler` purges expired tokens. Password reset keeps `PasswordHistory` (no reuse). Per-endpoint rate limiting: `AuthRateLimitFilter` + `RateLimiter` (`InMemoryBucketedSlidingWindowRateLimiter`, or `NoOpRateLimiter` when `security.rate-limit.enabled=false`).

**Storage:** `storage.ObjectStorageService` abstracts S3-compatible storage (AWS S3 in prod, MinIO locally and in tests; `ObjectStorageConfig`). Used for profile avatars (`profile.application.service.AvatarImageProcessor`, `AvatarValidator`).

## Testing

- `@IntegrationTest` (`support.IntegrationTest`) = `@SpringBootTest` + Testcontainers Postgres (`support.PostgresTestContainerConfig`, `@ServiceConnection`) and MinIO (`support.MinioTestContainerConfig`). Use it for anything that hits the DB, security or storage instead of a hand-rolled `@SpringBootTest`.
- Naming: `*Test` = unit test, no Spring context (Mockito); `*IT` = integration test with the full context and containers.
- Time: inject `Clock`; never call `Instant.now()` directly in code under test. `support.MutableClock` controls time in tests (rate-limit windows, token expiry).
