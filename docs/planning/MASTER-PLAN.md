# Enterprise Order Suite: master plan

**Read this first.** It is the single entry point for the plan. The detail files it builds on are
`planning/pm-tool-recommendation.md` (tool, hosting, early decisions), `planning/ai-ready-development-plan.md`
(Claude readiness detail) and `architecture/RESTAURANT-OPS-ARCHITECTURE.md` (Gabriel's architecture).
Where they disagree with this file, this file wins.

Updated 2026-09-29 after a full cross-check of every planning file, the legacy project docs and both working
branches (backend `feature/ai-agent` @ `af2634e`, frontend `Claude-Assisted-Development` @ `14a3cfd`).
Updated 2026-10-01 with Gabriel's register answers of 2026-10-01 (project thread, 2026-10-01T16:25Z; `planning/open-questions.md`
quotes his words).
Updated 2026-10-06 and 2026-10-07 with Gabriel's later answers (2026-10-06: Q-30, Q-86, ADR-0020 Accepted, the board set up;
2026-10-07: Q-87, Q-88 and Q-89 on decision cards); §1 and §8 give the state.

---

## 1. Where we are

**Current phase: S1, S2 and S3 done, and the project board is set up (Gabriel, 2026-10-06; Q-83 b; GitHub Projects,
ADR-0021; runbook `docs/ops/github-projects-setup.md`); next is S4.** S1 done 2026-10-01: Q-01..Q-03 answered 2026-09-29, Q-04..Q-10 on
2026-10-01, and the same batch answered every other open register question except Q-30 (not in the batch); Q-70 was only partly
answered, and the new Q-86 asked Gabriel to clarify it. Q-30 and Q-86 were answered on 2026-10-06 (both a); the same day Claude asked Q-87, Q-88 and Q-89, which follow from Gabriel's ADR-0020 answers, and he answered all three on 2026-10-07 (a, a, a). S2 done: docs skeleton in the backend repo `docs/` on 2026-09-29; the parts that waited
on Q-04, Q-05, Q-09, Q-10 done 2026-10-01. S3 done 2026-10-01: the frontend `.claude/` versioned by Gabriel (FE `c4a7309`)
and its `permissions.deny` git rules added (FE `30bc172`). S0 (decisions baseline) is done, 2026-09-29. No code for the new architecture
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
| Claude tooling | At S0: backend 5 skills, 4 agents, 1 hook (PowerShell only) in git; frontend skills, agents and hook only on Gabriel's machine; no CI; no frontend tests. **Since S3 (2026-09-29, 2026-10-01):** backend skills and agents aligned with the ADRs, two Node hooks, `permissions.deny` git rules, CI running `./gradlew test`; frontend `.claude/` versioned by Gabriel (FE `c4a7309`: agents, skills including graphify, the Node lint/typecheck hook, `settings.json` enabling ponytail and the wshobson plugins), `permissions.deny` git rules (FE `30bc172`), Vitest and CI running `yarn lint && yarn build && yarn test`; lint is clean. |
| Known bugs | Audit Stage 2 never started: storefront drops size/add-on choices (cart merges by item), `PayLater` hardcoded, `$` hardcoded in 3 places, status selector at creation, no street address, B2B Notifications content. About half the older audit list is already fixed. |

## 3. Decided

| # | Decision | By |
|---|---|---|
| D-1 | Multi-restaurant: **one shared multi-tenant SaaS**. Supersedes the 2026-09-09 "one deployment per restaurant". | Gabriel |
| D-2 | Architecture principle: one Restaurant Operational Core (one Order model with channel + source, one Menu) consumed by every interface; the frontend is just the first consumer. | Gabriel |
| D-3 | **Restaurant Edge is optional** and not built this run. Only worth building for restaurants with poor signal. Designed only where it constrains the core (see §4). **Refined (Q-80 b, 2026-10-01):** built when Gabriel decides, as an optional paid feature (ADR-0003). | Gabriel + Claude, in conversation; refinement: Gabriel |
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
| D-15 | Project management: **GitHub Projects since 2026-10-06 (ADR-0021)**; was Plane. **Changed (Q-83 b, 2026-10-01):** the Plane setup is the first step before work on the app (before Build 1; ADR-0017). **Changed again (Gabriel, 2026-10-06):** Plane runs on Plane Cloud's free plan instead of self-hosted Community Edition on a free VPS, because Oracle had no Always Free A1 capacity and a 1 GB Micro cannot run Plane. **Replaced the same day (Gabriel, 2026-10-06T14:02Z):** "lets redesign our plan around github projects instead of plane, much simpler, free and AI can access it": GitHub Projects, ADR-0021, which supersedes ADR-0017. **Set up by Gabriel on 2026-10-06** (15:12Z). | Gabriel |
| D-16 | Legacy backend decisions kept: authorization only in `@PreAuthorize` (D2), full `./gradlew test` before "done" (D5), SCREAMING_SNAKE error codes (D14), UTC instants (D15), auth D16–D24, server derives every money value, order lines snapshot name and price, cancel is a status change. **Extended (Q-05 a, 2026-10-01):** also D1 (`.claude/` is versioned), D3 (indentation), D4 (skills teach the target model), D12 (env-bound cookie and CORS properties), D13 (item edits only while an order is open) and LR-4 (`/public/*` never reuses an admin-scoped query; denied-access and leak tests) (ADR-0000). | Earlier sessions, still valid; extension: Gabriel |

