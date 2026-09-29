# Restaurant Ops: Platform Architecture and Operational Foundation

- **Status:** Proposed (by Gabriel, 2026-09-29; captured from his message in the project thread)
- **Scope:** Current product foundation
- **Architecture:** Multi-tenant SaaS + Restaurant Edge
- **Primary objective:** Establish the central restaurant operating model before implementing peripheral operational integrations.

> Guiding principle: build the restaurant's operational core first, but design it from the start to be consumed by multiple channels, online and local, with operation that survives loss of Internet.

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

**Cloud SaaS + Restaurant Edge.** The cloud provides the centralized SaaS platform. Each physical restaurant has a lightweight local operational environment, the Restaurant Edge.

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

The Edge is not a second complete SaaS deployment. It is a local operational node that lets the restaurant keep operating when the cloud is temporarily unavailable.

## 3. Online and offline are separate operational modes

Cloud connectivity (`Internet → Cloud`) and restaurant connectivity (`Device → Restaurant LAN → Edge`) are different dependencies. The restaurant must remain operational when `Internet = unavailable` and `LAN = available`. This is the primary offline scenario.

## 4. Operational availability model

**4.1 Normal operation:** Internet ✓, LAN ✓, Edge ✓, Cloud ✓. Devices communicate locally while the cloud stays synchronized.

**4.2 Internet unavailable:** Internet ✗, LAN ✓, Edge ✓, Cloud unavailable. The restaurant continues core operations through the Edge: waiter creates orders; table tablet creates orders; customer QR creates orders; POS operates; KDS receives orders; menu, table state and local configuration remain available. Changes are stored locally and synchronized when connectivity returns.

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
POS             → Order / Payment / Table APIs
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

Restaurant-customizable: brand name, logo, cover image, colors, typography, storefront layout, menu presentation, product images, descriptions, categories, promotional content. It is a restaurant-specific digital storefront, not a generic ordering page.

## 10. QR and table experience

QR is another entry point into the same customer experience, with configuration-driven capability levels (not separate products):

- **Menu-only:** QR → Menu
- **Ordering:** QR → Menu → Cart → Order
- **Full table experience:** QR → Menu → Order → Current table consumption → Call waiter → Request bill → Payment

## 11. Table tablets

Same customer ordering experience; the difference is device context. A registered tablet has `deviceId`, `restaurantId`, `tableId`, `deviceType`, `capabilities`, so `Tablet #17 → Restaurant A → Table 17` without the customer identifying the table. Tablets consume the local Edge API and keep working during Internet loss.

## 12. Restaurant Edge

```text
Restaurant Edge
├── Local API
├── Local Database
├── Synchronization Engine
├── Offline Transaction Queue
├── Device Registry
├── Local Configuration Cache
├── Menu Cache
├── Order Store
└── Connectivity Monitor
```

Future (not required for the first implementation): Printer Gateway, Fiscal Gateway, Payment Device Gateway, Local DNS / Service Discovery, Hardware Integration.

## 13. Local data

Minimum operational dataset: Restaurant, restaurant configuration, Menu, Categories, Products, Modifiers, Tables, Active orders, Order items, Order status, Operational policies, Device configuration. The Edge holds a local operational projection, not a full copy of the SaaS database.

## 14. Synchronization

Asynchronous.

```text
Local operation → Local Database → Sync Queue → (Internet restored) → Cloud API → Cloud Database
Cloud → Sync/Event → Edge → Local Database
```

Must be: idempotent; retryable; observable; resilient to duplicate delivery; recoverable after Edge restart; tolerant of temporary cloud unavailability.

## 15. Identity of offline operations

Entities that may be created locally use globally unique identifiers (ULID/UUID), never cloud-generated sequential IDs. The Edge creates `Order #01K...` without contacting the cloud; a retried sync of the same ID is recognized as already processed. This provides idempotency.

## 16. Source of truth

- **Normal operation:** the Cloud is the authoritative system of record.
- **Temporary disconnection:** the Edge is authoritative for that restaurant's supported offline operations.
- **Reconnection:** Edge → Synchronization → Cloud; the system reconciles local state with the cloud.

This is one logical restaurant system with a cloud state and a local operational projection, not two independent databases.

## 17. What must work offline

**Must work:** read menu; read restaurant configuration; read tables; create order; modify order; cancel order where policy permits; update operational order status; send order to production; receive KDS updates; table ordering; waiter ordering; POS order operations.

**May require additional infrastructure:** external delivery marketplace; external payment authorization; external messaging; external notifications; cloud analytics; third-party integrations.

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

The current phase needs strong contracts even though these consumers are not implemented yet.

## 20. Infrastructure model

```text
CLOUD: Shared Multi-Tenant Backend · PostgreSQL · Object Storage · Authentication
       Restaurant Management · Menu · Orders · Storefront
                    │ HTTPS/API
            RESTAURANT INTERNET → Restaurant LAN
                    │
             EDGE: Local API · Local DB · Sync Engine · Queue
                    │
          POS · KDS · Devices (Waiters, Tablets)
```

## 21. What we should NOT build yet (extension points only)

- **Payments:** PIX, credit/debit, SmartPOS, PSP, refund, payment reconciliation
- **Fiscal:** NFC-e, SAT where applicable, contingency, fiscal certificates
- **Delivery integrations:** iFood, Rappi, other marketplaces, logistics
- **Hardware:** printers, cash drawer, scanners, scales, payment terminals
- **Advanced operations:** inventory, purchasing, recipes, CMV, staff management, advanced financial management, loyalty, CRM

They influence the architecture now but must not expand the current implementation.

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

**One restaurant, one operational model, multiple interfaces, two execution environments.**

- **Cloud:** centralized SaaS; management; public storefront; persistence; analytics; integrations; synchronization.
- **Restaurant Edge:** local operation; offline continuity; local devices; local orders; local menu; KDS; POS; waiter; table tablets; synchronization.
- **Interfaces:** Customer, Waiter, Cashier, Kitchen, Manager, Tablet, QR, Delivery. All consume the same operational core.

## Derived documents (proposed)

```text
docs/architecture/
├── RESTAURANT-OPS-ARCHITECTURE.md
├── RESTAURANT-EDGE-ARCHITECTURE.md
├── ORDER-CORE-CONTRACT.md
├── MENU-AND-STOREFRONT-CONTRACT.md
├── OFFLINE-SYNC-DESIGN.md
└── RESTAURANT-DEVICE-MODEL.md
```

## Proposed implementation order (as written by Gabriel)

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

The critical decision is placing the Edge and the sync model before the operational consumers. KDS, POS and the waiter app are not built now, but when they arrive, `Order`, `Menu`, `Restaurant`, IDs, events, authentication, tenancy and persistence must not need redesign.

See `planning/pm-tool-recommendation.md`, section "Architecture review", for Claude's review of this order.
