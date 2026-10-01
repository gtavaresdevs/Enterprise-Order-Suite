# ADR-0017: Project management: Plane CE on a free VPS, set up before Build 1
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel (D-15; Q-03 row 3.10, 2026-09-29; changed by Q-83 b, 2026-10-01)
- Supersedes: the Plane-dependent parts of the earlier plans (§4 simplification accepted 2026-09-29, Q-03 row 3.10): history import and forward cycles (`planning/pm-tool-recommendation.md` L40-42), pre-created future modules (L135), GitHub Action automation (L22); every readiness item as a Plane work item, `EOS-<n>` plan ids and Plane updates in `finish-task` (`planning/ai-ready-development-plan.md` L43, L54, L56)
- Related: ADR-0011, ADR-0013, ADR-0015

## Context
- D-15 (MASTER-PLAN L48): project management is Plane Community Edition on a free VPS (Oracle Always Free recommended). `planning/pm-tool-recommendation.md` L58: "Hosting: Plane on a free VPS."
- Why Plane (pm-tool L5-29): official MCP server works against CE over stdio with a personal access token; Pages and Intake return 404 on CE; native GitHub sync is a paid feature; docs stay in git and Plane only links to them (L28).
- Hosting constraints (pm-tool L65-74): Plane needs 2 cores and 4 GB RAM; Oracle A1 is the only free tier that fits; signup needs Gabriel's card; the home region is permanent; A1 capacity can be unavailable; idle instances can be reclaimed; the allowance was halved on 2026-06-15; cloud agents need HTTPS exposure (Caddy with DuckDNS, or a Cloudflare Tunnel); off-box backups are needed.
- Not installed (pm-tool L3; memory `pm-tool-plane.md` L9).
- The earlier plans put Plane on the critical path: every R-item a Plane work item (ai-ready L43), `EOS-<n>` ids in plans (R7 L54), `finish-task` updates Plane (R15 L56), import of completed phases as cycles (pm-tool L41), empty future modules (L135), a GitHub Action calling the Plane API (L22).

## Decision
- Accepted (D-15): Plane Community Edition, self-hosted on a free VPS, is the project management tool. Canonical text stays in git; Plane items only link to it.
- Accepted (Gabriel, 2026-09-29; Q-03 row 3.10; MASTER-PLAN §4 L66):
  - Plane is off the critical path and is set up whenever Gabriel has time. Changed on 2026-10-01 (below).
  - Until it exists, `docs/roadmap.md` (location per ADR-0011) is the tracker.
  - No import of historical phases, no empty future modules, no automations (no GitHub Action, no webhook worker).
- Oracle Always Free A1 is Claude's recommendation for the VPS, not a decision. Since 2026-10-01 the provider is Gabriel's choice (below); the A1 shape stays Claude's recommendation.
- Changed by Gabriel on 2026-10-01 (Q-83 b): "Q83 - b -> other I already create the oracle free tier with always free account, its fresh and ready to begin the setup, so this is the first thing we do before begin working on the actual app."
  - Plane is no longer off the critical path for the build: its setup is the first step before work on the app, so Build 1 waits for it. Docs and readiness work (S2-S5) continue meanwhile.
  - The VPS is Gabriel's Oracle Cloud Always Free account, already created and fresh.
  - Not specified, so still pending: the HTTPS exposure method, off-box backups and the `EOS-<n>` id convention. The runbook `docs/ops/plane-setup.md` (Draft) asks them at setup time.
  - `docs/roadmap.md` stays the tracker until Plane is set up. No history import, no empty future modules and no automations, as before.

## Consequences
- Until 2026-10-01 no step waited on Plane (MASTER-PLAN §6 S1 L88: "Plane signup whenever convenient"). Since Q-83 b, Build 1 waits for the Plane setup; docs and readiness tooling do not.
- The setup follows the runbook `docs/ops/plane-setup.md` (Draft).
- An agent that changes a step's status updates its line in `docs/roadmap.md` in the same commit.
- The board structure in pm-tool L31-48 and L133-135 is not applied as written; it is revisited when Plane is set up.
- Agents must never:
  - invent or cite `EOS-<n>` ids before Plane exists;
  - create Plane structure, automations, tokens or secrets;
  - sign up for or configure hosting (Gabriel's card and region choice).

## Open questions
- Q-03 row 3.10 ("Plane off the critical path, `docs/roadmap.md` interim"): answered 2026-09-29, accepted.
- Q-83: answered 2026-10-01, b (other): Gabriel's Oracle Always Free account is created and fresh, and the Plane setup is the first step before Build 1 (Decision). HTTPS exposure, off-box backups and the work-item id convention were not specified: pending, asked in `docs/ops/plane-setup.md` (Draft).
- Work-item id convention once Plane exists (`EOS-<n>` in commit messages and plan files; no per-item branches or PRs under ADR-0013; pm-tool L27): pending, asked in `docs/ops/plane-setup.md` Open questions 3 (Q-83).

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §3 D-15 (L48), §4 Plane row (L66), §6 S1 (L88)
- `planning/open-questions.md` Q-83: Gabriel's answer of 2026-10-01 (project thread, 2026-10-01T16:25Z); `docs/ops/plane-setup.md` (Draft)
- `/mnt/project-files/planning/pm-tool-recommendation.md` L3, L5-29, L31-48, L56-58, L65-74, L133-135
- `/mnt/project-files/planning/ai-ready-development-plan.md` L43, R7 (L54), R15 (L56)
- `/tmp/claude/memory/team/silo/pm-tool-plane.md` L9-14; `/tmp/claude/memory/team/silo/MEMORY.md` L19
