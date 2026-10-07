## Start here (shared by backend and frontend; keep identical in both repos)

- **Docs live in the backend repo** `gtavaresdevs/enterprise-order-suite`, folder `docs/`, branch `feature/ai-agent`. Read `docs/README.md` first: it says what phase we are in and what to read for each kind of task. Frontend sessions clone the backend repo read-only to read them.
- **The backend owns the docs and the API contract** and keeps them current (ADR-0010, ADR-0011). The frontend reads them and may add an annotation only when necessary and only after Gabriel has agreed to it.
- **Decisions are ADRs** in `docs/adr/`. Check them before proposing anything that contradicts one; raise the conflict instead of working around it. Never apply a legacy decision from an old spec without checking `docs/adr/0000-legacy-decisions-triage.md`.
- **Open questions** live in `docs/planning/open-questions.md` (ids `Q-NN`, never renumbered). Never record an answer Gabriel did not give.
- **Current phase:** planning, documentation and Claude readiness (`docs/roadmap.md`). No feature code for the new architecture until the readiness gate in `docs/roadmap.md` passes.
- **Git:** work only on the working branch (backend `feature/ai-agent`, frontend `Claude-Assisted-Development`); never read or base work on `main`. Commit straight to the working branch with an explicit pathspec (`git commit -m "..." -- <files>`) and push. Never merge (no `git merge`, no PR merges, nothing into `main`), never `git stash`, never `git add -A`, never force-push (ADR-0013). Only the main agent commits; subagents never write git state.
- **Issues (ADR-0021, Gabriel 2026-10-07):** agents open GitHub issues for build work on their own, without being told. When a build step or a piece of build work starts, search the open issues of the backend repo `gtavaresdevs/Enterprise-Order-Suite` first; if none covers it, create one there (frontend work too, labelled `area:frontend`) with an `area:*` and a `type:*` label and a body that links the canonical doc. Every commit for it says `Refs #<n>` (from the frontend repo: `Refs gtavaresdevs/Enterprise-Order-Suite#<n>`), never `closes`/`fixes`/`resolves`; when the work is pushed, comment on the issue with the commits and leave it open; Gabriel closes it after approving (Done). Local sessions with the `gh` `project` scope also move the card to In Progress when starting and to In review when pushed; cloud sessions can't, so they skip that and say so in their reply. Issues are public: no restaurant, customer or order data, secrets, tokens, IPs or credentials. Never cite an issue number that does not exist. Details: `docs/ops/github-projects-setup.md` section 6, "How agents use issues".
- **Architecture in one paragraph:** one shared multi-tenant SaaS; every restaurant-owned row is scoped to its restaurant and fails closed without one (ADR-0001). One operational core (one Order model with channel + source, one Menu) that every interface calls through application services; no business rules in channel adapters (ADR-0002). The Restaurant Edge is optional and not built this run (ADR-0003); offline support means orders (ADR-0004). Payments are record-only: the app never processes or queues a payment and never reports an unconfirmed external operation as successful (ADR-0005). There is no production data yet, so schema and API may be reshaped (ADR-0008); ids are ULIDs (ADR-0009).
- **Language:** code, comments and docs in English; ask Gabriel questions in the language he writes in.

## This repo (backend)

Java 17, Spring Boot 3, PostgreSQL (Flyway), JWT auth. Base package `com.enterprise.ordersuite`. A modular monolith: modules under `src/main/java/com/enterprise/ordersuite/` (`auth`, `identity`, `profile`, `security`, `storage`, `notifications`, `common`, `config`, `api`) are isolated by dependency inversion, not by service boundaries.

