# Glossary

- Status: Draft (seed, S2)
- Updated: 2026-10-06 (PIX terms: ADR-0020 Accepted, Q-86 a; Gabriel, project thread, 2026-10-06T18:12Z). Earlier: 2026-10-01 (terms decided by Gabriel's register answers of 2026-10-01, project thread 2026-10-01T16:25Z). Earlier: 2026-09-29
- Maintained by: Claude. A contested term is settled only by Gabriel's register answer or the contract or ADR named in its row, never here first.
- Related: ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0005, ADR-0009, ADR-0012, ADR-0014, ADR-0020; `planning/open-questions.md`

How to read the Status column:
- **Accepted (ADR-NNNN)**: decided; use the term as defined.
- **Decided (Q-xx a, YYYY-MM-DD)**: Gabriel answered that register question ("Decided by Claude" when he delegated it); use the term as defined. The contract named in the row records the detail (field names, wire shape).
- **Proposed (ADR-NNNN)**: Claude's proposal waiting for Gabriel; no code depends on it until he confirms the ADR.
- **Open -> decided in <contract> (Q-xx)**: do not pick a value or a name in code, API or docs before that contract settles it. Recommendations quoted here are Claude's proposals, not decisions.
- "Code/API" names are English. PT-BR is UI copy only (section 3).

## 1. Domain terms

| Term | Meaning | Status |
|---|---|---|
| Restaurant / tenant | The restaurant is the tenant. One deployment and one database serve every restaurant. Restaurant-owned data carries the restaurant id and every read and write is scoped to the caller's restaurant; a missing restaurant context fails closed. One tenant = one restaurant location. | Accepted (ADR-0001). Isolation mechanism: Decided (Q-25 a, 2026-10-01): application-level scoping, `restaurant_id` on every restaurant-owned table, one cross-tenant test per endpoint and an ArchUnit rule. One tenant = one location: Decided (Q-22 a, 2026-10-01). Where a request's restaurant comes from: see Membership and Slug (Q-26 a, decided by Claude 2026-10-01). The Tenancy & Identity contract records the detail. |
| Platform admin vs restaurant roles | Platform admin: the deployment-wide operator (today's env-seeded root `SUPER_ADMIN`), separate from every restaurant; full read access for support (Q-18 b). Restaurant roles, per restaurant: owner, manager, staff, held through a Membership. Today's code still has global roles `SUPER_ADMIN` > `ADMIN` > `USER`; they are replaced, not mapped. Never assume the legacy mapping `SUPER_ADMIN` = owner (ADR-0001). | Decided (Q-17 a, Q-18 b, 2026-10-01). Kitchen and waiter roles: not this run (Q-17 a; the KDS signs in with a staff login, Q-54 a). Role names in code and on the wire: Tenancy & Identity contract (casing Q-34 a). |
| Membership | The link between a user and the restaurant they work for; it carries the user's restaurant role. Stored in a membership table. A user belongs to one restaurant for now; the table lets many come later. Staff requests take their restaurant from the signed-in user's membership on the server, never from a restaurant id in the path or body. | Decided (Q-16 a, 2026-10-01). Restaurant of a staff request: Decided by Claude (Q-26 a, 2026-10-01, delegated by Gabriel). Signup is invite-only: the platform admin creates the restaurant and its owner, and the owner invites staff (Q-19 a, 2026-10-01). Table and column names: Tenancy & Identity contract. |
| Slug, `/r/{slug}` | A restaurant's short unique name in its public URL: the storefront lives at the path `/r/{slug}` on one domain, and public routes move under `/r/{slug}/...` (Q-71 a). Public (storefront) requests take their restaurant from the slug in the path. The platform admin sets the slug at creation from the restaurant name; it is unique and does not change this run. | Decided (Q-20 a, Q-31 a, 2026-10-01; restaurant of a public request: Q-26 a, decided by Claude 2026-10-01). Subdomain or custom domain: possible later (Q-20). |
| Order Core (Restaurant Operational Core) | The one Order model (with channel and source) and the one Menu per restaurant that every interface consumes through application services. Adapters (controllers, `/public/*`, a future Edge or marketplace) authenticate, map input and set their own identifying fields; they hold no business rules. The frontend is the first consumer. | Accepted (ADR-0002). Class and service names: fixed by the Order Core contract. |
| Channel | How an order is served: `DINE_IN`, `TAKEAWAY` or `DELIVERY`. Today's fulfillment folds into it; there is no separate `fulfillment` field. | Concept Accepted (ADR-0002). Value set and legacy mapping: Decided (Q-49 a, 2026-10-01). Wire casing SCREAMING_SNAKE: Decided (Q-34 a, 2026-10-01). The Order Core contract records it. |
| Source | The interface that created an order: `STOREFRONT`, `PHONE`, `WAITER`, `POS`, `QR` or `TABLET`. `PHONE` is new (staff-taken phone orders); architecture §7's `FUTURE_MARKETPLACE` is not in the set (marketplaces are Out this run, ADR-0007). Set by the server-side adapter, never from client input (ADR-0002). | Same as Channel: Decided (Q-49 a, Q-34 a, 2026-10-01); concept Accepted (ADR-0002). |
| Menu item vs Product (legacy) | Menu item (`MenuItem`): an item on the restaurant's menu, the term in code and API; one menu per restaurant this run, with no separate catalog Product. Product: only the legacy backend B2B entity behind `/products` (with stock), replaced, not evolved (ADR-0008); the architecture also lists "Product" and "Items". | Decided (Q-43 a, 2026-10-01). The Menu contract records it. |
| Option group (modifier / option) | Choices a customer makes on an item, modeled as option groups: each group has required, min and max, and each option a price. Today's sizes (pick one) and add-ons (with a max quantity) both become groups. Architecture §8 lists "Modifiers" and "Options" under Menu. | Decided (Q-41 b, 2026-10-01). Per-option availability and nested groups are not in this run's shape (Q-41 c not chosen). Field names: Menu contract. |
| Table | A restaurant table: a name and a QR code, kept as a settings list with no floor or occupancy view (ADR-0000 FS-09, Kept). | Tables as a settings list: Kept per ADR-0000 FS-09 (ADR-0000 Accepted 2026-10-01, Q-05). A minimal Table (id, name) in Order Core this run, so staff dine-in orders reference a real table: Decided (Q-53 a, 2026-10-01). QR code generated in the frontend from the table's public URL: Decided (Q-63 a, 2026-10-01). QR/table self-ordering: Later (ADR-0007). |
| Customer | The person who places an order. A snapshot on each order (name, phone, address); no account and no Customer table this run. Architecture §1 lists a Customer entity. | Decided (Q-51 a, 2026-10-01). Retention (LGPD): Decided (Q-52 a, 2026-10-01): anonymized after a fixed period that the NFR page proposes. The Order Core contract records the fields. |
| Human order number | Short number shown to staff and customers for display and tracking: a running number per restaurant (#1, #2, ...). Server-generated, unique per restaurant, never client-supplied, never a key. | Accepted (ADR-0009). Format: Decided (Q-50 a, 2026-10-01). Tracking lookup: Decided (Q-69 a, 2026-10-01): the confirmation link carries the order ULID; a manual lookup needs order number + phone; rate-limited; no list endpoint. |
| ULID | The primary key of every table; generated by the application (orders and order lines may get it from the client). | Accepted (ADR-0009, ADR-0003 C1). Column type and wire format: Decided (Q-35 a, 2026-10-01): `char(26)` Crockford Base32 in the database, logs and API alike. |
| Edge (Restaurant Edge) | A local operational node at one restaurant that keeps orders working when the Internet is down (architecture §2, §12). Optional; not built this run; built when Gabriel decides, as an optional paid feature (Q-80 b), for restaurants with poor signal (D-3). Constraints C1-C6 bind the core now. | Accepted (ADR-0003). When it is built: Decided (Q-80 b, 2026-10-01): when Gabriel decides; an optional paid feature. |
| Offline Order Queue | The Edge component that holds orders (never payments) until they reach the cloud. Renamed from the architecture's "Offline Transaction Queue". Not built this run. Without an Edge, the app keeps unsent orders on the device and retries them with the same client-generated order id (ADR-0004). | Accepted (ADR-0004 decision 5). Architecture text: amendment A-08, applied 2026-10-01 (Q-04). Retry mechanism: Decided (Q-38 a, 2026-10-01). Connection-lost behavior: Decided (Q-60 a, 2026-10-01). |
| `businessDate` | The restaurant-local calendar date of an order, stamped by the server once at creation in the restaurant's IANA timezone and never recomputed. `createdAt` stays a UTC instant (legacy D15, kept). Not built. | Kept (ADR-0000 D10, Accepted 2026-10-01; basis Q-28 a and Q-21 a, 2026-10-01): the restaurant's IANA zone is required when the restaurant is created (default `America/Sao_Paulo`), with no deployment-wide fallback. Not built; the Order Core contract records it. |
| 86 / availability | "86" (kitchen slang): the item is unavailable. The 86 toggle (`available = false`) is the only availability control: no stock counts, no low-stock alerts, no auto-86. Customer views never show an unavailable item; public endpoints filter `available == true` on the server (`planning/superseded-docs.md` §4 S-1). | Decided (Q-42 c, 2026-10-01); the Home low-stock widget goes away. 86 toggle: built (frontend, mock data). The Menu contract records it. |
| Record-only payment | The app records a payment result (method, amount, who, when) against an order. It never processes, authorizes, captures, refunds or queues a payment; card terminals authorize on their own. Payment status is separate from order status. An order becomes PAID only when staff record the payment; manual entry is the only way this run. | Accepted (ADR-0005). When an order is Paid: Decided (Q-55 a, 2026-10-01). Cancel of a paid order: Decided (Q-56 a, 2026-10-01): allowed; staff record the refund made outside the app and the payment status becomes REFUNDED. How online customers pay: Q-65 (answered 2026-10-01): on delivery or pickup, plus PIX prepayment (ADR-0020, Accepted 2026-10-06). |
| Payment (record) | One recorded payment against an order: id, orderId, method (`PIX`, `CARD` or `CASH`), amount, recordedBy, recordedAt, and optionally the provider and authorization code/NSU. An order can have several (split payments, a refund record). | Decided (Q-58 a, 2026-10-01). Field names and wire shape: Order Core contract. |
| Payment status (`paymentStatus`) | The order's payment state, separate from its order status: `UNPAID`, `PAID` or `REFUNDED`. Replaces `Paid`, `Pending` and `PayLater` (`PayLater` was a UI hint, not a state). | Decided (Q-58 a, Q-55 a, 2026-10-01). |
| Order status | `NEW`, `PREPARING`, `READY`, `COMPLETED`, `CANCELLED`. A new order always starts at `NEW`; `COMPLETED` and `CANCELLED` orders are read-only (Q-48 a); lines are editable while the order is `NEW` or `PREPARING` (Q-59 a). Cancel is a status change (D-16). The "Ready" label varies by channel in the UI only. | Decided (Q-47 a, 2026-10-01); casing Q-34 a. The Order Core contract records it. |
| PIX prepayment | A storefront payment choice: the customer pays by PIX at checkout. The order is created `NEW` and `UNPAID`, flagged "PIX awaiting confirmation"; staff see it at once as "PIX to confirm", but it reaches the kitchen only after staff record the payment or the owner confirms it, and it is cancelled automatically after 5 minutes without confirmation. The customer may tap "Já paguei" ("I have paid"); that tap is customer-reported, never proof, and sets no payment state. The order becomes `PAID` only when staff check their own bank app and record the payment (method PIX). No PSP, no bank API. | ADR-0020 (Accepted 2026-10-06). Pickup only with PIX prepayment inside a delivery zone: Q-86 a. Auto-cancel after 5 minutes and the kitchen rule: Gabriel, 2026-10-06 (ADR-0020 point 4). |
| Static PIX code (PIX BR Code) | The EMV "copia e cola" payload and its QR code that the backend generates from the restaurant's PIX key (a typed restaurant setting), with the exact server-derived order total as the amount and the order number as the reference (txid), closed by a CRC16 checksum. Any Brazilian bank app pays it; the money never passes through the app. Not a dynamic PIX charge. | ADR-0020 (Accepted 2026-10-06). Accepted PIX key types, merchant name and city: ADR-0020 point 7. |
| Storefront | The restaurant's public customer site at `/r/{slug}`: menu, cart, checkout, order status. Owner customization is bounded (ADR-0014). It stays in the same frontend app (Q-71 a). | Customization bounds: Accepted (ADR-0014); what an owner can customize: Decided (Q-66 a, 2026-10-01). How it finds its restaurant: Decided (Q-20 a, Q-31 a, 2026-10-01; see Slug). |
| KDS | Kitchen Display System: the kitchen's live view of the one order stream (ADR-0002). Today a public route. | View over Order Core: Accepted (ADR-0002). Sign-in: Decided (Q-54 a, 2026-10-01): staff login with a restaurant role. Refresh: Decided (Q-61 a, 2026-10-01): polling. |
| Plan / package | Reserved words. Not used in code or API this run. | Deferred (ADR-0018). Which word means what: decided with the first paid tier (Q-78 a, 2026-10-01). |
| Draft / Reviewed / Ready | Doc statuses. Only Gabriel sets Reviewed, with a message in the project thread ("Reviewed: <doc>") that Claude records as "Reviewed by Gabriel on YYYY-MM-DD". | Accepted (ADR-0016). Draft and Ready definitions: Q-12 (decided by Claude: a). Reviewed signal: Decided (Q-09 a, 2026-10-01). |

## 2. Legacy -> new term map

Legacy sources: frontend `order-ui/src/types/orders.ts` L1-5 (`OrderChannel`, `Fulfillment`, `OrderStatus`, `PaymentStatus`); FE `order-ui/docs/superpowers/specs/2026-09-09-restaurant-ops-redesign-design.md` L52-57; legacy backend `/orders`, `/products`. The target column started as MP §5's proposed map (MP §5 "Glossary" bullet) and Q-49 option a; Gabriel's answers of 2026-10-01 decided the rows marked Decided.

| Legacy term | New term | Status |
|---|---|---|
| Channel `Online` | source `STOREFRONT` | Decided (Q-49 a, 2026-10-01) |
| Fulfillment `Pickup` | channel `TAKEAWAY` | Decided (Q-49 a, 2026-10-01). PT-BR label: section 3, Contested |
| Channel `Dine-in` | channel `DINE_IN` | Decided (Q-49 a, 2026-10-01) |
| Fulfillment `Delivery` | channel `DELIVERY` | Decided (Q-49 a, 2026-10-01) |
| Channel `Phone` (staff-taken phone orders) | source `PHONE` (new; architecture §7 had no such source) | Decided (Q-49 a, 2026-10-01) |
| `fulfillment` as its own field | folded into `channel` | Decided (Q-49 a, 2026-10-01) |
| Enum casing `Dine-in`, `New` (frontend, manifest) | `DINE_IN`, `NEW` (SCREAMING_SNAKE) | Decided (Q-34 a, 2026-10-01) |
| Sizes and add-ons | option groups (required, min, max, a price per option) | Decided (Q-41 b, 2026-10-01) |
| `Product` (legacy backend) | `MenuItem` | Decided (Q-43 a, 2026-10-01) |
| `stockQuantity`, low-stock alert, Inventory | dropped; the 86 toggle is the only availability control | Decided (Q-42 c, 2026-10-01); the Home low-stock widget goes away |
| Roles `SUPER_ADMIN` / `ADMIN` / `USER` (legacy mapping owner / manager / staff, FS-01, superseded) | platform admin + per-restaurant owner, manager, staff, held through a membership | Decided (Q-17 a, Q-18 b, Q-16 a, 2026-10-01) |
| Order status `New`, `Preparing`, `Ready`, `Completed`, `Cancelled` (frontend); `PENDING`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED` (legacy backend) | `NEW`, `PREPARING`, `READY`, `COMPLETED`, `CANCELLED` | Decided (Q-47 a, Q-34 a, 2026-10-01) |
| Payment status `Paid`, `Pending`, `PayLater` | `UNPAID`, `PAID`, `REFUNDED` | Decided (Q-58 a, Q-55 a, 2026-10-01) |
| Mock PIX "pay now" that confirms itself (FE `PixWaitingMock`, business rule 39) | PIX prepayment with a static PIX code, confirmed only when staff record the payment | ADR-0020 (Accepted 2026-10-06) |
| Anonymous customer (name + phone per order) | a snapshot on each order (name, phone, address); no Customer record | Decided (Q-51 a, 2026-10-01) |
| Integer ids (`Long`, `IDENTITY`, legacy D9) | ULID string ids | Accepted (ADR-0009) |
| Client-supplied `orderNumber` (`DEL-2026-8123` style) | server-generated running number per restaurant (#1, #2, ...) | Accepted (ADR-0009); format Decided (Q-50 a, 2026-10-01) |
| "Offline Transaction Queue" (architecture §12) | "Offline Order Queue" | Accepted (ADR-0004); architecture text amended (A-08, applied 2026-10-01, Q-04) |
| "Payment Device Gateway" (architecture §12) | removed; no payment queue | Accepted (ADR-0005, ADR-0003 C5); architecture text amended (A-08, applied 2026-10-01, Q-04) |
| Restaurant settings in `PreferencesState` / `localStorage` | typed backend-owned restaurant settings | Accepted (ADR-0014). Which settings are restaurant vs user: Decided (Q-27 a, 2026-10-01) |

## 3. PT-BR UI vocabulary

Rules (FE `order-ui/docs/superpowers/specs/2026-09-15-core-package-br-i18n-ux-design.md`):
- English keys are canonical and the fallback (L80).
- Translation is presentation only: API values, enums, permission ids, route paths and domain types stay language-neutral; a label maps from a stable enum value at render time (L85).
- PT-BR terms are product vocabulary, not literal translation (L92-96). UI copy is product content, not docs (ADR-0012).

Validated terms. Source: same spec L92-96, "deliberate choices already validated with the user, not open to per-feature drift". The English column is a gloss for agents; the spec gives only the PT-BR terms and their use.

| PT-BR | Concept (EN gloss) | Use per the spec |
|---|---|---|
| Cardápio | menu | |
| Pedido | order | delivery and takeout orders |
| Comanda | dine-in order / tab | dine-in specifically |
| Mesa | table | |
| Garçom | waiter | |
| Cozinha | kitchen | |
| Conta | bill | |
| Gorjeta | tip | |
| Para viagem | takeout | |
| Caixa | cashier / till | |
| Painel | dashboard / panel | |
| Relatório | report | |
| Estoque | stock / inventory | |
| Fornecedor | supplier | |

Current UI labels (FE `order-ui/src/i18n/locales/pt-BR/`; not in the validated list; they follow the contract decisions):

| PT-BR label | Current meaning | File | Status |
|---|---|---|---|
| Novo, Preparando, Pronto, Concluído, Cancelado | order statuses | `orders.json` L3-7 | Follows Q-47 (decided a, 2026-10-01: the same five statuses) |
| Presencial, Telefone | channel `Dine-in`, `Phone` | `orders.json` L77-78 | Follows Q-49 (decided a, 2026-10-01: channel `DINE_IN`, source `PHONE`) |
| Retirada, Entrega | fulfillment `Pickup`, `Delivery` | `orders.json` L83-84 | Follows Q-49 (decided a, 2026-10-01: channel `TAKEAWAY`, `DELIVERY`); label split: Contested below |
| Esgotado | 86'd item | `menu.json` L20 | Follows Q-42 (decided c, 2026-10-01: the 86 toggle stays) |
| Tamanhos, adicional / adicionais | sizes, add-ons | `menu.json` L22-23, L46 | Follows Q-41 (decided b, 2026-10-01: both become option groups); group labels: Menu contract |

Contested:
- Pickup / takeout label: the validated list has "Para viagem" (takeout); the UI shows "Retirada" for `Pickup`. Pickup becomes channel `TAKEAWAY` (Q-49 a, 2026-10-01), but Q-49 did not decide the label: which label applies where is Open -> decided with the channel labels in the Order Core contract; ask Gabriel.
- "Comanda" is validated for dine-in, but the comandas feature (Phase 10) stays Later (Q-81 a, 2026-10-01).

## Open questions

- Every "Open" row above is decided in the contract named there; this file is updated in the same commit as that decision (register rule).
- The PT-BR label split between "Para viagem" and "Retirada" (section 3, Contested) was not decided by Q-49 a; it is asked with the channel labels in the Order Core contract. Blocks the Order Core contract, not S2.
- Q-43: answered 2026-10-01, a: "Product" is retired as a term for new code; it names only the legacy backend entity.
- PIX prepayment terms come from ADR-0020 (Accepted 2026-10-06). Q-86 a decides when pickup is offered.
