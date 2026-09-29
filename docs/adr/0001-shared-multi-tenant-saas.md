# ADR-0001: One shared multi-tenant SaaS
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
- Supersedes: Legacy (PF `2026-09-09-restaurant-ops-redesign-design.md`) "Decisions: Tenancy" (L43-45: single-tenant, one deployment = one restaurant, `SUPER_ADMIN` = owner, `ADMIN` = manager, `USER` = staff) and "Explicitly out of scope: Multi-location/multi-tenant support" (L309) as far as it excludes multi-tenancy
- Related: ADR-0000, ADR-0002, ADR-0003, ADR-0007, ADR-0008, ADR-0009, ADR-0014, ADR-0018

## Context
- The 2026-09-09 concept fixed one restaurant per deployment and excluded multi-tenancy (PF `2026-09-09-restaurant-ops-redesign-design.md` L43-45, L309).
- On 2026-09-29 Gabriel chose multi-restaurant as one shared SaaS (PF `planning/MASTER-PLAN.md` §3 D-1, L34). The option compared was instance-per-restaurant (own server and database each) vs shared (one deployment, every row carries `restaurant_id`, flat ops cost, isolation enforced in every query) (PF `planning/pm-tool-recommendation.md` L76-88).
- pm-tool L76 labels "plan-based packages" as decided in the same heading. MASTER-PLAN §4 (L63) defers packages. This ADR covers tenancy only; packages are ADR-0018 (Accepted: deferred).
- The code has no tenant concept:
  - Backend: none of the 11 tables created by V1-V21 has a restaurant column, and there is no restaurants table (BE `src/main/resources/db/migration/`). Each user holds one global role (BE `V2__create_users_table.sql` L19). JWT claims are `userId`, `roles`, names and email, with no tenant (BE `security/jwt/JwtService.java` L29-34). `POST /auth/register` is public and creates a user (BE `auth/controllers/AuthenticationController.java` L36-38).
  - Frontend: public routes `/storefront`, `/checkout`, `/kds`, `/table-menu` and `/track-order` carry no restaurant key (FE `src/app/router.tsx` L54-70). Owner logo, cover, brand color, WhatsApp number and delivery zones live in the owner's own `localStorage` (FE `src/types/preferences.ts` L17-21; `src/features/preferences/services/preferences.service.ts` L7, L17). The storefront reads them from the viewer's own preferences (FE `src/features/storefront/components/StorefrontFeature.tsx` L24), so a customer on another device never sees them.
- The legacy timezone fallback (a settings row, then the `restaurant.timezone` property, then a failed startup) assumes one restaurant per deployment (PF `2026-09-24-restaurant-ops-phase-0-foundation-design.md` L120-146). ADR-0000 triages it.

## Decision
1. One deployment and one database serve every restaurant. The restaurant is the tenant.
2. Restaurant-owned data carries the restaurant id, and every read and write of restaurant-owned data is scoped to the caller's restaurant ("`restaurant_id` everywhere", PF `planning/pm-tool-recommendation.md` L86, L123).
3. The application enforces isolation. The Tenancy & Identity contract (S4; PF `planning/MASTER-PLAN.md` §5 L74, §6 L91) defines the mechanism, the user and role model, and how a public page finds its restaurant. This ADR does not.
4. One deployment per restaurant is rejected.

## Consequences
- Must: give every new restaurant-owned table and entity a restaurant id. Whether child rows (for example order lines) carry it directly is decided in the Tenancy & Identity contract.
- Must: scope every query on restaurant-owned data to the current restaurant. A missing restaurant context fails closed. The existing precedent: non-admin order search refuses a null scope (BE `orders/application/service/OrderService.java` L113-119).
- Must: meet the Build 1 acceptance criteria: an ArchUnit tenant rule, cross-tenant tests, tenant id in logs and a two-restaurant dev/test seed that never runs in production (PF `planning/MASTER-PLAN.md` §6 L93, §5 L81). This list is Claude's plan, reviewed by Gabriel in S1; moving ArchUnit from the readiness gate into Build 1 was accepted with Q-03 row 3.8 (2026-09-29).
- Must: resolve the restaurant from the request on every `/public/*` endpoint once the addressing is chosen. Never reuse admin queries there, and add a leak test per public endpoint (legacy rule, PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L72-73).
- Never: store a restaurant-specific value as deployment configuration (env var, `application.yml`) or in a global singleton row. Restaurant settings belong to the restaurant, in a typed backend schema (ADR-0014).
- Never: treat a browser's `localStorage` as the store for restaurant configuration. The values listed above move to backend restaurant settings (ADR-0014).
- Never: assume the legacy mapping `SUPER_ADMIN` = owner, `ADMIN` = manager, `USER` = staff. It was defined for one restaurant per deployment; the replacement is open (see below).
- Never: introduce Postgres row-level security, subdomain or custom-domain routing, or a tenant-resolution scheme before the Tenancy & Identity contract chooses one.
- Never: add plan, package or billing machinery to the restaurant entity (ADR-0018).

## Open questions
Tenancy & Identity batch (PF `planning/MASTER-PLAN.md` §8b L116, §5 L74). Ids from the register, PF `planning/open-questions.md` group 2.
- Q-16: can a user belong to one restaurant or to many?
- Q-17: are roles per restaurant, with a separate platform admin? Today `SUPER_ADMIN`/`ADMIN`/`USER` are global. What the platform admin may see: Q-18.
- Q-19: what does public signup do: create a restaurant, work by invitation only, or go away?
- Q-20: how does the storefront find its restaurant? (Slug ownership: Q-31.) The recommendation is the path `/r/{slug}` first, with subdomain or custom domain later. The choice also affects cookie, CORS and hosting settings.
- Q-21: Brazil only for now (BRL, pt-BR, Brazilian address, an IANA timezone per restaurant)?
- Q-22: single location or chains? The 2026-09-09 "one independent restaurant, not a chain" (L46-47) stays in force until this is answered.
- Q-25 (Claude decides, Gabriel may override): isolation mechanism: application-level scoping plus a cross-tenant test per endpoint (recommended, MASTER-PLAN §5 L74), or Postgres RLS.

## Sources
Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`), Java paths under `src/main/java/com/enterprise/ordersuite/`; FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `planning/MASTER-PLAN.md` §3 D-1 (L34), §4 (L63), §5 (L74, L81), §6 (L91, L93), §8b (L116)
- PF `planning/pm-tool-recommendation.md` L59, L76-88, L123
- PF `2026-09-09-restaurant-ops-redesign-design.md` L43-47, L309
- PF `2026-09-24-restaurant-ops-phase-0-foundation-design.md` L120-146
- PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L72-73
- BE `src/main/resources/db/migration/` (V1-V21), `V2__create_users_table.sql` L19, `security/jwt/JwtService.java` L29-34, `auth/controllers/AuthenticationController.java` L36-38, `orders/application/service/OrderService.java` L113-119
- FE `src/app/router.tsx` L54-70, `src/types/preferences.ts` L17-21, `src/features/preferences/services/preferences.service.ts` L7, L17, `src/features/storefront/components/StorefrontFeature.tsx` L24
