# ADR-0000: Legacy decisions triage
- Status: Proposed
- Date: 2026-09-29
- Decided by: Claude (proposed). Rows whose note starts with "Basis: D-16" or "Basis: D-x" restate a decision Gabriel already made (MASTER-PLAN §3); every other verdict is Claude's triage.
- Supersedes: none directly. Each row below names the ADR that supersedes that legacy decision.
- Related: ADR-0001, ADR-0002, ADR-0005, ADR-0006, ADR-0007, ADR-0008, ADR-0009, ADR-0010, ADR-0012, ADR-0013, ADR-0014, ADR-0015, ADR-0018, ADR-0019

## Context
- Legacy decisions are spread over five places, most of them without a status that says whether they still apply:
  - Backend D1-D8: "Decisions taken", PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L47-60 (doc status "approved (design)", L4).
  - Backend D9-D15 including D10a: "Decisions taken", PF `2026-09-24-restaurant-ops-phase-0-foundation-design.md` L75-89 (doc status "implemented", L4; only D13 and D15 were built in phase 0, D12 in phase 1; D9, D10, D10a, D11 target phases 2-4, which never started).
  - Backend D16-D24: "Decisions taken", PF `2026-09-25-restaurant-ops-phase-1-auth-design.md` L80-204 (doc status "implemented", L4).
  - Frontend "Decisions (confirmed with user, 2026-09-09)", PF `2026-09-09-restaurant-ops-redesign-design.md` L41-87. They have no ids; this ADR assigns FS-01..FS-14 (local to this ADR).
  - Manifest 0.4.0 `x-open-decisions`, FE `docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml` L978-1003: five entries marked decided, two still `open`.
  - Other user decisions found while verifying: PF `2026-09-20-test-failures-and-audit-followups.md` L87-99, L124-130; FE `docs/superpowers/specs/2026-09-15-core-package-br-i18n-ux-design.md`; FE `docs/superpowers/specs/2026-09-15-dev-tooling-workflow-design.md`. Ids AF-, CP-, DT- are local to this ADR.
- Id collision: legacy ids are `D1`..`D24` (no hyphen). MASTER-PLAN ids are `D-1`..`D-16` (hyphen). They are unrelated: legacy D9 (int64 ids) is not D-9 (backend owns the contract).
- MASTER-PLAN §3 D-16 (L49) already keeps: D2, D5, D14, D15, D16-D24, "server derives every money value", "order lines snapshot name and price", "cancel is a status change". Those rows are Accepted by Gabriel.
- MASTER-PLAN §5 (L72) asks for this triage; §4 (L68) marks legacy backend phases 2-6, the frontend roadmap's "read first"/"what's next" and the frontend-owned manifest rule as superseded, with salvage of phase 5 and phase 6. The doc-level supersession list is `planning/superseded-docs.md`.
- Build state checked on the working branches (BE `af2634e`, FE `14a3cfd`), 2026-09-29:
  - No `restaurant.timezone`, `RESTAURANT_TIMEZONE`, `businessDate` or `business_date` in BE `src/` or `.env.example` (grep): D10 and D10a are not built.
  - No `menu_categories` in BE `src/main/resources/db/migration/` (highest version V21): D11 is not built.
  - Live ids are still `BIGSERIAL`/`IDENTITY` (ADR-0009 Context): D9 is in force only by default.
  - `OrderController` carries only `@PreAuthorize` (BE `orders/api/OrderController.java` L36-99): the D2 drift that D7 froze around is fixed.

## Decision
Verdicts:
- **Kept**: still in force. Agents apply it.
- **Superseded by ADR-XXXX**: agents do not apply it; they follow that ADR. "(when Accepted)" means the replacing ADR is Proposed: until Gabriel accepts it, the question is open, and agents write no new code that depends on either answer (no new code is written before the §7 gate anyway, ADR-0015). Since Q-03 was answered (2026-09-29) no row carries it: ADR-0009, ADR-0018 and ADR-0019 are Accepted.
- **Pending re-examination**: existing code keeps working as built, but agents do not extend the decision into new design. The named contract or step decides it.

