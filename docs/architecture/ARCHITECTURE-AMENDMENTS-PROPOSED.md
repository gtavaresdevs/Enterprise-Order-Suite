# Architecture amendments (proposed)

- Status: Proposed (awaiting Gabriel's review in S1: Q-04, open). Updated 2026-09-29 after Q-01 and Q-03 were answered: the ADR bases below are Accepted; the amendments themselves still wait for Q-04.
- Date: 2026-09-29
- Proposed by: Claude (proposed)
- Target: `architecture/RESTAURANT-OPS-ARCHITECTURE.md`, Gabriel's text (header Status "Proposed", L3; 341 lines on 2026-09-29). All line numbers below refer to that version.
- Related: ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0005, ADR-0006, ADR-0007, ADR-0009, ADR-0011, ADR-0014, ADR-0016, ADR-0019
- Plan step: written in S0 (MASTER-PLAN §5 L73, §6 L87). Gabriel accepts it in S1 (§6 L88). "The amended architecture is Accepted by Gabriel" is a readiness-gate item (§7 L99).

## How to use this file
- No agent edits Gabriel's text until Gabriel has ticked the checklist at the end.
- Each amendment gives:
  - the section and the current text (a short quote with line numbers);
  - replacement text, ready to paste;
  - the reason and the ADR;
  - its basis: an Accepted decision (MASTER-PLAN §3 D-1..D-16), or a Proposed ADR that Gabriel has not confirmed.
- After Q-04 is answered, Claude pastes the accepted and changed amendments verbatim (S2). The doc then moves to the backend repo `docs/architecture/` (ADR-0011; Q-01 answered 2026-09-29).
- Section numbers §1-§24 stay as they are. The two new sections are appended as §25-§26 (A-12, A-13). Nothing is renumbered.
- Text marked "(Proposed)" depends on an ADR that Gabriel has not accepted. If he accepts the amendment but not that ADR, the marker stays in the pasted text. Since Q-03 was answered (2026-09-29), ADR-0003 C1/C3/C4, ADR-0009 and ADR-0019 are Accepted and no amendment below carries the marker.
- Some amendments only restate an Accepted decision. If Gabriel rejects one of those, the doc and the decision conflict. Claude then asks whether the decision itself changes. Until it does, MASTER-PLAN and the ADR win (MASTER-PLAN L3-6: "Where they disagree with this file, this file wins").
- Until the amendments are applied: an agent reading the architecture doc follows MASTER-PLAN and the ADRs wherever they disagree with it.

## Summary

| Id | Section(s) of Gabriel's doc | Change | ADR | Basis |
|---|---|---|---|---|
| A-01 | Header (L3); new closing section | Status Proposed → Accepted with amendments; amendment log | all below | MASTER-PLAN §5 L73, §7 L99 |
| A-02 | Header (L5); §2 (L42, L71); §20 (L264); §24 (L302-305) | The Restaurant Edge is optional | ADR-0003 | Accepted D-3 |
| A-03 | Guiding principle (L8); §3 (L75); §4.1-4.2 (L79-81) | Offline means orders. Without an Edge, the app shows connection lost, keeps orders on the device and retries safely | ADR-0004 | Accepted D-4 |
| A-04 | §6 (L117); §10 (L179) | The "Payment" steps become payment records | ADR-0005, ADR-0007 | Accepted D-5, D-7 |
| A-05 | §7 (after L149) | Channel/source vocabulary flagged open; staff phone orders have no source | ADR-0002 | Accepted D-2 (the concept); the values are open |
| A-06 | §9 (L171) | Owner customization is bounded | ADR-0014 | Accepted D-12 |
| A-07 | §11 (L183) | Tablets use the Edge only where one exists | ADR-0003, ADR-0004 | Accepted D-3, D-4 |
| A-08 | §12 (L185-200) | Edge marked optional; "Offline Order Queue"; payment and fiscal gateways dropped | ADR-0003, ADR-0004, ADR-0005 | Accepted D-3, D-4, D-5. Read-only caches: ADR-0003 C4 (Q-03 row 3.4, accepted 2026-09-29) |
| A-09 | §13-§16 (L202-227) | Local data, sync, ids and source of truth apply only with an Edge; design deferred; constraints in ADR-0003 | ADR-0003, ADR-0004, ADR-0009, ADR-0019 | Accepted D-3, D-4. ULID everywhere, events, C1/C3/C4: Q-03 rows 3.4, 3.5, 3.11 (accepted 2026-09-29) |
| A-10 | §17 (L229-235) | Offline scope narrowed to orders | ADR-0004, ADR-0005 | Accepted D-4, D-5 |
| A-11 | §19 (after L252); §21 (after L277) | Future payment, fiscal and marketplace work: integrate, never build | ADR-0005, ADR-0006 | Accepted D-5, D-6 |
| A-12 | New §25 | "Tenancy and identity" pointer | ADR-0001 | Accepted D-1 |
| A-13 | New §26 | "Payments and external systems" principle | ADR-0005, ADR-0006 | Accepted D-5, D-6 |
| A-14 | Derived documents (L308-318) | Shorter list: Tenancy & Identity and API conventions added; Edge docs deferred | ADR-0003, ADR-0011, ADR-0016 | Q-03 row 3.3 and Q-01 (accepted 2026-09-29); new docs from MASTER-PLAN §5 L74-75 |
| A-15 | Proposed implementation order (L320-341) | Marked superseded | ADR-0007 | Accepted D-7 |

---

## A-01: Status Proposed → Accepted with amendments
- Section: header, L3. Also adds a new closing section.
- Current (L3): "**Status:** Proposed (by Gabriel, 2026-09-29; captured from his message in the project thread)"
- Proposed text (a), which replaces L3:
~~~markdown
- **Status:** Accepted with amendments (Gabriel, <date of S1 acceptance>). Proposed by Gabriel on 2026-09-29 (captured from his message in the project thread). Amended as listed in "Amendment log" at the end of this document.
~~~
- Proposed text (b), which is appended as the last section of the document, after the text from A-15:
~~~markdown
## Amendment log

- <date of S1 acceptance>: amendments from `ARCHITECTURE-AMENDMENTS-PROPOSED.md` applied as accepted by Gabriel. Accepted: <ids>. Changed: <ids>. Rejected: <ids>.
- Text marked "(Proposed)" follows an ADR that is not yet Accepted.
- Where this document disagrees with an Accepted ADR, the ADR wins until this document is amended again.
~~~
- Reason:
  - The gate requires "the amended architecture Accepted by Gabriel" (MASTER-PLAN §7 L99).
  - The header still says Proposed (L3), so agents cannot tell which parts hold.
  - The log keeps Gabriel's original text traceable.
- ADR: every ADR cited below.
- Basis: MASTER-PLAN §5 L73 ("status → Accepted"), §7 L99.

## A-02: The Restaurant Edge is optional (header, §2, §20, §24)
- Current (L5): "**Architecture:** Multi-tenant SaaS + Restaurant Edge"
- Proposed text (a), which replaces L5:
~~~markdown
- **Architecture:** Multi-tenant SaaS (§25); Restaurant Edge optional, not built this run (§2, §12)
~~~
- Current §2 (L42): "**Cloud SaaS + Restaurant Edge.** The cloud provides the centralized SaaS platform. Each physical restaurant has a lightweight local operational environment, the Restaurant Edge."
- Proposed text (b), which replaces L42 (the diagram L44-69 stays):
~~~markdown
**Cloud SaaS, with an optional Restaurant Edge.** The cloud provides the centralized SaaS platform. It is the only execution environment this run. A physical restaurant with poor connectivity may later add a lightweight local operational environment, the Restaurant Edge. The Edge is not built this run. It is designed only where it constrains the core (ADR-0003). The diagram below shows a restaurant that has an Edge. Without one, POS, KDS and devices reach the cloud directly over the restaurant's Internet connection.
~~~
- Current §2 (L71): "The Edge is not a second complete SaaS deployment. It is a local operational node that lets the restaurant keep operating when the cloud is temporarily unavailable."
- Proposed text (c), which replaces L71:
~~~markdown
The Edge is not a second complete SaaS deployment. It is a local operational node that lets a restaurant that has one keep taking orders when the cloud is temporarily unavailable (§17).
~~~
- Current §20 (L264, inside the diagram): "EDGE: Local API · Local DB · Sync Engine · Queue"
- Proposed text (d), which replaces that diagram line. The indentation stays as is.
~~~text
             EDGE (optional): Local API · Local DB · Sync Engine · Order Queue
~~~
- Proposed text (e), which is appended after the §20 diagram (after L267):
~~~markdown
The EDGE layer exists only where a restaurant has an Edge (§2). Without one, POS, KDS and devices reach the cloud over HTTPS through the restaurant's Internet connection. Connectivity backup, for example a 4G/5G failover router, is the restaurant's own infrastructure (§4.3).
~~~
- Current §24 (L302-305): "**One restaurant, one operational model, multiple interfaces, two execution environments.**" and "**Restaurant Edge:** local operation; offline continuity; local devices; local orders; local menu; KDS; POS; waiter; table tablets; synchronization."
- Proposed text (f), which replaces L302-305 (the Interfaces bullet, L306, stays):
~~~markdown
**One restaurant, one operational model, multiple interfaces, two execution environments (the second optional).**

- **Cloud:** centralized SaaS; management; public storefront; persistence; analytics; integrations with existing external systems (§26); synchronization with an Edge where one exists.
- **Restaurant Edge (optional; not built this run, ADR-0003):** local order continuity during Internet loss; local devices; local orders; read-only menu and configuration cache; KDS; POS; waiter; table tablets; synchronization.
~~~
- Reason:
  - D-3: the Edge is optional and not built this run. It is only worth building for restaurants with poor signal, and it is designed only where it constrains the core.
  - The current text gives every restaurant an Edge (L42) and treats "two execution environments" as the base case (L302).
  - Today the existing KDS polls the cloud every 5 s (FE `src/features/kds/constants/kds.constants.ts` L3). It stays cloud-only until an Edge exists (ADR-0007).
- ADR: ADR-0003.
- Basis: Accepted D-3 (MASTER-PLAN L36). "Read-only ... cache" in (f) restates ADR-0003 C4 (accepted with Q-03 row 3.4, 2026-09-29).

## A-03: Offline means orders (guiding principle, §3, §4)
- Current (L8): "> Guiding principle: ... with operation that survives loss of Internet."
- Proposed text (a), which replaces L8:
~~~markdown
> Guiding principle: build the restaurant's operational core first, but design it from the start to be consumed by multiple channels, online and local, with order taking that survives loss of Internet (§4.2, §17).
~~~
- Current §3 (L75): "The restaurant must remain operational when `Internet = unavailable` and `LAN = available`. This is the primary offline scenario."
- Proposed text (b), which replaces L75:
~~~markdown
Cloud connectivity (`Internet → Cloud`) and restaurant connectivity (`Device → Restaurant LAN → Edge`) are different dependencies. A restaurant with an Edge keeps taking orders when `Internet = unavailable` and `LAN = available`; this is the scenario the Edge exists for. Without an Edge (every restaurant this run), Internet loss is handled by the connection-lost behavior in §4.2. Connectivity backup, for example a 4G/5G failover router, is the restaurant's own infrastructure. In every case, offline means orders (§17).
~~~
- Current §4.1 (L79): "Internet ✓, LAN ✓, Edge ✓, Cloud ✓. Devices communicate locally while the cloud stays synchronized."
- Proposed text (c), which replaces L79:
~~~markdown
**4.1 Normal operation:** Internet ✓, LAN ✓, Cloud ✓ (Edge ✓ where one exists). Devices talk to the Edge where one exists, and to the cloud otherwise. The cloud stays synchronized.
~~~
- Current §4.2 (L81): "The restaurant continues core operations through the Edge: waiter creates orders; table tablet creates orders; customer QR creates orders; POS operates; KDS receives orders; ..."
- Proposed text (d), which replaces L81:
~~~markdown
**4.2 Internet unavailable:** Internet ✗, LAN ✓, Cloud unavailable.
- **Without an Edge (every restaurant this run):** order-creating surfaces show a clear connection-lost state, keep in-progress orders on the device, and retry them safely. A retry reuses the order's client-generated id, so the server never creates a duplicate. Nothing else is promised offline.
- **With an Edge (optional, later):** orders keep flowing from waiter and POS to the KDS through the Edge. Menu, table state and restaurant configuration stay readable from the Edge's cache. Customer QR and table-tablet ordering work offline only in this case. Orders are stored locally and synchronized when connectivity returns.
- Payments are never queued or processed offline (§26).
~~~
- Reason:
  - D-4: offline means orders (waiter → kitchen). Connectivity backup is the customer's infrastructure. The app shows a connection-lost state, keeps in-progress orders on the device and retries safely.
  - §4.2 as written promises offline QR, tablet and POS operation to every restaurant through an Edge that is not built (D-3).
  - Today the frontend has no connection-lost handling for orders (ADR-0004 Context, citing FE `src/api/client.ts` L71, L96).
- ADR: ADR-0004 (and ADR-0003 C2 for idempotent create).
- Basis: Accepted D-4 (MASTER-PLAN L37). §4.3 (L83) already lists connectivity backups as infrastructure and needs no change. "Client-generated id" in (d) restates ADR-0003 C1 (accepted with Q-03 row 3.4, 2026-09-29).

## A-04: The "Payment" steps become payment records (§6, §10)
- Current §6 (L117): "POS → Order / Payment / Table APIs"
- Proposed text (a), which replaces L117. The diagram alignment stays.
~~~text
POS             → Order / Payment record / Table APIs
~~~
- Current §10 (L179): "- **Full table experience:** QR → Menu → Order → Current table consumption → Call waiter → Request bill → Payment"
- Proposed text (b), which replaces L179 and adds one line after the list:
~~~markdown
- **Full table experience:** QR → Menu → Order → Current table consumption → Call waiter → Request bill → Payment record. The customer pays with the restaurant's card terminal, in cash or through an existing payment system. The platform records the result and never processes the payment (§26).

QR ordering and the full table experience are Later (ADR-0007). Where the existing menu-only QR page (`/table-menu`) lands this run is decided in the scope-of-run page (S5).
~~~
- Reason:
  - D-5: the app never processes or queues payment transactions. Card terminals authorize on their own, and the app records the result.
  - "→ Payment" as a step of the app reads as in-app payment. The PSP is out this run (D-6).
  - QR/table is on the Later list (MASTER-PLAN §6 L95).
  - The frontend has a menu-only `/table-menu` page (FE `src/app/router.tsx` L66). No table experience beyond it exists: frontend phase 10 comandas were never built (inventory, "Are dine-in comandas in this run?").
- ADR: ADR-0005, ADR-0006, ADR-0007.
- Basis: Accepted D-5, D-6, D-7 (MASTER-PLAN L38-40).

## A-05: Channel and source vocabulary flagged open (§7)
- Current §7 (L132-133): "channel DINE_IN | TAKEAWAY | DELIVERY" and "source WAITER | POS | QR | TABLET | STOREFRONT | FUTURE_MARKETPLACE"
- Proposed text, appended after the §7 diagrams (after L149):
~~~markdown
**Open until the Order Core contract is Reviewed.** The `channel` and `source` value sets above are not final. Neither are their wire casing or the mapping from today's vocabulary. Today the frontend uses channel `Online | Dine-in | Phone` plus fulfillment `Pickup | Delivery`, and the backend order has no channel. Staff-taken phone orders exist today but have no `source` above.

Also open:
- the order status set;
- whether `customer` is a per-restaurant record or a snapshot on each order;
- whether `table` enters the Order Core this run.

Until that contract is Reviewed, nobody implements, renames or adds these enum values (ADR-0002).
~~~
- Reason:
  - D-2 fixes the concept: one Order with channel and source. The values are still open (MASTER-PLAN §5 L78 "Phone→?", §8c L118 "channel/source mapping including staff phone orders").
  - Staff create `Phone` orders today:
    - FE `src/types/orders.ts` L1-2 (`OrderChannel`, `Fulfillment`);
    - FE `src/features/orders/components/CreateOrderModal.tsx` L23 (default channel `"Phone"`);
    - business rules 1-2 (PF `2026-09-16-business-rules-master-en.md` L16-17).
  - A grep of BE `src/main/java/com/enterprise/ordersuite/orders/` and `src/main/resources/db/migration/` finds no `channel` or `source` field.
  - The enum lists in §7 read as final, so an agent could implement them as they stand.
- ADR: ADR-0002.
- Basis: Accepted D-2 (MASTER-PLAN L35) for the concept. The values stay open (§8c).

## A-06: Owner customization is bounded (§9)
- Current §9 (L171): "Restaurant-customizable: brand name, logo, cover image, colors, typography, storefront layout, menu presentation, product images, descriptions, categories, promotional content. ..."
- Proposed text, which replaces L171:
~~~markdown
Restaurant-customizable, within bounded choices (ADR-0014):
- a logo, a cover image and brand colors;
- one font from a fixed list;
- one storefront layout preset from a fixed list.

There is no custom CSS, HTML or script, and owner-entered text is never rendered as HTML. Restaurant settings are a typed schema owned by the backend. Public pages read them from the backend, never from the viewer's device.

Brand name, menu presentation, product images, descriptions, categories and promotional content are available only as far as the Storefront or Menu spec defines them. Menu presentation builds on the existing menu features (frontend phases 1-3).

It is a restaurant-specific digital storefront, not a generic ordering page.
~~~
- Reason:
  - D-12 bounds customization: logo, cover, brand colors, a font from a list, a layout preset, and no custom CSS/HTML. Settings are a typed, backend-owned schema. Each area's spec decides exactly what it allows.
  - The current list is open-ended.
  - Today branding lives in the owner's own `localStorage`, so customers never see it (MASTER-PLAN §2 L24; ADR-0014 Context).
- ADR: ADR-0014.
- Basis: Accepted D-12 (MASTER-PLAN L45).

## A-07: Tablets use the Edge only where one exists (§11)
- Current §11 (L183, last sentence): "Tablets consume the local Edge API and keep working during Internet loss."
- Proposed text, which replaces that sentence:
~~~markdown
Where the restaurant has an Edge, tablets consume the local Edge API and keep taking orders during Internet loss. Without an Edge, tablets consume the cloud API and follow the connection-lost behavior in §4.2. Tablets are Later (ADR-0007). Device registration and pairing are designed in the device model document (Derived documents).
~~~
- Reason:
  - The current sentence assumes every restaurant has an Edge (D-3).
  - Tablets are on the Later list (MASTER-PLAN §6 L95).
  - The device model is one of the deferred Edge docs (A-14).
- ADR: ADR-0003, ADR-0004, ADR-0007.
- Basis: Accepted D-3, D-4, D-7.

## A-08: Edge components: optional, "Offline Order Queue", no payment or fiscal gateway (§12)
- Current §12 (L192): "├── Offline Transaction Queue"; (L200): "Future (not required for the first implementation): Printer Gateway, Fiscal Gateway, Payment Device Gateway, Local DNS / Service Discovery, Hardware Integration."
- Proposed text, which replaces L185-200 (the whole §12 body):
~~~markdown
## 12. Restaurant Edge

Optional. Not built this run; built only when a restaurant with poor signal needs it (ADR-0003). This is the intended shape. The full design comes in the Edge documents, written after the Order Core contract is Reviewed.

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
~~~
- Reason:
  - "Transaction" suggests payment transactions. The queue holds orders only (D-4; MASTER-PLAN §5 L73: rename to "Offline Order Queue").
  - The app never processes or queues payments (D-5), and it integrates with existing payment and fiscal systems instead of building them. This drops "Payment Device Gateway" (MASTER-PLAN §5 L73) and "Fiscal Gateway".
  - The Edge is optional (D-3).
- ADR: ADR-0003, ADR-0004, ADR-0005.
- Basis:
  - Accepted D-3, D-4, D-5.
  - The "read-only; ... cloud-owned" notes restate ADR-0003 C4 (accepted with Q-03 row 3.4, 2026-09-29).
  - See Reviewer note 1 on "Fiscal Gateway".

## A-09: Local data, sync, identity and source of truth apply only with an Edge (§13-§16)
- Current §13 (L204), §14 (L208-215), §15 (L219) and §16 (L223-227) describe the Edge's local projection, its asynchronous sync, locally created ids and a source of truth that switches to the Edge while offline. They assume every restaurant has an Edge.
- Proposed text (a), inserted as the first line of §13 (before L204):
~~~markdown
> §13-§16 apply only where a restaurant has an Edge (§2). This run there is no Edge, no local data and no sync. Their detailed design is deferred to `RESTAURANT-EDGE-ARCHITECTURE.md` and `OFFLINE-SYNC-DESIGN.md`, written after the Order Core contract is Reviewed: per-entity ownership, the sync mechanism, reconciliation and the local projection. What the core must honor now is the constraint list in ADR-0003.
~~~
- Proposed text (b), appended to §14 (after L215):
~~~markdown
The sync mechanism, for example replaying domain events through an outbox, is not chosen here. This run records a status history and uses in-process events only (ADR-0019). An outbox is added when the first external consumer, such as Edge sync, is scheduled.
~~~
- Proposed text (c), appended to §15 (after L219):
~~~markdown
This part applies now, without an Edge. Orders and order lines get client-generated ids, so a retry after a lost connection never creates a second order (ADR-0003, ADR-0004). The id is a ULID, every table uses ULID primary keys, and each order also gets a short per-restaurant human order number generated by the server (ADR-0009).
~~~
- Current §16 (L224): "- **Temporary disconnection:** the Edge is authoritative for that restaurant's supported offline operations."
- Proposed text (d), which replaces L224:
~~~markdown
- **Temporary disconnection, no Edge (every restaurant this run):** the Cloud stays authoritative. An order kept on a device is not a record until the server acknowledges it (§4.2).
- **Temporary disconnection, with an Edge:** the Edge is authoritative for that restaurant's supported offline operations, which are orders only (§17). Menu and configuration stay cloud-owned, and order status only moves forward (ADR-0003 C3-C4). Per-entity ownership beyond that is decided in `OFFLINE-SYNC-DESIGN.md`.
~~~
- Reason:
  - D-3: the Edge is designed only where it constrains the core. MASTER-PLAN §4 L60 reduces "Edge designed now" to one constraints ADR, with the full Edge docs after the Order Core contract is Reviewed.
  - D-4 needs idempotent retry of orders even without an Edge, which is why §15's identity rule applies now.
  - The current backend uses database `IDENTITY` ids (BE `src/main/java/com/enterprise/ordersuite/common/persistence/BaseEntity.java` L20-21) and a client-supplied `orderNumber` (BE `src/main/java/com/enterprise/ordersuite/orders/api/dto/OrderCreateRequest.java` L22), as cited in ADR-0003 and ADR-0009.
- ADR: ADR-0003, ADR-0004, ADR-0009, ADR-0019.
- Basis:
  - (a) and safe retry without duplicates: Accepted D-3, D-4, through ADR-0003 C2 and ADR-0004 decision 3.
  - Client-generated order and line ids as the retry key (ADR-0003 C1), ULID everywhere and the human order number (ADR-0009), status history and in-process events (ADR-0019), and C3/C4: accepted with Q-03 rows 3.4, 3.5 and 3.11 (2026-09-29). Retry mechanism details: Q-38.

## A-10: Offline scope narrowed to orders (§17)
- Current §17 (L231): "**Must work:** read menu; read restaurant configuration; read tables; create order; modify order; cancel order where policy permits; update operational order status; send order to production; receive KDS updates; table ordering; waiter ordering; POS order operations."
- Proposed text, which replaces L231-233. L235 ("Must not be falsely represented as successful ...") stays unchanged.
~~~markdown
Offline means orders: taking an order and getting it to production, waiter → KDS/POS (ADR-0004). Nothing else is promised to work without a connection.

**Every restaurant (this run, no Edge):** order-creating surfaces show a clear connection-lost state, keep in-progress orders on the device, and retry them safely with the same client-generated order id. An order counts as sent only after the server acknowledges it.

**With an Edge (optional, later):** during Internet loss the restaurant keeps working on orders through the Edge:
- create order; modify order; cancel order where policy permits;
- update operational order status; send order to production; receive KDS updates;
- waiter ordering; POS order operations.

Menu, restaurant configuration and tables are readable from the Edge's cache. Customer QR and table-tablet ordering work offline only in this case.

**Never offline:** payments. They are never processed, captured or queued, online or offline; the platform records results only (§26).

**Needs the cloud or additional infrastructure:** menu and configuration changes; external delivery marketplace; external messaging; external notifications; cloud analytics; third-party integrations.
~~~
- Reason:
  - D-4 narrows offline to orders, with a no-Edge fallback of connection-lost state, keep and retry.
  - D-5 removes payments from anything offline: card terminals authorize on their own over 4G/5G. That is why "external payment authorization" leaves the infrastructure list (L233) and becomes "Never offline".
  - The current "Must work" list applies to every restaurant, but only an Edge could deliver it (D-3).
- ADR: ADR-0004, ADR-0005, ADR-0003.
- Basis: Accepted D-3, D-4, D-5 (MASTER-PLAN L36-38). "The same client-generated order id" restates ADR-0003 C1 (accepted with Q-03 row 3.4, 2026-09-29).

## A-11: Future external systems are integrations, not builds (§19, §21)
- Current §19 (L250-251): "Payment (future) · Fiscal (future) · Delivery (future)". Current §21 (L277): "They influence the architecture now but must not expand the current implementation."
- Proposed text (a), appended after the §19 diagram (after L252):
~~~markdown
Payment, Fiscal and Delivery (marketplaces) are integrations with existing external systems. The platform never builds these systems (§26).
~~~
- Proposed text (b), appended to §21 (after L277):
~~~markdown
For payments, fiscal and delivery marketplaces, "not yet" means: when an integration is scheduled, integrate with an existing system; never build one (§26). None of them is integrated this run (ADR-0006, ADR-0007).
~~~
- Reason:
  - D-5: integrate with existing payment, fiscal and marketplace systems; never build them.
  - §21's "should NOT build yet" reads as "build later".
  - D-6 and the Out list (MASTER-PLAN §6 L95) exclude PSP, fiscal, marketplaces and billing this run.
- ADR: ADR-0005, ADR-0006, ADR-0007.
- Basis: Accepted D-5, D-6, D-7.

## A-12: New section "Tenancy and identity" (pointer)
- Current: no section. Multi-tenancy appears only in the header (L5) and §1 (L12), and §20 names a "Shared Multi-Tenant Backend" (L259).
- Proposed text, appended after §24 (after L306) as a new section:
~~~markdown
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
~~~
- Reason:
  - D-1 replaced one deployment per restaurant with one shared SaaS. Tenancy had no document at all (MASTER-PLAN §4 L59).
  - The Tenancy & Identity contract is the first design gate before Build 1 (§5 L74, §7 L105).
  - The code has no tenant concept yet (MASTER-PLAN §2 L26; ADR-0001 Context).
- ADR: ADR-0001.
- Basis: Accepted D-1 (MASTER-PLAN L34). The contract's choices are open (§8b L116).

## A-13: New section "Payments and external systems" (principle)
- Current: no section. Payments appear in §6 (L117), §10 (L179), §12 (L200), §17 (L233-235), §19 (L250) and §21 (L271).
- Proposed text, appended after the text from A-12:
~~~markdown
## 26. Payments and external systems

- The platform never processes, authorizes, captures, refunds or queues payment transactions, online or offline. Card terminals (4G/5G) authorize on their own. The platform records the result against the order, and payment status stays separate from order status (ADR-0005).
- The platform integrates with existing payment, fiscal and marketplace systems. It never builds one (ADR-0005).
- An integration writes through the Order Core like any other consumer (§23). If the external system cannot be reached, the result is pending, never a faked success (§17).
- This run integrates no external payment, fiscal, marketplace or messaging system. The PSP gateway and automatic WhatsApp messages are out; manual `wa.me` links stay (ADR-0006).
~~~
- Reason:
  - D-5 and D-6 are cross-cutting and are stated nowhere in the doc.
  - The pieces are scattered across six sections, and some contradict D-5: "Payment Device Gateway" (L200) and "→ Payment" (L179).
- ADR: ADR-0005, ADR-0006.
- Basis: Accepted D-5, D-6 (MASTER-PLAN L38-39).

## A-14: Derived documents list
- Current (L308-318): "Derived documents (proposed)", six files under `docs/architecture/`, three of them about the Edge (`RESTAURANT-EDGE-ARCHITECTURE.md`, `OFFLINE-SYNC-DESIGN.md`, `RESTAURANT-DEVICE-MODEL.md`).
- Proposed text, which replaces L308-318:
~~~markdown
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
~~~
- Reason:
  - MASTER-PLAN §4 L59 reduces the derived docs to: architecture root, Tenancy & Identity, API conventions, Order Core, Menu & Storefront, and one Edge-ready constraints ADR. §4 L60 defers the full Edge docs until Order Core is reviewed.
  - The build-step gating follows §6 L93-94 and §7 L105.
  - The folder layout follows ADR-0011.
- ADR: ADR-0003, ADR-0011, ADR-0016.
- Basis: a MASTER-PLAN §4 simplification accepted with Q-03 row 3.3, and the location from Q-01 (both 2026-09-29). The new Tenancy and API-conventions docs are MASTER-PLAN §5 additions (L74-75).

## A-15: Original implementation order marked superseded
- Current (L320): "## Proposed implementation order (as written by Gabriel)". (L339): "The critical decision is placing the Edge and the sync model before the operational consumers. ..." (L341): "See `planning/pm-tool-recommendation.md`, section "Architecture review", for Claude's review of this order."
- Proposed text (a), which replaces the heading L320 and adds a banner under it. The list L322-337 stays unchanged, for history.
~~~markdown
## Original implementation order (superseded by ADR-0007)

> Superseded on 2026-09-29 by ADR-0007 (D-7). The current order, after the readiness gate, is Tenant foundation → Menu → Order Core → Storefront ↔ Order Core.
> - Later: QR/table, waiter, KDS on Edge, POS, tablets, Edge build.
> - Out this run: PSP, automatic WhatsApp, fiscal, marketplaces, billing.
>
> The list below is kept as Gabriel wrote it, for history. Do not plan from it.
~~~
- Proposed text (b), which replaces L339:
~~~markdown
The original critical decision, placing the Edge and the sync model before the operational consumers, is superseded. The Edge is optional (§2, ADR-0003), and storefront delivery never needs it (ADR-0007). The goal behind it stands: when waiter, KDS and POS arrive, `Order`, `Menu`, `Restaurant`, IDs, events, authentication, tenancy and persistence must not need redesign. ADR-0003 (Edge-ready constraints), ADR-0009 (IDs) and ADR-0019 (events) carry that goal.
~~~
- Proposed text (c), which replaces L341:
~~~markdown
Current order of work: `planning/MASTER-PLAN.md` §6 and ADR-0007. Claude's original review of this list: `planning/pm-tool-recommendation.md`, section "Architecture review".
~~~
- Reason:
  - D-7 replaced this order: Gabriel accepted Claude's review gap #5 (PF `planning/pm-tool-recommendation.md` L117).
  - The section still presents the Edge-first order as "the critical decision" (L339). An agent reading it would plan the Edge before the storefront.
  - MASTER-PLAN §5 L73 asks for the original build order to be "marked superseded".
- ADR: ADR-0007.
- Basis: Accepted D-7 (MASTER-PLAN L40).

---

## Sections left unchanged
Reviewed against D-1..D-16. No amendment is proposed for these:
- §1 Vision (L10-38): already names future payment, fiscal and marketplace work as integrations (L14). Its domain list (L30-38) names Customer, Product and Table; whether each is a separate entity this run is open, not decided (Q-51, Q-43, Q-53). A-05 flags the same for §7. No text change proposed.
- §4.3 (L83): already treats connectivity backup as infrastructure, which matches D-4.
- §5 (L85-110): matches Build 1-4.
- §8 (L151-165): matches D-2. Modifier depth is a Menu-contract question (MASTER-PLAN §8c L118).
- §18 (L237-239): delivery is a channel in the same Order Core. Marketplace and WhatsApp integrations are future, which matches D-5 and D-6.
- §22-§23 (L279-298): match D-2 and D-7.

## Open questions
- Q-03 (answered 2026-09-29, all twelve rows): the MASTER-PLAN §4 simplifications that A-08, A-09 and A-14 rely on are Accepted: ADR-0003 C1/C3/C4 (row 3.4), ADR-0019 (row 3.5), ADR-0009 (row 3.11) and the smaller derived-docs set (row 3.3). The amendments themselves still wait for Q-04.
- Q-01 (answered 2026-09-29): the docs live in the backend repo `docs/`; A-14's paths and the amended doc's location follow ADR-0011.
- Channel/source mapping, including staff phone orders; the order status set and casing; Customer as a record or a snapshot; a minimal Table in Order Core. Order Core batch, MASTER-PLAN §8c L118. Register: Q-49, Q-47 (with Q-34), Q-51, Q-53.
- Connection lost at the restaurant while customers stay online: a customer's phone on mobile data can still reach the cloud when the restaurant's Internet is down. Its storefront order is then acknowledged, but the restaurant's KDS cannot see it. What do staff screens and the KDS show in that state? Register: Q-60 (ADR-0004 open questions).
- How online storefront customers pay this run, and what a payment record holds. MASTER-PLAN §8c L118; ADR-0005. Register: Q-65, Q-58.
- Exact owner customization per area, and the logo/cover upload. MASTER-PLAN §8c L118; ADR-0014. Register: Q-66, Q-44.
- The Tenancy & Identity batch for §25. MASTER-PLAN §8b L116. Register: Q-16..Q-31.
- Edge build criteria. MASTER-PLAN §8d L120. Register: Q-80.

## Reviewer notes
- Reviewer note 1: MASTER-PLAN §5 L73 only asks to drop "Payment Device Gateway". A-08 also drops "Fiscal Gateway", per this step's assignment and D-5 ("integrate ... never build").
  - Concern: §21 (L273) lists "SAT where applicable". Some fiscal integrations need an adapter at the restaurant, next to an existing fiscal device.
  - Such an adapter would be an integration, not a build, so D-5 would allow it.
  - If Gabriel wants to keep that option visible, change A-08's last line to name a future "fiscal device integration (adapter to an existing system)" instead of dropping the item entirely. Register: Q-04.
- Reviewer note 2: A-01 sets the architecture doc's status to "Accepted", because MASTER-PLAN says so (§5 L73 "status → Accepted"; §7 L99 "the amended architecture ... Accepted by Gabriel").
  - Conflict: ADR-0016 lists architecture docs under the Draft / Reviewed / Ready lifecycle and says "There is no Final, Done or Approved status". It reserves Proposed/Accepted for ADRs.
  - Suggestion: MASTER-PLAN or ADR-0016 should state that the architecture root carries a decision status ("Accepted with amendments"), because it records decisions, in addition to or instead of a doc status.
  - This file follows MASTER-PLAN. Register: Q-12 (decided by Claude 2026-09-29, Gabriel may override: a, the root gets a decision status next to its doc status once Q-04 is answered).
- Reviewer note 3: MASTER-PLAN §7 L99 gates on "the amended architecture ... Accepted" but does not say how partial answers count.
  - Proposed reading: the gate item is met when every amendment in the checklist is ticked (accept, change or reject). For a rejected amendment that restates an Accepted decision, the decision question must also be settled (see "How to use this file").
  - Unticked amendments keep the gate open. Register: Q-13 (decided by Claude 2026-09-29, Gabriel may override: a, this reading).

## Checklist for Gabriel
Tick one box per line. For "change", write the change after the arrow.

- A-01 Status → Accepted with amendments, plus amendment log: [X] accept [ ] change → ____ [ ] reject
- A-02 Restaurant Edge optional (header, §2, §20, §24): [X] accept [ ] change → ____ [ ] reject
- A-03 Offline means orders (guiding principle, §3, §4.1, §4.2): [X] accept [ ] change → ____ [ ] reject
- A-04 Payment steps become payment records (§6, §10): [X] accept [ ] change → ____ [ ] reject
- A-05 Channel/source vocabulary flagged open (§7): [X] accept [ ] change → ____ [ ] reject
- A-06 Bounded owner customization (§9): [X] accept [ ] change → ____ [ ] reject
- A-07 Tablets use the Edge only where one exists (§11): [X] accept [ ] change → ____ [ ] reject
- A-08 Edge optional, "Offline Order Queue", no payment/fiscal gateway (§12): [X] accept [ ] change → ____ [ ] reject
  - Includes the read-only caches (ADR-0003 C4, accepted with Q-03 on 2026-09-29; no separate tick)
  - Includes dropping "Fiscal Gateway" (Reviewer note 1): [X] drop [ ] keep as "fiscal device integration"
- A-09 §13-§16 apply only with an Edge; design deferred: [X] accept [ ] change → ____ [ ] reject
  - Includes ULID everywhere and the human order number (ADR-0009, accepted with Q-03 on 2026-09-29; no separate tick)
  - Includes status history and in-process events, no outbox (ADR-0019, accepted with Q-03 on 2026-09-29; no separate tick)
- A-10 Offline scope narrowed to orders (§17): [X] accept [ ] change → ____ [ ] reject
- A-11 Future payment/fiscal/marketplace work: integrate, never build (§19, §21): [X] accept [ ] change → ____ [ ] reject
- A-12 New §25 "Tenancy and identity" (pointer): [X] accept [ ] change → ____ [ ] reject
- A-13 New §26 "Payments and external systems" (principle): [X] accept [ ] change → ____ [ ] reject
- A-14 Shorter derived-documents list; Edge docs deferred: [X] accept [ ] change → ____ [ ] reject
- A-15 Original implementation order marked superseded (ADR-0007): [X] accept [ ] change → ____ [ ] reject

## Sources
Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`); FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md`: header (L3-8); §2 (L40-71); §3 (L73-75); §4 (L77-83); §6 (L112-123); §7 (L125-149); §9 (L167-171); §10 (L173-179); §11 (L181-183); §12 (L185-200); §13-§17 (L202-235); §19 (L241-254); §20 (L256-267); §21 (L269-277); §24 (L300-306); Derived documents (L308-318); Proposed implementation order (L320-341)
- PF `planning/MASTER-PLAN.md`: L3-6; §2 (L24, L26); §3 D-1..D-16 (L34-49); §4 (L59-60, L67); §5 (L73-75, L78); §6 (L87-95); §7 (L99, L105); §8 (L111-120)
- PF `planning/pm-tool-recommendation.md` L109-131 (architecture review, revised order)
- PF `2026-09-16-business-rules-master-en.md` rules 1-2 (L16-17)
- PF `adr/0001`-`0019` drafts (S0), in particular ADR-0003 (constraints C1-C6), ADR-0004, ADR-0005, ADR-0014, ADR-0016
- PF `planning/s0-evidence/inventory-2026-09-29.txt` (inventory: phase 10 comandas never built; channel/source gap) and PF `planning/s0-evidence/critique-2026-09-29.txt` (amendment scope)
- BE `src/main/java/com/enterprise/ordersuite/common/persistence/BaseEntity.java` L20-21; `src/main/java/com/enterprise/ordersuite/orders/api/dto/OrderCreateRequest.java` L22; grep of `src/main/java/com/enterprise/ordersuite/orders/` and `src/main/resources/db/migration/` for `channel`/`source` (no match, 2026-09-29)
- FE `src/types/orders.ts` L1-2; `src/features/orders/components/CreateOrderModal.tsx` L23; `src/features/kds/constants/kds.constants.ts` L3; `src/app/router.tsx` L66; `src/api/client.ts` L71, L96 (via ADR-0004)
