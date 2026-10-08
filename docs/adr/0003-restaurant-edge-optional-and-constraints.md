# ADR-0003: Restaurant Edge is optional; Edge-ready constraints on the core
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel (in conversation with Claude; MASTER-PLAN §3 D-3). C1, C3, C4 and the Edge reduced to this ADR: Gabriel, Q-03 row 3.4, 2026-09-29. When the Edge is built: Gabriel, Q-80 b, 2026-10-01
- Supersedes: Two sources treated the Edge as universal. Architecture §2 (L42) has "each physical restaurant has ... the Restaurant Edge", with §24 (L302-306) "two execution environments"; the architecture amendment proposal carries the text change, and Gabriel's doc is not edited here. PF `planning/pm-tool-recommendation.md` L59 records "a per-restaurant Restaurant Edge". Also superseded: the "Design only" scope of the revised-order rows 5-6 (L127-128) and the three Edge derived docs as current work (architecture L313, L316-317).
- Related: ADR-0002, ADR-0004, ADR-0005, ADR-0007, ADR-0009, ADR-0016, ADR-0019

## Context
- Gabriel's architecture (status Proposed; Accepted with amendments on 2026-10-01, Q-04; PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md`) makes the Edge universal:
  - A local node per restaurant (§2 L40-71) keeps operating when the Internet is down and the LAN is up (§3-4 L73-83).
  - It has a local API, a local DB, a sync engine, an "Offline Transaction Queue" and a device registry (§12 L185-200).
  - Sync is idempotent (§14 L206-215), and locally created entities use ULID/UUID ids (§15 L217-219).
  - The original order put the Edge and sync before every consumer (L320-339).
- On 2026-09-29 the Edge was narrowed:
  - It is optional, not built this run, and only worth building for restaurants with poor signal. It is designed only where it constrains the core (PF `planning/MASTER-PLAN.md` §3 D-3, L36).
  - Offline means orders (D-4 / ADR-0004). Payments are record-only (D-5 / ADR-0005).
  - MASTER-PLAN §4 (L60) reduces "Edge designed now" to one ADR listing the constraints that would be expensive to change later. The full Edge docs follow after the Order Core contract is Reviewed.
- The current code conflicts with these constraints:
  - Order ids come from a database identity (BE `common/persistence/BaseEntity.java` L20-21; `V10__create_orders_table.sql` L2).
  - The client supplies `orderNumber` (BE `orders/api/dto/OrderCreateRequest.java` L22).
  - The frontend mock generates a random id at create time (FE `src/features/orders/services/orders.service.ts` L21) and accepts any status transition (L28-33; business rule 7, PF `2026-09-16-business-rules-master-en.md` L24).
  - The legacy backend entity already rejects invalid and backward transitions in the domain (BE `orders/domain/Order.java` L49-70).
- The KDS and order tracking poll the cloud every 5 s (FE `src/features/kds/constants/kds.constants.ts` L3; `src/features/track-order/hooks/useTrackOrder.ts` L23). No Edge code exists.

## Decision
1. The Restaurant Edge is **optional**. It is not built this run. It is built when Gabriel decides, as an optional paid feature (Q-80 b, 2026-10-01); D-3: it is worth building for restaurants with poor signal.
   - Decided later (Q-80 b, 2026-10-01): the build is triggered by Gabriel's decision, not by a restaurant's request (option a was not chosen), and the Edge is an optional paid feature. Gabriel: "b when I decide, for now we focus only on making sure everything is funcional but keeping in mind we will have edge later and that its optional paid feature". Until then the work makes everything functional and keeps C1-C6. What it costs and how it is sold waits for pricing (ADR-0018, Q-78 a).
2. It is designed now **only where it constrains the core**. The constraints:

