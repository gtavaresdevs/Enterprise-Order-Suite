# ADR-0002: Restaurant Operational Core: one Order model and one Menu for every interface
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
- Supersedes: none. It restates and generalizes the legacy "one Order model, one KDS queue" and "Menu is configured once and served everywhere" (PF `2026-09-09-restaurant-ops-redesign-design.md` L52-60). The legacy channel vocabulary (`Online | Dine-in | Phone` plus `Pickup | Delivery`) stays in place until the mapping below is decided.
- Related: ADR-0001, ADR-0003, ADR-0004, ADR-0007, ADR-0010, ADR-0019

## Context
- Gabriel's architecture sets the direction (status Proposed; PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md`):
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
   - The value sets, their wire casing and the mapping from the legacy vocabulary are open (see Open questions).
4. There is one Menu per restaurant. No surface keeps its own menu copy or menu store. Each surface consumes a representation of the same menu.

## Consequences
- Must: put order rules in the application and domain layers, reachable by every adapter. These are validation, price and total derivation, line snapshots, status transitions and cancel-as-status.
- Must: apply the legacy D-16 rules identically to every channel and source: the server derives every money value, order lines snapshot name and price, and cancel is a status change (PF `planning/MASTER-PLAN.md` §3 D-16, L49).
- Must: set `source` (and any server-owned field) on the server in public adapters, never from client input. Precedent: the legacy plan forced the channel on the public endpoint (PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L144).
- Must: keep KDS, the Orders list, Home, Analytics and order tracking as views over the same order data.
- Never: create per-interface order models, tables, types or services. Examples: `StorefrontOrder`, `WaiterOrder`, or a `KdsTicket`-style duplicate (the frontend deleted `types/kds.ts` for this reason, PF `RESTAURANT-OPS-ROADMAP.md` L112-114).
- Never: write a channel-specific service that holds business rules, such as `StorefrontService.createOrder()` with storefront-only rules.
- Never: add a second menu data source in any feature or module.
- Never: invent channel or source values, rename existing enums or pick a wire casing before the Order Core contract and the API conventions settle them. Use the glossary once it exists.
- The class name `OrderApplicationService` comes from architecture §23 and illustrates the rule. The Order Core contract and its plan fix the actual names.
- Where the core code lives until an Edge exists (existing packages, free of web types) is ADR-0019.

## Open questions
Order Core / Menu batch (PF `planning/MASTER-PLAN.md` §8c L118). Ids from the register, PF `planning/open-questions.md`.
- **Channel/source vocabulary mapping.** Proposed map: `Online -> STOREFRONT`, `Pickup -> TAKEAWAY`, `Dine-in -> DINE_IN`. Staff phone orders have no source yet ("Phone -> ?"). Does `fulfillment` stay a separate field or fold into `channel`? (MASTER-PLAN §5 L78, §8c.) Register: Q-49.
- Order status set and casing (§8c): Q-47. Enum casing on the wire is in the API conventions (§5 L75): Q-34.
- Customer as a per-restaurant record, or a snapshot on each order (§8c)? Q-51.
- A minimal Table in Order Core, or does dine-in wait (§8c)? Q-53.
- Modifier model depth: option groups, required, min/max (§8c; Menu contract): Q-41.

## Sources
Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`), Java paths under `src/main/java/com/enterprise/ordersuite/`; FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `planning/MASTER-PLAN.md` §3 D-2 (L35), D-16 (L49), §5 (L75, L78), §8c (L118)
- PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md` §1 (L18-38), §7 (L125-149), §8 (L151-165), §23 (L294-298)
- PF `2026-09-09-restaurant-ops-redesign-design.md` L52-60, L146-151
- PF `RESTAURANT-OPS-ROADMAP.md` L21-23, L104-116
- PF `2026-09-16-business-rules-master-en.md` rules 1-3 (L16-18)
- PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L81-83, L144
- BE `CLAUDE.md` L67, L69; `orders/application/service/OrderService.java` L54; `orders/api/dto/OrderCreateRequest.java` L22; `orders/domain/OrderStatus.java` L3-9
- FE `src/types/orders.ts` L1-3