It is becoming the restaurant-ops multi-tenant SaaS (ADR-0001, ADR-0002). Build 1 (Tenant foundation, #31) is replacing the legacy B2B shape: the legacy `orders` and `products` modules are deleted (Q-90 a); Menu and Order Core are rebuilt in Builds 2 and 3. What remains of the legacy shape (one tenant, `Long` ids, `roles`) is replaced, not evolved (ADR-0008); do not extend it for new-architecture work.

**Direction:** start at `docs/README.md` and the ADRs in `docs/adr/`; build order is Tenant foundation, Menu, Order Core, Storefront to Order Core (ADR-0007). The old direction (single restaurant, frontend-owned contract, the `docs/contracts/` snapshot, the 2026-09-20 migration design) is superseded: see the banners on those files and ADR-0000. Customers stay anonymous: a snapshot on each order, no Customer table this run (Q-51 a, ADR-0000 FS-03).

**Kept legacy rules** (MASTER-PLAN D-16): authorization only in `@PreAuthorize` (D2); full `./gradlew test` before "done" (D5); `SCREAMING_SNAKE` error codes (D14); every persisted timestamp is an `Instant` on `timestamptz` (D15); auth D16-D24; the server derives every money value; order lines snapshot name and price; cancel is a status change. Added by Q-05 a (ADR-0000): `.claude/` is versioned (D1); indentation 4 spaces in `src/main`, 2 in `src/test` (D3); skills teach the target model and mark legacy explicitly (D4); cookie and CORS properties are env-bound with production-safe defaults (D12); item edits only while the order is open, else 409 `ORDER_NOT_EDITABLE` (D13); `/public/*` never reuses an admin-scoped query, with a denied-access test for every resource-scoped endpoint and a leak test for every `/public/*` endpoint (LR-4).

## Skills, agents, hooks

Project skills live in `.claude/skills/` and are versioned. Invoke them; do not re-derive the conventions. Where a skill differs from an ADR, the ADR wins.

| Skill | Invoke before | Status |
|---|---|---|
| `writing-backend-tests` | writing or changing any test | current |
| `spring-security-changes` | touching `security/`, `auth/`, any `@PreAuthorize`, JWT or rate limiting | current |
| `backend-module-development` | adding a module, endpoint, service, entity or DTO | current |
| `flyway-migrations` | adding a migration or changing an entity's schema | current |

`api-contract-sync` is retired (ADR-0010): the model cannot invoke it and the file is kept only as history; the backend owns the contract.

The skills carry only the Accepted new-architecture rules (ULID keys, restaurant-scoped rows that fail closed); the tenancy mechanism arrives with the Tenancy & Identity contract (S4).

Agents in `.claude/agents/`: `spring-security-reviewer` (read-only audit), `backend-test-writer`, `backend-feature-builder`, `flyway-migration-author`.

`.claude/settings.json` (Node hooks, every OS; ADR-0015):
- `PreToolUse` `security-sensitive-file.mjs`: asks for confirmation before an edit to a security-sensitive file and points to `spring-security-changes`. It never denies.
- `SessionStart` `session-start.mjs`: reports whether this session can run the full `./gradlew test` (JDK, Docker). In cloud sessions it also starts the Docker daemon.
- `permissions.deny`: the git rules below (ADR-0013). They match command prefixes, so they are a guardrail, not a boundary.

**Verification:** a full `./gradlew test` (Docker required) before claiming anything works.

**Git (backend):** working branch `feature/ai-agent`. Update with `git pull --ff-only` (a plain `git pull` can create a merge commit). Parallel worktree results land by `git cherry-pick`, never a merge. CI (`.github/workflows/ci.yml`) runs `./gradlew test` on every push to it.

## Commands

Gradle, not Maven: ignore the Maven instructions in `README.md`.

```bash
./gradlew build
./gradlew bootRun      # profile local (optional, gitignored application-local.yml); needs .env (see Environment)
./gradlew test         # JUnit 5 + Testcontainers; Docker must be running
./gradlew test --tests "com.enterprise.ordersuite.auth.service.AuthenticationServiceTest"
./gradlew test --tests "com.enterprise.ordersuite.auth.controllers.RefreshCookieIT"
./gradlew flywayMigrate   # migrations: src/main/resources/db/migration (also applied on boot)
./gradlew flywayInfo
```

No lint task; rely on compilation and tests.

The repo has no `gradle.properties` (Q-84): Gradle uses `JAVA_HOME`. To pin a JDK on your machine, set `org.gradle.java.home` in your user-level `~/.gradle/gradle.properties`, never in the repo.

## Environment

Config is env-var driven: `spring-dotenv` loads `.env`; `.env.example` lists the minimum (`DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`). `build.gradle` reads `.env` directly for the Flyway Gradle plugin. Never hardcode secrets: extend `.env`/`.env.example` and reference them as `${VAR}` in `application.yml`, following the `storage:`, `jwt:` and `app.email:` blocks. JPA runs with `ddl-auto: validate`.

Tests never need `.env` or a local Postgres: they start real containers through Testcontainers, and `src/test/resources/application-test.yml` gives every required variable a test fallback. `spring-dotenv` still loads a `.env` when one exists, so a test that asserts on a configurable value pins it with `@TestPropertySource` (see `RefreshCookieIT`).

## Code conventions

**Layering per module:** `api` (controllers, DTOs) → `application` (services, mappers) → `domain` (entities, domain exceptions) → `persistence` (Spring Data repositories). Not every module has all four; new code follows this shape.

**Cross-module dependency inversion:** the consuming module defines the interface it needs and the providing module implements it. (The legacy `orders`/`products` pair was the worked example; it was deleted in Build 1.) Never import across module packages directly.

**Reuse, do not re-implement:**
- `identity.application.CurrentUserService`: the logged-in user (id, email) from the security context.
- `common.util.PagedResult`: pagination wrapper for list and search endpoints.
- `api.errors.ApiErrorResponse` with `GlobalExceptionHandler` / `AuthExceptionHandler`: the error shape (`code`, `message`, `timestamp`, optional `errors` for validation).

**Authorization:** only in `@PreAuthorize` (D2), never in a method body. Role hierarchy `ROLE_SUPER_ADMIN > ROLE_ADMIN > ROLE_USER` (`SecurityConfig.roleHierarchy()`) is applied by `methodSecurityExpressionHandler`, so it affects annotations and not a raw `getAuthorities()` call. Resource checks are helper beans referenced from SpEL. Code that must know a role for query filtering resolves it through `RoleHierarchy`, never `getAuthorities()`. "Tenant" means restaurant (ADR-0001); the scoping mechanism is the Tenancy & Identity contract (`docs/architecture/TENANCY-AND-IDENTITY.md`). Do not copy per-user ownership into new-architecture endpoints.

**Menu:** no stock counts; the 86 toggle is the only availability control (Q-42 c, ADR-0000 FS-08).

**Auth:** stateless JWT access token (`SessionCreationPolicy.STATELESS`). The refresh token is an HttpOnly cookie on `<context-path>/auth`, rotated per use; reusing a rotated token revokes its whole family (V21). The body `refreshToken` is a backward-compatible fallback (`RefreshTokenSource`) until the auth cleanup in Tenant foundation. `RefreshOriginFilter` checks `Origin` on `/auth/refresh` and `/auth/logout`. `RefreshTokenCleanupScheduler` purges expired tokens. Password reset keeps `PasswordHistory` (no reuse). Per-endpoint rate limiting: `AuthRateLimitFilter` + `RateLimiter` (`InMemoryBucketedSlidingWindowRateLimiter`, or `NoOpRateLimiter` when `security.rate-limit.enabled=false`).

**Storage:** `storage.ObjectStorageService` abstracts S3-compatible storage (AWS S3 in prod, MinIO locally and in tests; `ObjectStorageConfig`). Used for profile avatars (`profile.application.service.AvatarImageProcessor`, `AvatarValidator`).

## Testing

- `@IntegrationTest` (`support.IntegrationTest`) = `@SpringBootTest` + Testcontainers Postgres (`support.PostgresTestContainerConfig`, `@ServiceConnection`) and MinIO (`support.MinioTestContainerConfig`). Use it for anything that hits the DB, security or storage instead of a hand-rolled `@SpringBootTest`.
- Naming: `*Test` = unit test, no Spring context (Mockito); `*IT` = integration test with the full context and containers.
- Time: inject `Clock`; never call `Instant.now()` directly in code under test. `support.MutableClock` controls time in tests (rate-limit windows, token expiry).
