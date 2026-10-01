# Architecture decision records (ADRs)

- Status: Draft (S0 decisions baseline; S1 review: Q-01..Q-03 answered 2026-09-29, Q-04..Q-10 answered 2026-10-01)
- Date: 2026-09-29
- Updated: 2026-10-01 (Gabriel's register answers of 2026-10-01; ADR-0000 Accepted; ADR-0020 added as Proposed; Conventions: register answers that change an Accepted ADR in place)
- Maintained by: Claude. Only Gabriel moves an ADR to Accepted (ADR-0016; MASTER-PLAN §7 L99).
- Location: drafted in `/mnt/project-files/adr/` using the final layout, so the folder moves unchanged to the backend repo `docs/adr/` (ADR-0011; Q-01 answered 2026-09-29; MASTER-PLAN §6 S0 L87).

## Rule for every agent

**Check the ADRs before proposing anything that contradicts them.**

- Before proposing or designing something, read this index and the ADRs it touches.
- If the idea contradicts an Accepted ADR, do not build it and do not quietly work around the ADR. Say which ADR it contradicts and why, and ask Gabriel. Only Gabriel changes an Accepted ADR: by accepting a new ADR, after which the old ADR's status becomes "Superseded by ADR-XXXX", or by a register answer recorded in place (Conventions).
- If the idea touches a Proposed ADR (or a part of an Accepted ADR labelled Proposed), write no new code that depends on either answer. Cite the register question (Q-xx) that decides it.
- Never apply a legacy decision straight from an old doc. Look up its verdict in ADR-0000 first.
- Where an ADR disagrees with `planning/MASTER-PLAN.md`, MASTER-PLAN wins (MASTER-PLAN L3-6). Raise the conflict; do not pick a side silently.

## Index

Status meanings:
- **Accepted**: Gabriel decided it (MASTER-PLAN §3 D-1..D-16, or his answer to a register question, for example Q-03 on 2026-09-29).
- **Proposed**: Claude's recommendation that Gabriel has not confirmed.
- **Superseded by ADR-XXXX**: no longer applies; follow the named ADR.

Since Q-03 was answered (2026-09-29, all MASTER-PLAN §4 simplifications accepted), no Accepted ADR has a part labelled Proposed, except ADR-0014's PIX key setting, which belongs to ADR-0020. Details still open are named in the Status column by register id; ADR-0011's annotation location is a proposal inside that ADR. ADR-0020 (2026-10-01) is Proposed: Claude decided it under Gabriel's delegation in Q-65, and it awaits his confirmation.

| ID | Title | Status | Summary |
|---|---|---|---|
| [ADR-0000](0000-legacy-decisions-triage.md) | Legacy decisions triage | Accepted (Gabriel, 2026-10-01, Q-05 a) | Gives a verdict (Kept, Superseded or Pending) for each legacy decision: backend D1-D24, the frontend 2026-09-09 decisions, the manifest `x-open-decisions` and the other legacy user decisions. |
| [ADR-0001](0001-shared-multi-tenant-saas.md) | One shared multi-tenant SaaS | Accepted (D-1; tenancy details: Q-16..Q-22, Q-25..Q-28, Q-31, 2026-10-01). Open: Q-30 | One deployment and one database serve every restaurant. Restaurant-owned data is scoped to the caller's restaurant, and a request with no restaurant context fails closed. The Tenancy & Identity contract defines the mechanism. |
| [ADR-0002](0002-restaurant-operational-core.md) | Restaurant Operational Core: one Order model and one Menu for every interface | Accepted (D-2) | Every interface consumes one Order model (with channel and source) and one Menu. Adapters call application services and never hold channel-specific rules. Channel and source values: Q-49 a (2026-10-01): channel DINE_IN, TAKEAWAY or DELIVERY; source STOREFRONT, PHONE, WAITER, POS, QR or TABLET. |
| [ADR-0003](0003-restaurant-edge-optional-and-constraints.md) | Restaurant Edge is optional; Edge-ready constraints on the core | Accepted (D-3; C1, C3, C4: Q-03 row 3.4, 2026-09-29; when the Edge is built: Q-80 b, 2026-10-01, Gabriel decides, an optional paid feature) | The Edge is not built this run. Constraints C1-C6 bind the core now: client ULIDs, idempotent create, forward-only status, cloud-owned menu and config, no payment queue, Edge optional. |
| [ADR-0004](0004-offline-scope-orders-only.md) | Offline scope is orders only | Accepted (D-4; retry key = client id: Q-03 row 3.4; mechanism: Q-38 a, 2026-10-01) | Offline means orders (waiter to kitchen). Without an Edge, the app shows that the connection is lost, keeps unsent orders on the device and retries them without duplicates. Connectivity backup is the customer's infrastructure. |
| [ADR-0005](0005-payments-record-only.md) | Payments are record-only; integrate, never build | Accepted (D-5; payment record and statuses: Q-55 a, Q-56 a, Q-58 a, 2026-10-01). Storefront PIX prepayment: ADR-0020 (Proposed) | The app never processes, authorizes or queues payments; it records results against the order. It integrates with existing payment, fiscal and marketplace systems and never builds them. |
| [ADR-0006](0006-this-run-scope-psp-and-auto-whatsapp-out.md) | This run improves existing features; PSP and automatic WhatsApp are out | Accepted (D-6) | This run adds no payment, messaging, fiscal or marketplace integration. A PSP and automatic WhatsApp are Out. Manual `wa.me` links stay. |
| [ADR-0007](0007-build-order.md) | Build order: Tenant foundation, Menu, Order Core, Storefront to Order Core | Accepted (D-7; slim gate: Q-03 row 3.8; S5 joins the gate before Build 1: Q-08 b, 2026-10-01; Plane setup first: Q-83 b) | Build order: Build 1-4 after the MASTER-PLAN §7 readiness gate. Each build step waits for its contract to be Reviewed. Lists what is Later and what is Out. |
| [ADR-0008](0008-pre-launch-reshape-allowed.md) | Pre-launch: schema and API may be reshaped freely | Accepted (D-8; end of the window: first pilot restaurant, Q-32 a, 2026-10-01) | There is no production data, so breaking schema and API changes need no data migration or compatibility layer. Legacy `/orders` and `/products` are replaced, not evolved. |
| [ADR-0009](0009-ulid-primary-keys-everywhere.md) | ULID primary keys on every table, pre-launch Flyway re-baseline, human order number | Accepted (Q-03 row 3.11, 2026-09-29; end of the re-baseline window: first pilot restaurant, Q-32 a, 2026-10-01) | Every table gets a ULID primary key. Flyway is re-baselined once before launch. The server generates a per-restaurant human order number. Supersedes legacy D9. |
| [ADR-0010](0010-backend-owns-api-contract.md) | The backend owns the API contract | Accepted (D-9; drift-test mechanism: Q-03 row 3.2; drafts: Q-40 a; frontend spec copy: Q-76 a; 2026-10-01) | Design drafts live in `docs/api/drafts/` (seeded from manifest 0.4.0), one file per contract area. A drift test guards the committed `docs/api/openapi.yaml`. The frontend keeps a vendored copy synced from a pinned backend commit and generates its types from it. The frontend manifest is frozen. |
| [ADR-0011](0011-docs-location.md) | Shared docs live in the backend repo `docs/` folder | Accepted (Q-01, Q-03 row 3.1, 2026-09-29; README notes: Q-10 a, 2026-10-01). Annotation location: proposal | The shared docs live in the backend repo `docs/` on `feature/ai-agent`. The frontend fetches them from there and backend commits keep them updated. The frontend annotates only if necessary and only with Gabriel's prior agreement. |
| [ADR-0012](0012-english-only-docs.md) | Documentation in English only; questions to Gabriel in his language | Accepted (D-10) | Docs, code, commits and prompts are written in English. Questions to Gabriel use his language. i18n resources and UI copy are product content. |
| [ADR-0013](0013-git-rules-push-never-merge.md) | Git rules for agents: working branches only, push allowed, never merge | Accepted (D-11; scope: Q-02 option a; `permissions.deny` and extended rules: Q-03 row 3.9; 2026-09-29). Branch protection on `main`: Q-72 a, 2026-10-01 (Gabriel's action; not yet confirmed done) | Agents read only the working branches, commit straight to them and push. They never merge (no `git merge` at all, no PR merges) and never push to `main`. The rules are enforced with `permissions.deny` in each repo's `.claude/settings.json`. |
| [ADR-0014](0014-bounded-owner-customization.md) | Bounded owner customization through a typed, backend-owned settings schema | Accepted (D-12; exact list Q-66 a, ordering settings Q-67 a, uploads Q-44 a; 2026-10-01). PIX key setting: ADR-0020 (Proposed). Settings and notification screens: Q-24 proposal (Draft) | Owners choose a logo, a cover, one brand color, a font from a list and a layout preset, plus menu category order and item photos. No custom CSS or HTML. Settings, including the ordering settings, live in a typed schema on the backend, never in the browser. |
| [ADR-0015](0015-claude-readiness-first.md) | Claude readiness before software development; Jev dropped | Accepted (D-13; slim gate: Q-03 row 3.8; S5 joins the gate: Q-08 b; frontend `.claude/` versioned: Q-06 a, Q-07; 2026-10-01) | No Build 1 code before every MASTER-PLAN §7 gate item is met and S5 is Reviewed; Build 1 also waits for the Plane setup (ADR-0017). Docs and readiness tooling may proceed before the gate. Jev is not used. |
| [ADR-0016](0016-docs-open-ended-review-status.md) | Docs are open-ended; Draft / Reviewed / Ready statuses | Accepted (D-14). Draft and Ready definitions: Q-12 (decided by Claude: a). How Reviewed is signalled: Q-09 a, 2026-10-01 | Docs have no fixed "done". Only Gabriel sets Reviewed, by posting "Reviewed: <doc>" in the project thread, and a build step needs its doc to be Reviewed. ADR statuses are separate from doc statuses. |
| [ADR-0017](0017-project-management-plane.md) | Project management: Plane CE on a free VPS, set up before Build 1 | Accepted (D-15; Q-03 row 3.10; changed by Q-83 b, 2026-10-01: setup before Build 1). HTTPS exposure, backups, `EOS-<n>` ids: pending (`docs/ops/plane-setup.md`) | Plane Community Edition is the chosen tool. Its setup on Gabriel's Oracle Always Free account is the first step before Build 1. `docs/roadmap.md` is the tracker until then. No history import and no automations. |
| [ADR-0018](0018-packages-and-pricing-deferred.md) | Packages, pricing and billing deferred | Accepted (Q-03 row 3.7, 2026-09-29; pricing and vocabulary with the first paid tier: Q-78 a, 2026-10-01) | No plans, entitlements, feature flags or billing this run. Every restaurant gets the same features. |
| [ADR-0019](0019-events-in-process-until-external-consumer.md) | Status history and in-process events; outbox and core-module split deferred | Accepted (Q-03 rows 3.5-3.6, 2026-09-29; Q-57 a, Q-61 a, Q-64 a and Edge timing Q-80 b, 2026-10-01) | Order Core writes a status history and publishes in-process events after commit; a per-restaurant notifications feed consumes them, and the KDS and tracking keep polling. The outbox waits for the first external consumer. The Gradle core-module split waits for the Edge build, which Gabriel schedules. |
| [ADR-0020](0020-storefront-pix-prepayment-manual-confirmation.md) | Storefront PIX prepayment with manual confirmation, no PSP | Proposed (Claude under Gabriel's Q-65 delegation, 2026-10-01). Pickup eligibility: Q-86 | Storefront customers pay on delivery or pickup, or prepay by PIX at checkout with a static PIX code (order total, order number) that the backend generates from the restaurant's PIX key. Staff confirm by recording the payment; the customer's "Já paguei" is never proof. Card payments and automatic PIX confirmation come later through a payment provider. |

## Related S0 files (outside this folder)

- Open-questions register: `/mnt/project-files/planning/open-questions.md`. ADRs cite its ids (`Q-01`..). Q-01, Q-02 and Q-03 were answered on 2026-09-29; Q-04..Q-10 were answered on 2026-10-01 (MASTER-PLAN §8a).
- Architecture amendment proposal: `/mnt/project-files/architecture/ARCHITECTURE-AMENDMENTS-PROPOSED.md` (A-01..A-15, register Q-04; all accepted and "Fiscal Gateway" dropped, 2026-10-01).
- Superseded and stale legacy docs (S2 banner list): `/mnt/project-files/planning/superseded-docs.md`.
- Single source of truth for the plan: `/mnt/project-files/planning/MASTER-PLAN.md`.

## Conventions

- File name: `NNNN-slug.md`. The numbers and file names are fixed so cross-references hold. Never renumber or reuse a number. A new ADR takes the next free number (ADR-0021 onward) and gets a row in the index above in the same change.
- Never delete an ADR. To reverse a decision, write a new ADR and set the old one's status to "Superseded by ADR-XXXX".
- A register answer by Gabriel that refines or changes part of an Accepted ADR is recorded in that ADR in place, as a dated block that names the question and quotes him ("Decided later (Q-NN, YYYY-MM-DD)" or "Changed by Gabriel on YYYY-MM-DD (Q-NN)"); the ADR keeps its number and status. Reversing an ADR's whole decision still takes a new ADR. Example: ADR-0017 (Q-83 b, 2026-10-01). (Claude, 2026-10-01; Gabriel may override.)
- Status values: `Accepted`, `Proposed`, `Superseded by ADR-XXXX`. Only Gabriel moves an ADR to Accepted. When a register question is answered, the ADRs that cite it are updated in the same change (`planning/open-questions.md`, "How to use this file").
- Legacy ids `D1`..`D24` (no hyphen, from old design docs) are not the same as MASTER-PLAN ids `D-1`..`D-16`. Always cite a legacy id with its doc, for example "legacy D9, phase-0 design" (ADR-0000).
- Written in English (ADR-0012). Sources are file paths with line or section numbers. Repo paths are on the working branches: backend `feature/ai-agent`, frontend `Claude-Assisted-Development` (ADR-0013).
- A disagreement with MASTER-PLAN goes in a line starting "Reviewer note:" in the ADR. The ADR still follows MASTER-PLAN.

## ADR template

Copy exactly. Keep every heading, even when its content is "none".

```markdown
# ADR-NNNN: <title>
- Status: Accepted | Proposed | Superseded by ADR-XXXX
- Date: YYYY-MM-DD
- Decided by: Gabriel | Claude (proposed) | Legacy (<doc>)
- Supersedes: <legacy decision ids/docs or "none">
- Related: <ADR ids>

## Context
## Decision
## Consequences
(what agents must do / must never do; bullet list)
## Open questions
(bullets with register ids like Q-01, or "none")
## Sources
```

## Sources

- `/mnt/project-files/planning/MASTER-PLAN.md` L3-6, §3 (L30-49), §4 (L51-68), §5 (L72), §6 (L87-88), §7 (L99, L107), §8a (L111-114)
- `/mnt/project-files/planning/open-questions.md` (Summary; groups 1-8; Q-03 row table)
- `/mnt/project-files/adr/0000-legacy-decisions-triage.md` through `0019-events-in-process-until-external-consumer.md` (headers and Decision sections)
- `planning/open-questions.md`: Gabriel's answers of 2026-10-01 (project thread, 2026-10-01T16:25Z); `0020-storefront-pix-prepayment-manual-confirmation.md`
