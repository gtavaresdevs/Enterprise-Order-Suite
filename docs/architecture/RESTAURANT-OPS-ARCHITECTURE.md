# Restaurant Ops: Platform Architecture and Operational Foundation

- **Decision status:** Accepted with amendments (Gabriel, 2026-10-01, Q-04). Proposed by Gabriel on 2026-09-29 (captured from his message in the project thread). Amended as listed in "Amendment log" at the end of this document.
- **Status:** Draft (doc status, ADR-0016; it sits next to the decision status above per Q-12, decided by Claude, Gabriel may override)
- **Scope:** Current product foundation
- **Architecture:** Multi-tenant SaaS (§25); Restaurant Edge optional, not built this run (§2, §12)
- **Primary objective:** Establish the central restaurant operating model before implementing peripheral operational integrations.

> Guiding principle: build the restaurant's operational core first, but design it from the start to be consumed by multiple channels, online and local, with order taking that survives loss of Internet (§4.2, §17).

## 1. Vision

Restaurant Ops is a multi-tenant restaurant operations platform designed around a central operational model consumed by multiple restaurant interfaces and channels.

The platform must support: restaurant management; public storefront; delivery ordering; digital menu; table ordering; waiter ordering; kitchen operations; point of sale; table tablets; future payment and fiscal integrations; future delivery marketplace integrations.

The current implementation does not need to implement every operational surface. The current phase establishes the central domain and operational infrastructure that all future surfaces will consume.

Avoid building independent implementations for:

```text
Storefront Order
Waiter Order
Tablet Order
POS Order
Delivery Order
```

All of them must operate on the same domain model:

```text
Restaurant
Menu
Order
Table
Customer
Product
Configuration
```

## 2. Core architectural principle

**Cloud SaaS, with an optional Restaurant Edge.** The cloud provides the centralized SaaS platform. It is the only execution environment this run. A physical restaurant with poor connectivity may later add a lightweight local operational environment, the Restaurant Edge. The Edge is not built this run. It is designed only where it constrains the core (ADR-0003). The diagram below shows a restaurant that has an Edge. Without one, POS, KDS and devices reach the cloud directly over the restaurant's Internet connection.

```text
                         CLOUD
              ┌─────────────────────────┐
              │     Restaurant Ops      │
              │ Multi-tenant SaaS       │
              │ Menu                    │
              │ Orders                  │
              │ Storefront              │
              │ Configuration           │
              │ Management              │
              │ Analytics               │
              └────────────┬────────────┘
                           │ Synchronization (Internet)
              ┌────────────▼────────────┐
              │     Restaurant Edge     │
              │ Local API               │
              │ Local Database          │
              │ Sync Engine             │
              │ Offline Queue           │
              │ Device Registry         │
              └────────────┬────────────┘
                           │ Restaurant LAN
        ┌──────────────────┼──────────────────┐
       POS                KDS              Devices
     Cashier            Kitchen         Waiter/Tablets
```

The Edge is not a second complete SaaS deployment. It is a local operational node that lets a restaurant that has one keep taking orders when the cloud is temporarily unavailable (§17).

## 3. Online and offline are separate operational modes

Cloud connectivity (`Internet → Cloud`) and restaurant connectivity (`Device → Restaurant LAN → Edge`) are different dependencies. A restaurant with an Edge keeps taking orders when `Internet = unavailable` and `LAN = available`; this is the scenario the Edge exists for. Without an Edge (every restaurant this run), Internet loss is handled by the connection-lost behavior in §4.2. Connectivity backup, for example a 4G/5G failover router, is the restaurant's own infrastructure. In every case, offline means orders (§17).

## 4. Operational availability model

**4.1 Normal operation:** Internet ✓, LAN ✓, Cloud ✓ (Edge ✓ where one exists). Devices talk to the Edge where one exists, and to the cloud otherwise. The cloud stays synchronized.

**4.2 Internet unavailable:** Internet ✗, LAN ✓, Cloud unavailable.
- **Without an Edge (every restaurant this run):** order-creating surfaces show a clear connection-lost state, keep in-progress orders on the device, and retry them safely. A retry reuses the order's client-generated id, so the server never creates a duplicate. Nothing else is promised offline.
- **With an Edge (optional, later):** orders keep flowing from waiter and POS to the KDS through the Edge. Menu, table state and restaurant configuration stay readable from the Edge's cache. Customer QR and table-tablet ordering work offline only in this case. Orders are stored locally and synchronized when connectivity returns.
- Payments are never queued or processed offline (§26).