This ADR stays Proposed until Gabriel accepts it in S1 (MASTER-PLAN §6 S1, §7 L99). The "Basis: D-16" rows are already Accepted and do not wait for S1.

### A. Backend D1-D8 (PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md`)

| id | Short statement | Source | Verdict | Note |
|---|---|---|---|---|
| D1 | `.claude/` is versioned; only `settings.local.json` is ignored | L53 | Kept | Basis: triage; matches MASTER-PLAN §6 S3 and §7 ("Frontend `.claude/` is in git"), which extend it to the frontend (ADR-0015). Frontend still ignores `.claude/` (FE `.gitignore` L17). |
| D2 | Authorization lives only in `@PreAuthorize`, never in a method body | L54 | Kept | Basis: D-16. The resource-ownership helper `isOrderOwner` is legacy and goes away with the anonymous-customer model (L146-148); how tenant scoping is expressed is set by the Tenancy & Identity contract (ADR-0001). |
| D3 | Indentation: 4 spaces in `src/main`, 2 in `src/test` | L55 | Kept | Basis: triage. |
| D4 | Skills teach the target model and mark legacy explicitly | L56 | Kept | Basis: triage. The principle stands; the "target" the backend skills teach today is the 2026-09-20 single-restaurant, frontend-manifest target, which is superseded. Skills are updated in S3 (`planning/superseded-docs.md`). |
| D5 | Full `./gradlew test` before any completion claim | L57 | Kept | Basis: D-16. |
| D6 | Questions in the user's language; code and docs in English | L58 | Kept | Basis: D-10; restated by ADR-0012. |
| D7 | The `/orders` permission matrix is frozen; only the `isAdmin()` defect changes | L59, L118-130 | Superseded by ADR-0008 | Basis: triage on D-8. The freeze protected the 2026-09-20 authorization refactor, which is done (PF `2026-09-20-orders-authorization-fix.md` status L5-12). Legacy `/orders` is replaced, not evolved (ADR-0008 Decision 2); the role model is redesigned in Tenancy & Identity. The ITs that pin the matrix stay green until the endpoints are replaced; no new work preserves the matrix. |
| D8 | Auth migrates early and backward-compatible (cookie issued and body token kept) | L60, L150-156 | Superseded by ADR-0008 | Basis: triage on D-8. "Early" is done (phase 1 implemented). Backward compatibility is no longer a constraint. The interim body token ends with the phase 6 auth cleanup, salvaged into Tenant foundation (MASTER-PLAN §4 L68). |

### B. Backend D9-D15 (PF `2026-09-24-restaurant-ops-phase-0-foundation-design.md`)

