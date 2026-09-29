# ADR-0017: Project management: Plane CE on a free VPS, off the critical path
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
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
  - Plane is off the critical path and is set up whenever Gabriel has time.
  - Until it exists, `docs/roadmap.md` (location per ADR-0011) is the tracker.
  - No import of historical phases, no empty future modules, no automations (no GitHub Action, no webhook worker).
- Oracle Always Free A1 is Claude's recommendation for the VPS, not a decision.

## Consequences
- No step waits on Plane (MASTER-PLAN §6 S1 L88: "Plane signup whenever convenient").
- An agent that changes a step's status updates its line in `docs/roadmap.md` in the same commit.
- The board structure in pm-tool L31-48 and L133-135 is not applied as written; it is revisited when Plane is set up.
- Agents must never:
  - invent or cite `EOS-<n>` ids before Plane exists;
  - create Plane structure, automations, tokens or secrets;
  - sign up for or configure hosting (Gabriel's card and region choice).

## Open questions
- Q-03 row 3.10 ("Plane off the critical path, `docs/roadmap.md` interim"): answered 2026-09-29, accepted.
- VPS provider and home region, HTTPS exposure method, off-box backups: Gabriel at setup time. Register: Q-83.
- Work-item id convention once Plane exists (`EOS-<n>` in branch, commit and PR names; pm-tool L27): confirm at setup (Q-83).

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §3 D-15 (L48), §4 Plane row (L66), §6 S1 (L88)
- `/mnt/project-files/planning/pm-tool-recommendation.md` L3, L5-29, L31-48, L56-58, L65-74, L133-135
- `/mnt/project-files/planning/ai-ready-development-plan.md` L43, R7 (L54), R15 (L56)
- `/tmp/claude/memory/team/silo/pm-tool-plane.md` L9-14; `/tmp/claude/memory/team/silo/MEMORY.md` L19
