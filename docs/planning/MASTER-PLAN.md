# Enterprise Order Suite: master plan

**Read this first.** It is the single entry point for the plan. The detail files it builds on are
`planning/pm-tool-recommendation.md` (tool, hosting, early decisions), `planning/ai-ready-development-plan.md`
(Claude readiness detail) and `architecture/RESTAURANT-OPS-ARCHITECTURE.md` (Gabriel's architecture).
Where they disagree with this file, this file wins.

Updated 2026-09-29 after a full cross-check of every planning file, the legacy project docs and both working
branches (backend `feature/ai-agent` @ `af2634e`, frontend `Claude-Assisted-Development` @ `14a3cfd`).

---

## 1. Where we are

**Current phase: S3 mostly done 2026-09-29 (the frontend `.claude/` waits on Q-06, Q-07); next is S4. S2 done 2026-09-29
(docs skeleton in the backend repo `docs/`; parts wait on Q-04, Q-05, Q-09, Q-10). S1 partially answered: Q-01..Q-03 answered 2026-09-29, Q-04..Q-10 open.** S0 (decisions baseline) is done, 2026-09-29. No code for the new architecture
exists yet. Documentation and Claude readiness run in parallel; software development starts only after the gate in §7.

S0 outputs (drafted in the project folder, pushed to the backend repo `docs/` in S2):
- `adr/README.md` + ADR-0000..0019: every decision, with Accepted vs Proposed marked honestly, and the legacy D1–D24 triage.
- `architecture/ARCHITECTURE-AMENDMENTS-PROPOSED.md`: amendments A-01..A-15 to Gabriel's architecture doc, with an accept/change/reject checklist.
- `planning/open-questions.md`: the single question register (Q-01..Q-83, grouped by the step each one blocks). Never renumber.
- `planning/superseded-docs.md`: every legacy doc/section that misleads agents, the ADR that replaces it, and the banner text for S2.
- `planning/s0-evidence/`: the cross-check inventory and critique these were built from.

## 2. What already exists (document it, don't rebuild it)

| Area | State |
|---|---|
| Frontend phases 0–9 | **Done.** Unified Order/MenuItem types, Menu (categories, items with sizes/add-ons, 86 toggle), Tables + QR, public `/table-menu`, Orders + KDS on one order stream, Administration (team, roles, audit log) on the real backend, Home + Analytics, i18n EN/PT-BR (15 namespaces), PIX/Card/Cash picker, delivery zones, Pickup/Delivery checkout, `/track-order`, `wa.me` links. |
| Frontend auth | **Done** (2026-09-25): HttpOnly refresh cookie, cross-tab single-flight, real logout. |
| Frontend data | Real: auth, profile, administration. **Everything else is mock** (menu, tables, orders, KDS, storefront, track-order, home, analytics). Owner branding, WhatsApp number and delivery zones are saved in the owner's own browser (`localStorage`), so customers never see them. |
| Backend phases 0–1 | **Done.** UTC instants everywhere (D15), order editability (D13), full auth rework (D16–D24), orders authorization fix, 213 tests green before phase 1. |
| Backend domain | Still the legacy B2B shape: `/orders` (PENDING..CANCELLED, customerId), `/products`. No restaurant/tenant concept, no menu, tables, zones, settings or `/public/*` endpoints. Integer `IDENTITY` ids. |
| Claude tooling | Backend: 5 skills, 4 agents, 1 hook (PowerShell only) in git. Frontend: skills, agents and hook exist **only on Gabriel's machine** (`.claude/` is gitignored). No CI in either repo. Frontend has no tests; ~28 old lint errors. |
| Known bugs | Audit Stage 2 never started: storefront drops size/add-on choices (cart merges by item), `PayLater` hardcoded, `$` hardcoded in 3 places, status selector at creation, no street address, B2B Notifications content. About half the older audit list is already fixed. |

## 3. Decided

| # | Decision | By |
|---|---|---|
| D-1 | Multi-restaurant: **one shared multi-tenant SaaS**. Supersedes the 2026-09-09 "one deployment per restaurant". | Gabriel |
| D-2 | Architecture principle: one Restaurant Operational Core (one Order model with channel + source, one Menu) consumed by every interface; the frontend is just the first consumer. | Gabriel |
| D-3 | **Restaurant Edge is optional** and not built this run. Only worth building for restaurants with poor signal. Designed only where it constrains the core (see §4). | Gabriel + Claude, in conversation |
| D-4 | Offline means **orders** (waiter → kitchen). Connectivity backup (4G failover router) is the customer's infrastructure. The app shows a clear connection-lost state, keeps in-progress orders on the device and retries safely. | Conversation |
| D-5 | **Payments are record-only.** The app never processes or queues payment transactions; card terminals (4G/5G) authorize on their own. The app records the result. Integrate with existing payment, fiscal and marketplace systems; never build them. | Gabriel |
| D-6 | This run: improve existing features. PSP gateway and automatic WhatsApp are **out**. Manual `wa.me` links stay. | Gabriel |
| D-7 | Build order: Tenant foundation → Menu → Order Core → Storefront ↔ Order Core. | Claude, accepted by Gabriel |
| D-8 | No production data (0 users/orders/products), so schema and API can be reshaped freely. | Gabriel |
| D-9 | **Backend owns the API contract.** One shared place for docs that both repos and every agent read. **Location (Q-01, 2026-09-29):** the backend repo `docs/` on `feature/ai-agent`. The frontend fetches the docs from it; backend-repo commits keep them updated; the frontend annotates only if necessary and only after prior agreement with Gabriel (ADR-0011). | Gabriel |
| D-10 | Docs in English only. Questions to Gabriel in his language. | Gabriel |
| D-11 | Agents may push but never merge. Always read the working branches, never `main`. **Scope (Q-02, 2026-09-29):** agents commit straight to the working branch and push; no merges at all (ADR-0013). | Gabriel |
| D-12 | Owner customization is **bounded**: logo, cover, brand colors, a font from a list, a layout preset. No custom CSS/HTML. Settings are a typed, backend-owned schema. What exactly each area allows is decided in its spec. Menu config builds on phases 1–3. | Gabriel + Claude |
| D-13 | Claude readiness is a prerequisite before software development. Jev was evaluated and dropped. | Gabriel |
| D-14 | Documentation has no fixed "done"; it is iterated with Gabriel, who is asked questions as each contract is drafted. | Gabriel |
| D-15 | Project management: Plane Community Edition on a free VPS (Oracle Always Free recommended). | Gabriel |
| D-16 | Legacy backend decisions kept: authorization only in `@PreAuthorize` (D2), full `./gradlew test` before "done" (D5), SCREAMING_SNAKE error codes (D14), UTC instants (D15), auth D16–D24, server derives every money value, order lines snapshot name and price, cancel is a status change. | Earlier sessions, still valid |

S1 answers (Gabriel, 2026-09-29; register Q-01..Q-03 quotes his words): Q-01 docs location and the frontend annotation rule (D-9 above); Q-02 never-merge scope (D-11 above); Q-03 all 12 simplifications in §4 accepted.

## 4. Refined after review: changed, simplified or cut

These are the parts of the earlier plans that did not make sense for a solo developer with zero users.
**Accepted by Gabriel 2026-09-29** (register Q-03, all 12 rows: "3 Yes").

| Was | Now | Why |
|---|---|---|
| New docs-hub repo `enterprise-order-suite-docs` | **A `docs/` folder in the backend repo** (Gabriel, Q-01, 2026-09-29) | Backend owns the contract anyway. A third repo adds a third branch rule and cross-repo PR plumbing for no gain. Frontend sessions attach the backend repo read-only and fetch the docs from it; frontend annotations only with Gabriel's prior agreement (ADR-0011). |
| Contract export via GitHub Action PRs across repos | One backend test fails if the committed `docs/api/openapi.yaml` differs from the live spec; the frontend generates types from that file. Design-first drafts live in `docs/api/drafts/`, seeded from manifest 0.4.0. | Same result, no tokens or cross-repo PRs. |
| Six derived architecture docs (3 about the Edge) | Architecture root + **Tenancy & Identity** + **API conventions** + **Order Core** + **Menu & Storefront** + one **Edge-ready constraints** ADR | The Edge isn't built; only its constraints matter now. Tenancy had no document at all. |
| Edge "designed now" (sync engine, device registry, ownership tables) | One ADR of constraints that would be expensive to change later: ULIDs, idempotent order creation, status only moves forward, menu/config cloud-owned, no payment queue, Edge optional. Full Edge docs after Order Core is reviewed. | D-3, D-4, D-5. |
| Order Core with domain events + outbox | Status history + in-process events. Outbox when the first external consumer is scheduled. | Nothing consumes events outside the app this run. |
| Separate "core module deployed twice" | Keep order/menu rules in the existing domain/application packages, free of web types. Split into a Gradle module when the Edge build is scheduled. | The split only pays off with a second runtime. |
| Packages as Basic/Pro feature flags in Tenant foundation | **Deferred.** No pricing, no billing, no plan machinery yet. | No customers; the old spec had 10 sellable modules, which conflicts anyway. |
| 16 readiness actions and an 8-point gate, all before any code | Slim gate (§7). ArchUnit rules, Playwright and the contract drift test become **acceptance criteria of the first Tenant slice**, where there is code to check. | Rules with nothing to guard are ceremony. |
| Git guard as a custom hook | `permissions.deny` rules in each repo's `.claude/settings.json` (merge, stash, push to main, `add -A`). | Built-in, cross-platform, no script. |
| Plane on the critical path (EOS ids, import of historical cycles, 8 empty future modules, API automations) | Plane stays the chosen tool, **set up whenever Gabriel has time**. Until then `docs/roadmap.md` is the tracker. No history import, no empty modules, no automations. | Oracle signup needs Gabriel's card; nothing should wait on it. |
| ULIDs "on offline-capable entities" in one file, "everywhere" in another | **ULID primary keys on every table**, Flyway re-baselined before launch, plus a short human order number per restaurant for display and tracking. | One pattern agents can't get wrong; no data to migrate. |
| Legacy backend phases 2–6, the frontend roadmap's "read first" and "what's next", the frontend-owned manifest rule | **Marked superseded** (not deleted). Salvage: phase 5 `/public/*` hardening → Storefront contract; phase 6 auth cleanup → Tenant foundation. | They contradict D-1, D-7, D-9. |

## 5. Added: what was missing

- **Decision records (ADRs)** for every decision in §3, plus a triage of legacy D1–D24 (kept / superseded).
- **An amendment to the architecture doc**, proposed as a changelog for Gabriel: status → Accepted, Edge optional, offline = orders, payments record-only (rename "Offline Transaction Queue" to "Offline Order Queue", drop "Payment Device Gateway"), bounded customization, original build order marked superseded.
- **Tenancy & Identity** design: tenant isolation mechanism (app-level scoping + a cross-tenant test per endpoint), how users belong to restaurants and roles (platform admin vs owner/manager/staff), what signup does, how a public page knows its restaurant, tenant id in logs.
- **API conventions**: money type (today a `double` in the contract vs `DECIMAL` in the DB), enum casing, ids, pagination, `Idempotency-Key`, error envelope, how drafts graduate to the live spec.
- **Scope of this run** (the short PRD): every existing feature placed in step 1–4, Later or Out; the audit backlog re-verified and split into "fold into a contract" vs "standalone fix"; one end-to-end acceptance scenario as the run's definition of done.
- **Short NFR page**: LGPD basics (customer phone/address, retention, phone as lookup credential), environments (local, CI), app hosting deferred until the storefront addressing is chosen; backups and error tracking listed as pre-pilot.
- **Glossary** with a legacy→new term map (Online→STOREFRONT, Pickup→TAKEAWAY, Dine-in→DINE_IN, Phone→?, MenuItem/Product, sizes/add-ons→modifiers/options) and the PT-BR UI vocabulary.
- **Current state file** (`docs/README.md`): where to look, current phase, what exists.
- **Cloud session setup** (SessionStart script) so any agent can run the full verification, and a **frontend lint baseline** so CI can go green.
- **Dev/test seed**: two demo restaurants with menus (never in production).

## 6. Order of work

| Step | What | Owner | Blocks on |
|---|---|---|---|
| S0 | ✅ **Decisions baseline** (done 2026-09-29): ADRs (new + legacy triage), architecture amendment proposal, open-questions register, superseded-docs list, memory cleanup. See §1. | Claude | — |
| S1 | **Partially done.** Answered 2026-09-29: Q-01 docs location, Q-02 never-merge scope, Q-03 the 12 simplifications. Open: Q-04..Q-10 (the amendments, the legacy triage, how the frontend `.claude/` reaches git and which tools stay out, whether Build 1 waits for S5, how Reviewed is marked, README notes). Plane signup whenever convenient. | Gabriel | S0 |
| **S2** | **Done 2026-09-29**, parts waiting on Q-04, Q-05, Q-09, Q-10. Docs skeleton in the backend repo `docs/` (ADR-0011): `docs/README.md` (map + current phase), `docs/roadmap.md`, ADRs, glossary seed, doc templates (with Status and Open questions), superseded banners on legacy docs. Rewrite both `CLAUDE.md` files to match. | Claude | S1 Q-01..Q-03 (answered). Parts wait on open Q-04, Q-05, Q-09, Q-10 (register "Blocks"); Q-12..Q-15 decided by Claude |
| S3 | **Mostly done 2026-09-29**; the frontend `.claude/` waits on Q-06, Q-07 (status: `docs/roadmap.md`). Readiness tooling: version frontend `.claude/`, port/remove the PowerShell hook, `permissions.deny` git rules, SessionStart setup, frontend lint baseline, Vitest, CI running today's checks in both repos. | Claude (+ Gabriel for S1 push) | S2 |
| S4 | Cross-cutting design: API conventions ADR, finalize the Edge-ready constraints (already drafted in ADR-0003), then **Tenancy & Identity** contract with Gabriel's question batch (register group 2). | Claude + Gabriel | S2 |
| S5 | Scope of this run (short PRD) + NFR page + audit backlog triage. | Claude + Gabriel | S4 |
| **Build 1** | Tenant foundation (acceptance includes: ArchUnit tenant rule, cross-tenant tests, contract drift test, seed data, tenant id in logs). | Claude | Gate §7 |
| Build 2–4 | Menu → Order Core → Storefront ↔ Order Core. Each contract is reviewed before its step. Small existing-feature fixes from the audit ride alongside. | Claude | their contract |
| Later | QR/table, waiter, KDS on Edge, POS, tablets, Edge build. **Out:** PSP, automatic WhatsApp, fiscal, marketplaces, billing. | | |

## 7. Readiness gate (before Build 1)

- [ ] ADRs and the amended architecture are Accepted by Gabriel.
- [ ] Docs live in one agreed place with a "where to look" map; legacy docs carry superseded banners.
- [ ] Both `CLAUDE.md` files match the current decisions.
- [ ] Frontend `.claude/` is in git; no Windows-only hooks; git rules enforced by `permissions.deny`.
- [ ] A fresh cloud session can run the full verification in both repos.
- [ ] CI runs `./gradlew test` and `yarn lint && yarn build && yarn test` on the working branches, green.
- [ ] API conventions + Tenancy & Identity contract are Reviewed. (Menu and Order Core contracts gate their own steps, not this one.)

"Reviewed" = Gabriel read it and no open question blocks the next build step. Only Gabriel sets it.

## 8. Open questions

The full list, with options and recommendations, is `planning/open-questions.md`. This section is only the summary.

**8a. S1 questions (register Q-01..Q-10). Q-01..Q-03 answered 2026-09-29; Q-04..Q-10 open:**
1. Where do the shared docs live? **Answered (Q-01):** backend repo `docs/`. The frontend fetches the docs from it, the backend keeps them updated, and the frontend annotates only if necessary and only after prior agreement with Gabriel.
2. What does "never merge" cover? **Answered (Q-02):** agents commit straight to the working branch and push; no merges at all.
3. Do you accept all 12 simplifications in §4? **Answered (Q-03):** yes, all rows 3.1–3.12.
4–10. Open: accept the architecture amendments; accept the legacy triage; how the frontend `.claude/` reaches git and which personal tools stay out of it; whether Build 1 waits for S5; how you mark a doc Reviewed; README notes on the old B2B description.

**8b. Asked during the Tenancy & Identity contract (S4):** can a user belong to one restaurant or many; roles per restaurant and a separate platform admin; what public signup does (creates a restaurant, invite-only, or removed); how the storefront finds its restaurant (path `/r/{slug}` recommended first, subdomain or custom domain later); Brazil only for now (BRL, pt-BR, Brazilian address, IANA timezone per restaurant)?; single location or chains.

**8c. Asked during Menu / Order Core / Storefront contracts:** modifier model depth (option groups, required/min/max); keep stock counts or only the 86 toggle; order status set and casing; channel/source mapping including staff phone orders; human order number format; Customer as a per-restaurant record or a snapshot on each order; how online customers pay this run (pay on delivery/pickup, static PIX key with manual confirmation?) and what a payment record holds; minimal Table in Order Core or dine-in waits; how the KDS signs in (staff login vs device pairing); logo/cover upload; exact owner customization per area.

**8d. Later:** pricing and packages, app hosting, Edge build criteria, Phase 10 comandas, Phase 11 polish.

## 9. When is the plan "complete"?

The **plan** is complete when S1 is done: Gabriel has accepted the decisions baseline and answered §8a. From then
on, the work is execution against this file. The **docs** stay open-ended (D-14) and grow contract by contract;
each one only has to reach "Reviewed" before the build step that needs it.
