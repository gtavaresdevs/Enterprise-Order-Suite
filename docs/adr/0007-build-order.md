# ADR-0007: Build order: Tenant foundation, Menu, Order Core, Storefront to Order Core
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel (accepted Claude's proposal; MASTER-PLAN §3 D-7). S5 before Build 1 (Q-08 b), Plane setup first (Q-83 b), comandas Later (Q-81 a), polish with Build 3-4 (Q-82 a): Gabriel, 2026-10-01
- Supersedes:
  - Gabriel's original 16-step order and its "critical decision" of placing the Edge and sync before the consumers (PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md` L320-339). The architecture amendment proposal marks it superseded; Gabriel's text is not edited here.
  - The "Proposed order (revised)" table (PF `planning/pm-tool-recommendation.md` L119-131) where it differs: row 3 "Core module ... domain events + outbox" moves to ADR-0019; rows 5-6 "Design only" become ADR-0003.
  - The readiness order and gate (PF `planning/ai-ready-development-plan.md` §4-5, L72-91), replaced by MASTER-PLAN §6-7.
  - Legacy backend migration phases 2-6 (PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L150-166) and the frontend roadmap's "What's next" (PF `RESTAURANT-OPS-ROADMAP.md` L209-221). Salvage: phase 5 `/public/*` hardening goes into the Storefront contract; phase 6 auth cleanup goes into Tenant foundation (PF `planning/MASTER-PLAN.md` §4 L68).
  - The forward cycles "docs -> audit -> backend phases 2+" (PF `planning/pm-tool-recommendation.md` L42).
- Related: ADR-0001, ADR-0002, ADR-0003, ADR-0006, ADR-0013, ADR-0015, ADR-0016, ADR-0017, ADR-0019

## Context
- Gabriel's order put Storefront (3) before Order Core (4), and Edge, local persistence and sync (5-7) before Storefront <-> Order Core (8) (architecture L320-337).
- Claude's review, gap #5, noted that storefront delivery is cloud-only and never needs the Edge. It proposed Storefront <-> Order Core right after Order Core, with the Edge later as the gate for waiter, KDS and POS. The existing KDS stays cloud-only until then. Gabriel accepted this on 2026-09-29 (PF `planning/pm-tool-recommendation.md` L117; PF `planning/MASTER-PLAN.md` §3 D-7, L40).
- Claude readiness comes before any software development (D-13, L46; ADR-0015). Docs have no fixed "done"; each contract only has to be Reviewed before the step that needs it (D-14, L47; ADR-0016).
- No code for the new architecture exists yet. The current phase is S0 (PF `planning/MASTER-PLAN.md` §1 L15).

## Decision
1. **Build order (D-7):** Build 1 Tenant foundation -> Build 2 Menu -> Build 3 Order Core -> Build 4 Storefront <-> Order Core.
2. The steps before and around it follow MASTER-PLAN §6 (L83-95), reproduced here as of 2026-10-01:

| Step | What | Owner | Blocks on |
|---|---|---|---|
| S0 | **Decisions baseline** (done 2026-09-29): ADRs (new + legacy triage), architecture amendment proposal, open-questions register, superseded-docs list, memory cleanup. | Claude | nothing |
| S1 | Gabriel's review (done 2026-10-01): accepts ADRs and the architecture amendment; answers register Q-01..Q-10; pushes the frontend `.claude/` (FE `c4a7309`). Still open in the register: Q-30 and Q-86. | Gabriel | S0 |
| S2 | Docs skeleton (done 2026-09-29; the parts that waited on Q-04, Q-05, Q-09, Q-10 done 2026-10-01): `docs/README.md` (map + current phase), `docs/roadmap.md`, ADRs, glossary seed, doc templates (with Status and Open questions), superseded banners on legacy docs; both `CLAUDE.md` files rewritten to match. | Claude | S1 |
| S3 | Readiness tooling (done 2026-10-01): version frontend `.claude/`, port/remove the PowerShell hook, `permissions.deny` git rules, SessionStart setup, frontend lint baseline, Vitest, CI running today's checks in both repos. | Claude (+ Gabriel for S1 push) | S2 |
| S4 | Cross-cutting design: API conventions doc, finalize the Edge-ready constraints (ADR-0003), then **Tenancy & Identity** contract with Gabriel's question batch. | Claude + Gabriel | S2 |
| S5 | Scope of this run (short PRD) + NFR page + audit backlog triage. Must be Reviewed before Build 1 (Q-08 b). | Claude + Gabriel | S4 |
| Plane | Plane setup (Q-83 b), following the runbook `docs/ops/plane-setup.md` (Draft). On Plane Cloud's free plan since 2026-10-06, so it is sign-up, one workspace, one project and a token (ADR-0017). Docs and readiness work do not wait for it. | Gabriel (+ Claude: runbook) | nothing |
| **Build 1** | Tenant foundation (acceptance includes: ArchUnit tenant rule, cross-tenant tests, contract drift test, seed data, tenant id in logs). | Claude | Gate §7, including S5 Reviewed (Q-08 b) and the Plane setup (Q-83 b) |
| Build 2–4 | Menu → Order Core → Storefront ↔ Order Core. Each contract is reviewed before its step. Small existing-feature fixes from the audit ride alongside; so do Phase 11's small polish items, with Build 3-4 (Q-82 a). | Claude | their contract |

3. **Readiness gate before Build 1** (MASTER-PLAN §7, L97-107):
   - ADRs and the amended architecture are Accepted by Gabriel (ADR-0000..ADR-0019; an ADR added later, such as ADR-0020, gates the build step that depends on it, here Build 4, not Build 1; Claude's reading, 2026-10-01, Gabriel may override).
   - Docs live in one agreed place with a "where to look" map; legacy docs carry superseded banners.
   - Both `CLAUDE.md` files match the current decisions.
   - Frontend `.claude/` is in git; no Windows-only hooks; git rules enforced by `permissions.deny`.
   - A fresh cloud session can run the full verification in both repos.
   - CI runs `./gradlew test` and `yarn lint && yarn build && yarn test` on the working branches, green.
   - API conventions + Tenancy & Identity contract are Reviewed. The Menu and Order Core contracts gate their own steps, not this one.
   - Added 2026-10-01 (Q-08 b): S5's output (scope of this run, NFR page, audit triage) is Reviewed. S5 joins the gate.
   - Added 2026-10-01 (Q-83 b): Plane set up (ADR-0017; runbook `docs/ops/plane-setup.md`). It is the first thing done before work on the app starts; docs and readiness work do not wait for it. Gabriel: "other I already create the oracle free tier with always free account, its fresh and ready to begin the setup, so this is the first thing we do before begin working on the actual app." Where Plane runs changed on 2026-10-06 to Plane Cloud's free plan (ADR-0017); the gate item itself is unchanged.
4. **Later** (not scheduled this run): QR/table, waiter, KDS on Edge, POS, tablets, Edge build. Added 2026-10-01: Phase 10 comandas (seating, `/table-menu` cart, call waiter and request bill, waiter mobile ordering), with QR/table and waiter (Q-81 a). The Edge is built when Gabriel decides (Q-80 b, ADR-0003).
   - Phase 11 polish is not Later: its small items (one status-color map for KDS and Orders, elapsed time, Home card hierarchy, empty states) ride alongside Build 3-4 as existing-feature fixes (Q-82 a, 2026-10-01).
5. **Out** (not this run): PSP, automatic WhatsApp, fiscal, marketplaces, billing.
6. Provenance:
   - Rows Build 1-4 are D-7 (Accepted).
   - Rows S0-S5 and the slimmed gate are Claude's plan in MASTER-PLAN §6-7, built on D-13 and D-14. Gabriel reviews them in S1. The simplifications behind them (MASTER-PLAN §4), including the slim gate (row 3.8), were accepted with Q-03 on 2026-09-29. Build 1 waits for S5 to be Reviewed (Q-08 b, 2026-10-01). The Plane row is Gabriel's (Q-83 b, 2026-10-01).

## Consequences
- Never: start Build 1 code before every gate item in §7 is true. Docs and readiness tooling (S0-S5) may proceed before the gate (D-13).
- Never: start Build 2, 3 or 4 before its contract (Menu, Order Core, Storefront) is Reviewed. Only Gabriel sets Reviewed (ADR-0016).
- Never: schedule, design in detail or build Later or Out items this run. New ideas go to the Later list in `docs/roadmap.md` (ADR-0017).
- Never: follow the superseded orders listed above (architecture L320-339, legacy backend phases 2-6, the frontend roadmap's "What's next", the ai-ready plan §4-5).
- Must: treat the ArchUnit tenant rule, cross-tenant tests, contract drift test, seed data and tenant id in logs as acceptance criteria of Build 1, not as readiness items (MASTER-PLAN §4 L64, §6 L93).
- Must: fold small existing-feature fixes from the audit into the Build step they touch, as triaged in S5.
- Must: keep the existing KDS screen cloud-only until an Edge exists (pm-tool L117).
- Must: when MASTER-PLAN §6-7 changes, update this ADR. Where they disagree, MASTER-PLAN wins, including over the 2026-10-01 additions in Decision 3 and 4 and over the table reproduced in Decision 2.

## Open questions
- Q-01 docs location, Q-02 never-merge scope, Q-03 the §4 simplifications: answered 2026-09-29 (PF `planning/MASTER-PLAN.md` §8a; ADR-0011, ADR-0013). S1, S2 and S3 are done (2026-10-01).
- Where do existing features outside Build 1-4 land? Tables/QR, delivery zones, restaurant settings, track-order, KDS, Administration, Home, Analytics, i18n and Notifications are placed in the S5 scope-of-run page (MASTER-PLAN §5 L76). Restaurant settings and delivery zones: Q-30.
- Q-53: answered 2026-10-01, a: a minimal Table (id, name) enters Order Core, so staff dine-in orders are part of Build 3; QR self-ordering stays Later.
- Q-81: answered 2026-10-01, a: Phase 10 comandas are Later, with QR/table and waiter. Q-82: answered 2026-10-01, a: Phase 11's small polish items ride alongside Build 3-4.
- Q-08: answered 2026-10-01, b: S5 must be Reviewed before Build 1; S5 joins the gate (Decision 3). This resolves the earlier reviewer note that §6 placed S5 before Build 1 while §7 did not require it.
- Q-83: answered 2026-10-01, b: the Plane setup comes first, before Build 1 (Decision 3). On 2026-10-06 Gabriel moved Plane to Plane Cloud's free plan, which closes HTTPS exposure and off-box backups as moot; `EOS-<n>` ids stay pending as an answer, with Claude's default in the runbook (ADR-0017).

## Sources
Legend: PF = `/mnt/project-files/`.
- PF `planning/MASTER-PLAN.md` §1 (L15), §3 D-7 (L40), D-13 (L46), D-14 (L47), §4 (L64, L68), §5 (L76), §6 (L83-95), §7 (L97-107), §8 (L111-120)
- `planning/open-questions.md` Q-08, Q-53, Q-80, Q-81, Q-82, Q-83 (Gabriel's answers of 2026-10-01, project thread, 2026-10-01T16:25Z); Q-30 (open)
- PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md` L320-341
- PF `planning/pm-tool-recommendation.md` L42, L117, L119-131
- PF `planning/ai-ready-development-plan.md` L6, L72-91
- PF `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L150-166
- PF `RESTAURANT-OPS-ROADMAP.md` L209-221
