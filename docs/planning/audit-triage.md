# Planning: audit backlog triage (S5)

- Status: Draft
- Updated: 2026-10-07
- Reviewed: not yet
- Roadmap step: S5 (`docs/roadmap.md`)
- Related: ADR-0005, ADR-0006, ADR-0008, ADR-0014, ADR-0020; register Q-19, Q-24, Q-27, Q-29, Q-42, Q-44, Q-45, Q-46, Q-48, Q-53, Q-56, Q-57, Q-58, Q-62, Q-67, Q-68, Q-81, Q-82, Q-86; `planning/run-scope.md`, `architecture/NFR.md`

## Goal
Every item of the old audit list (business-rules master of 2026-09-16, "Diverges", "Partially implemented" and "[NEW]" tags, plus the Stage 2 known-bug list) is re-verified against today's code and given one home: **contract** (the fix is part of a contract and its build step), **standalone fix** (a small fix outside any contract), **done** (already fixed), **Later** or **Out**. Agents plan from this page, not from the pt-BR audit docs (ADR-0012).

## How it was checked
- Frontend `order-ui` on `Claude-Assisted-Development` (shallow clone, 2026-10-07). Paths below are relative to `order-ui/src/`.
- Rule numbers are from the English master, FE `order-ui/docs/superpowers/specs/2026-09-16-business-rules-master-en.md` (snapshot; PF copy `2026-09-16-business-rules-master-en.md`).
- "Re-verified" means the cited line was read on 2026-10-07. Items marked "not re-read" keep the 2026-09-16 evidence; they are rebuilt by a contract anyway, so the old line does not matter.

## Summary
- Done since the audit: rules 26, 33, 58 and most of 86 (currency), 68 (logout), 81 (absolute link; the path changes again in Build 4).
- Standalone fixes: two leftover hardcoded `$` (S-1). Nothing else is worth fixing on mocks that Builds 2-4 replace (ADR-0006: improve existing features; ADR-0008: reshape is free).
- Everything else folds into a contract and lands in Build 1-4, or is Later/Out.

## Standalone fixes
| # | Item | Evidence (2026-10-07) | Fix | When |
|---|---|---|---|---|
| S-1 | Two hardcoded `$` left (rules 58 and 86 class): the public table menu price and the Analytics revenue chart axis and tooltip | `features/table-menu/components/PublicMenuItemCard.tsx` L10; `features/analytics/components/RevenueChart.tsx` L19, L23 | Use `formatCurrency` (i18n presentation boundary) | Any frontend session; the Analytics screen is rewired in Build 3, so fix it in the same change if Build 3 starts first |