S1 answers (Gabriel, 2026-09-29; register Q-01..Q-03 quotes his words): Q-01 docs location and the frontend annotation rule (D-9 above); Q-02 never-merge scope (D-11 above); Q-03 all 12 simplifications in §4 accepted.

S1 answers (Gabriel, 2026-10-01; register Q-04..Q-10 quotes his words): Q-04 all amendments A-01..A-15 accepted and "Fiscal Gateway" dropped (applied to the architecture doc); Q-05 the legacy triage (ADR-0000) accepted and D-16 extended (above); Q-06 and Q-07 the frontend `.claude/` versioned whole (FE `c4a7309`); Q-08 b S5 joins the gate (§7); Q-09 a Reviewed is a message in the project thread that Claude records; Q-10 a notes on both public READMEs. The same batch answered almost every later register question (§8).

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
| Plane on the critical path (EOS ids, import of historical cycles, 8 empty future modules, API automations) | Plane stays the chosen tool, **set up whenever Gabriel has time**. Until then `docs/roadmap.md` is the tracker. No history import, no empty modules, no automations. **Changed 2026-10-01 (Q-83 b):** Gabriel's Oracle account exists, and the Plane setup now comes before Build 1 (§6); the rest of this row stands. **Changed 2026-10-06:** the tool is GitHub Projects (ADR-0021); the board setup keeps this row's limits (no history import, no empty milestones, no automation code). | Oracle signup needs Gabriel's card; nothing should wait on it. |
| ULIDs "on offline-capable entities" in one file, "everywhere" in another | **ULID primary keys on every table**, Flyway re-baselined before launch, plus a short human order number per restaurant for display and tracking. | One pattern agents can't get wrong; no data to migrate. |
| Legacy backend phases 2–6, the frontend roadmap's "read first" and "what's next", the frontend-owned manifest rule | **Marked superseded** (not deleted). Salvage: phase 5 `/public/*` hardening → Storefront contract; phase 6 auth cleanup → Tenant foundation. | They contradict D-1, D-7, D-9. |

## 5. Added: what was missing

- **Decision records (ADRs)** for every decision in §3, plus a triage of legacy D1–D24 (kept / superseded).
- **An amendment to the architecture doc**, proposed as a changelog for Gabriel: status → Accepted, Edge optional, offline = orders, payments record-only (rename "Offline Transaction Queue" to "Offline Order Queue", drop "Payment Device Gateway"), bounded customization, original build order marked superseded.
- **Tenancy & Identity** design: tenant isolation mechanism (app-level scoping + a cross-tenant test per endpoint), how users belong to restaurants and roles (platform admin vs owner/manager/staff), what signup does, how a public page knows its restaurant, tenant id in logs.
- **API conventions**: money type (today a `double` in the contract vs `DECIMAL` in the DB), enum casing, ids, pagination, `Idempotency-Key`, error envelope, how drafts graduate to the live spec.
- **Scope of this run** (the short PRD): every existing feature placed in step 1–4, Later or Out; the audit backlog re-verified and split into "fold into a contract" vs "standalone fix"; one end-to-end acceptance scenario as the run's definition of done.
- **Short NFR page**: LGPD basics (customer phone/address, retention, phone as lookup credential), environments (local, CI), app hosting deferred until the storefront addressing is chosen; backups and error tracking listed as pre-pilot.
- **Glossary** with a legacy→new term map (Online→STOREFRONT, Pickup→TAKEAWAY, Dine-in→DINE_IN, Phone→?, MenuItem/Product, sizes/add-ons→modifiers/options) and the PT-BR UI vocabulary. The map was settled by Gabriel's 2026-10-01 answers (Phone→source PHONE, Q-49 a; `MenuItem`, Q-43 a; option groups, Q-41 b); `glossary.md` records them.
- **Current state file** (`docs/README.md`): where to look, current phase, what exists.
- **Cloud session setup** (SessionStart script) so any agent can run the full verification, and a **frontend lint baseline** so CI can go green.
- **Dev/test seed**: two demo restaurants with menus (never in production).