| id | Short statement | Source | Verdict | Note |
|---|---|---|---|---|
| D9 | `id-type`: backend keeps int64; frontend ids become `number`; `Order.orderNumber` is the human display and `/track-order` key; opaque public ids rejected | L82, L91-106 | Superseded by ADR-0009 | Not built (phases 2-4 never started). ADR-0009 keeps the idea of a human order number but makes it server-generated and unique per restaurant; the order number format is an Order Core question (MASTER-PLAN §8c). |
| D10 | `createdAt` is a UTC instant; the server stamps `businessDate` (restaurant zone) once at creation, never recomputed | L83, L108-156 | Pending re-examination | Order Core contract. The instant part is D15 (Kept). `businessDate` is not built; it works under ADR-0001 if the zone is per restaurant; recommendation: keep it. Zone source: D10a. |
| D10a | Restaurant timezone is admin-editable in `RestaurantSettings`; config property `restaurant.timezone` is the only fallback; an unset zone fails startup | L84, L120-146 | Superseded by ADR-0001 | A deployment-level fallback and a fail-at-startup rule assume one restaurant per deployment; ADR-0001 forbids restaurant values in deployment config. What carries over: an admin-editable IANA zone per restaurant, never the host's system zone (L130-136). Per-restaurant zone at onboarding: Tenancy & Identity (MASTER-PLAN §8b). Not built. |
| D11 | `category-identity`: `menu_categories(id, name UNIQUE)` with FK; wire contract stays name-based, id never exposed | L86, L158-172 | Pending re-examination | Menu contract. With ULIDs and possible offline sync, a stable category id on the wire may be needed (ADR-0009 Open questions). Not built. |
| D12 | `dev-cookie-secure`: env-bound cookie and CORS properties with production-safe defaults, not derived from the Spring profile | L87, L174-196 | Kept | Basis: triage. Built in phase 1 (PF `2026-09-25-restaurant-ops-phase-1-auth-design.md` L206-212; BE `application.yml` L85-89). Values may change with storefront addressing (cross-site means `SameSite=None; Secure` plus an anti-CSRF token): Tenancy & Identity (MASTER-PLAN §8b). |
| D13 | Item edits are accepted only while an order is open; otherwise 409 `ORDER_NOT_EDITABLE` | L88, L198-214 | Kept | Basis: triage. Built in phase 0 on legacy statuses (open = `PENDING`, `PROCESSING`); scheduled there by the user's 2026-09-24 decision (PF `2026-09-20-test-failures-and-audit-followups.md` L87-88). The Order Core contract maps "open" onto the new status set (MASTER-PLAN §8c). |
| D14 | Error codes are `SCREAMING_SNAKE` everywhere | L89, L261-266 | Kept | Basis: D-16. |
| D15 | Every persisted timestamp is an `Instant` on a `timestamptz` column; `Z` on the wire | L85, L216-259 | Kept | Basis: D-16. Built (V20). |

### C. Backend D16-D24 (PF `2026-09-25-restaurant-ops-phase-1-auth-design.md`)

All built in backend phase 1. Basis for every row: D-16.

| id | Short statement | Source | Verdict | Note |
|---|---|---|---|---|
| D16 | Refresh-token families; reuse of a used or revoked token revokes the whole family | L82-95 | Kept | |
| D17 | Strict reuse detection, no grace window; rotation row-locked in one transaction | L96-110 | Kept | The frontend single-flight it requires is done (FE `docs/superpowers/plans/2026-09-25-auth-refresh-cookie-cross-tab.md`; FE HEAD `14a3cfd`). |
| D18 | Refresh token source: cookie first, body as fallback | L112-126 | Kept | The body fallback is interim by its own text (L77, L376-377). It is removed by the phase 6 auth cleanup, salvaged into Tenant foundation (MASTER-PLAN §4 L68). |
| D19 | `Origin` validated only on cookie-borne `/auth/refresh` and `/auth/logout`; 403 `ORIGIN_NOT_ALLOWED` | L128-146 | Kept | Becomes universal with the same cleanup (L78, L137-139). |
| D20 | Invalid refresh answers 401 `INVALID_REFRESH_TOKEN` | L148-153 | Kept | |
| D21 | Password reset revokes every refresh token of the user | L155-159 | Kept | |
| D22 | Path-matching filters use the path within the application (context path stripped) | L161-178 | Kept | |
| D23 | Client-IP trust is explicit, env-bound, off by default (`SERVER_FORWARD_HEADERS_STRATEGY:none`) | L180-195 | Kept | Deploying behind a proxy requires `native`: launch checklist (`planning/superseded-docs.md` §5). |
| D24 | Used and revoked tokens are kept until their own expiry | L197-204 | Kept | |

### D. Rules named in D-16 that have no legacy id

