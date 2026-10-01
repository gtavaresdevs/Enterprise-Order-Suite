# ADR-0002: Restaurant Operational Core: one Order model and one Menu for every interface
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
- Supersedes: none. It restates and generalizes the legacy "one Order model, one KDS queue" and "Menu is configured once and served everywhere" (PF `2026-09-09-restaurant-ops-redesign-design.md` L52-60). The legacy channel vocabulary (`Online | Dine-in | Phone` plus `Pickup | Delivery`) is replaced by the mapping Gabriel chose on 2026-10-01 (Q-49 a, Decision 5); the existing code keeps it until the Order Core rewires it.
- Related: ADR-0001, ADR-0003, ADR-0004, ADR-0007, ADR-0010, ADR-0019

## Context
- Gabriel's architecture sets the direction (status Proposed; Accepted with amendments on 2026-10-01, Q-04; PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md`):
  - §1 L18-38: no independent Storefront, Waiter, Tablet, POS or Delivery orders. All of them work on one domain (Restaurant, Menu, Order, Table, Customer, Product, Configuration).
  - §7 L125-140: an Order has `channel` `DINE_IN | TAKEAWAY | DELIVERY` and `source` `WAITER | POS | QR | TABLET | STOREFRONT | FUTURE_MARKETPLACE`.
  - §8 L151-165: one menu model; "No surface keeps its own menu database".
  - §23 L294-298: the frontend is the first consumer. `OrderApplicationService.createOrder(...)` holds the core order behavior, not a `StorefrontService.createOrder()` with storefront-only rules.
- MASTER-PLAN §3 D-2 (L35) accepts this principle.
- The frontend already follows it on mock data:
  - Orders, KDS, Home and Analytics share one `["orders"]` cache, and KDS is a filtered view (PF `RESTAURANT-OPS-ROADMAP.md` L104-116).
  - `features/menu` is the only `MenuItem` source, and 86'd items are never shown to customers (PF `RESTAURANT-OPS-ROADMAP.md` L21-23).
  - The vocabulary today: channel `Online | Dine-in | Phone`, fulfillment `Pickup | Delivery`, status `New | Preparing | Ready | Completed | Cancelled` (FE `src/types/orders.ts` L1-3; business rules 1-3, PF `2026-09-16-business-rules-master-en.md` L16-18).
- The backend still has the legacy B2B order:
  - `OrderService.createOrder` takes a client-supplied `orderNumber` and the status set `PENDING..CANCELLED` (BE `orders/application/service/OrderService.java` L54; `orders/api/dto/OrderCreateRequest.java` L22; `orders/domain/OrderStatus.java` L3-9).
  - No `OrderApplicationService` exists.
  - Modules are layered `api -> application -> domain -> persistence`, and the consuming module owns cross-module interfaces (BE `CLAUDE.md` L67, L69; PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L81-83).
- Vocabulary gap: staff-taken phone orders exist today (business rule 2, L17), but architecture §7 has no `source` for them (PF `planning/MASTER-PLAN.md` §5 L78: "Phone->?").

## Decision
1. The system is built around one **Restaurant Operational Core** per restaurant: one Order model and one Menu model.
   - Every interface is a consumer of that core: storefront, QR/table, waiter, KDS, POS, tablet, back office and future marketplace integrations.
   - The React frontend is the first consumer. It does not drive the design.
2. Rule: **adapters call application services, e.g. `OrderApplicationService.createOrder`, never channel-specific services holding business rules.**
   - An adapter authenticates, maps its input and sets the values that identify it (such as `source`). Adapters are controllers, `/public/*` endpoints, and later an Edge or marketplace integration.
   - Order behavior is implemented once, in the application and domain layers.
3. An Order carries two dimensions, **channel** and **source** (architecture §7).
   - Reading of the §7 value sets: channel is how the order is served (`DINE_IN`, `TAKEAWAY`, `DELIVERY`); source is the interface that created it (`WAITER`, `POS`, `QR`, `TABLET`, `STOREFRONT`, `FUTURE_MARKETPLACE`). This wording is Claude's reading of §7 and goes to the glossary for confirmation.
   - The two-dimension concept is decided.
   - The value sets, their wire casing and the mapping from the legacy vocabulary were decided on 2026-10-01 (Decision 5).