## 6. Order of work

| Step | What | Owner | Blocks on |
|---|---|---|---|
| S0 | ✅ **Decisions baseline** (done 2026-09-29): ADRs (new + legacy triage), architecture amendment proposal, open-questions register, superseded-docs list, memory cleanup. See §1. | Claude | — |
| S1 | **Done 2026-10-01.** Answered 2026-09-29: Q-01 docs location, Q-02 never-merge scope, Q-03 the 12 simplifications. Answered 2026-10-01: Q-04..Q-10 (the amendments, the legacy triage, the frontend `.claude/` pushed whole by Gabriel, S5 joins the gate, how Reviewed is marked, README notes; §3), plus almost every later register question. The last two, Q-30 and Q-86, were answered on 2026-10-06 (§8). The project board setup (GitHub Projects since 2026-10-06, ADR-0021) is now its own step before Build 1 (Q-83 b). | Gabriel | S0 |
| **S2** | **Done** (2026-09-29; the parts that waited on Q-04, Q-05, Q-09, Q-10 done 2026-10-01: amendments applied to the architecture doc, ADR-0000 Accepted, Reviewed signal defined, notes on both public READMEs, BE `c8bf761` and FE `30bc172`). Docs skeleton in the backend repo `docs/` (ADR-0011): `docs/README.md` (map + current phase), `docs/roadmap.md`, ADRs, glossary seed, doc templates (with Status and Open questions), superseded banners on legacy docs. Rewrite both `CLAUDE.md` files to match. | Claude | S1 Q-01..Q-03 (answered). Parts waited on Q-04, Q-05, Q-09, Q-10 (answered 2026-10-01); Q-12..Q-15 decided by Claude |
| S3 | **Done 2026-10-01** (status: `docs/roadmap.md`): everything but the frontend `.claude/` on 2026-09-29; the frontend `.claude/` versioned by Gabriel (FE `c4a7309`, Q-06 a, Q-07) and its `permissions.deny` git rules added (FE `30bc172`) on 2026-10-01. Readiness tooling: version frontend `.claude/`, port/remove the PowerShell hook, `permissions.deny` git rules, SessionStart setup, frontend lint baseline, Vitest, CI running today's checks in both repos. | Claude (+ Gabriel for S1 push) | S2 |
| S4 | Cross-cutting design: API conventions doc (`docs/architecture/API-CONVENTIONS.md`), finalize the Edge-ready constraints (already drafted in ADR-0003), then **Tenancy & Identity** contract with Gabriel's question batch (register group 2). Register groups 2 and 3 were answered 2026-10-01, and Q-30 on 2026-10-06 (Q-26 decided by Claude on Gabriel's delegation). | Claude + Gabriel | S2 |
| S5 | Scope of this run (short PRD) + NFR page + audit backlog triage. Input also: Claude's settings and notifications proposal, `planning/proposals/settings-and-notifications.md` (Q-24). Must be Reviewed before Build 1 (Q-08 b, §7). **Done 2026-10-07:** `planning/run-scope.md`, `architecture/NFR.md`, `planning/audit-triage.md`, Reviewed by Gabriel. | Claude + Gabriel | S4 |
| Board | **Project board setup** (Q-83 b, 2026-10-01; GitHub Projects since Gabriel's decision of 2026-10-06, ADR-0021), following the runbook `docs/ops/github-projects-setup.md` (Reviewed 2026-10-06): one project on his account, an "In review" Status, the auto-add workflow and the labels. Work items are issues in the backend repo, cited as `Refs #<n>`; that board design was Claude's default; Gabriel confirmed it with the runbook on 2026-10-06 (18:12Z) and may still change it. Once the board is in use it replaces `docs/roadmap.md` as the tracker. Docs and readiness work do not wait for it. **Done 2026-10-06** (Gabriel, project chat 15:12Z: "Board setup done https://github.com/users/gtavaresdevs/projects/2"); the tracker has not moved yet. | Gabriel (+ Claude: runbook) | — |
| **Build 1** | Tenant foundation (acceptance includes: ArchUnit tenant rule, cross-tenant tests, contract drift test, seed data, tenant id in logs). | Claude | Gate §7 (since 2026-10-01 it includes S5 Reviewed, Q-08 b, and the board setup, Q-83 b) |
| Build 2–4 | Menu → Order Core → Storefront ↔ Order Core. Each contract is reviewed before its step. Small existing-feature fixes from the audit ride alongside; so do Phase 11's small polish items, with Build 3-4 (Q-82 a). | Claude | their contract |
| Later | QR/table, waiter, Phase 10 comandas (Q-81 a), KDS on Edge, POS, tablets, Edge build (when Gabriel decides; an optional paid feature, Q-80 b). **Out:** PSP, automatic WhatsApp, fiscal, marketplaces, billing. | | |

## 7. Readiness gate (before Build 1)

- [ ] ADRs and the amended architecture are Accepted by Gabriel. (ADR-0000..ADR-0019. An ADR added later, such as ADR-0020, gates the build step that depends on it, here Build 4, not Build 1; Claude's reading, 2026-10-01, Gabriel may override.)
- [ ] Docs live in one agreed place with a "where to look" map; legacy docs carry superseded banners.
- [ ] Both `CLAUDE.md` files match the current decisions.
- [ ] Frontend `.claude/` is in git; no Windows-only hooks; git rules enforced by `permissions.deny`.
- [ ] A fresh cloud session can run the full verification in both repos.
- [ ] CI runs `./gradlew test` and `yarn lint && yarn build && yarn test` on the working branches, green.
- [x] API conventions + Tenancy & Identity contract are Reviewed (Gabriel, 2026-10-07). (Menu and Order Core contracts gate their own steps, not this one.)
- [x] S5 (scope of this run, NFR page, audit triage) Reviewed. (Added 2026-10-01, Q-08 b; Reviewed by Gabriel 2026-10-07.)
- [ ] Project board set up. (Added 2026-10-01, Q-83 b: "the first thing we do before begin working on the actual app"; GitHub Projects since 2026-10-06, ADR-0021; runbook `docs/ops/github-projects-setup.md`.)

"Reviewed" = Gabriel read it and no open question blocks the next build step. Only Gabriel sets it, with a message in the project thread ("Reviewed: <doc>") that Claude records (Q-09 a).

Which items hold today, with evidence: `docs/roadmap.md`.

## 8. Open questions

The full list, with options and recommendations, is `planning/open-questions.md`. This section is only the summary.
State 2026-10-01: Gabriel answered his batch on 2026-10-01 (project thread, 2026-10-01T16:25Z). State 2026-10-06: he answered Q-30 a and Q-86 a (project thread, 2026-10-06T18:12Z). State 2026-10-07: he answered Q-87 a, Q-88 a and Q-89 a on decision cards (project thread, 2026-10-07T00:01Z). **Nothing is open in the register.**

**8a. S1 questions (register Q-01..Q-10). Q-01..Q-03 answered 2026-09-29; Q-04..Q-10 answered 2026-10-01:**
1. Where do the shared docs live? **Answered (Q-01):** backend repo `docs/`. The frontend fetches the docs from it, the backend keeps them updated, and the frontend annotates only if necessary and only after prior agreement with Gabriel.
2. What does "never merge" cover? **Answered (Q-02):** agents commit straight to the working branch and push; no merges at all.
3. Do you accept all 12 simplifications in §4? **Answered (Q-03):** yes, all rows 3.1–3.12.
4. Accept the architecture amendments? **Answered (Q-04):** all 15 accepted and "Fiscal Gateway" dropped (Gabriel ticked the AMD checklist, commit `6a60b6d`); applied to the architecture doc.
5. Accept the legacy triage? **Answered (Q-05):** yes; ADR-0000 is Accepted and D-16 is extended (§3).
6. How does the frontend `.claude/` reach git? **Answered (Q-06):** a; Gabriel took it out of `.gitignore` and pushed it.
7. Which personal tools stay out of it? **Answered (Q-07), by action, closest option a:** none; the whole folder is committed (FE `c4a7309`), and four machine-only files stay ignored.
8. Does Build 1 wait for S5? **Answered (Q-08):** b, yes; S5 joins the gate (§7).
9. How do you mark a doc Reviewed? **Answered (Q-09):** a, a message in the project thread ("Reviewed: <doc>"), which Claude records.
10. README notes on the old B2B description? **Answered (Q-10):** a; added to both public READMEs (BE `c8bf761`, FE `30bc172`).

**8b. Asked during the Tenancy & Identity contract (S4). Answered 2026-10-01 (register group 2):** a user belongs to one restaurant for now, stored in a membership table (Q-16 a); a platform admin separate from per-restaurant owner, manager and staff (Q-17 a), with full read access for support (Q-18 b); signup is invite-only (Q-19 a); the storefront finds its restaurant by the path `/r/{slug}` (Q-20 a), a slug the platform admin sets at creation and that does not change this run (Q-31 a); Brazil only for now (Q-21 a); one tenant = one restaurant location (Q-22 a); no pilot planned yet (Q-23 b). Q-24 (the mock security and notification screens): none of the options; Gabriel asked Claude to rethink them, and Claude's proposal is `planning/proposals/settings-and-notifications.md` (Reviewed by Gabriel on 2026-10-06). Q-26 (where a request's restaurant comes from): delegated by Gabriel, decided by Claude (a). Q-30 (where restaurant settings and delivery zones are built; not in the batch) was answered on 2026-10-06 (a): Build 1 creates the restaurant, the typed settings schema and the timezone; the other settings and delivery zones land in Build 4.

**8c. Asked during Menu / Order Core / Storefront contracts. Answered 2026-10-01 (register groups 4-6):** option groups with required, min and max (Q-41 b); stock counts dropped, the 86 toggle is the only availability control (Q-42 c); status set NEW, PREPARING, READY, COMPLETED, CANCELLED (Q-47 a), SCREAMING_SNAKE on the wire (Q-34 a); channel DINE_IN, TAKEAWAY or DELIVERY and source STOREFRONT, PHONE, WAITER, POS, QR or TABLET (Q-49 a); a running order number per restaurant (Q-50 a); the customer is a snapshot on each order (Q-51 a); online customers pay on delivery or pickup (pickup only with PIX prepayment since Q-86 a), plus PIX paid in advance (Q-65; Gabriel delegated the approach: ADR-0020, Accepted 2026-10-06); a payment record with order payment status UNPAID, PAID or REFUNDED (Q-58 a), PAID only when staff record the payment (Q-55 a); a minimal Table in Order Core (Q-53 a); the KDS signs in with a staff login (Q-54 a); uploads for item photos, logo and cover (Q-44 a); owner customization as listed in Q-66 a. Q-86, which clarified Gabriel's Q-70 pickup answer, was answered on 2026-10-06 (a): pickup only with PIX prepayment inside a delivery zone, and rules 54 and 56 stay in Build 4. Asked on 2026-10-06 and answered on 2026-10-07 (decision cards, project thread, 00:01Z): owner, manager and staff accept orders, and only the owner and the manager release an unpaid PIX order to the kitchen (Q-87 a); a "Já paguei" tap before the 5 minutes run out holds a PIX order for staff (Q-88 a); payment and refund records may be added to cancelled and completed orders (Q-89 a). All three are recorded in ADR-0020.

**8d. Later. Answered 2026-10-01 (register group 8):** pricing and packages are decided with the first paid tier (Q-78 a); app hosting is chosen before the first pilot, with app and API on one registrable domain (Q-79 a); the Edge is built when Gabriel decides, as an optional paid feature (Q-80 b); Phase 10 comandas stay Later (Q-81 a); Phase 11 polish rides alongside Build 3-4 (Q-82 a); the project board setup comes first, before Build 1 (Q-83 b; GitHub Projects since 2026-10-06, ADR-0021, so HTTPS exposure and off-box backups are moot; the `Refs #<n>` convention, Claude's default, was confirmed by Gabriel with the runbook on 2026-10-06).

**8e. Waiting for Gabriel (not register questions):** nothing since 2026-10-06 (project thread, 18:12Z). He accepted ADR-0020, reviewed `planning/proposals/settings-and-notifications.md` (Q-24) and `docs/ops/github-projects-setup.md` (Q-83, ADR-0021), and confirmed branch protection on `main` in both repos (Q-72 a).

## 9. When is the plan "complete"?

The **plan** is complete when S1 is done: Gabriel has accepted the decisions baseline and answered §8a. From then
on, the work is execution against this file. The **docs** stay open-ended (D-14) and grow contract by contract;
each one only has to reach "Reviewed" before the build step that needs it.

S1 is done (2026-10-01): ADR-0000..ADR-0019 and the amended architecture are Accepted, and §8a is answered. Q-30, Q-86
and ADR-0020 belonged to later steps (S5, the Storefront contract); all three were settled on 2026-10-06.