| id | Short statement | Source | Verdict | Note |
|---|---|---|---|---|
| LR-1 | The server derives every money value; a client-supplied total is never persisted | PF `2026-09-20-...-migration-design.md` L83-84, L144; BE `.claude/skills/backend-module-development/SKILL.md` L50-57 | Kept | Basis: D-16. Also user decision AF-2. |
| LR-2 | Order lines snapshot item name and unit price at order time | BE `backend-module-development/SKILL.md` L59-60; PF phase-0 design L150-152; PF `2026-09-16-business-rules-master-en.md` L185 | Kept | Basis: D-16. |
| LR-3 | Cancelling an order is a status change, never a hard delete | PF `2026-09-16-business-rules-master-en.md` L26 (rule 9); PF `RESTAURANT-OPS-ROADMAP.md` L108-109 | Kept | Basis: D-16. The legacy backend still hard-deletes on `DELETE /orders/{id}` (ADMIN; BE `orders/api/OrderController.java` L97-100, `OrderService.java` L245-251). It is replaced by Order Core, not extended (ADR-0008). |
| LR-4 | `/public/*` never reuses an admin-scoped query; every resource-scoped endpoint gets a denied-access test and every `/public/*` endpoint a leak test | PF `2026-09-20-...-migration-design.md` L72-73; BE `.claude/skills/spring-security-changes/SKILL.md` L136-140 | Kept | Basis: triage; restated in ADR-0001 Consequences. Not in D-16's list. |

### E. Frontend decisions of 2026-09-09 (PF `2026-09-09-restaurant-ops-redesign-design.md`)

| id | Short statement | Source | Verdict | Note |
|---|---|---|---|---|
| FS-01 | Single-tenant: one deployment = one restaurant; `SUPER_ADMIN` = owner, `ADMIN` = manager, `USER` = staff; no multi-tenant concept | L43-45; also "out of scope: multi-tenant" L309 | Superseded by ADR-0001 | Basis: D-1. The role mapping is not carried over; roles are decided in Tenancy & Identity (MASTER-PLAN §8b). |
| FS-02 | One independent restaurant, not a chain; no multi-location modeling | L46-47 | Pending re-examination | Tenancy & Identity: "single location or chains" (MASTER-PLAN §8b). Stays in force until answered (ADR-0001 Open questions). |
| FS-03 | Customers are anonymous (no account); each order captures name and phone; customers never appear in `/users` | L48-51 | Pending re-examination | Order Core: Customer as a per-restaurant record or a snapshot on each order (MASTER-PLAN §8c); LGPD basics (§5 L77). "No customer login" is not challenged by any ADR. The phone's stated use, automatic status messages, is Out (ADR-0006). |
| FS-04 | All channels funnel into one `Order` model and one KDS queue | L52-55 | Kept | Basis: D-2; generalized by ADR-0002. |
| FS-05 | Channel values `Online` / `Dine-in` / `Phone` | L52-55 | Pending re-examination | Order Core: channel/source mapping, including staff phone orders (MASTER-PLAN §8c); wire casing: API conventions (§5 L75). ADR-0002 keeps the legacy vocabulary until then. |
| FS-06 | Fulfillment `Pickup` / `Delivery` only for `Online`/`Phone`; `Dine-in` carries a table instead | L56-57 | Pending re-examination | Same forum as FS-05. |
| FS-07 | The Menu is configured once and served to storefront, QR view and staff entry | L58-60 | Kept | Basis: D-2 (ADR-0002). |
| FS-08 | Inventory folds into Menu: `stockQuantity` on the item, no ingredient-level inventory, Inventory page becomes a Menu view | L61-65 | Pending re-examination | Menu contract: keep stock counts or only the 86 toggle (MASTER-PLAN §8c). |
| FS-09 | Tables are a settings list (name + QR), no live floor or occupancy view | L66-69 | Kept | Basis: triage (scope of the Tables feature). QR/table context is Later (ADR-0007; MASTER-PLAN §6 L95); whether a minimal Table enters Order Core is open (§8c). |
| FS-10 | `Online` orders must pay in-app (card/PIX) before confirmation; `Dine-in`/`Phone` default to pay-later | L70-72 | Superseded by ADR-0005 | Basis: D-5, D-6 (PSP out, ADR-0006). Already revised on 2026-09-15 to in-person methods (CP-1). What a payment record holds and how online customers pay this run: MASTER-PLAN §8c. |
| FS-11 | Every order status change sends the customer a WhatsApp message through a provider | L73-76 | Superseded by ADR-0006 | Basis: D-6. Manual `wa.me` links stay. |
| FS-12 | Administration is reshaped around the real backend: Team, Roles reference, Audit Log | L77-81 | Kept | Basis: triage. Built on the real backend in frontend phase 5 (PF `RESTAURANT-OPS-ROADMAP.md` L124-146). Per-restaurant roles and membership: Tenancy & Identity. |
| FS-13 | Profile stays as-is | L82-83 | Kept | Basis: triage. Correction: the spec calls avatar upload real; the frontend `updateAvatar` is a mock (FE `src/features/profile/services/profile.service.ts` L26-27) while the backend endpoint exists. Goes to the S5 audit triage. |
| FS-14 | Scope discipline: not a full rebuild; keep and extend what already works | L84-87 | Kept | Basis: D-6 ("improve existing features") and MASTER-PLAN §2 ("document it, don't rebuild it"). |

