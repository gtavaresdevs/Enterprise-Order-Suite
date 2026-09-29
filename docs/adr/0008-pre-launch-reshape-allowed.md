# ADR-0008: Pre-launch: schema and API may be reshaped freely
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
- Supersedes: none directly. It removes the backward-compatibility premise behind the legacy migration approach: "Auth migrates early, backward-compatible" (D8) and the frozen `/orders` permission matrix, "The frontend contract must not shift under a refactor" (D7) (PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L59-60, L152-156). Their final status is set in ADR-0000.
- Related: ADR-0000, ADR-0001, ADR-0002, ADR-0009, ADR-0010

## Context
- The app has no real users, orders or products; only a test profile exists (PF `planning/pm-tool-recommendation.md` L139; PF `planning/MASTER-PLAN.md` §3 D-8, L41: "0 users/orders/products").
- Known non-production records:
  - A verification user left in the backend instance used for phase 5 verification (`phase5-verify-test-delete-me@example.com`, PF `RESTAURANT-OPS-ROADMAP.md` L144-146).
  - Seeded roles (BE `src/main/resources/db/migration/V15__seed_super_admin.sql`).
  - The env-seeded root super admin.
- The legacy migration was designed around compatibility:
  - Auth was built backward-compatible, and the body `refreshToken` is kept "for older clients" until backend Phase 6 (PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L152-156; PF `2026-09-14-backend-integration-manifest.openapi.yaml` L393-394).
  - The `flyway-migrations` skill says "Never edit an applied migration" and teaches an enum-rename-without-data-loss pattern (BE `.claude/skills/flyway-migrations/SKILL.md` L26-30, L46).
  - The manifest's semver rules treat breaking changes as a major version (PF `2026-09-14-backend-integration-manifest.openapi.yaml` L1010).
- The target model differs from the live backend in ids, tenancy, order shape, status set and catalog (PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L134-148; ADR-0001, ADR-0002).
- pm-tool L139: "ULID primary keys, `restaurant_id` on every table and the Order Core reshape can be done directly (or by re-baselining Flyway) instead of through careful data migrations. Backward compatibility with the current API is not a constraint either."

## Decision
1. Until real data exists, the database schema and the API contract **may be changed in breaking ways**, with no data migration and no backward-compatibility layer.
2. The legacy B2B `/orders` and `/products` shapes are **replaced**, not evolved, by the Tenant, Menu and Order Core contracts (ADR-0007 build order).
3. The decision rests on the fact "no production data". When that stops being true, this ADR must be superseded, and normal migration and compatibility discipline returns.

## Consequences
- Allowed: breaking changes to tables, columns, keys, enums, endpoints and payloads; dropping legacy endpoints; replacing id types (ADR-0009).
- Must: breaking API changes still go through the backend-owned contract, and the frontend's generated types follow (ADR-0010). "Breaking is allowed" does not mean "uncoordinated".
- Must: keep verification green; a full `./gradlew test` before any "done" claim (D-16 / legacy D5).
- Never: write data migrations or compatibility shims for legacy B2B data pre-launch, for example the enum-rename-without-data-loss pattern for `PENDING..CANCELLED`.
- Never: keep deprecated fields or endpoints "for older clients" unless a live client in this project still needs them. The body `refreshToken` is removed in the auth cleanup salvaged into Tenant foundation (PF `planning/MASTER-PLAN.md` §4 L68).
- Never: apply this ADR once a real restaurant's data exists.
- How the Flyway history is handled (one re-baseline of V1-V21 before launch) and how the `flyway-migrations` skill rule changes are set by ADR-0009 (Accepted 2026-09-29). Until that skill amendment lands (with or before the re-baseline, ADR-0009 Consequences), the skill's rule stands.

## Open questions
- What event ends the reshape window: the first pilot restaurant, the first production deployment, or something else? Topic for the S5 scope page (PF `planning/MASTER-PLAN.md` §5 L76) and the API conventions (§5 L75: how drafts graduate to the live spec). Register: Q-32 (with Q-23, pilot timing).
- Flyway re-baseline vs incremental migrations: settled by ADR-0009 (Q-03 row 3.11, accepted 2026-09-29).
- Contract versioning and deprecation rules after launch (API conventions, S4): Q-32.

## Sources
Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`).
- PF `planning/MASTER-PLAN.md` §3 D-8 (L41), D-16 (L49), §4 (L67-68), §5 (L75-76)
- PF `planning/pm-tool-recommendation.md` L137-139
- PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L59-60, L134-156
- PF `2026-09-14-backend-integration-manifest.openapi.yaml` L393-394, L1010
- PF `RESTAURANT-OPS-ROADMAP.md` L144-146
- BE `.claude/skills/flyway-migrations/SKILL.md` L26-30, L46; `src/main/resources/db/migration/V15__seed_super_admin.sql`