## Folded into contracts
| Rule / bug | Item | State on 2026-10-07 | Decision | Contract / build |
|---|---|---|---|---|
| 69 | Self-service `/register` reachable | Still there: `app/router.tsx` L40, link in `pages/Login.tsx` L25 | Invite-only, `POST /auth/register` removed (Q-19 a; T&I D-5) | Tenancy & Identity, Build 1 |
| 63 | Client role union vs backend roles; `normalizeRole` drops unknown roles | `features/auth/utils/auth.utils.ts` L3 | New roles platform admin, owner, manager, staff (Q-17 a); the frontend generates types from the spec (Q-76 a) | Tenancy & Identity, Build 1 |
| 71 | Avatar upload is a no-op | Still a mock: `features/profile/services/profile.service.ts` L26-28 | Made real (settings proposal C2; T&I `POST /me/avatar`) | Tenancy & Identity, Build 1 |
| 72 | Security settings (2FA, API keys, sessions, delete account) do nothing | Not re-read (settings proposal F1-F6, 2026-10-06) | Removed; replaced by change password and "sign out of other devices" (proposal C4, C5; Reviewed) | Tenancy & Identity, Build 1 |
| 73 | Preferences hold restaurant data per device | Not re-read (proposal F14-F16) | Timezone and currency become restaurant data (Q-27 a, Q-28 a); storefront and zone sections move to restaurant settings (proposal D2, D3) | Build 1 (timezone), Build 4 (the rest) |
| 27 | Stock count vs 86 toggle | Not re-read | Stock counts dropped; the 86 toggle is the only availability control (Q-42 c) | Menu, Build 2 |
| 29 | Hard delete of items with order history | Not re-read | Archive instead of delete once an item has orders (Q-45 a) | Menu, Build 2 |
| 30 | Price silently defaults to 0 | Not re-read | Price required and above 0 (Q-45 a) | Menu, Build 2 |
| 31 | Categories are a fixed list | Not re-read | Categories get a stable id; owner orders them (Q-46 a, Q-66 a) | Menu, Build 2 |
| 6, 32 | Staff free-type order lines and prices; ad hoc lines implied by seed data | Still free-typed: `features/orders/components/CreateOrderModal.tsx` L172-173 | Lines must reference menu items and the server prices them (Q-48 a); ad hoc lines are not built | Order Core, Build 3 |
| 18 | Status selector at creation, including Cancelled | Still there: `CreateOrderModal.tsx` L122-123 | Every new order starts at NEW (Q-48 a) | Order Core, Build 3 |
| 15, 45 | Staff orders hardcode `paymentStatus: "PayLater"` | Still there: `CreateOrderModal.tsx` L76; storefront derives it from the method: `features/storefront/hooks/useStorefront.ts` L75 | `PayLater` goes away; payment status comes from payment records (Q-55 a, Q-58 a) | Order Core, Build 3 |
| 7, 8, 13 | Any status transition allowed; Cancelled and Completed not locked | Not re-read | Status only moves forward (ADR-0003); CANCELLED and COMPLETED read-only, payment and refund records still allowed (Q-48 a, Q-89 a) | Order Core, Build 3 |
| 46 | Payment method editable after creation | Not re-read | Fixed at creation (Q-48 a; Claude's reading in ADR-0020 point 8) | Order Core, Build 3 |
| 48 | Payment status of a cancelled paid order undefined | n/a (no rule existed) | Refund recorded, status REFUNDED (Q-56 a) | Order Core, Build 3 |
| 12, 21 | "Ready" and the timeline copy are not fulfillment-aware | Not re-read | Phase 11 polish item riding with Build 3-4 (Q-82 a) | Order Core, Build 3 |
| 37 | Public table lookup shares the admin roster source | Not re-read (documented, intentional on mocks) | A minimal Table (id, name) for staff dine-in (Q-53 a); public QR self-ordering is Later (Q-81 a) | Order Core, Build 3 |
| 82, 83 | KPI definitions inconsistent; "Heatmap" naming | Not re-read | One server aggregate per dashboard on `businessDate`, CANCELLED excluded and shown as its own count (Q-62 a); naming rides with Q-82 a | Order Core, Build 3 |
| 88, 89, 90 | Notifications show B2B demo content; preferences discarded | Still B2B: `features/notifications/constants/notifications.constants.ts` | Small per-restaurant feed from in-process order events; no per-user history or mute (Q-57 a; proposal E1-E5) | Order Core, Build 3 (E4 in Build 4) |
| bug | Storefront cart merges lines by item, dropping size and add-on choices | Still there: `useStorefront.ts` L38-42 (`find` by `menuId`) | Lines carry their option choices (Q-41 b) | Storefront, Build 4 |
| 47 | No minimum delivery order | n/a | Minimum delivery order is a restaurant setting (Q-67 a) | Storefront, Build 4 |
| 50 | No zones: delivery disabled vs hidden | Disabled with explanation | Keep disabled with explanation (functionally equivalent; Claude's default) | Storefront, Build 4 |
| 51, 24, 55 | Zone ETA vs flat prep and delivery times; pickup ETA hardcoded | Pickup ETA constant: `features/storefront/components/CheckoutFlow.tsx` L8, L34 | Per-zone ETA kept; pickup ETA becomes a setting (Q-67 a) | Storefront, Build 4 |
| 53 | No "bairro not covered, switch to pickup" | Not built | Pickup only with PIX prepayment inside a zone; no pickup outside every zone (Q-86 a, ADR-0020 point 6) | Storefront, Build 4 |
| 54 | No street address | Not built | Structured address (Q-68 a) | Storefront, Build 4 |
| 56 | No customer "Continuar no WhatsApp" link | Not built: `features/storefront/components/SuccessView.tsx` has no WhatsApp link | Kept in Build 4 (Q-86 a) | Storefront, Build 4 |
| 23 | Tracking page loads the whole order list | Not re-read | Link carries the order ULID; manual lookup needs number + phone; rate-limited (Q-69 a) | Storefront, Build 4 |
| 81 | "Copy ordering link" | Now absolute: `features/home/components/LaunchCards.tsx` L20 (`origin + /storefront`) | The path becomes `/r/{slug}` (Q-20 a, Q-71 a) | Storefront, Build 4 |
| 62 | Audit-log retention undefined | n/a | Retention set in `architecture/NFR.md` | NFR page |

## Done since the audit
| Rule / bug | Evidence (2026-10-07) |
|---|---|
| 26 Storefront hides unavailable items | `features/storefront/hooks/useStorefront.ts` L20 filters `item.available` |
| 33, 58, 86 (most) Hardcoded `$` in Menu cards, cart, zone picker, Analytics KPIs | No literal `$` left in `features/menu`, `features/storefront`, or the KPI cards; the two leftovers are S-1 |
| 68 Logout is client-only | `features/auth/services/*` `logoutRequest` calls `POST /auth/logout`, which revokes the token family |

## Later and Out
| Item | Later or Out | Reason |
|---|---|---|
| 36 Block deleting a table with an open comanda | Later | Comandas are Later (Q-81 a) |
| Automatic WhatsApp status messages | Out | ADR-0006 |
| In-app card or automatic PIX confirmation | Out this run | ADR-0005, ADR-0006, ADR-0020 point 5 |

## Open questions
- none. Every item above maps to a register answer or to a Claude default that Gabriel can change at review (rule 50).

## Sources
- Business-rules master (EN), rules 6-90, and PF `RESTAURANT-OPS-ROADMAP.md` L223-254 (Stage 2 known bugs).
- `planning/open-questions.md` (answers quoted there), `planning/proposals/settings-and-notifications.md` (Reviewed 2026-10-06), `architecture/TENANCY-AND-IDENTITY.md` (Reviewed 2026-10-07), ADR-0020.