### F. Manifest `x-open-decisions` marked decided (FE manifest 0.4.0)

| id | Short statement | Source | Verdict | Note |
|---|---|---|---|---|
| MF-base-path | Base path `/api`, no version segment (decided 2026-09-19) | L979-981 | Kept | Basis: triage. Matches BE `application.yml` L4 (`${SERVER_CONTEXT_PATH}`). Recorded again in the API conventions doc (S4). |
| MF-id-type | Backend keeps int64; frontend ids become `number`; `orderNumber` for tracking (decided 2026-09-24) | L982-985 | Superseded by ADR-0009 | Same decision as D9. |
| MF-category-identity | Normalized category table with FK; name-based wire contract (decided 2026-09-24) | L986-989 | Pending re-examination | Same decision as D11 (Menu contract). |
| MF-created-at-format | `createdAt` UTC instant; `businessDate` stamped once in `RestaurantSettings.timezone`; config fallback, refuse to start if unset (decided 2026-09-24) | L990-993 | Pending re-examination | Mixed: the instant is D15 (Kept); `businessDate` is D10 (Pending); the config fallback is D10a (Superseded by ADR-0001). |
| MF-dev-cookie-secure | Env-bound cookie/CORS properties with production-safe defaults (decided 2026-09-24) | L1000-1003 | Kept | Same decision as D12. Its note on `SameSite=None` plus anti-CSRF for cross-site deployments goes to the launch checklist. |

Not triaged here because they were never decided: `payment-sequencing` (L994-996; reframed, not closed, by ADR-0005) and `settings-images` (L997-999; open, blocks logo and cover in ADR-0014). The manifest's auth descriptions marked "DECIDED 2026-09-25" restate D16-D21 (changelog L1021) and follow rows C.

### G. Other legacy decisions found