**4.3 Restaurant LAN unavailable:** Internet ?, LAN ✗, Edge ?. A local infrastructure failure, outside the primary offline guarantee. Devices may keep limited local state. Infrastructure recommendations (redundant Wi-Fi, Ethernet for critical equipment, network monitoring, backup router, secondary Internet, 4G/5G failover, UPS) are infrastructure concerns, not domain concerns.

## 5. Current product scope

```text
Restaurant
    ├── Home / Landing Page
    ├── Storefront
    │      ├── Branding
    │      ├── Menu
    │      ├── Categories
    │      ├── Products
    │      ├── Product customization
    │      └── Delivery ordering
    ├── Menu Management
    │      ├── Categories
    │      ├── Products
    │      ├── Options
    │      ├── Availability
    │      └── Presentation
    └── Orders
           ├── Creation
           ├── Items
           ├── Customizations
           ├── Status
           ├── Channel
           └── Lifecycle
```

## 6. Future consumers

```text
Waiter App      → Order API → Order Core
KDS             → Order / Production Events → Order Core
POS             → Order / Payment record / Table APIs
Table Tablet    → Customer Ordering → Order Core
Customer Phone  → QR Storefront → Order Core
Delivery Storefront → Order Core
```

These are consumers of the same restaurant operational model, not independent order systems.

## 7. Order as the central operational object

An order is not tied to a particular interface.

```text
Order
 ├── restaurant
 ├── channel     DINE_IN | TAKEAWAY | DELIVERY
 ├── source      WAITER | POS | QR | TABLET | STOREFRONT | FUTURE_MARKETPLACE
 ├── customer
 ├── table
 ├── items
 ├── status
 ├── timestamps
 └── metadata
```

```text
                 ORDER CORE
      ┌──────────────┼──────────────┐
   WAITER           QR           STOREFRONT
      └──────────────┼──────────────┘
                    KDS
                    POS
```

**Open until the Order Core contract is Reviewed.** The `channel` and `source` value sets above are not final. Neither are their wire casing or the mapping from today's vocabulary. Today the frontend uses channel `Online | Dine-in | Phone` plus fulfillment `Pickup | Delivery`, and the backend order has no channel. Staff-taken phone orders exist today but have no `source` above.

Also open:
- the order status set;
- whether `customer` is a per-restaurant record or a snapshot on each order;
- whether `table` enters the Order Core this run.

Until that contract is Reviewed, nobody implements, renames or adds these enum values (ADR-0002).

> Note 2026-10-01 (pointer; the text above is unchanged): Gabriel has since answered these in the register, and the Order Core contract records them. `channel` is DINE_IN, TAKEAWAY or DELIVERY; `source` is STOREFRONT, PHONE, WAITER, POS, QR or TABLET; SCREAMING_SNAKE on the wire (Q-49 a, Q-34 a; ADR-0002 Decision 5). Today's Online, Pickup, Dine-in and Phone become source STOREFRONT, channel TAKEAWAY, channel DINE_IN and source PHONE. `FUTURE_MARKETPLACE` in the diagram is not in the set. The status set is NEW, PREPARING, READY, COMPLETED, CANCELLED (Q-47 a). The customer is a snapshot on each order, with no Customer table this run (Q-51 a). A minimal Table (id, name) enters the Order Core this run (Q-53 a). The rule above still holds until that contract is Reviewed.

## 8. Menu as a shared capability

One restaurant menu model consumed by Storefront, Waiter, POS, KDS, Tablet and QR.

```text
Restaurant
  └── Menu
       ├── Categories
       │    └── Items
       ├── Modifiers
       ├── Options
       └── Availability
```

No surface keeps its own menu database. They consume different representations of the same menu.

## 9. Storefront

Public customer experience: Home, Menu, Product details, Customization, Cart, Customer information, Delivery information, Order creation, Order status.

Restaurant-customizable, within bounded choices (ADR-0014):
- a logo, a cover image and brand colors;
- one font from a fixed list;
- one storefront layout preset from a fixed list.

There is no custom CSS, HTML or script, and owner-entered text is never rendered as HTML. Restaurant settings are a typed schema owned by the backend. Public pages read them from the backend, never from the viewer's device.

Brand name, menu presentation, product images, descriptions, categories and promotional content are available only as far as the Storefront or Menu spec defines them. Menu presentation builds on the existing menu features (frontend phases 1-3).

