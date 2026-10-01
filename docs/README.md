# Enterprise Order Suite docs: read first

- Status: Draft
- Updated: 2026-10-01 (Gabriel's register answers of 2026-10-01; S1, S2 and S3 done). Earlier: 2026-09-29 (S2, docs skeleton)
- Maintained by: Claude, in this repo on `feature/ai-agent` (ADR-0011). Only Gabriel sets Reviewed (ADR-0016).
- Related: `planning/MASTER-PLAN.md`, `adr/README.md`, `roadmap.md`, `planning/open-questions.md`

This folder is the one shared place for project docs (ADR-0011): backend repo `gtavaresdevs/enterprise-order-suite`, folder `docs/`, branch `feature/ai-agent`. Agents in both repos start here. Where this file disagrees with `planning/MASTER-PLAN.md` (MP), MP wins; raise the conflict.

## 1. What this is and where we are

- Product: Enterprise Order Suite (Restaurant Ops), a multi-tenant restaurant operations SaaS: one deployment serves every restaurant, the restaurant is the tenant (ADR-0001). One Restaurant Operational Core (one Order model with channel and source, one Menu) serves every interface; the React frontend is only the first consumer (ADR-0002).
- Repos and working branches (never read or cite `main`, ADR-0013):
  - backend: this repo, Spring Boot, `feature/ai-agent`;
  - frontend: `gtavaresdevs/enterprise-order-suite-frontend`, app in `order-ui/`, `Claude-Assisted-Development`.
- People: Gabriel (solo developer; owns every product decision) and Claude agents.
- Current phase (MP §1): **S1, S2 and S3 done** (S1 and S3 on 2026-10-01; S2's waiting parts on 2026-10-01). Next: S4 (API conventions, Tenancy & Identity). **The Plane setup on Gabriel's Oracle VPS comes before Build 1** (Q-83 b; runbook `ops/plane-setup.md`). Gabriel answered his batch on 2026-10-01; **still open: Q-30 and Q-86**. S0 done 2026-09-29.
- **No feature code for the new architecture until every item of the readiness gate (MP §7, mirrored in `roadmap.md`) is checked** (ADR-0015). Docs and readiness tooling (S2-S5) proceed before the gate.
- After the gate: Build 1 Tenant foundation -> Build 2 Menu -> Build 3 Order Core -> Build 4 Storefront <-> Order Core (ADR-0007). Each build step waits for its contract to be Reviewed (ADR-0016). Since 2026-10-01 the gate also requires S5 Reviewed (Q-08 b) and Plane set up on Gabriel's Oracle VPS (Q-83 b).
- Step status: `roadmap.md`.

## 2. Where to look

Read the rows in order. "Not written" = the doc is planned (MP §5-§6) but does not exist yet; do not invent its content. Legacy docs are listed only with their status; per-section detail is in `planning/superseded-docs.md`, per-decision verdicts in `adr/0000-legacy-decisions-triage.md`. Never apply a legacy decision without its ADR-0000 verdict.

| Task | Read, in order | Legacy docs (status) |
|---|---|---|
| Any task | 1. this README; 2. `planning/MASTER-PLAN.md`; 3. `adr/README.md`, then every ADR the task touches; 4. `roadmap.md`; 5. `planning/open-questions.md` rows for the area; 6. `glossary.md` | All legacy docs: see `planning/superseded-docs.md` before relying on one. |
| Architecture overview | `architecture/RESTAURANT-OPS-ARCHITECTURE.md` (Gabriel's text; decision status Accepted with amendments, Q-04, 2026-10-01). `architecture/ARCHITECTURE-AMENDMENTS-PROPOSED.md` records amendments A-01..A-15, accepted and applied 2026-10-01. Where the text still differs from MP or an ADR, MP and the ADRs win; raise it. | Architecture "Proposed implementation order" (L320-341): superseded by ADR-0007. |
| Tenancy, restaurants, users, roles | ADR-0001, ADR-0008, ADR-0009, ADR-0018; register group 2 (Q-16..Q-31); Tenancy & Identity contract (S4, not written; will live in `architecture/`). | BE `docs/superpowers/specs/2026-09-25-restaurant-ops-phase-1-auth-design.md`: in force for D16-D24; its "Phase 6" now means the auth cleanup inside Tenant foundation; its "Contract work" is superseded. BE `docs/superpowers/specs/2026-09-24-restaurant-ops-phase-0-foundation-design.md`: partly superseded (D9, D10a, "Contract work"); D12-D15 in force. FE `order-ui/docs/superpowers/specs/2026-09-09-restaurant-ops-redesign-design.md`: partly superseded (single-tenant role mapping, ADR-0001). |
| Menu | ADR-0002, ADR-0014, ADR-0009; register group 4 (Q-41..Q-46); `glossary.md`; Menu contract (not written; blocks Build 2). | FE `2026-09-09-restaurant-ops-redesign-design.md`: partly superseded. FE `order-ui/docs/superpowers/specs/2026-09-16-business-rules-master-en.md`: status snapshot of 2026-09-16, re-verify every rule; "[NEW]" rules are unconfirmed. FE manifest 0.4.0: frozen, seeds drafts only. |
| Orders, Order Core, KDS | ADR-0002, ADR-0003 (C1-C3), ADR-0004, ADR-0005, ADR-0009 (human order number), ADR-0019; register group 5 (Q-47..Q-64); Order Core contract (not written; blocks Build 3). | FE `2026-09-09-restaurant-ops-redesign-design.md`: partly superseded (order model, statuses and `PayLater` decided 2026-10-01: Q-47 a, Q-49 a, Q-58 a). FE business-rules master: snapshot. BE phase-0 design D10 `businessDate`: Kept (ADR-0000, on Q-28 a and Q-21 a); the Order Core contract records it. |
| Storefront, public pages, delivery | ADR-0001 (public endpoints resolve their restaurant from the request), ADR-0014, ADR-0005, ADR-0006, ADR-0020 (PIX prepayment, Proposed); register group 6 (Q-65..Q-71, Q-86), Q-20, Q-31, Q-27, Q-30; `planning/superseded-docs.md` §4 S-1 (salvaged `/public/*` hardening); Storefront contract (not written; blocks Build 4). | FE `order-ui/docs/superpowers/specs/2026-09-15-core-package-br-i18n-ux-design.md`: partly superseded (i18n, BR formatting, delivery zones as a bairro list still valid; PIX "pay now", PSP, browser-stored settings superseded). FE `order-ui/docs/superpowers/plans/2026-09-16-restaurant-ops-phase9-delivery-whatsapp.md`: executed plan, historical. |
| API contract change (any endpoint, request or response shape) | ADR-0010, `api/drafts/README.md`, ADR-0011; register group 3 (Q-32..Q-40), Q-76; API conventions doc (S4, not written). Change shapes only in this repo. `api/openapi.yaml` does not exist until Build 1. | BE `docs/contracts/`: superseded, frozen 0.3.0 snapshot, never re-sync. FE manifest 0.4.0: frozen, read-only, seed only. Backend skill `api-contract-sync`: retired, do not invoke. BE `docs/superpowers/specs/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md`: partly superseded (contract ownership reversed; phases 2-6 replaced by ADR-0007). |
| Database, migration | ADR-0009 (ULID keys on every table; one Flyway re-baseline before launch; window closes at launch, which is the first pilot restaurant, Q-32 a), ADR-0008, ADR-0001 (restaurant id on restaurant-owned tables); register Q-25, Q-32, Q-35. | Backend skill `flyway-migrations` and agent `flyway-migration-author`: "never edit an applied migration" holds except the single pre-launch re-baseline in ADR-0009; the enum-rename-as-data-migration section does not apply to pre-launch legacy data (`planning/superseded-docs.md` B5). |
| Payments, fiscal, marketplace, WhatsApp | ADR-0005 (record-only; integrate, never build) and ADR-0006 (PSP and automatic WhatsApp are Out this run; manual `wa.me` links stay); ADR-0007 Out list (fiscal, marketplaces, billing). No payment integration this run. Payment record: Q-55, Q-56, Q-58 (answered 2026-10-01). Online payment: Q-65 (answered 2026-10-01) and ADR-0020 (Proposed: storefront PIX prepayment with a static PIX code, confirmed by staff recording the payment; Build 4). | FE `2026-09-09-restaurant-ops-redesign-design.md` in-app payment and WhatsApp on status change: superseded. FE i18n spec PIX "pay now" and the PSP package: superseded. FE `order-ui/docs/superpowers/plans/RESTAURANT-OPS-ROADMAP.md` "Blocked" rows: now Out. |
| Offline, Restaurant Edge | ADR-0003 (Edge optional, not built this run; constraints C1-C6), ADR-0004 (offline = orders only), ADR-0019; AMD A-08, A-09; register Q-38, Q-60, Q-80. Full Edge docs wait for a Reviewed Order Core contract. | Architecture §2-§4, §11-§17, §24 describe an Edge at every restaurant: follow ADR-0003/0004 where they differ (the amendments, applied 2026-10-01 per Q-04, align the text). `planning/pm-tool-recommendation.md` per-restaurant Edge: superseded. |
| Owner customization, restaurant settings | ADR-0014 (bounded choices; typed backend-owned settings schema; no custom CSS or HTML), ADR-0001 (no restaurant values in deployment config), ADR-0018; register Q-27, Q-30, Q-44, Q-66, Q-67; settings and notification screens: `planning/proposals/settings-and-notifications.md` (Draft, Q-24). | FE i18n spec `PreferencesState` restaurant settings in the browser: superseded by ADR-0014. Architecture §9 open-ended customization: follow ADR-0014 (amendment A-06 applied 2026-10-01). |
| Git, workflow, doc changes | ADR-0013 (commit straight to the working branch and push; never merge), ADR-0011, ADR-0016, ADR-0017 (update `roadmap.md` in the same commit; Plane runbook `ops/plane-setup.md`), ADR-0015; `templates/`; register Q-72, Q-83. | FE `RESTAURANT-OPS-ROADMAP.md` "merge to Claude-Assisted-Development locally": superseded by ADR-0013. Executed plans in `docs/superpowers/plans/` of both repos: historical, never re-execute. |
| Tests, tooling, CI | ADR-0015, ADR-0013; `roadmap.md` S3; register group 7 (Q-72..Q-77). | `planning/ai-ready-development-plan.md`: superseded where it differs from MP. FE `order-ui/docs/superpowers/specs/2026-09-15-dev-tooling-workflow-design.md`: its "`.claude/` is git-ignored, machine-local" premise is out of date; Q-06 and Q-07 (2026-10-01) versioned the whole folder, Graphify, ponytail and wshobson included (FE `c4a7309`). |
| A question only Gabriel can answer | `planning/open-questions.md`: check existing rows first and extend one instead of duplicating. A new row takes the next free id after the highest existing one, in the group of the step it blocks. Ask Gabriel in his language, record the answer in English (ADR-0012). Never record an answer he did not give. | none |
| A legacy decision or doc | `adr/0000-legacy-decisions-triage.md` (per decision), `planning/superseded-docs.md` (per doc and section, with banner text). | as listed there |

## 3. What already exists

Condensed from MP §2 (2026-09-29; Claude tooling row updated 2026-10-01). Document it; do not rebuild it. Plans listed here are executed plans: historical records, never re-executed (`planning/superseded-docs.md` F8, B11).

| Area | State | Plans and specs (repo path) |
|---|---|---|
| Frontend phases 0-9 | Done: unified Order/MenuItem types; Menu (categories, sizes, add-ons, 86 toggle); Tables + QR; public `/table-menu`; Orders + KDS on one order stream; Administration (team, roles, audit log) on the real backend; Home + Analytics; i18n EN/PT-BR (15 namespaces); PIX/Card/Cash picker; delivery zones; Pickup/Delivery checkout; `/track-order`; `wa.me` links. | FE `order-ui/docs/superpowers/plans/`: `2026-09-10-restaurant-ops-phase0-foundation-types.md`, `2026-09-10-restaurant-ops-phase1-menu-tables.md`, `2026-09-10-restaurant-ops-phase2-retire-duplicates.md`, `2026-09-10-restaurant-ops-phase3-public-menu-view.md`, `2026-09-12-restaurant-ops-phase4-orders-kds-unified-model.md`, `2026-09-13-restaurant-ops-phase5-administration.md`, `2026-09-13-restaurant-ops-phase6-home-analytics.md`, `2026-09-15-core-package-phase7-i18n-foundation.md`, `2026-09-15-core-package-phase8-payment-method.md`, `2026-09-16-restaurant-ops-phase9-delivery-whatsapp.md`; history: `RESTAURANT-OPS-ROADMAP.md` (superseded as entry point) |
| Frontend auth | Done 2026-09-25: HttpOnly refresh cookie, cross-tab single-flight refresh, real logout. | FE `order-ui/docs/superpowers/plans/2026-09-25-auth-refresh-cookie-cross-tab.md` |
| Frontend data | Real backend: auth, profile, administration. Everything else is mock (menu, tables, orders, KDS, storefront, track-order, home, analytics). Owner branding, WhatsApp number and delivery zones sit in the owner's own `localStorage`, so customers never see them. | none |
| Backend phases 0-1 | Done: UTC instants (D15), order editability (D13), auth rework (D16-D24), orders authorization fix; 213 tests green before phase 1. | BE [`2026-09-24-restaurant-ops-phase-0-foundation.md`](superpowers/plans/2026-09-24-restaurant-ops-phase-0-foundation.md), [`2026-09-25-restaurant-ops-phase-1-auth.md`](superpowers/plans/2026-09-25-restaurant-ops-phase-1-auth.md), [`2026-09-20-orders-authorization-fix.md`](superpowers/plans/2026-09-20-orders-authorization-fix.md), [`2026-09-20-test-failures-and-audit-followups.md`](superpowers/plans/2026-09-20-test-failures-and-audit-followups.md); specs [`2026-09-24-restaurant-ops-phase-0-foundation-design.md`](superpowers/specs/2026-09-24-restaurant-ops-phase-0-foundation-design.md), [`2026-09-25-restaurant-ops-phase-1-auth-design.md`](superpowers/specs/2026-09-25-restaurant-ops-phase-1-auth-design.md) |
| Backend domain | Legacy B2B shape: `/orders` (PENDING..CANCELLED, `customerId`), `/products`. No restaurant or tenant, menu, tables, zones, settings or `/public/*` endpoints. Integer `IDENTITY` ids. Replaced, not evolved (ADR-0008). | BE [`2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md`](superpowers/specs/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md) (partly superseded) |
| Claude tooling | Backend (S3, 2026-09-29): skills and agents aligned with the ADRs, two Node hooks (security-file confirmation, SessionStart environment check), `permissions.deny` git rules, CI running `./gradlew test`. Frontend: Vitest and CI running `yarn lint && yarn build && yarn test`; lint is clean. Frontend `.claude/` versioned by Gabriel on 2026-10-01 (FE `c4a7309`, Q-06 a, Q-07): agents, skills (including graphify), the Node lint/typecheck hook, `settings.json` (enables the ponytail and wshobson plugins) and `.claude/CLAUDE.md`; four machine-only files stay ignored. Its `permissions.deny` git rules were added on 2026-10-01 (FE `30bc172`). | BE [`2026-09-20-claude-tooling-install.md`](superpowers/plans/2026-09-20-claude-tooling-install.md) (Task 14 folded into the SessionStart check, Task 2 dropped: Q-75 a); FE `order-ui/docs/superpowers/specs/2026-09-15-dev-tooling-workflow-design.md` |
| Known bugs | Audit Stage 2 never started: storefront drops size and add-on choices (cart merges by item), `PayLater` hardcoded, `$` hardcoded in 3 places, status selector at creation, no street address, B2B Notifications content. About half of the older audit list is already fixed. Triage: S5. | FE `order-ui/docs/superpowers/specs/2026-09-16-business-rules-master-en.md` (snapshot); FE `RESTAURANT-OPS-ROADMAP.md` audit section |

FE files: `https://github.com/gtavaresdevs/enterprise-order-suite-frontend/blob/Claude-Assisted-Development/<path>`, or a checkout of that branch.

## 4. Path conventions

Files in `adr/`, `architecture/` and `planning/` were drafted in S0 in the Claude project's shared folder and copied here on 2026-09-29. Later edits: the superseded banners on `planning/pm-tool-recommendation.md` and `planning/ai-ready-development-plan.md` (`planning/superseded-docs.md` P1, P2), the updates after Q-01..Q-03 were answered (register statuses, ADR statuses and the literal docs path in `planning/superseded-docs.md`), and the updates after Gabriel's 2026-10-01 answers. They keep their original citations. Resolve them as follows.

| Cited as | Means |
|---|---|
| `PF <path>` or `/mnt/project-files/<path>` | the Claude project's shared folder (S0 drafts). Not a repo. |
| PF `planning/X` | `docs/planning/X` (this repo) |
| PF `adr/X` | `docs/adr/X` |
| PF `architecture/X` | `docs/architecture/X` |
| PF `2026-*.md` (legacy flat copies) | the same filename in BE `docs/superpowers/{specs,plans}/` or FE `order-ui/docs/superpowers/{specs,plans}/` (table below) |
| PF `RESTAURANT-OPS-ROADMAP.md` | FE `order-ui/docs/superpowers/plans/RESTAURANT-OPS-ROADMAP.md` |
| PF `2026-09-14-backend-integration-manifest.openapi.yaml`, `MF` | FE `order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml` (manifest 0.4.0, frozen) |
| PF `backend-integration-manifest.openapi.yaml`, PF `README.md` | BE `docs/contracts/backend-integration-manifest.openapi.yaml` (0.3.0 snapshot, frozen) and `docs/contracts/README.md` |
| `MP` | `docs/planning/MASTER-PLAN.md` |
| `ARCH`, `AMD` | `docs/architecture/RESTAURANT-OPS-ARCHITECTURE.md`, `docs/architecture/ARCHITECTURE-AMENDMENTS-PROPOSED.md` |
| `BR` | FE `order-ui/docs/superpowers/specs/2026-09-16-business-rules-master-en.md` |
| `BE`, `FE` | backend repo (`feature/ai-agent`), frontend repo `order-ui/` (`Claude-Assisted-Development`) |
| `/tmp/claude/memory/...` | Claude session memory. Not in any repo; not required reading. |
| `D1`..`D24` vs `D-1`..`D-16` | legacy design-doc decisions (cite with their doc) vs MP §3 decisions (ADR README "Conventions") |

Legacy flat copies by repo:
- BE `docs/superpowers/specs/`: `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md`, `2026-09-24-restaurant-ops-phase-0-foundation-design.md`, `2026-09-25-restaurant-ops-phase-1-auth-design.md`.
- BE `docs/superpowers/plans/`: `2026-09-20-claude-tooling-install.md`, `2026-09-20-orders-authorization-fix.md`, `2026-09-20-test-failures-and-audit-followups.md`, `2026-09-24-restaurant-ops-phase-0-foundation.md`, `2026-09-25-restaurant-ops-phase-1-auth.md`.
- FE `order-ui/docs/superpowers/specs/`: `2026-09-09-restaurant-ops-redesign-design.md`, `2026-09-14-backend-integration-manifest.openapi.yaml`, `2026-09-15-core-package-br-i18n-ux-design.md`, `2026-09-15-dev-tooling-workflow-design.md`, `2026-09-16-business-rules-master-en.md` (pt-BR companions `2026-09-16-regras-de-negocio.md`, `2026-09-16-fluxo-de-dados.md` have no PF copy).
- FE `order-ui/docs/superpowers/plans/`: every other `2026-09-1*` file, `2026-09-15-dev-tooling-workflow-design.md` (also in specs), `2026-09-25-auth-refresh-cookie-cross-tab.md`, `RESTAURANT-OPS-ROADMAP.md`.

Line numbers:
- Line numbers cited in ADRs and planning files refer to the 2026-09-29 versions: BE @ `af2634e`, FE @ `14a3cfd`, PF files as of 2026-09-29. Re-check before editing a cited line.
- "MP L<n>" citations point into `planning/MASTER-PLAN.md` as of S0; MP has been edited since, so find the cited text by section (§n) first.

Layout of `docs/` (ADR-0011):

```text
docs/
  README.md          this map (read first)
  roadmap.md         interim tracker (ADR-0017)
  glossary.md        domain terms, legacy->new map, PT-BR UI vocabulary
  adr/               decision records; index and rules in adr/README.md
  architecture/      Gabriel's architecture + amendment proposal; contract docs land here (S4 onward)
  planning/          MASTER-PLAN, open-questions register, superseded-docs list, S0 evidence; proposals/ (Claude proposals for Gabriel's review)
  ops/               runbooks (Plane setup)
  api/drafts/        design-first contract drafts (ADR-0010); api/openapi.yaml arrives in Build 1
  templates/         doc templates (Status line + Open questions, ADR-0016)
  contracts/         superseded 0.3.0 snapshot (ADR-0010); never re-sync
  superpowers/       legacy backend specs and executed plans; new backend plans go in superpowers/plans/
```

## 5. Rules

- The backend owns the docs and the API contract and keeps them updated: every shared-doc change is a commit in this repo on `feature/ai-agent` (ADR-0010, ADR-0011, ADR-0013).
- The frontend reads the docs from this repo: frontend sessions attach a read-only checkout of this repo on `feature/ai-agent`. The frontend repo never holds an edited or diverging copy of a shared doc. It annotates a shared doc only when necessary and only after Gabriel agreed to that annotation beforehand; where an agreed annotation lives is not decided (ADR-0011 point 4). How frontend CI gets the API spec: Q-76 a, a vendored copy of `api/openapi.yaml` synced by a script from a pinned backend commit (ADR-0010 Decision 7).
- Docs have no fixed "done" (ADR-0016). Every doc carries `- Status: Draft | Reviewed | Ready` near the top and an `## Open questions` block. Only Gabriel sets Reviewed; record it as "Reviewed by Gabriel on YYYY-MM-DD". A build step uses only a Reviewed (or Ready) doc. A change that alters a decision or adds a blocking question sets the doc back to Draft (Claude proposal, Q-12). Gabriel signals Reviewed with a message in the project thread ("Reviewed: <doc>"), which Claude records (Q-09 a, 2026-10-01).
- ADR statuses are separate: Proposed, Accepted, Superseded by ADR-XXXX. Only Gabriel moves an ADR to Accepted (`adr/README.md`).
- Never renumber or reuse Q-ids or ADR numbers. Never delete a register row or an ADR; change its status.
- When a register question is answered, update every ADR and doc that cites it in the same commit (`planning/open-questions.md`, "How to use this file").
- Update `roadmap.md` in the same commit as the work that changes a step's status (ADR-0017).
- New docs start from `templates/`. English only; questions to Gabriel in his language (ADR-0012).
- These repo files are the shared docs (ADR-0011 point 1). The PF copies of `adr/`, `architecture/` and `planning/` stay as working copies under a pointer saying this repo wins (Q-15, decided by Claude): edit the file here first, then mirror it to PF. If a PF copy differs from the file here, the file here wins; report the difference.

## Open questions

- Still open in the register: Q-30 (where restaurant settings and delivery zones are built; not in Gabriel's 2026-10-01 batch) and Q-86 (clarifies his Q-70 pickup answer, which ends mid-sentence).
- Waiting for Gabriel's review: ADR-0020 (storefront PIX prepayment, Proposed), `planning/proposals/settings-and-notifications.md` (Draft, Q-24), `ops/plane-setup.md` (Draft, Q-83; it asks HTTPS exposure, off-box backups and `EOS-<n>` ids).
- Q-72: Gabriel turns on branch protection for `main` in both repos; not yet confirmed done.
- Decided by Claude, Gabriel may override: Q-12 (Draft and Ready definitions), Q-13 (when the amended architecture counts as Accepted), Q-14 (business-rules master stays in FE until S5; pt-BR banner wording), Q-15 (PF copies), all 2026-09-29; Q-26 (where a request's restaurant comes from), 2026-10-01, on Gabriel's delegation.
- ADR-0011 point 4: where an agreed frontend annotation lives (local to ADR-0011).
