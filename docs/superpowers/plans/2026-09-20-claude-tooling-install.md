# Claude Tooling Install Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Install the project's guardrails — 5 skills, 4 agents, 1 hook, the `security-guidance` plugin, the API contract snapshot and an updated `CLAUDE.md` — so later sessions inherit the conventions instead of re-deriving them.

**Architecture:** Three layers with different reliability. Skills are documents Claude must choose to load. Agents are subagents someone must dispatch. The hook is harness-executed configuration that fires on every matching edit whether or not anyone remembers. The hook therefore carries the rule that has already been violated once in this repo.

**Tech Stack:** Markdown with YAML frontmatter (skills/agents), PowerShell 7 (hook), JSON (`settings.json`). No Java changes in this plan.

**Spec:** `docs/superpowers/specs/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md`

## Global Constraints

- All skill, agent, hook and documentation content is written in **English**. Questions to the user are asked in **the language the user is writing in**; implementation is always English. (Spec D6)
- Skill frontmatter requires `name` and `description`; `description` states *when to use it*, not what it does. Follow `superpowers:writing-skills`.
- Indentation in examples: **4 spaces** for `src/main` code, **2 spaces** for `src/test` code. (Spec D3)
- Skills teach the **target** (restaurant-ops) model and mark legacy explicitly as legacy. (Spec D4)
- Authorization lives in `@PreAuthorize`. Never in a method body. (Spec D2)
- This plan changes **no Java files**. `./gradlew test` is not a gate here; Task 9 is the verification gate.
- `.claude/` is versioned. Only `.claude/settings.local.json` is ignored. (Spec D1)

---

### Task 1: Contract snapshot + provenance

The manifest is canonical in the frontend repo. Keeping the snapshot **byte-identical** makes future re-syncs a clean diff, so provenance goes in a sibling README rather than as a header inside the YAML.

**Files:**
- Create: `docs/contracts/backend-integration-manifest.openapi.yaml` (byte-identical copy)
- Create: `docs/contracts/README.md`

**Interfaces:**
- Produces: the path `docs/contracts/backend-integration-manifest.openapi.yaml`, referenced by the `api-contract-sync` skill (Task 7) and by `CLAUDE.md` (Task 8).

- [ ] **Step 1: Copy the manifest byte-identical**

```bash
mkdir -p docs/contracts
cp "/c/Users/Gabriel/Desktop/Projetos/enterprise-order-suite-frontend/order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml" \
   docs/contracts/backend-integration-manifest.openapi.yaml
```

- [ ] **Step 2: Verify the copy is identical**

Run:
```bash
diff "/c/Users/Gabriel/Desktop/Projetos/enterprise-order-suite-frontend/order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml" \
     docs/contracts/backend-integration-manifest.openapi.yaml && echo IDENTICAL
```
Expected: `IDENTICAL`, no diff output.

- [ ] **Step 3: Write the provenance README**

Create `docs/contracts/README.md`:

```markdown
# API contracts

## backend-integration-manifest.openapi.yaml

**This is a snapshot. It is not the source of truth.**

| | |
|---|---|
| Canonical location | `order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml` in the **frontend** repository |
| Snapshot of version | 0.2.0 (dated 2026-09-19) |
| Taken on | 2026-09-20 |
| Owner | the frontend repository |

The copy is kept **byte-identical** to the canonical file so re-syncing is a clean diff.

### Rules

- Never edit this file to record a backend decision. It is a mirror.
- When the backend must diverge from the contract, report it back so the canonical
  copy is patched. The manifest's own `x-maintenance` section defines how: patch only
  what the decision touches, bump `info.version`, add an `x-changelog` entry at the top.
- When the canonical file's version changes, re-copy the whole file and update the
  version row above.
- The `api-contract-sync` skill enforces this.

### Source-of-truth order (from the manifest's `x-maintenance`)

1. The live `/api/v3/api-docs` for anything marked `x-status: live` or `target-change`
2. The frontend's `src/types/*` and services for mock-backed target shapes
3. `docs/superpowers/specs/2026-09-09-restaurant-ops-redesign-design.md` in the frontend repo for the concept
```

- [ ] **Step 4: Commit**

```bash
git add docs/contracts/
git commit -m "docs: snapshot the frontend backend-integration manifest v0.2.0"
```

---

### Task 2: Install the `security-guidance` plugin

**Files:**
- No repository files. This changes the user's Claude Code plugin configuration.

**Interfaces:**
- Produces: a hook-based security reminder that fires independently of `.claude/hooks/` (Task 8), giving two independent layers.

- [ ] **Step 1: Check whether it is already installed**

Run:
```bash
ls ~/.claude/plugins/cache/claude-plugins-official/ | grep -x security-guidance && echo ALREADY_INSTALLED || echo NOT_INSTALLED
```

- [ ] **Step 2: Install it if absent**

Plugin installation is interactive. Ask the user to run, in this session:

```
/plugin install security-guidance@claude-plugins-official
```

Tell them the `!` prefix does not apply here — `/plugin` is a Claude Code command, not a shell command.

- [ ] **Step 3: Verify**

Run:
```bash
ls ~/.claude/plugins/cache/claude-plugins-official/security-guidance/hooks/hooks.json && echo INSTALLED
```
Expected: the path prints, followed by `INSTALLED`.

If the user declines or the install fails, record it and continue — Task 8's hook covers the same risk for this repository.

---

### Task 3: Skill — `writing-backend-tests`

**Files:**
- Create: `.claude/skills/writing-backend-tests/SKILL.md`

**Interfaces:**
- Produces: skill name `writing-backend-tests`, referenced by the `backend-test-writer` agent (Task 10) and the `backend-feature-builder` agent (Task 12).

- [ ] **Step 1: Load the skill-authoring conventions**

Invoke `superpowers:writing-skills` and follow it while writing this file.

- [ ] **Step 2: Write the file with this exact frontmatter**

```yaml
---
name: writing-backend-tests
description: Use when writing or modifying any test in this repository - covers the *Test versus *IT split, the real-login integration pattern, naming, AssertJ style, and the mandatory denied-access and public-endpoint leak coverage.
---
```

- [ ] **Step 3: Encode these facts — every one is verifiable in the repo**

The body MUST state each of the following. Read the cited file before writing the section so the example matches reality.

1. **The split.** `*Test` = plain JUnit 5 + Mockito, `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`, **no Spring context**. `*IT` = `@IntegrationTest` + `@AutoConfigureMockMvc`, real Postgres and MinIO via Testcontainers. Reference: `src/test/java/com/enterprise/ordersuite/orders/application/service/OrderServiceTest.java:48` and `src/test/java/com/enterprise/ordersuite/orders/api/OrderControllerIT.java:34-35`.
2. **Never hand-roll `@SpringBootTest`.** Use the `@IntegrationTest` composite (`support/IntegrationTest.java`), which imports `PostgresTestContainerConfig` and `MinioTestContainerConfig`. This is what guarantees tests never reach a real database.
3. **Authenticate with a real login, not `@WithMockUser`.** The house pattern creates a user through `userRepository`, then POSTs to `/auth/login` and extracts `$.accessToken`, then sends `.header("Authorization", "Bearer " + token)`. Reference: `OrderControllerIT.java:341-371`. Explain why: it exercises `JwtAuthenticationFilter` and the real authority mapping, which `@WithMockUser` bypasses — and authority mapping is exactly where this repo has had defects.
4. **`@WithMockUser` is acceptable only** for tests that assert URL-level rules without touching the JWT filter. Existing precedent: `security/ActuatorSecurityIT.java:63`.
5. **Test naming:** `method_scenario_expectedOutcome`, e.g. `getOrderById_notAsOwner_returns403`. `@DisplayName` is optional; when present it is a full sentence starting with "Should" (precedent: `security/ratelimit/LoginRateLimitIT.java:39`).
6. **AssertJ, not JUnit assertions.** Use `assertThat` / `assertThatThrownBy`, and attach `.as("why this must hold")` on any assertion whose failure would otherwise be cryptic. Precedent: `LoginRateLimitIT.java:48-53`.
7. **Unique test data.** Emails are `"prefix-" + UUID.randomUUID() + "@test.com"` because integration tests share one container across the class. Precedent: `OrderControllerIT.java:69`.
8. **Time.** Inject `Clock` in production code and use `support/MutableClock.java` in tests. Never call `Instant.now()` directly in code that needs to be time-tested.
9. **Property overrides** use `@TestPropertySource` on the class. Precedent: `LoginRateLimitIT.java:23-27`.
10. **Indentation is 2 spaces** in `src/test`.
11. **Mandatory coverage — state these as non-negotiable:**
    - Any endpoint scoped to a resource owner gets a test asserting the **denied** path (403), not only the allowed one.
    - Any endpoint reachable without authentication (`/public/*`, once it exists) gets a test asserting it does **not** leak fields or rows an anonymous caller must not see.
    - Any change to role handling gets a `SUPER_ADMIN` case. Explain why: `SUPER_ADMIN` inherits `ADMIN` only through `RoleHierarchy`, which applies inside `@PreAuthorize` but **not** to a raw `getAuthorities()` check — the exact defect found in `OrderService.java:209`.
12. **Running tests:** `./gradlew test` for everything, `./gradlew test --tests "fully.qualified.ClassName"` for one class. Docker must be running. A completion claim requires the full suite. (Spec D5)

- [ ] **Step 4: Verify the frontmatter parses and required fields exist**

Run:
```bash
head -5 .claude/skills/writing-backend-tests/SKILL.md
grep -c "^name:\|^description:" .claude/skills/writing-backend-tests/SKILL.md
```
Expected: frontmatter delimited by `---`, and the grep count is `2`.

- [ ] **Step 5: Verify every cited path exists**

Run:
```bash
for f in src/test/java/com/enterprise/ordersuite/orders/application/service/OrderServiceTest.java \
         src/test/java/com/enterprise/ordersuite/orders/api/OrderControllerIT.java \
         src/test/java/com/enterprise/ordersuite/support/IntegrationTest.java \
         src/test/java/com/enterprise/ordersuite/support/MutableClock.java \
         src/test/java/com/enterprise/ordersuite/security/ratelimit/LoginRateLimitIT.java \
         src/test/java/com/enterprise/ordersuite/security/ActuatorSecurityIT.java; do
  test -f "$f" && echo "OK   $f" || echo "MISS $f"
done
```
Expected: every line starts with `OK`.

- [ ] **Step 6: Commit**

```bash
git add .claude/skills/writing-backend-tests/
git commit -m "feat(claude): add writing-backend-tests skill"
```

---

### Task 4: Skill — `spring-security-changes`

This is the highest-value skill in the plan. It carries the rule the repo already broke.

**Files:**
- Create: `.claude/skills/spring-security-changes/SKILL.md`

**Interfaces:**
- Produces: skill name `spring-security-changes`, referenced by the hook (Task 8) and the `spring-security-reviewer` agent (Task 9).

- [ ] **Step 1: Load the skill-authoring conventions**

Invoke `superpowers:writing-skills` and follow it.

- [ ] **Step 2: Write the file with this exact frontmatter**

```yaml
---
name: spring-security-changes
description: Use before changing anything under security/ or auth/, any SecurityConfig, JwtAuthenticationFilter, rate limiter, or any @PreAuthorize expression - requires asking the user detailed questions first and forbids authorization logic inside method bodies.
---
```

- [ ] **Step 3: Encode the map of what exists**

Read each file before describing it.

- **Filter chain order** (`security/config/SecurityConfig.java:164-181`): `RequestIdFilter` → `AuthRateLimitFilter` → `JwtAuthenticationFilter`, all before `UsernamePasswordAuthenticationFilter`. Session policy is `STATELESS`; CSRF is disabled because authentication is a bearer header.
- **Public paths** (`SecurityConfig.java:169`): `/error`, `/auth/**`, `/actuator/health/**`, Swagger, `/webjars/**`, `/logo.png`. `/actuator/info` and `/actuator/metrics/**` and `/admin/users/**` and `/admin/identity-audit/**` require `SUPER_ADMIN`; `/admin/**` and `/roles` require `ADMIN`.
- **Role hierarchy** (`SecurityConfig.java:53-55`): `ROLE_SUPER_ADMIN > ROLE_ADMIN > ROLE_USER`, wired into `methodSecurityExpressionHandler` (line 58).
- **JWT** (`security/jwt/`): `JwtService` issues and validates; `JwtAuthenticationFilter` maps roles to `ROLE_*` authorities.
- **Rate limiting** (`security/ratelimit/`, `security/web/AuthRateLimitFilter.java`): per-endpoint buckets from `RateLimitProperties`, toggled by `security.rate-limit.enabled`, falling back to `NoOpRateLimiter`. Tests disable it via `src/test/resources/application-test.yml`.
- **Error shape**: `api/errors/ApiErrorResponse` with `code`, `message`, `timestamp`, optional `errors`.

- [ ] **Step 4: Encode the hard rules**

1. **Authorization lives in `@PreAuthorize`. Never in a method body.** Give the worked example verbatim, because it is a live defect at the time of writing:

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

2. **No `@PostAuthorize` for ownership.** Filtering after the fact means the work already happened; express the rule in `@PreAuthorize` or in the query.
3. **Never invent authorities that are not claims.** `JwtAuthenticationFilter:66-75` fabricates `SCOPE_order:*` from the role. They look like OAuth2 scopes, carry no independent meaning, and `SCOPE_order:write` is granted to every authenticated user — so `hasRole('ADMIN') or hasAuthority('SCOPE_order:write')` denies nobody. Do not add more of these.
4. **Multi-tenant list endpoints force-filter at the query level** for non-admins, never in a post-filter loop.
5. **Secrets come from `.env`** via `${VAR}` in `application.yml`. Extend `.env.example` in the same change. Never hardcode.
6. **Every security change ships with a test for both the allowed and the denied path**, plus a `SUPER_ADMIN` case when role handling is involved.

- [ ] **Step 5: Encode the questions-first rule**

State that before changing authentication, authorization, token handling or rate limiting, the assistant must ask the user explicit questions and wait — never infer. The questions must cover, at minimum:

- Who is allowed to call this, by role, and who must be denied?
- Is the resource owned by someone? If so, what identifies the owner?
- Does this change an **existing** permission? If so, is it a widening, a narrowing, or a defect fix — and has the frontend been told?
- What must the failure look like: 401, 403, or 404 to avoid confirming the resource exists?
- Does it need rate limiting?

Ask in the user's language; implement in English. (Spec D6)

- [ ] **Step 6: Encode the target-model security surface**

Mark this section clearly as **target, not yet built** (Spec D4). Source: `docs/contracts/backend-integration-manifest.openapi.yaml`.

- Refresh token moves to an **HttpOnly cookie**, `Path=/api/auth`, `SameSite=Lax`, `Secure` configurable for local HTTP dev.
- Refresh **rotates on every call**; reuse of an already-rotated token must revoke the entire token family.
- `/auth/refresh` and `/auth/logout` become the only cookie-dependent endpoints, therefore the **only CSRF surface**. Defence is `Origin` validation plus requiring `Content-Type: application/json`. Everything else authenticates by header.
- The access token gains `firstName`, `lastName`, `email` claims. A JWT payload is **base64, not encrypted** — display data only, never phone, address or anything sensitive.
- `/public/*` endpoints are unauthenticated and must never reuse an admin-scoped query. Specific obligations from the manifest: filter `available == true` server-side so 86'd items do not leak; expose a single-table lookup rather than the full roster; treat `customerPhone` as a lookup credential without enabling enumeration; derive `total`, `etaMinutes` and `paymentStatus` server-side and force `channel=Online` rather than trusting the client.

- [ ] **Step 7: Verify the frontmatter and cited paths**

Run:
```bash
grep -c "^name:\|^description:" .claude/skills/spring-security-changes/SKILL.md
for f in src/main/java/com/enterprise/ordersuite/security/config/SecurityConfig.java \
         src/main/java/com/enterprise/ordersuite/security/jwt/JwtAuthenticationFilter.java \
         src/main/java/com/enterprise/ordersuite/security/web/AuthRateLimitFilter.java \
         src/main/java/com/enterprise/ordersuite/api/errors/ApiErrorResponse.java \
         docs/contracts/backend-integration-manifest.openapi.yaml; do
  test -f "$f" && echo "OK   $f" || echo "MISS $f"
done
```
Expected: grep prints `2`; every path line starts with `OK`.

- [ ] **Step 8: Commit**

```bash
git add .claude/skills/spring-security-changes/
git commit -m "feat(claude): add spring-security-changes skill"
```

---

### Task 5: Skill — `backend-module-development`

**Files:**
- Create: `.claude/skills/backend-module-development/SKILL.md`

**Interfaces:**
- Produces: skill name `backend-module-development`, referenced by the `backend-feature-builder` agent (Task 12).

- [ ] **Step 1: Load the skill-authoring conventions**

Invoke `superpowers:writing-skills` and follow it.

- [ ] **Step 2: Write the file with this exact frontmatter**

```yaml
---
name: backend-module-development
description: Use when adding or changing a module, endpoint, service, entity or DTO in this backend - covers the api/application/domain/persistence layering, cross-module dependency inversion, pagination, the error response shape, and server-side derivation of monetary values.
---
```

- [ ] **Step 3: Encode these rules**

1. **Layering:** `api` (controllers + DTOs) → `application` (services, mappers) → `domain` (entities, domain exceptions) → `persistence` (Spring Data repositories). Not every module has all four; new code follows the shape.
2. **Cross-module dependency inversion.** The consumer declares the interface. Worked example: `orders` needs product data, so `orders.application.service.ProductService` is an interface **owned by orders**, and `products.application.service.ProductService` implements it. Never import across module packages directly.
3. **Reuse, do not re-implement:** `identity.application.CurrentUserService` for the logged-in user; `common.util.PagedResult` for any list or search response; `api.errors.ApiErrorResponse` + `GlobalExceptionHandler`/`AuthExceptionHandler` for errors.
4. **Authorization** — defer to the `spring-security-changes` skill; do not restate the rules, link to them. State only the boundary: the controller declares the coarse permission, the service declares resource ownership, and neither puts a permission check in a method body.
5. **The server derives money.** Never persist a client-supplied total. Compute it from the line items plus any server-resolved fee. The current `OrderService.calculateTotalAmount` (line 200) is the precedent; the manifest makes this explicit for the target model, where `total` includes a delivery fee the server must resolve from an **active** zone.
6. **Price snapshots.** Line items capture name and unit price at order time; later catalogue edits must not rewrite historical orders.
7. **Logging.** Controllers and services log with the `requestId` from `MDC.get("requestId")`, populated by `RequestIdFilter`. Precedent: `OrderController.java:38-39`.
8. **Indentation is 4 spaces** in `src/main`.
9. **Validation** with `@Valid` on request bodies; validation failures surface through `GlobalExceptionHandler` as the `errors` list on `ApiErrorResponse`.
10. **Before adding or changing an endpoint, invoke `api-contract-sync`.**

- [ ] **Step 4: Encode the target direction**

Mark clearly as target (Spec D4): the product is becoming **restaurant operations**, not a B2B order suite. New modules should be named for the restaurant domain (`menu`, `tables`, `settings`) rather than extending `products`/`orders` semantics that are scheduled for replacement. Point to `docs/contracts/backend-integration-manifest.openapi.yaml` and the migration spec.

- [ ] **Step 5: Verify**

Run:
```bash
grep -c "^name:\|^description:" .claude/skills/backend-module-development/SKILL.md
for f in src/main/java/com/enterprise/ordersuite/orders/application/service/ProductService.java \
         src/main/java/com/enterprise/ordersuite/common/util/PagedResult.java \
         src/main/java/com/enterprise/ordersuite/identity/application/CurrentUserService.java \
         src/main/java/com/enterprise/ordersuite/security/web/RequestIdFilter.java; do
  test -f "$f" && echo "OK   $f" || echo "MISS $f"
done
```
Expected: grep prints `2`; every path line starts with `OK`.

- [ ] **Step 6: Commit**

```bash
git add .claude/skills/backend-module-development/
git commit -m "feat(claude): add backend-module-development skill"
```

---

### Task 6: Skill — `flyway-migrations`

**Files:**
- Create: `.claude/skills/flyway-migrations/SKILL.md`

**Interfaces:**
- Produces: skill name `flyway-migrations`, referenced by the `flyway-migration-author` agent (Task 13).

- [ ] **Step 1: Load the skill-authoring conventions**

Invoke `superpowers:writing-skills` and follow it.

- [ ] **Step 2: Write the file with this exact frontmatter**

```yaml
---
name: flyway-migrations
description: Use when adding or changing a database migration, or changing a JPA entity's mapped schema - covers version numbering, keeping entity and schema in sync under ddl-auto validate, and renaming an enum without losing data.
---
```

- [ ] **Step 3: Encode these rules**

1. **Location and naming:** `src/main/resources/db/migration/V<N>__Description.sql`. The highest version at the time of writing is `V19__Add_Avatar_Key_To_User_Profile.sql`; always list the directory and take the next free integer rather than assuming.
2. **Never edit an applied migration.** Flyway checksums them; editing one breaks every environment that already ran it. Write a new version instead.
3. **`ddl-auto: validate`** (`application.yml`) means Hibernate refuses to start if an entity and its table disagree. Entity change and migration ship in the **same commit**, always.
4. **Migrations run automatically on boot**, and in tests against a real Postgres container. A broken migration fails the whole integration suite, not one test.
5. **Idempotent seeds** use `ON CONFLICT ... DO NOTHING`. Precedent: `V15__seed_super_admin.sql`.
6. **Enum rename without data loss** — needed by migration phase 4. Give the three-step pattern explicitly: add the new value set, backfill with an explicit `UPDATE ... SET status = CASE old THEN new ... END` mapping every old value, then drop the old constraint. State that the enum is stored as a string (`@Enumerated(EnumType.STRING)` in `Order.java:25`), so the stored values are the Java constant names and a rename is a **data migration**, not a schema-only change.
7. **Gradle tasks:** `./gradlew flywayMigrate`, `./gradlew flywayInfo`. `build.gradle` reads `.env` directly to configure the plugin outside Spring's context.
8. **Verify after writing:** run `./gradlew test` — the integration suite boots against a real container and will reject a migration that disagrees with an entity.

- [ ] **Step 4: Verify**

Run:
```bash
grep -c "^name:\|^description:" .claude/skills/flyway-migrations/SKILL.md
ls src/main/resources/db/migration/ | sort -V | tail -1
grep -n "EnumType.STRING" src/main/java/com/enterprise/ordersuite/orders/domain/Order.java
```
Expected: grep prints `2`; the latest migration prints; the `@Enumerated` line is found.

- [ ] **Step 5: Commit**

```bash
git add .claude/skills/flyway-migrations/
git commit -m "feat(claude): add flyway-migrations skill"
```

---

### Task 7: Skill — `api-contract-sync`

**Files:**
- Create: `.claude/skills/api-contract-sync/SKILL.md`

**Interfaces:**
- Consumes: `docs/contracts/backend-integration-manifest.openapi.yaml` and `docs/contracts/README.md` from Task 1.
- Produces: skill name `api-contract-sync`, referenced by `backend-module-development` (Task 5) and the `backend-feature-builder` agent (Task 12).

- [ ] **Step 1: Load the skill-authoring conventions**

Invoke `superpowers:writing-skills` and follow it.

- [ ] **Step 2: Write the file with this exact frontmatter**

```yaml
---
name: api-contract-sync
description: Use before creating or changing any REST endpoint, request body or response shape - checks it against the frontend's backend-integration manifest and defines how to report a necessary divergence back to the canonical copy.
---
```

- [ ] **Step 3: Encode these rules**

1. **The snapshot is a mirror, not the truth.** Canonical copy lives in the frontend repo; `docs/contracts/README.md` records where. Never edit the snapshot to record a backend decision.
2. **Before adding or changing an endpoint:** find it in the manifest. Match the path, the method, the request shape, the response shape and the status codes.
3. **Read the `x-status` marker.** No marker means the frontend's mock-backed target contract — not yet built. `live` means verified against the running backend. `target-change` means it exists but the frontend needs it changed. `proposed` means a new resource whose shape should be confirmed before building.
4. **The base path is `/api`, with no version segment** — `server.servlet.context-path` is `${SERVER_CONTEXT_PATH}` in `application.yml`. The manifest's `x-open-decisions` records this as decided on 2026-09-19.
5. **When the backend must diverge:** stop, tell the user, and report it back so the canonical copy is patched. Quote the manifest's own `x-maintenance` rules — patch only what the decision touches, bump `info.version` (patch = clarification, minor = additive, major = breaking), add an `x-changelog` entry at the top with date, version, what and why, and never delete a superseded decision.
6. **Unresolved decisions belong in `x-open-decisions`, not silently in a schema.** List the ones currently open so they are not accidentally decided in code: `id-type`, `category-identity`, `created-at-format`, `payment-sequencing`, `settings-images`, `dev-cookie-secure`.
7. **When a mock-backed endpoint goes live**, verify against the running `/api/v3/api-docs`, then report the `x-status` flip.

- [ ] **Step 4: Verify**

Run:
```bash
grep -c "^name:\|^description:" .claude/skills/api-contract-sync/SKILL.md
test -f docs/contracts/backend-integration-manifest.openapi.yaml && echo SNAPSHOT_OK
grep -n "x-open-decisions" docs/contracts/backend-integration-manifest.openapi.yaml
```
Expected: grep prints `2`; `SNAPSHOT_OK`; the `x-open-decisions` line is found.

- [ ] **Step 5: Commit**

```bash
git add .claude/skills/api-contract-sync/
git commit -m "feat(claude): add api-contract-sync skill"
```

---

### Task 8: The sensitive-file hook

The only layer that does not depend on anyone remembering. Build it after the security skill exists, so the reminder can point at a skill that is actually there.

**Files:**
- Create: `.claude/hooks/security-sensitive-file.ps1`
- Modify: `.claude/settings.json`
- Delete: `.claude/hooks/.gitkeep`

**Interfaces:**
- Consumes: the `spring-security-changes` skill from Task 4.
- Produces: a `PreToolUse` hook on `Edit|Write`.

- [ ] **Step 1: Write the hook script**

Create `.claude/hooks/security-sensitive-file.ps1`:

```powershell
#Requires -Version 7
# PreToolUse hook: warn before editing a security-sensitive file.
# Reads the tool call as JSON on stdin; emits additionalContext when the path matches.
# Always exits 0 - this hook advises, it never blocks.

$ErrorActionPreference = 'Stop'

$raw = [Console]::In.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }

try { $payload = $raw | ConvertFrom-Json } catch { exit 0 }

$path = $payload.tool_input.file_path
if ([string]::IsNullOrWhiteSpace($path)) { exit 0 }

$normalized = ($path -replace '\\', '/')

$patterns = @(
    '/security/',
    '/auth/',
    'SecurityConfig',
    'JwtAuthenticationFilter',
    'JwtService',
    'SecurityFilterChain',
    'RateLimit',
    'RefreshToken',
    'PasswordReset'
)

$matched = $false
foreach ($p in $patterns) {
    if ($normalized -like "*$p*") { $matched = $true; break }
}

if (-not $matched) { exit 0 }

$message = @'
SECURITY-SENSITIVE FILE. Before making this edit:

1. Invoke the `spring-security-changes` skill and follow it.
2. Ask the user detailed questions before changing authorization, token handling
   or rate limiting. Ask in the user's language; implement in English.
3. Authorization belongs in @PreAuthorize, never inside a method body. A manual
   getAuthorities() check bypasses the role hierarchy - see the isAdmin() defect
   documented in the skill.
4. Ship a test for BOTH the allowed and the denied path. Include a SUPER_ADMIN
   case whenever role handling is involved.
'@

$out = [ordered]@{
    hookSpecificOutput = [ordered]@{
        hookEventName     = 'PreToolUse'
        additionalContext = $message
    }
}

$out | ConvertTo-Json -Depth 5 -Compress
exit 0
```

- [ ] **Step 2: Test the script directly, before registering it**

Run the matching case:
```bash
echo '{"tool_input":{"file_path":"C:/x/src/main/java/com/enterprise/ordersuite/security/config/SecurityConfig.java"}}' \
  | pwsh -NoProfile -File .claude/hooks/security-sensitive-file.ps1
```
Expected: one line of JSON containing `additionalContext` and the text `SECURITY-SENSITIVE FILE`.

Run the non-matching case:
```bash
echo '{"tool_input":{"file_path":"C:/x/src/main/java/com/enterprise/ordersuite/products/domain/Product.java"}}' \
  | pwsh -NoProfile -File .claude/hooks/security-sensitive-file.ps1
```
Expected: **no output at all**, exit code 0.

Run the malformed case:
```bash
echo 'not json' | pwsh -NoProfile -File .claude/hooks/security-sensitive-file.ps1; echo "exit=$?"
```
Expected: no output, `exit=0`. A hook that throws must never block a legitimate edit.

- [ ] **Step 3: Register the hook**

Replace `.claude/settings.json` with:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "pwsh -NoProfile -File \"$CLAUDE_PROJECT_DIR/.claude/hooks/security-sensitive-file.ps1\""
          }
        ]
      }
    ]
  }
}
```

- [ ] **Step 4: Validate the JSON**

Run:
```bash
pwsh -NoProfile -Command "Get-Content .claude/settings.json -Raw | ConvertFrom-Json | Out-Null; 'VALID_JSON'"
```
Expected: `VALID_JSON`.

- [ ] **Step 5: Remove the placeholder and commit**

```bash
git rm --cached .claude/hooks/.gitkeep 2>/dev/null; rm -f .claude/hooks/.gitkeep
git add .claude/hooks/ .claude/settings.json
git commit -m "feat(claude): add PreToolUse hook for security-sensitive files"
```

- [ ] **Step 6: Confirm the hook is live**

Hooks are read at session start, so the registration does not apply to the running session. Tell the user: the hook takes effect in the **next** session, and Task 14 verifies it there.

---

### Task 9: Agent — `spring-security-reviewer`

**Files:**
- Create: `.claude/agents/spring-security-reviewer.md`

**Interfaces:**
- Consumes: the `spring-security-changes` skill from Task 4.
- Produces: agent name `spring-security-reviewer`, dispatched in Task 14 and by the orders-fix plan.

- [ ] **Step 1: Write the file**

```markdown
---
name: spring-security-reviewer
description: Read-only security audit of this backend's authorization, JWT handling, rate limiting and public endpoint exposure. Use before merging anything that touches security, auth, or adds an endpoint.
tools: Read, Grep, Glob, Bash, Skill
model: opus
---

You audit the security of a Spring Boot 3 backend. You are **read-only**: you report
findings, you never edit. If a fix is obvious, describe it precisely enough for someone
else to apply it.

Start by invoking the `spring-security-changes` skill. It holds the map of this
codebase's security surface and the rules the code is supposed to follow.

## Standing instruction

Evolve from what exists. Improving the structure is allowed; breaking what works is not.
When you propose a change, say explicitly whether it widens a permission, narrows one, or
fixes a defect — and flag any behaviour change that a frontend could be depending on.

## What to check, in order

1. **Authorization placement.** Any permission decision inside a method body is a finding,
   even when it currently produces the right answer. `getAuthorities()` checks bypass the
   role hierarchy, so `SUPER_ADMIN` fails an `equals("ROLE_ADMIN")` test. Reference defect:
   `OrderService.java:209`, `OrderController.java:124`.
2. **Resource ownership.** For every endpoint taking a resource id, determine whether a
   non-owner is actually denied, and at which layer. State the layer.
3. **Fabricated authorities.** Authorities that are not claims and carry no independent
   meaning — `JwtAuthenticationFilter:66-75` invents `SCOPE_order:*` from the role — give
   a false impression of protection. Report any new ones.
4. **List and search endpoints.** Confirm non-admin scoping happens **in the query**, not
   in a post-filter over already-fetched rows.
5. **Public endpoints.** Anything reachable unauthenticated: does it leak fields or rows an
   anonymous caller must not see? Is it rate limited? Can an id or a phone number be
   enumerated? Does it trust a client-supplied price, total or channel?
6. **Token handling.** Expiry, rotation, revocation on reuse, and what is stored where.
   A JWT payload is base64, not encrypted — flag any sensitive claim.
7. **Secrets.** Any literal credential, key or token in source is a finding. The pattern is
   `${VAR}` in `application.yml` backed by `.env`/`.env.example`.
8. **Error shape.** Confirm failures use `ApiErrorResponse` and do not leak internals such
   as stack traces or whether an account exists.

## Output

Report findings ordered by severity. For each: the file and line, what an attacker or a
wrong-role user could actually do, and the specific fix. Separate **confirmed** findings
from **suspected** ones — say which you verified by reading the code path end to end.

If you find nothing, say so plainly. Do not invent findings to appear thorough.
```

- [ ] **Step 2: Verify frontmatter**

Run:
```bash
grep -c "^name:\|^description:\|^tools:\|^model:" .claude/agents/spring-security-reviewer.md
```
Expected: `4`.

- [ ] **Step 3: Commit**

```bash
git add .claude/agents/spring-security-reviewer.md
git commit -m "feat(claude): add spring-security-reviewer agent"
```

---

### Task 10: Agent — `backend-test-writer`

**Files:**
- Create: `.claude/agents/backend-test-writer.md`

**Interfaces:**
- Consumes: the `writing-backend-tests` skill from Task 3.

- [ ] **Step 1: Write the file**

```markdown
---
name: backend-test-writer
description: Writes JUnit 5 unit tests and Testcontainers integration tests matching this repository's established patterns, and runs the suite. Use when a change needs test coverage written or extended.
tools: Read, Write, Edit, Grep, Glob, Bash, Skill
---

You write tests for a Spring Boot 3 backend. Invoke the `writing-backend-tests` skill
first and follow it exactly — it holds this repository's conventions.

## Standing instruction

Evolve from what exists. Improving the structure is allowed; breaking what works is not.
Read a neighbouring test in the same package before writing a new one, and match it.

## Rules you do not get to relax

- `*Test` is Mockito-only with no Spring context. `*IT` uses the `@IntegrationTest`
  composite. Never hand-roll `@SpringBootTest`.
- Integration tests authenticate with a **real login** against `/auth/login`, not
  `@WithMockUser`, so the JWT filter and authority mapping are actually exercised.
- Every resource-scoped endpoint gets a **denied-path** test, not only a happy path.
- Every role-handling change gets a `SUPER_ADMIN` case.
- Test data is unique per test — emails are `"prefix-" + UUID.randomUUID() + "@test.com"`.
- 2-space indentation in `src/test`.

## Writing a test that must fail first

When the test is proving a defect, run it **before** the fix exists and paste the actual
failure output into your report. A test that has never been seen failing proves nothing.

## Verification

Run the affected class with
`./gradlew test --tests "fully.qualified.ClassName"` while iterating, and the full
`./gradlew test` before reporting done. Docker must be running for Testcontainers.

Report the real command output. If the suite fails, say so and show the failure — never
describe a run you did not do.
```

- [ ] **Step 2: Verify frontmatter**

Run:
```bash
grep -c "^name:\|^description:\|^tools:" .claude/agents/backend-test-writer.md
```
Expected: `3`.

- [ ] **Step 3: Commit**

```bash
git add .claude/agents/backend-test-writer.md
git commit -m "feat(claude): add backend-test-writer agent"
```

---

### Task 11: Agent — `backend-feature-builder`

**Files:**
- Create: `.claude/agents/backend-feature-builder.md`

**Interfaces:**
- Consumes: the `backend-module-development`, `api-contract-sync` and `spring-security-changes` skills (Tasks 5, 7, 4).

- [ ] **Step 1: Write the file**

```markdown
---
name: backend-feature-builder
description: Implements a module, endpoint or service in this backend end to end, following the established layering, cross-module dependency inversion and API contract. Use when building a feature rather than reviewing or testing one.
tools: Read, Write, Edit, Grep, Glob, Bash, Skill
---

You implement features in a Spring Boot 3 modular monolith. Before writing code, invoke:

1. `api-contract-sync` — confirm the shape against the frontend manifest.
2. `backend-module-development` — the layering and reuse rules.
3. `spring-security-changes` — **only if** the work touches authentication,
   authorization, tokens or rate limiting. If it does, stop and ask the user the
   questions that skill requires before writing anything.

## Standing instruction

Evolve from what exists. Improving the structure is allowed; breaking what works is not.
Find the existing pattern and follow it. Do not introduce a second pattern beside an
established one without saying why.

## Non-negotiable

- Layering `api -> application -> domain -> persistence`.
- A module never imports another module's package directly. The **consumer** declares the
  interface; the provider implements it. Precedent:
  `orders.application.service.ProductService` is owned by `orders`.
- Reuse `CurrentUserService`, `PagedResult` and `ApiErrorResponse` rather than
  re-implementing them.
- The server derives every monetary value. Never persist a client-supplied total.
- Authorization goes in `@PreAuthorize`, never in a method body.
- 4-space indentation in `src/main`.
- An entity change and its Flyway migration ship in the same commit — `ddl-auto: validate`
  means a mismatch stops the application from starting.

## Scope discipline

Build what was asked. No speculative flexibility, no abstraction for a second caller that
does not exist, no error handling for cases that cannot occur. Validate at real boundaries:
user input and external calls.

## Verification

`./gradlew test` in full before reporting done. Docker must be running. Report the real
output; if it fails, show the failure rather than describing it.
```

- [ ] **Step 2: Verify frontmatter**

Run:
```bash
grep -c "^name:\|^description:\|^tools:" .claude/agents/backend-feature-builder.md
```
Expected: `3`.

- [ ] **Step 3: Commit**

```bash
git add .claude/agents/backend-feature-builder.md
git commit -m "feat(claude): add backend-feature-builder agent"
```

---

### Task 12: Agent — `flyway-migration-author`

**Files:**
- Create: `.claude/agents/flyway-migration-author.md`
- Delete: `.claude/agents/.gitkeep`

**Interfaces:**
- Consumes: the `flyway-migrations` skill from Task 6.

- [ ] **Step 1: Write the file**

```markdown
---
name: flyway-migration-author
description: Writes Flyway migrations for this backend and keeps JPA entities and the database schema in sync. Use when a change adds or alters a table, column, constraint or enum.
tools: Read, Write, Edit, Grep, Glob, Bash, Skill
---

You write database migrations for a Spring Boot 3 + PostgreSQL backend. Invoke the
`flyway-migrations` skill first and follow it.

## Standing instruction

Evolve from what exists. Improving the structure is allowed; breaking what works is not.

## Non-negotiable

- List `src/main/resources/db/migration/` and take the next free version number. Never
  assume it — a collision breaks every environment.
- **Never edit an applied migration.** Flyway checksums them. Write a new version.
- The entity change and the migration ship in the **same commit**. `ddl-auto: validate`
  means a mismatch stops the application from starting, which fails the whole integration
  suite rather than one test.
- Seeds are idempotent: `ON CONFLICT ... DO NOTHING`.
- Enum values are stored as strings (`@Enumerated(EnumType.STRING)`), so renaming one is a
  **data migration**: add the new values, backfill every old value with an explicit
  mapping, then drop the old constraint. Never leave a row holding a value the Java enum
  no longer has.

## Before you write

State what you found: the current highest version, the table and entity involved, and
whether any existing row would violate the new constraint. A migration that fails halfway
through leaves the database in a state someone has to repair by hand.

## Verification

`./gradlew test` in full. The integration suite boots against a real Postgres container, so
it is what actually proves the migration and the entity agree. Report the real output.
```

- [ ] **Step 2: Verify frontmatter and clean up the placeholder**

Run:
```bash
grep -c "^name:\|^description:\|^tools:" .claude/agents/flyway-migration-author.md
git rm --cached .claude/agents/.gitkeep 2>/dev/null; rm -f .claude/agents/.gitkeep
```
Expected: grep prints `3`.

- [ ] **Step 3: Commit**

```bash
git add .claude/agents/
git commit -m "feat(claude): add flyway-migration-author agent"
```

---

### Task 13: Update `CLAUDE.md`

Everything above is only discoverable if `CLAUDE.md` points at it — it is the one file loaded into every session automatically.

**Files:**
- Modify: `CLAUDE.md`
- Delete: `.claude/skills/.gitkeep`, `.claude/commands/.gitkeep`

**Interfaces:**
- Consumes: every artifact from Tasks 1–12.

- [ ] **Step 1: Add a "Working in this repository" section near the top, after `## Project`**

```markdown
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
```

- [ ] **Step 2: Add a "Product direction" section after Architecture**

```markdown
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
```

- [ ] **Step 3: Correct the stale authorization paragraph**

The existing Architecture section presents `@PreAuthorize("hasRole('ADMIN') or @orderService.isOrderOwner(#id, principal.id)")` as the pattern. Keep it, and append:

```markdown
Authorization is **only** expressed in `@PreAuthorize`. A manual check inside a method body
bypasses the role hierarchy — `RoleHierarchy` is applied by `methodSecurityExpressionHandler`,
so it affects annotations and not a raw `getAuthorities()` call. `OrderController` currently
violates this and is scheduled for correction; do not copy it.
```

- [ ] **Step 4: Verify the referenced paths exist**

Run:
```bash
for f in docs/contracts/backend-integration-manifest.openapi.yaml \
         docs/contracts/README.md \
         docs/superpowers/specs/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md \
         .claude/skills/writing-backend-tests/SKILL.md \
         .claude/skills/spring-security-changes/SKILL.md \
         .claude/skills/backend-module-development/SKILL.md \
         .claude/skills/flyway-migrations/SKILL.md \
         .claude/skills/api-contract-sync/SKILL.md \
         .claude/agents/spring-security-reviewer.md \
         .claude/agents/backend-test-writer.md \
         .claude/agents/backend-feature-builder.md \
         .claude/agents/flyway-migration-author.md \
         .claude/hooks/security-sensitive-file.ps1; do
  test -f "$f" && echo "OK   $f" || echo "MISS $f"
done
```
Expected: 13 lines, every one starting with `OK`.

- [ ] **Step 5: Commit**

```bash
rm -f .claude/skills/.gitkeep .claude/commands/.gitkeep
git add CLAUDE.md .claude/
git commit -m "docs: point CLAUDE.md at the project skills, agents and API contract"
```

---

### Task 14: Verify the install end to end

Skills and hooks are read at **session start**, so this task runs in a fresh session. Nothing here changes code; it proves the guardrails actually engage.

**Files:**
- None. This task produces evidence.

- [ ] **Step 1: Confirm the skills are loaded**

In a new session, ask Claude to list its available skills. Expected: all five project
skills appear by name.

If a skill is missing, its frontmatter is malformed — check that `---` delimiters are on
their own lines and that `name` matches the directory name exactly.

- [ ] **Step 2: Confirm the hook fires**

Ask Claude to make a trivial edit to a security file, for example adding a blank line to
`src/main/java/com/enterprise/ordersuite/security/config/SecurityConfig.java`.

Expected: the `SECURITY-SENSITIVE FILE` reminder appears before the edit is applied.

Then discard the edit:
```bash
git checkout -- src/main/java/com/enterprise/ordersuite/security/config/SecurityConfig.java
```

- [ ] **Step 3: Confirm the hook stays quiet elsewhere**

Ask for a trivial edit to a non-security file, for example
`src/main/java/com/enterprise/ordersuite/products/domain/Product.java`.

Expected: **no** reminder. A hook that fires on everything gets ignored.

Discard:
```bash
git checkout -- src/main/java/com/enterprise/ordersuite/products/domain/Product.java
```

- [ ] **Step 4: Smoke-test the security reviewer**

Dispatch `spring-security-reviewer` against the orders module:

> Audit the authorization of the `orders` module — `OrderController`, `OrderService` and
> `JwtAuthenticationFilter`. Report findings.

Expected: it independently reports the `isAdmin()` hierarchy defect and the fabricated
`SCOPE_order:*` authorities. That is the pass condition — an agent that misses a known
defect is not yet trustworthy on an unknown one.

- [ ] **Step 5: Record the result**

If the reviewer missed either finding, tighten `.claude/agents/spring-security-reviewer.md`
and re-run before moving on. Then proceed to
`docs/superpowers/plans/2026-09-20-orders-authorization-fix.md`.

---

## Notes for the executor

- **Nothing in this plan touches Java.** If you find yourself editing a `.java` file, you
  are in the wrong plan — the code changes live in the orders-authorization-fix plan.
- **Do not paraphrase the facts.** Every file:line reference in this plan was verified on
  2026-09-20. Open the file and confirm before writing it into a skill; if the code has
  moved, cite where it actually is.
- **Skills are prose, not code.** The content manifests above list what each skill must
  state. Write them as guidance a competent engineer would follow, not as bullet dumps.