> Note 2026-10-01 (pointer; the text above is unchanged): Gabriel has since fixed the exact list (Q-66 a; ADR-0014 Decision). Storefront: logo, cover, one brand color (text contrast computed), a font from a list of about five, one of two or three layout presets. Menu: category order and item photos. Nothing else this run, so promotional content stays out. The Storefront contract names the fonts and presets.

It is a restaurant-specific digital storefront, not a generic ordering page.

## 10. QR and table experience

QR is another entry point into the same customer experience, with configuration-driven capability levels (not separate products):

- **Menu-only:** QR → Menu
- **Ordering:** QR → Menu → Cart → Order
- **Full table experience:** QR → Menu → Order → Current table consumption → Call waiter → Request bill → Payment record. The customer pays with the restaurant's card terminal, in cash or through an existing payment system. The platform records the result and never processes the payment (§26).

QR ordering and the full table experience are Later (ADR-0007). Where the existing menu-only QR page (`/table-menu`) lands this run is decided in the scope-of-run page (S5).

## 11. Table tablets

Same customer ordering experience; the difference is device context. A registered tablet has `deviceId`, `restaurantId`, `tableId`, `deviceType`, `capabilities`, so `Tablet #17 → Restaurant A → Table 17` without the customer identifying the table. Where the restaurant has an Edge, tablets consume the local Edge API and keep taking orders during Internet loss. Without an Edge, tablets consume the cloud API and follow the connection-lost behavior in §4.2. Tablets are Later (ADR-0007). Device registration and pairing are designed in the device model document (Derived documents).

## 12. Restaurant Edge

Optional. Not built this run; built only when a restaurant with poor signal needs it (ADR-0003). This is the intended shape. The full design comes in the Edge documents, written after the Order Core contract is Reviewed.

> Note 2026-10-01 (pointer; the text above is unchanged): Gabriel has since set the trigger (Q-80 b; ADR-0003 Decision 1). The Edge is built when he decides, as an optional paid feature, not on a restaurant's request. Gabriel: "b when I decide, for now we focus only on making sure everything is funcional but keeping in mind we will have edge later and that its optional paid feature". Until then the work makes everything functional and keeps the Edge-ready constraints (ADR-0003).

```text
Restaurant Edge (optional)
├── Local API
├── Local Database
├── Synchronization Engine
├── Offline Order Queue          orders only; never payments
├── Device Registry
├── Local Configuration Cache    read-only; configuration is cloud-owned
├── Menu Cache                   read-only; the menu is cloud-owned
├── Order Store
└── Connectivity Monitor
```

Future (not required for the first implementation): Printer Gateway, Local DNS / Service Discovery, Hardware Integration.

Payment and fiscal are not Edge capabilities. The platform integrates with existing payment and fiscal systems and never builds them (§26).

## 13. Local data

> §13-§16 apply only where a restaurant has an Edge (§2). This run there is no Edge, no local data and no sync. Their detailed design is deferred to `RESTAURANT-EDGE-ARCHITECTURE.md` and `OFFLINE-SYNC-DESIGN.md`, written after the Order Core contract is Reviewed: per-entity ownership, the sync mechanism, reconciliation and the local projection. What the core must honor now is the constraint list in ADR-0003.

Minimum operational dataset: Restaurant, restaurant configuration, Menu, Categories, Products, Modifiers, Tables, Active orders, Order items, Order status, Operational policies, Device configuration. The Edge holds a local operational projection, not a full copy of the SaaS database.

## 14. Synchronization

Asynchronous.

```text
Local operation → Local Database → Sync Queue → (Internet restored) → Cloud API → Cloud Database
Cloud → Sync/Event → Edge → Local Database
```

Must be: idempotent; retryable; observable; resilient to duplicate delivery; recoverable after Edge restart; tolerant of temporary cloud unavailability.

The sync mechanism, for example replaying domain events through an outbox, is not chosen here. This run records a status history and uses in-process events only (ADR-0019). An outbox is added when the first external consumer, such as Edge sync, is scheduled.

## 15. Identity of offline operations

Entities that may be created locally use globally unique identifiers (ULID/UUID), never cloud-generated sequential IDs. The Edge creates `Order #01K...` without contacting the cloud; a retried sync of the same ID is recognized as already processed. This provides idempotency.

This part applies now, without an Edge. Orders and order lines get client-generated ids, so a retry after a lost connection never creates a second order (ADR-0003, ADR-0004). The id is a ULID, every table uses ULID primary keys, and each order also gets a short per-restaurant human order number generated by the server (ADR-0009).