| id | Short statement | Source | Verdict | Note |
|---|---|---|---|---|
| AF-1 | Narrow the `/orders` endpoints to `hasAnyRole('USER','ADMIN')` (user, 2026-09-20) | PF `2026-09-20-test-failures-and-audit-followups.md` L126 | Superseded by ADR-0008 | Built (BE `OrderController.java` L36-84). Legacy `/orders` is replaced; roles are redesigned in Tenancy & Identity. |
| AF-2 | Fix stock and server-side pricing now, not at the migration (user, 2026-09-20) | same, L127 | Kept | Pricing part = LR-1 (Basis: D-16). The stock part (decrement on create, increment on cancel) follows FS-08 (Pending). |
| AF-3 | The root super admin is env-driven (`SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD_HASH`); `V15` may be edited in place, local/dev only (user, 2026-09-20) | same, L128-130 | Pending re-examination | Tenancy & Identity: platform admin vs restaurant roles (MASTER-PLAN §8b). The env-seeded root admin is deployment-wide, so under ADR-0001 it reads as a platform admin (inference, unconfirmed). |
| AF-4 | Rotating the exposed seeded super-admin password is waived for local development; revisit before any deployment with a real credential (user, 2026-09-24) | same, L94-96; PF phase-0 design L430-431 | Kept | Basis: triage. Kept as a launch-checklist item (`planning/superseded-docs.md` §5). |
| CP-1 | All Core payment methods are in-person (PIX through a mock QR screen, card and cash collected physically); payment counts as received when staff completes the order | FE `specs/2026-09-15-core-package-br-i18n-ux-design.md` L109-143 | Pending re-examination | In-person card and cash match ADR-0005 (terminals authorize on their own; the app records). The mock PIX QR and "waiting for confirmation" screen (L135-138) imply a provider; PSP is Out (ADR-0006). Payment record content and storefront options this run: MASTER-PLAN §8c. |
| CP-2 | In-app card payment is not built now; it lands later with a PSP in "Package 10 — Integrações & Plugins" | same, L144-152, L348-351 | Superseded by ADR-0005 | Basis: D-5, D-6 (ADR-0006: PSP out this run). The app never processes payments; it integrates with existing systems. |
| CP-3 | The product grows into 10 separately sellable packages | same, L7-10, L328-351 | Pending re-examination | Pricing and packages are Later (MASTER-PLAN §8d L120). ADR-0018 (Accepted, Q-03 row 3.7) keeps the list as a reference that schedules nothing. |
| CP-4 | Delivery zones are an owner-curated neighborhood (bairro) list with a flat ETA; no geocoding or routing | same, L154-189, L361-362 | Kept | Basis: triage. Built in frontend phase 9. Storage moves from the owner's `localStorage` to backend restaurant settings (ADR-0014). The missing street address is audit item 54 (Storefront scope). |
| CP-5 | Phase 10 (dine-in comandas, waiter ordering) and Phase 11 (dashboard and visual polish) are planned next | same, L43-62, L228-313 | Pending re-examination | MASTER-PLAN §8d (L120): Later questions. QR/table and waiter are Later (ADR-0007). |
| DT-1 | Graphify, ponytail and two wshobson plugins are installed project-scoped in `order-ui/.claude/` | FE `specs/2026-09-15-dev-tooling-workflow-design.md` L1-9, L19 | Pending re-examination | Keep or drop when the frontend `.claude/` is versioned in S3 (ADR-0015 Open questions). Graphify runs with Windows `python` (PF `RESTAURANT-OPS-ROADMAP.md` L258-260). |

## Consequences
- Agents must look up a legacy decision here before applying it from an old doc, and cite legacy ids with their doc ("legacy D9, phase-0 design"), never bare, to avoid confusion with `D-1`..`D-16`.
- Agents must never:
  - apply a Superseded row, even when a legacy doc, skill or `CLAUDE.md` line still states it (those carry banners or are rewritten in S2-S3, `planning/superseded-docs.md`);
  - extend a Pending row into a new contract, schema or endpoint without the named contract deciding it;
  - treat a "(when Accepted)" supersession as settled before Gabriel accepts the replacing ADR;
  - revert built code (D13, D15, D16-D24) because the legacy doc around it is superseded.
- When a Pending row is decided, the contract doc records the decision, and this table's row changes to Kept or Superseded with the doc's name.
- When a Proposed ADR named here is accepted or rejected, the matching rows are updated in the same change.
- Skills and agents that still encode superseded rows (backend `api-contract-sync`, `backend-module-development`, `flyway-migrations`, `spring-security-changes`, `backend-feature-builder`, `flyway-migration-author`) are updated in S3; the list and banners are in `planning/superseded-docs.md`.

