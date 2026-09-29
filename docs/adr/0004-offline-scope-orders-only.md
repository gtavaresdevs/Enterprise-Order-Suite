# ADR-0004: Offline scope is orders only
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel (in conversation; MASTER-PLAN §3 D-4 lists the source as "Conversation")
- Supersedes: Gabriel's architecture treats a wide offline scope as a requirement: §4.2 (L81: waiter, table tablet and customer QR create orders, POS operates, through the Edge) and §17 "Must work" (L231: POS operations, table ordering and more). That scope now applies only if and when an Edge is built (ADR-0003). Also superseded: the name "Offline Transaction Queue" (§12 L192). The architecture amendment proposal applies the text changes.
- Related: ADR-0002, ADR-0003, ADR-0005, ADR-0009

## Context
- Architecture (status Proposed; PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md`):
  - The primary offline scenario is "Internet down, LAN up", served by the Edge (§3 L73-75, §4.2 L81).
  - LAN failure and connectivity backups (backup router, secondary Internet, 4G/5G failover, UPS) are infrastructure concerns (§4.3 L83).
  - External operations must never be shown as successful when they are not (§17 L235).
  - The Cloud is authoritative in normal operation (§16 L223).
- 2026-09-29 conversation (PF `planning/MASTER-PLAN.md` §3 D-4, L37):
  - Offline means orders (waiter -> kitchen).
  - The 4G failover router is the customer's infrastructure.
  - The app shows a clear connection-lost state, keeps in-progress orders on the device and retries safely.
- No Edge is built this run (ADR-0003). There is no waiter app. Staff create orders in the back office (`CreateOrderModal`); customers create them in the storefront checkout (PF `RESTAURANT-OPS-ROADMAP.md` L104-116, L176-196).
- The frontend has no connection-lost handling for orders:
  - No `navigator.onLine` or offline handling exists in `order-ui/src` (grep).
  - The API client only rethrows network errors and 5xx so the UI shows an outage (FE `src/api/client.ts` L71, L96).
  - Order ids are generated at create time in the mock service (FE `src/features/orders/services/orders.service.ts` L21).
  - KDS and order tracking poll every 5 s (FE `src/features/kds/constants/kds.constants.ts` L3).

## Decision
1. In this product, "offline" means **orders**: creating an order and getting it to the kitchen (waiter -> kitchen). Nothing else is promised to work without a connection.
2. **Connectivity backup is the customer's infrastructure** (for example a 4G/5G failover router). It is not a product feature.
3. Without an Edge (this run), when the connection to the cloud is lost the app:
   - (a) shows a clear connection-lost state;
   - (b) keeps in-progress orders on the device;
   - (c) retries safely: a retry never creates a duplicate order (ADR-0003 C2). Retry key: the order's client-generated id, reused on every retry (ADR-0003 C1, accepted with Q-03 row 3.4 on 2026-09-29). Mechanism details: Q-38.
4. If an Edge is built later (ADR-0003), its offline guarantee covers this order flow. Payments are never part of it (ADR-0005).
5. The architecture's "Offline Transaction Queue" becomes an **"Offline Order Queue"** (amendment proposal, PF `planning/MASTER-PLAN.md` §5 L73).

## Consequences
- Must: every surface that creates orders generates its retry key before the first send and reuses it on every retry, so a lost response never produces a second order. The key is the order's client-generated id (ADR-0003 C1).
- Must: never show an order as sent or received until the server has acknowledged it (architecture §17 L235). Once acknowledged, the server's record wins; the copy on the device is not a source of truth (§16 L223).
- Must: order-creating surfaces show a connection-lost state that the user can see and understand.
- Never: capture or queue payments offline (ADR-0005).
- Never: build connectivity features (failover, router management, network monitoring). They are customer infrastructure.
- Never: build a client-side sync engine, a local replica of menu or order data, or an offline-first mode this run. Offline continuity beyond "keep and retry unsent orders" needs an Edge (ADR-0003) or a new decision.
- Never: promise that menu edits, analytics, notifications or external integrations work offline (architecture §17 L233 already lists those as needing extra infrastructure).

## Open questions
Storefront / Order Core contracts and the NFR page (PF `planning/MASTER-PLAN.md` §5 L75-77, §8c L118). Ids from the register, PF `planning/open-questions.md`.
- Which surfaces keep in-progress orders this run (storefront checkout, staff order entry), where on the device, and for how long? Q-60.
- Which personal data (customer name, phone, address) may be stored on the device? This is an LGPD question for the NFR page: Q-60, Q-52.
- What do the KDS and staff status changes do while the connection is lost: queue, block, or show stale data? Q-60.
- Retry semantics: the same id with a different payload, and the client id vs the `Idempotency-Key` header in the API conventions (see ADR-0003): Q-38.
- The wording of the connection-lost state in the pt-BR UI vocabulary (glossary).

## Sources
Legend: PF = `/mnt/project-files/`; FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `planning/MASTER-PLAN.md` §3 D-4 (L37), §5 (L73, L75-77), §8c (L118)
- PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md` §3 (L73-75), §4.2 (L81), §4.3 (L83), §12 (L192), §16 (L221-227), §17 (L229-235)
- PF `RESTAURANT-OPS-ROADMAP.md` L104-116, L176-196
- FE `src/api/client.ts` L71, L96; `src/features/orders/services/orders.service.ts` L18-26; `src/features/kds/constants/kds.constants.ts` L3