## 16. Source of truth

- **Normal operation:** the Cloud is the authoritative system of record.
- **Temporary disconnection, no Edge (every restaurant this run):** the Cloud stays authoritative. An order kept on a device is not a record until the server acknowledges it (§4.2).
- **Temporary disconnection, with an Edge:** the Edge is authoritative for that restaurant's supported offline operations, which are orders only (§17). Menu and configuration stay cloud-owned, and order status only moves forward (ADR-0003 C3-C4). Per-entity ownership beyond that is decided in `OFFLINE-SYNC-DESIGN.md`.
- **Reconnection:** Edge → Synchronization → Cloud; the system reconciles local state with the cloud.

This is one logical restaurant system with a cloud state and a local operational projection, not two independent databases.

## 17. What must work offline

Offline means orders: taking an order and getting it to production, waiter → KDS/POS (ADR-0004). Nothing else is promised to work without a connection.

**Every restaurant (this run, no Edge):** order-creating surfaces show a clear connection-lost state, keep in-progress orders on the device, and retry them safely with the same client-generated order id. An order counts as sent only after the server acknowledges it.

**With an Edge (optional, later):** during Internet loss the restaurant keeps working on orders through the Edge:
- create order; modify order; cancel order where policy permits;
- update operational order status; send order to production; receive KDS updates;
- waiter ordering; POS order operations.

Menu, restaurant configuration and tables are readable from the Edge's cache. Customer QR and table-tablet ordering work offline only in this case.

**Never offline:** payments. They are never processed, captured or queued, online or offline; the platform records results only (§26).

**Needs the cloud or additional infrastructure:** menu and configuration changes; external delivery marketplace; external messaging; external notifications; cloud analytics; third-party integrations.

**Must not be falsely represented as successful:** payment authorization; external marketplace confirmation; any external API operation. If an external system cannot be reached, the operation is `PENDING`, never faked as success.

## 18. Delivery

In current scope at the storefront/order level: `Order.channel = DELIVERY` vs `DINE_IN`. Current: Restaurant Storefront → Delivery Order → Order Core. Future integrations (iFood, Rappi, WhatsApp, other marketplaces, delivery platforms, own logistics) are planned, not required now, and will create orders in the same Order Core.

## 19. Future operational architecture

```text
                       RESTAURANT OPS CORE
        ┌─────────────┬───────┼────────┬─────────────┐
     Storefront     Waiter   POS      KDS         Tablet
        └─────────────┴───────┼────────┴─────────────┘
                           Orders
                ┌─────────────┼──────────────┐
             Payment        Fiscal        Delivery
             (future)       (future)      (future)
```

Payment, Fiscal and Delivery (marketplaces) are integrations with existing external systems. The platform never builds these systems (§26).

The current phase needs strong contracts even though these consumers are not implemented yet.

## 20. Infrastructure model

```text
CLOUD: Shared Multi-Tenant Backend · PostgreSQL · Object Storage · Authentication
       Restaurant Management · Menu · Orders · Storefront
                    │ HTTPS/API
            RESTAURANT INTERNET → Restaurant LAN
                    │
             EDGE (optional): Local API · Local DB · Sync Engine · Order Queue
                    │
          POS · KDS · Devices (Waiters, Tablets)
```

The EDGE layer exists only where a restaurant has an Edge (§2). Without one, POS, KDS and devices reach the cloud over HTTPS through the restaurant's Internet connection. Connectivity backup, for example a 4G/5G failover router, is the restaurant's own infrastructure (§4.3).

## 21. What we should NOT build yet (extension points only)

- **Payments:** PIX, credit/debit, SmartPOS, PSP, refund, payment reconciliation
- **Fiscal:** NFC-e, SAT where applicable, contingency, fiscal certificates
- **Delivery integrations:** iFood, Rappi, other marketplaces, logistics
- **Hardware:** printers, cash drawer, scanners, scales, payment terminals
- **Advanced operations:** inventory, purchasing, recipes, CMV, staff management, advanced financial management, loyalty, CRM

They influence the architecture now but must not expand the current implementation.

For payments, fiscal and delivery marketplaces, "not yet" means: when an integration is scheduled, integrate with an existing system; never build one (§26). None of them is integrated this run (ADR-0006, ADR-0007).

## 22. Current development boundary

```text
                 Restaurant Platform
        ┌────────────────┼────────────────┐
    Restaurant          Menu            Orders
    Configuration                         │
        └───────────────┬──────────────────┘
                   Storefront
             ┌──────────┴──────────┐
           Home                 Delivery Ordering
```

