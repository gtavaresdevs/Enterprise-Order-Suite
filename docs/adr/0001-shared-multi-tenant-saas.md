# ADR-0001: One shared multi-tenant SaaS
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel. Tenancy details (Decision 5): his register answers of 2026-10-01; Q-26 decided by Claude on 2026-10-01 at his request
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
3. The application enforces isolation. The Tenancy & Identity contract (S4; PF `planning/MASTER-PLAN.md` §5 L74, §6 L91) defines the mechanism, the user and role model, and how a public page finds its restaurant. This ADR does not; it lists the choices Gabriel made for that contract on 2026-10-01 in Decision 5.
4. One deployment per restaurant is rejected.
5. Decided later (2026-10-01; Gabriel's register answers unless marked; the Tenancy & Identity contract records the detail):
   - Q-16 a: a user belongs to one restaurant for now, stored in a membership table so many can come later.
   - Q-17 a: roles are a platform admin (today's env-seeded root `SUPER_ADMIN`), separate from the per-restaurant roles owner, manager and staff.
   - Q-18 b: the platform admin has full read access for support. Its LGPD implications are handled by the NFR page.
   - Q-19 a: signup is invite-only: the platform admin creates the restaurant and its owner, and the owner invites staff.
   - Q-20 a: the storefront finds its restaurant by the path `/r/{slug}` on one domain. Q-31 a: the platform admin sets the slug at creation from the restaurant name; it is unique and does not change this run.
   - Q-21 a: Brazil only for now: BRL, pt-BR default (EN kept), Brazilian address, +55 phone numbers, an IANA timezone per restaurant.
   - Q-22 a: one tenant = one restaurant location.
   - Q-25 a: isolation is application-level scoping (`restaurant_id` on every tenant-owned table) plus one cross-tenant test per endpoint and an ArchUnit rule; no Postgres row-level security.
   - Q-26 a (decided by Claude; Gabriel delegated it: "confusing, not sure, follow best practices considering our app context"): a staff request's restaurant comes only from the signed-in user's membership, on the server, never from the path or body; a public request's restaurant comes from the `/r/{slug}` path (Q-20 a). Reason: a client-supplied restaurant id is the classic cross-tenant hole, and a public request has no signed-in user.
   - Q-27 a: restaurant settings are the IANA timezone, currency, storefront default language, branding, WhatsApp number, delivery zones and ordering settings (Q-67); theme, font size, sidebar, dense tables and UI language belong to the user or device.
   - Q-28 a: an IANA zone is required when the restaurant is created (default `America/Sao_Paulo`); there is no deployment-wide fallback; `businessDate` is stamped in it (legacy D10, ADR-0000).

## Consequences
- Must: give every new restaurant-owned table and entity a restaurant id. Whether child rows (for example order lines) carry it directly is decided in the Tenancy & Identity contract.
- Must: scope every query on restaurant-owned data to the current restaurant. A missing restaurant context fails closed. The existing precedent: non-admin order search refuses a null scope (BE `orders/application/service/OrderService.java` L113-119).
- Must: meet the Build 1 acceptance criteria: an ArchUnit tenant rule, cross-tenant tests, tenant id in logs and a two-restaurant dev/test seed that never runs in production (PF `planning/MASTER-PLAN.md` §6 L93, §5 L81). This list is Claude's plan, reviewed by Gabriel in S1; moving ArchUnit from the readiness gate into Build 1 was accepted with Q-03 row 3.8 (2026-09-29).
- Must: resolve the restaurant from the `/r/{slug}` path on every public endpoint (Q-20 a, Q-26 a). Never reuse admin queries there, and add a leak test per public endpoint (legacy rule, PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L72-73).
- Never: take a staff request's restaurant from a client-sent restaurant id (path, body or header); it comes from the signed-in user's membership (Q-26 a).
- Never: store a restaurant-specific value as deployment configuration (env var, `application.yml`) or in a global singleton row. Restaurant settings belong to the restaurant, in a typed backend schema (ADR-0014).
- Never: treat a browser's `localStorage` as the store for restaurant configuration. The values listed above move to backend restaurant settings (ADR-0014).
- Never: assume the legacy mapping `SUPER_ADMIN` = owner, `ADMIN` = manager, `USER` = staff. It was defined for one restaurant per deployment; the replacement is a platform admin plus per-restaurant roles (Q-17 a, Decision 5).
- Never: introduce Postgres row-level security, or subdomain or custom-domain routing, this run. The 2026-10-01 answers chose application-level scoping (Q-25 a) and the path `/r/{slug}` (Q-20 a); changing either needs a new decision by Gabriel.
- Never: add plan, package or billing machinery to the restaurant entity (ADR-0018).

## Open questions
Tenancy & Identity batch (PF `planning/MASTER-PLAN.md` §8b L116, §5 L74). Ids from the register, PF `planning/open-questions.md` group 2.
- Q-16: answered 2026-10-01, a: one restaurant per user for now, in a membership table.
- Q-17: answered 2026-10-01, a: a platform admin separate from the per-restaurant roles owner, manager and staff.
- Q-18: answered 2026-10-01, b: the platform admin has full read access for support (LGPD handling: NFR page).
- Q-19: answered 2026-10-01, a: invite-only; the platform admin creates the restaurant and its owner, and the owner invites staff.
- Q-20: answered 2026-10-01, a: path `/r/{slug}` on one domain.
- Q-21: answered 2026-10-01, a: Brazil only for now (BRL, pt-BR default, Brazilian address, +55, an IANA timezone per restaurant).
- Q-22: answered 2026-10-01, a: one tenant = one restaurant location; the 2026-09-09 "not a chain" (L46-47) stands.
- Q-25: answered 2026-10-01, a: application-level scoping plus a cross-tenant test per endpoint and an ArchUnit rule.
- Q-26: decided by Claude 2026-10-01, a (delegated by Gabriel): staff requests take the restaurant from the user's membership; public requests from the `/r/{slug}` path.
- Q-27: answered 2026-10-01, a: the restaurant/user settings split in Decision 5.
- Q-28: answered 2026-10-01, a: IANA zone required at creation (default `America/Sao_Paulo`), no deployment-wide fallback.
- Q-31: answered 2026-10-01, a: the platform admin sets the slug at creation; unique; unchanged this run.
- Q-30: answered 2026-10-06, a (project thread, 2026-10-06T18:12Z: "2- Q30 - a"): Build 1 creates the restaurant, the typed settings schema and the timezone; branding, ordering settings and delivery zones land in Build 4.

## Sources
Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`), Java paths under `src/main/java/com/enterprise/ordersuite/`; FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `planning/MASTER-PLAN.md` §3 D-1 (L34), §4 (L63), §5 (L74, L81), §6 (L91, L93), §8b (L116)
- `planning/open-questions.md` group 2 (Q-16..Q-22, Q-25..Q-28, Q-30, Q-31): Gabriel's answers of 2026-10-01 (project thread, 2026-10-01T16:25Z); Q-30 on 2026-10-06 (project thread, 18:12Z)
- PF `planning/pm-tool-recommendation.md` L59, L76-88, L123
- PF `2026-09-09-restaurant-ops-redesign-design.md` L43-47, L309
- PF `2026-09-24-restaurant-ops-phase-0-foundation-design.md` L120-146
- PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L72-73
- BE `src/main/resources/db/migration/` (V1-V21), `V2__create_users_table.sql` L19, `security/jwt/JwtService.java` L29-34, `auth/controllers/AuthenticationController.java` L36-38, `orders/application/service/OrderService.java` L113-119
- FE `src/app/router.tsx` L54-70, `src/types/preferences.ts` L17-21, `src/features/preferences/services/preferences.service.ts` L7, L17, `src/features/storefront/components/StorefrontFeature.tsx` L24
