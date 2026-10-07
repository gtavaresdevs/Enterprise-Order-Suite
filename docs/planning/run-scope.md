# Product: scope of this run (short PRD)

- Status: Draft
- Updated: 2026-10-07
- Reviewed: not yet
- Roadmap step: S5 (`docs/roadmap.md`)
- Related: ADR-0001, ADR-0002, ADR-0003, ADR-0005, ADR-0006, ADR-0007, ADR-0008, ADR-0014, ADR-0018, ADR-0020; register Q-17..Q-31, Q-41..Q-71, Q-78..Q-82, Q-86..Q-89 (all answered); `planning/audit-triage.md`, `architecture/NFR.md`, `planning/proposals/settings-and-notifications.md` (Reviewed 2026-10-06), `architecture/TENANCY-AND-IDENTITY.md` (Reviewed 2026-10-07)

## Goal
At the end of this run, a restaurant runs its day on the app against the real backend, with no mocks on the core screens: the platform admin opens a restaurant, the owner sets up the menu and storefront, customers order from `/r/{slug}`, staff take orders, the kitchen works the KDS, and two restaurants never see each other's data. Nothing new is invented: this run improves the features that already exist (ADR-0006) and moves them onto one shared multi-tenant core (ADR-0001, ADR-0002).

## Users and surfaces
- Platform admin (today's root `SUPER_ADMIN`): creates restaurants and invites owners; full read access for support (Q-17 a, Q-18 b).
- Owner, manager, staff: one restaurant each (Q-16 a); back office, Orders, KDS (staff login, Q-54 a).
- Customer: anonymous, no account (Q-51 a); storefront and order tracking under `/r/{slug}` in the same SPA (Q-20 a, Q-71 a).
- Market: Brazil only, BRL, pt-BR default with EN kept, one location per tenant (Q-21 a, Q-22 a).

## What exists today
- Frontend `order-ui` (`Claude-Assisted-Development`): phases 0-9 done; real backend for auth, profile and administration; everything else is mock, and owner branding, WhatsApp number and delivery zones live in the owner's browser (MASTER-PLAN §2).
- Backend (`feature/ai-agent`): phases 0-1 done (UTC instants, auth rework); domain still the legacy B2B `/orders` and `/products` with `Long` ids and no restaurant. It is replaced, not evolved (ADR-0008).

## Feature placement
Every existing feature has one home. "Build N" is the roadmap step; each build starts when its contract is Reviewed (ADR-0007).

| Feature (frontend `features/`) | Today | This run | What changes | Source |
|---|---|---|---|---|
| Auth (`auth`, login, password setup and reset) | Real | Build 1 | Invite-only, `/register` removed; restaurant membership and new roles; token revocation on role or membership change; phase 6 cleanup | Q-19 a, Q-17 a, Q-29 a; T&I |
| Restaurants (platform admin) | Absent | Build 1 | Create restaurant (name, slug, timezone) and invite its owner | Q-19 a, Q-28 a, Q-31 a; proposal B1 |
| Team, roles, audit log (`administration`) | Real, deployment-wide | Build 1 | Scoped to the restaurant; Roles folds into Team | Proposal B2-B6 |
| Profile (`profile`) | Real except avatar | Build 1 | Avatar made real; B2B fields removed; change password; sign out of other devices | Proposal C1-C5; T&I D-18 |
| Settings (`settings`) | Mock | Build 1 shell, Build 4 sections | Mock security, API keys, sessions and danger zone removed; restaurant settings page (general in Build 1; ordering, zones, appearance, PIX in Build 4) | Q-24, Q-30 a; proposal A1-A5, F1-F6 |
| Preferences (`preferences`) | `localStorage` | Build 1 and Build 4 | Stays per device for display only; timezone and currency become restaurant data; storefront and zone sections move to settings | Q-27 a; proposal D1-D4 |
| Menu (`menu`) | Mock | Build 2 | Categories with ids, items with option groups, 86 toggle only (no stock), archive instead of delete, price > 0, photos | Q-41 b, Q-42 c, Q-43 a, Q-44 a, Q-45 a, Q-46 a |
| Tables (`tables`) | Mock | Build 3 | Minimal Table (id, name) so staff dine-in orders reference a real table; QR generated in the frontend | Q-53 a, Q-63 a |
| Orders (`orders`) | Mock | Build 3 | One Order with channel and source, server-priced lines, starts at NEW, forward-only status, read-only when terminal, payment records, human order number, idempotent create | Q-47..Q-50, Q-55..Q-59, Q-38 a |
| KDS (`kds`) | Mock, public route | Build 3 | Staff login; polling kept; kitchen gate for unpaid PIX and pay-at-home orders | Q-54 a, Q-61 a; ADR-0020 point 4; Q-87 a |
| Notifications (`notifications`) | B2B mock | Build 3 | Small per-restaurant feed from in-process order events; new-order alert | Q-57 a; proposal E1-E5 |
| Home and Analytics (`home`, `analytics`) | Mock, client-side | Build 3 | One server aggregate per dashboard on `businessDate` | Q-62 a |
| Storefront (`storefront`) | Mock | Build 4 | `/r/{slug}`; option choices kept per line; structured address; PIX prepayment with static code; pickup only with PIX inside a zone; "Continuar no WhatsApp"; minimum order and opening hours | Q-20 a, Q-41 b, Q-67 a, Q-68 a, Q-86 a; ADR-0020 |
| Order tracking (`track-order`) | Mock, loads all orders | Build 4 | Link with the order ULID; manual lookup by number + phone, rate-limited | Q-69 a |
| Restaurant settings: ordering, zones, appearance, PIX key | `localStorage` or hardcoded | Build 4 | Typed backend settings on the Build 1 schema | Q-30 a, Q-66 a, Q-67 a; ADR-0014, ADR-0020 point 7 |
| Phase 11 polish (status colors, elapsed time, Home hierarchy) | Partly | Alongside Build 3-4 | Small fixes on the screens those builds rewire | Q-82 a |
| Public table menu (`table-menu`) | Mock | Later | Read-only view stays as is on mocks; QR self-ordering is Later | Q-81 a |

## Later and Out
| Item | Later or Out | Reason |
|---|---|---|
| QR self-ordering, waiter mobile ordering, Phase 10 comandas | Later | Q-81 a, ADR-0007 |
| Restaurant Edge, outbox, core-module split | Later (when Gabriel decides; optional paid feature) | Q-80 b, ADR-0003, ADR-0019 |
| Pricing, packages, billing | Later (with the first paid tier) | Q-78 a, ADR-0018 |
| App hosting and domain | Later (before the first pilot) | Q-79 a; `architecture/NFR.md` |
| Real 2FA | Later (pre-pilot candidate) | Proposal F2; `architecture/NFR.md` |
| PSP, in-app card, automatic PIX confirmation | Out this run | ADR-0005, ADR-0006, ADR-0020 point 5 |
| Automatic WhatsApp messages, fiscal, marketplaces | Out | ADR-0005, ADR-0006 |
| Customer accounts, Customer table | Out this run | Q-51 a |

## Rules
1. Existing features are improved, not rebuilt from scratch, and no new feature enters this run without Gabriel (ADR-0006).
2. Every screen moved onto the real backend in a build stops reading or writing its data from mocks or `localStorage` (proposal D3).
3. Audit items land as `planning/audit-triage.md` says; the pt-BR audit docs are reference only (ADR-0012).
4. Non-functional requirements each build must meet are in `architecture/NFR.md`.
5. Schema and API may still break freely until the first pilot restaurant (Q-32 a, ADR-0008).

## Acceptance scenario
The run's definition of done, run end to end against the real backend with the dev seed's two restaurants (MASTER-PLAN §5):
1. Given the platform admin, when they create restaurant A (name, slug, timezone) and invite its owner, then the owner sets a password from the email and signs in to A only.
2. When the owner invites a manager and a staff member, adds categories and items with a size option group and an add-on, sets one delivery zone, the ordering settings and a PIX key, then the storefront at `/r/{slug-a}` shows that menu, branding and zone.
3. When a customer adds the same item twice with different sizes and add-ons, enters a structured address in the zone, chooses PIX prepayment and taps "Já paguei", then the order is created at NEW with two distinct lines priced by the server, a payment page shows the static PIX code with the exact total, and the order stays out of the kitchen.
4. When staff record the PIX payment, then the order is PAID, it reaches the KDS, the bell shows it, and the customer's tracking link shows "payment confirmed".
5. When the kitchen moves it NEW to PREPARING to READY and staff complete it, then status never moves backward and the completed order is read-only.
6. When a staff member creates a dine-in order for a real table, paid on handover, then it starts at NEW with no status selector, counts as accepted at creation and reaches the kitchen at once (ADR-0020 point 8, Q-87 a).
7. When restaurant B's owner signs in, then none of A's orders, menu, settings or customers are visible, and every A resource URL returns not found; Home and Analytics for A show the day's totals with cancelled orders counted apart.

## Open questions
- none for this page. Retention periods and other proposals are in `architecture/NFR.md` for Gabriel's review.

## Sources
- MASTER-PLAN §2, §5, §6; `roadmap.md`; register `planning/open-questions.md`; ADRs cited above.