## Open questions
- Gabriel accepts or amends the verdicts not backed by D-16 (every row whose note does not start with "Basis: D-"): S1 (MASTER-PLAN §6 S1, §7 L99). Register: Q-05.
- D-16 does not list D1, D3, D4, D12, D13 or LR-4, though they fit every current decision. Should Gabriel add them to the kept list? Register: Q-05.
- D10 `businessDate` and the per-restaurant timezone: Order Core contract and Tenancy & Identity ("IANA timezone per restaurant", MASTER-PLAN §8b). Register: Q-28, Q-21.
- D11 category identity on the wire: Menu contract. Register: Q-46.
- FS-02 chains vs single location; AF-3 platform admin vs restaurant roles: Tenancy & Identity (MASTER-PLAN §8b). Register: Q-22 (FS-02), Q-17 and Q-18 (AF-3).
- FS-03 Customer; FS-05/FS-06 channel, source and fulfillment vocabulary; CP-1 payment record and storefront payment options: Order Core and Storefront contracts (MASTER-PLAN §8c). Register: Q-51 (FS-03), Q-49 (FS-05/FS-06), Q-55, Q-58 and Q-65 (CP-1).
- FS-08 stock counts: Menu contract (MASTER-PLAN §8c). Register: Q-42.
- CP-3 packages: deferral Accepted (ADR-0018; Q-03 row 3.7, 2026-09-29); pricing and packages themselves: Q-78 (MASTER-PLAN §8d).
- DT-1 frontend tooling keep/drop: Q-07 (ADR-0015).

## Sources
Legend: PF = `/mnt/project-files/` (flat copies of the repo docs; the six legacy files cited here were checked identical to the repo versions, ignoring CRLF); BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`), Java paths under `src/main/java/com/enterprise/ordersuite/`; FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `planning/MASTER-PLAN.md` §2 (L22-28), §3 D-1..D-16 (L34-49), §4 (L68), §5 (L72, L75, L77), §6 (L88-95), §7 (L99), §8 (L109-120)
- PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L4, L47-60, L72-73, L83-84, L118-130, L144-156, L165-166
- PF `2026-09-24-restaurant-ops-phase-0-foundation-design.md` L4, L75-266, L430-431
- PF `2026-09-25-restaurant-ops-phase-1-auth-design.md` L4, L61-78, L80-213, L374-379
- PF `2026-09-09-restaurant-ops-redesign-design.md` L41-87, L309
- PF `2026-09-20-test-failures-and-audit-followups.md` L87-99, L124-130
- PF `2026-09-20-orders-authorization-fix.md` L5-12
- PF `2026-09-16-business-rules-master-en.md` L26, L185
- PF `RESTAURANT-OPS-ROADMAP.md` L108-109, L124-146, L258-260
- FE `docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml` L978-1003, L1021
- FE `docs/superpowers/specs/2026-09-15-core-package-br-i18n-ux-design.md` L7-10, L43-62, L109-189, L228-313, L328-365
- FE `docs/superpowers/specs/2026-09-15-dev-tooling-workflow-design.md` L1-9, L19
- FE `.gitignore` L17; `src/features/profile/services/profile.service.ts` L26-27
- BE `orders/api/OrderController.java` L36-100; `orders/application/service/OrderService.java` L245-251; `src/main/resources/application.yml` L4, L85-89; `src/main/resources/db/migration/` (V1-V21); `.claude/skills/backend-module-development/SKILL.md` L50-60; `.claude/skills/spring-security-changes/SKILL.md` L136-140
- ADR-0001, ADR-0002, ADR-0005, ADR-0006, ADR-0008, ADR-0009, ADR-0010, ADR-0012, ADR-0014, ADR-0015, ADR-0018 (drafts in `/mnt/project-files/adr/`)