Implemented against contracts that let Storefront, Waiter, POS, KDS, Tablet and QR become consumers later.

## 23. The most important architectural decision

The system is designed around the **Restaurant Operational Core**, not the current frontend. The frontend is the first consumer.

Avoid business logic like `StorefrontService.createOrder()` containing storefront-only rules. Instead, `OrderApplicationService.createOrder(...)` holds the core order behavior; the storefront, waiter, POS and tablet adapters call it.

## 24. Final architectural principle

**One restaurant, one operational model, multiple interfaces, two execution environments (the second optional).**

- **Cloud:** centralized SaaS; management; public storefront; persistence; analytics; integrations with existing external systems (§26); synchronization with an Edge where one exists.
- **Restaurant Edge (optional; not built this run, ADR-0003):** local order continuity during Internet loss; local devices; local orders; read-only menu and configuration cache; KDS; POS; waiter; table tablets; synchronization.
- **Interfaces:** Customer, Waiter, Cashier, Kitchen, Manager, Tablet, QR, Delivery. All consume the same operational core.

## 25. Tenancy and identity

Restaurant Ops is one shared multi-tenant SaaS. One deployment and one database serve every restaurant, and the restaurant is the tenant (ADR-0001). Restaurant-owned data carries the restaurant id, and every read and write of it is scoped to the caller's restaurant. A request without a restaurant context fails closed.

The Tenancy & Identity contract defines the rest; this document does not:
- the isolation mechanism;
- how users belong to restaurants;
- restaurant roles versus a platform administrator;
- what signup does;
- how a public page (storefront, QR, order tracking) finds its restaurant;
- the tenant id in logs.

Until that contract is Reviewed, no tenant-resolution scheme, role model or row-level-security mechanism is assumed.