4. There is one Menu per restaurant. No surface keeps its own menu copy or menu store. Each surface consumes a representation of the same menu.
5. Decided later (Gabriel's register answers, 2026-10-01; the Order Core and Menu contracts and the glossary record the detail):
   - Q-49 a: `channel` = DINE_IN, TAKEAWAY or DELIVERY (today's fulfillment folds in; no separate `fulfillment` field); `source` = STOREFRONT, PHONE, WAITER, POS, QR or TABLET (PHONE added for staff-taken phone orders). Online becomes source STOREFRONT, Pickup becomes TAKEAWAY, Dine-in becomes DINE_IN, Phone becomes source PHONE. Architecture §7's `FUTURE_MARKETPLACE` is not in this set; marketplaces are Out this run (ADR-0007).
   - Q-34 a: enums are SCREAMING_SNAKE on the wire (`DINE_IN`, `NEW`).
   - Q-47 a: the order status set is NEW, PREPARING, READY, COMPLETED, plus CANCELLED.
   - Q-51 a: the customer is a snapshot on each order (name, phone, address); no Customer table this run.
   - Q-53 a: Order Core gets a minimal Table (id, name) so staff dine-in orders reference a real table; QR self-ordering stays Later.
   - Q-41 b: modifiers are option groups, each with required, min and max and a price per option; today's sizes and add-ons both become groups.

## Consequences
- Must: put order rules in the application and domain layers, reachable by every adapter. These are validation, price and total derivation, line snapshots, status transitions and cancel-as-status.
- Must: apply the legacy D-16 rules identically to every channel and source: the server derives every money value, order lines snapshot name and price, and cancel is a status change (PF `planning/MASTER-PLAN.md` §3 D-16, L49).
- Must: set `source` (and any server-owned field) on the server in public adapters, never from client input. Precedent: the legacy plan forced the channel on the public endpoint (PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L144).
- Must: keep KDS, the Orders list, Home, Analytics and order tracking as views over the same order data.
- Never: create per-interface order models, tables, types or services. Examples: `StorefrontOrder`, `WaiterOrder`, or a `KdsTicket`-style duplicate (the frontend deleted `types/kds.ts` for this reason, PF `RESTAURANT-OPS-ROADMAP.md` L112-114).
- Never: write a channel-specific service that holds business rules, such as `StorefrontService.createOrder()` with storefront-only rules.
- Never: add a second menu data source in any feature or module.
- Never: invent channel or source values beyond Decision 5 (Q-49 a), or use another wire casing than SCREAMING_SNAKE (Q-34 a). Use the glossary once it exists.
- The class name `OrderApplicationService` comes from architecture §23 and illustrates the rule. The Order Core contract and its plan fix the actual names.
- Where the core code lives until an Edge exists (existing packages, free of web types) is ADR-0019.

## Open questions
Order Core / Menu batch (PF `planning/MASTER-PLAN.md` §8c L118). Ids from the register, PF `planning/open-questions.md`.
- Q-49: answered 2026-10-01, a: channel DINE_IN/TAKEAWAY/DELIVERY with fulfillment folded in; source adds PHONE for staff phone orders (Decision 5).
- Q-47: answered 2026-10-01, a: NEW, PREPARING, READY, COMPLETED, CANCELLED. Q-34: answered 2026-10-01, a: SCREAMING_SNAKE on the wire.
- Q-51: answered 2026-10-01, a: customer snapshot on each order; no Customer table this run.
- Q-53: answered 2026-10-01, a: a minimal Table (id, name) in Order Core; QR self-ordering stays Later.
- Q-41: answered 2026-10-01, b: option groups with required, min, max and a price per option.

## Sources
Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`), Java paths under `src/main/java/com/enterprise/ordersuite/`; FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `planning/MASTER-PLAN.md` §3 D-2 (L35), D-16 (L49), §5 (L75, L78), §8c (L118)
- `planning/open-questions.md` Q-34, Q-41, Q-47, Q-49, Q-51, Q-53: Gabriel's answers of 2026-10-01 (project thread, 2026-10-01T16:25Z)
- PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md` §1 (L18-38), §7 (L125-149), §8 (L151-165), §23 (L294-298)
- PF `2026-09-09-restaurant-ops-redesign-design.md` L52-60, L146-151
- PF `RESTAURANT-OPS-ROADMAP.md` L21-23, L104-116
- PF `2026-09-16-business-rules-master-en.md` rules 1-3 (L16-18)
- PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L81-83, L144
- BE `CLAUDE.md` L67, L69; `orders/application/service/OrderService.java` L54; `orders/api/dto/OrderCreateRequest.java` L22; `orders/domain/OrderStatus.java` L3-9
- FE `src/types/orders.ts` L1-3