| # | Constraint | Basis |
|---|---|---|
| C1 | Orders and order lines use **client-generated ULIDs**. The creating client generates the id before the first send; the server stores it and never replaces it. | Architecture §15 (L217-219); D-4 safe retry. ULID vs UUID, and ULIDs beyond orders: ADR-0009. |
| C2 | Order creation is **idempotent**. A retried create is recognized as already processed and never creates a second order. With C1, the retry key is the client-generated order id (mechanism: Q-38 a, 2026-10-01; see Open questions). | Architecture §14-15 (L215, L219); D-4 "retries safely". Using the client id as the key rests on C1. |
| C3 | Order **status only moves forward**; no transition returns to an earlier status. Cancel is a status change (D-16). | PF `planning/pm-tool-recommendation.md` L114 (review gap #2); business rule 7. |
| C4 | **Menu and restaurant configuration are cloud-owned.** A future Edge only holds a read-only cache. | PF `planning/pm-tool-recommendation.md` L114 (review gap #2). |
| C5 | The app **never processes or queues payments**. Any offline queue carries orders only. | D-5 (ADR-0005), D-4 (ADR-0004). |
| C6 | The Edge is **optional**; it is built when Gabriel decides, as an optional paid feature. | D-3 (meant for poor-signal restaurants); Q-80 b, 2026-10-01. |

   - C2, C5 and C6 follow directly from Accepted decisions D-3, D-4 and D-5.
   - C1, C3 and C4 were Claude's refinements (MASTER-PLAN §4 L60). Gabriel accepted them on 2026-09-29 (Q-03 row 3.4, "3 Yes"). All of C1-C6 are Accepted.
3. The **full Edge docs** are deferred until the Order Core contract is Reviewed (ADR-0016): `RESTAURANT-EDGE-ARCHITECTURE.md`, `OFFLINE-SYNC-DESIGN.md` and `RESTAURANT-DEVICE-MODEL.md` (architecture L313, L316-317).
4. The following are **not** constraints now. They are deferred to those docs:
   - Per-entity ownership beyond C4 (dine-in Edge-owned vs delivery cloud-owned) and a local "86" override (pm-tool L114).
   - Device registry and device context (architecture §11-12).
   - The local data projection (§13) and source-of-truth switching (§16).
   - Sync via events and an outbox (pm-tool L115) and "one core module deployed twice" (pm-tool L113). Both are deferred by ADR-0019.

## Consequences
- Must: the Order Core contract (Build 3) and the Storefront contract (Build 4) specify C1-C3: client-supplied order and line ids, idempotent create, and forward-only transitions.
- Must: enforce forward-only transitions on the server, in the domain or application layer, not only in the UI. The legacy entity shows the pattern (BE `orders/domain/Order.java` L49-70). The frontend mock enforces nothing.
- Must: write menu and configuration only through the cloud API.
- Never: build Edge components this run: local API, local DB, sync engine, offline queue, device registry or connectivity monitor (architecture §12 L185-198).
- Never: generate order or order-line primary keys from a database sequence or identity, and never have the server replace a client-supplied order id.
- Never: add a payment queue, offline payment capture or a "Payment Device Gateway" (PF architecture §12 L200, pre-amendment; A-08 removed it, 2026-10-01). See ADR-0005.
- Never: let an Edge or device write menu or configuration directly.
- Never: write the full Edge docs before the Order Core contract is Reviewed. Edge ideas go to the Later list (ADR-0007).
- This ADR is the minimum that keeps the Edge possible. It is not the Edge design; do not extend it into one.

## Open questions
- Q-03 row 3.4 (the Edge reduced to this constraints ADR, including C1, C3 and C4): answered 2026-09-29, accepted.
- ULID or UUID: settled by ADR-0009 (ULID on every table; Q-03 row 3.11, accepted 2026-09-29). Q-35: answered 2026-10-01, a: `char(26)` Crockford Base32 in the database, logs and API alike.
- Q-38: answered 2026-10-01, a: the client-generated ULID in the body is the idempotency key for order creation; the same id with the same payload returns the existing order; the same id with a different payload returns 409 with a SCREAMING_SNAKE code; an `Idempotency-Key` header only for other non-idempotent calls, if one ever needs it. The API conventions and the Order Core contract record it.
- Q-50: answered 2026-10-01, a: a running number per restaurant (#1, #2, ...), server-generated (ADR-0009). Who issues it when orders may one day be created offline: Edge docs, once Gabriel decides to build the Edge (Q-80 b).
- Q-47: answered 2026-10-01, a: NEW, PREPARING, READY, COMPLETED, CANCELLED. Q-34: answered 2026-10-01, a: SCREAMING_SNAKE on the wire.
- Q-80: answered 2026-10-01, b: the Edge is built when Gabriel decides, as an optional paid feature (Decision 1).
- Q-54: answered 2026-10-01, a: the KDS signs in with a staff login and a restaurant role this run; device pairing comes with the Edge.
- Reviewer note: MASTER-PLAN §6 S4 (L91) schedules "API conventions + Edge-ready constraints ADRs" for S4, while S0 (§5 L72, §6 L87) drafts an ADR for every §3 decision, and this file already carries the constraint list. S4 should review and finalize this ADR, not write a second Edge-constraints ADR. Resolved: MASTER-PLAN §6 S4 now reads "finalize the Edge-ready constraints (already drafted in ADR-0003)".
- Reviewer note: two stale records still describe the Edge as designed now or universal, which D-3 and §4 narrow. The memory note `/tmp/claude/memory/team/silo/project-decisions-2026-09-29.md` (L9, L11) says "Restaurant Edge + offline sync designed now, built later (accepted)", and pm-tool L59 says "per-restaurant Restaurant Edge". The S0 memory cleanup should cover the memory note; pm-tool L59 is listed in PF `planning/superseded-docs.md` P1.

## Sources
Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`), Java paths under `src/main/java/com/enterprise/ordersuite/`; FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `planning/MASTER-PLAN.md` §3 D-3 (L36), D-4 (L37), D-5 (L38), D-16 (L49), §4 (L60-62, L67), §5 (L72, L75), §6 (L87, L91), §8 (L114, L118, L120)
- `planning/open-questions.md` Q-34, Q-35, Q-38, Q-47, Q-50, Q-54, Q-80: Gabriel's answers of 2026-10-01 (project thread, 2026-10-01T16:25Z)
- PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md` §2 (L40-71), §3-4 (L73-83), §11-17 (L181-235), §24 (L300-306), derived docs (L308-318), original order (L320-341)
- PF `planning/pm-tool-recommendation.md` L59, L111-117, L119-131
- PF `2026-09-16-business-rules-master-en.md` rule 7 (L24)
- BE `common/persistence/BaseEntity.java` L20-21; `src/main/resources/db/migration/V10__create_orders_table.sql` L2-3; `orders/api/dto/OrderCreateRequest.java` L22; `orders/domain/Order.java` L49-70
- FE `src/features/orders/services/orders.service.ts` L18-33; `src/features/kds/constants/kds.constants.ts` L3; `src/features/track-order/hooks/useTrackOrder.ts` L23
- `/tmp/claude/memory/team/silo/project-decisions-2026-09-29.md` L9, L11 (memory note, for the reviewer note only)