> Note 2026-10-01 (pointer; the text above is unchanged): Gabriel has since answered most of this list in the register (Q-16..Q-31; Q-30 is still open). ADR-0001 Decision 5 records the answers and the Tenancy & Identity contract records the detail. Isolation is application-level scoping, `restaurant_id` on every tenant-owned table, with no row-level security (Q-25 a). A user belongs to one restaurant for now, through a membership table (Q-16 a). A platform admin is separate from the per-restaurant roles owner, manager and staff (Q-17 a) and has full read access for support (Q-18 b). Signup is invite-only (Q-19 a). A public page finds its restaurant by the path `/r/{slug}` (Q-20 a). A staff request's restaurant comes from the signed-in user's membership, never from the path or body (Q-26 a, decided by Claude at Gabriel's request). The rule above still holds until that contract is Reviewed.

## 26. Payments and external systems

- The platform never processes, authorizes, captures, refunds or queues payment transactions, online or offline. Card terminals (4G/5G) authorize on their own. The platform records the result against the order, and payment status stays separate from order status (ADR-0005).
- The platform integrates with existing payment, fiscal and marketplace systems. It never builds one (ADR-0005).
- An integration writes through the Order Core like any other consumer (§23). If the external system cannot be reached, the result is pending, never a faked success (§17).
- This run integrates no external payment, fiscal, marketplace or messaging system. The PSP gateway and automatic WhatsApp messages are out; manual `wa.me` links stay (ADR-0006).

## Derived documents

They live in the backend repo `docs/` (ADR-0011). The names of the two new files are proposals.

```text
docs/architecture/
├── RESTAURANT-OPS-ARCHITECTURE.md   root (this document)
├── TENANCY-AND-IDENTITY.md          Tenancy & Identity contract; Reviewed before Build 1
├── API-CONVENTIONS.md               money, enum casing, ids, pagination, Idempotency-Key, error envelope, draft graduation; Reviewed before Build 1
├── ORDER-CORE-CONTRACT.md           Reviewed before Build 3
└── MENU-AND-STOREFRONT-CONTRACT.md  Menu part before Build 2; Storefront part before Build 4
```

Edge-ready constraints: ADR-0003, not a separate document. Deferred until the Order Core contract is Reviewed: `RESTAURANT-EDGE-ARCHITECTURE.md`, `OFFLINE-SYNC-DESIGN.md`, `RESTAURANT-DEVICE-MODEL.md`.

## Original implementation order (superseded by ADR-0007)

> Superseded on 2026-09-29 by ADR-0007 (D-7). The current order, after the readiness gate, is Tenant foundation → Menu → Order Core → Storefront ↔ Order Core.
> - Later: QR/table, waiter, KDS on Edge, POS, tablets, Edge build.
> - Out this run: PSP, automatic WhatsApp, fiscal, marketplaces, billing.
>
> The list below is kept as Gabriel wrote it, for history. Do not plan from it.

1. Restaurant/Tenant foundation
2. Menu domain
3. Storefront
4. Order Core
5. Restaurant Edge
6. Local persistence
7. Offline synchronization
8. Storefront ↔ Order Core
9. QR/table context
10. Waiter client
11. KDS
12. POS
13. Tablet
14. Payments
15. Fiscal
16. Marketplace integrations

The original critical decision, placing the Edge and the sync model before the operational consumers, is superseded. The Edge is optional (§2, ADR-0003), and storefront delivery never needs it (ADR-0007). The goal behind it stands: when waiter, KDS and POS arrive, `Order`, `Menu`, `Restaurant`, IDs, events, authentication, tenancy and persistence must not need redesign. ADR-0003 (Edge-ready constraints), ADR-0009 (IDs) and ADR-0019 (events) carry that goal.

Current order of work: `planning/MASTER-PLAN.md` §6 and ADR-0007. Claude's original review of this list: `planning/pm-tool-recommendation.md`, section "Architecture review".

## Open questions

Cross-doc questions live in `planning/open-questions.md` (ADR-0016). Those that touch this document:
- Q-30: where restaurant settings and delivery zones are built (§9, §25). Blocks: S5 scope page, Build 1 scope. Open.
- Q-86: confirms Gabriel's Q-70 pickup answer, which ends mid-sentence, and whether street address and the "Continuar no WhatsApp" link (rules 54, 56) stay in Build 4 (§9). Blocks: Storefront contract. Open.
- ADR-0020 (Proposed, awaiting Gabriel; Q-65): storefront PIX prepayment through a static PIX code that staff confirm by hand, with no PSP. §21 lists PIX under payments not built yet; whether §21 and §26 need a note or an amendment waits for Gabriel's decision on ADR-0020. Blocks: Storefront contract.

## Amendment log

- 2026-10-01: amendments from `ARCHITECTURE-AMENDMENTS-PROPOSED.md` applied as accepted by Gabriel (Q-04; he ticked every line of its checklist in commit `6a60b6d`). Accepted: A-01, A-02, A-03, A-04, A-05, A-06, A-07, A-08, A-09, A-10, A-11, A-12, A-13, A-14, A-15. Changed: none. Rejected: none.
  - A-01: decision status (header); this log. Adapted per Q-12 (decided by Claude, Gabriel may override): the ticked "**Status:** Accepted with amendments" line is labelled "**Decision status:**", and a "**Status:**" line carries the doc status (Draft, ADR-0016).
  - A-02: the Restaurant Edge is optional (header, §2, §20, §24).
  - A-03: offline means orders (guiding principle, §3, §4.1, §4.2).
  - A-04: the "Payment" steps become payment records (§6, §10).
  - A-05: channel and source vocabulary flagged open (§7).
  - A-06: owner customization is bounded (§9).
  - A-07: tablets use the Edge only where one exists (§11).
  - A-08: Edge optional, "Offline Order Queue", read-only caches, no payment or fiscal gateway (§12). "Fiscal Gateway" dropped (Gabriel ticked "drop"; Reviewer note 1 of `ARCHITECTURE-AMENDMENTS-PROPOSED.md`).
  - A-09: §13-§16 apply only with an Edge; design deferred; client-generated ULID ids and the human order number apply now (§13-§16).
  - A-10: offline scope narrowed to orders (§17).
  - A-11: future payment, fiscal and marketplace work is integration, never a build (§19, §21).
  - A-12: new §25 "Tenancy and identity".
  - A-13: new §26 "Payments and external systems".
  - A-14: shorter derived-documents list; Edge docs deferred.
  - A-15: original implementation order marked superseded by ADR-0007.
- 2026-10-01: pointer notes, not amendments. Gabriel's register answers of the same day are noted under §7 (Q-34, Q-47, Q-49, Q-51, Q-53), §9 (Q-66), §12 (Q-80) and §25 (Q-16..Q-20, Q-25; Q-26 decided by Claude at Gabriel's request), and an "Open questions" block is added (ADR-0016). The text above each note is unchanged; rewording it to match needs a new amendment ticked by Gabriel.
- Text marked "(Proposed)" follows an ADR that is not yet Accepted.
- Where this document disagrees with an Accepted ADR, the ADR wins until this document is amended again.
