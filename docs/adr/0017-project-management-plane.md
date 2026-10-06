# ADR-0017: Project management: Plane (Cloud free plan since 2026-10-06), set up before Build 1
- Status: Superseded by ADR-0021 (Gabriel, 2026-10-06T14:02Z: GitHub Projects instead of Plane)
- Date: 2026-09-29
- Decided by: Gabriel (D-15; Q-03 row 3.10, 2026-09-29; changed by Q-83 b, 2026-10-01; changed again 2026-10-06: Plane Cloud instead of self-hosting)
- Supersedes: the Plane-dependent parts of the earlier plans (§4 simplification accepted 2026-09-29, Q-03 row 3.10): history import and forward cycles (`planning/pm-tool-recommendation.md` L40-42), pre-created future modules (L135), GitHub Action automation (L22); every readiness item as a Plane work item, `EOS-<n>` plan ids and Plane updates in `finish-task` (`planning/ai-ready-development-plan.md` L43, L54, L56)
- Related: ADR-0011, ADR-0013, ADR-0015

> **Superseded by ADR-0021 on 2026-10-06.** Gabriel: "Ok, so lets redesign our plan around github projects instead of plane, much simpler, free and AI can access it." ADR-0021 restates the parts of this ADR that carry over (docs stay in git, setup before Build 1, `docs/roadmap.md` until the board is in use, no history import, no empty future modules, no automation code). Everything Plane-specific below is history. The runbooks it names now live at `docs/ops/superseded/plane-cloud-setup.md` (Plane Cloud) and `docs/ops/superseded/plane-selfhost-oracle.md` (self-hosted); `docs/ops/plane-setup.md` no longer exists.

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
  - Not specified, so still pending as answers: the HTTPS exposure method, off-box backups and the `EOS-<n>` id convention. The runbook `docs/ops/plane-setup.md` (Draft, section 0) states Claude's defaults (Tailscale Funnel, encrypted Google Drive backups, `EOS-<n>` ids). Claude chose them on 2026-10-01 because Gabriel asked not to be asked during this run; they are not his answers, and he can change any.
  - `docs/roadmap.md` stays the tracker until Plane is set up. No history import, no empty future modules and no automations, as before.
- Changed by Gabriel on 2026-10-06 (project thread 13:48Z and 13:49Z), after Oracle had no Always Free A1 capacity and gave him only a 1 GB `VM.Standard.E2.1.Micro`, which cannot run Plane (its self-host docs ask for 4 GB; the free VMs of AWS, Google Cloud and Azure are the same size). Told that the free plan's seat limit does not matter with one user, he wrote "bro the only sit I need is mine" and then: "Ok, so lets readjust our plan so that we use the plane free tier in their cloud."
  - **Plane runs on Plane Cloud's free plan** (`app.plane.so`), not self-hosted. Plane Community Edition, the VPS, the Oracle account and the hosting constraints in pm-tool L65-74 no longer apply to this project.
  - What does not change: Plane is the tool; canonical text stays in git and Plane items only link to it (ADR-0011); the setup is still the first step before Build 1; no history import, no empty future modules, no automations; `docs/roadmap.md` is the tracker until Plane is in use.
  - Of the three things Q-83 left unspecified, two are now moot: HTTPS exposure (Plane Cloud serves itself) and off-box backups (nothing of ours lives only in Plane: the docs are in git and work items hold status only). The `EOS-<n>` id convention stays Claude's default.
  - Free-plan limits that touch us (checked 2026-10-06): 12 seats and $0; work item types and properties, dashboards, initiatives, templates, the workspace wiki and time tracking are paid, so labels carry that information and `docs/roadmap.md` stays the plan-level view; the API allows 60 requests per minute, which is far above anything an agent does here. Plane's hosted MCP server is free and follows the plan: a plan-gated action returns a message naming the unavailable feature.
  - The free plan lists the GitHub integration as included, which contradicts pm-tool L5-29 ("native GitHub sync is a paid feature", written about CE). It stays unused: ADR-0017 allows no automations, and nothing in the build depends on it.
  - Added constraint, because the data now sits on a third party: no restaurant, customer or order data in Plane, not even as an example. Work items carry a title, a link and a state.
  - The runbook `docs/ops/plane-setup.md` is rewritten for Plane Cloud. The self-hosted runbook and its tested scripts are kept as `docs/ops/superseded/plane-selfhost-oracle.md` and `docs/ops/plane/`, marked superseded and not run, in case Plane is ever self-hosted.

## Consequences
- Until 2026-10-01 no step waited on Plane (MASTER-PLAN §6 S1 L88: "Plane signup whenever convenient"). Since Q-83 b, Build 1 waits for the Plane setup; docs and readiness tooling do not.
- The setup follows the runbook `docs/ops/plane-setup.md` (Draft; Plane Cloud since 2026-10-06). It is sign-up, one workspace, one project, states and labels, and a token: no server, no HTTPS setup, no backup job, no maintenance.
- Since 2026-10-06 there is no infrastructure to own, so nothing in this project waits on hosting, capacity or a card. In exchange, the project depends on a third-party free plan: if Plane changes or withdraws it, the loss is the work-item statuses, because every canonical document is in git.
- An agent that changes a step's status updates its line in `docs/roadmap.md` in the same commit.
- The board structure in pm-tool L31-48 and L133-135 is not applied as written; it is revisited when Plane is set up.
- Agents must never:
  - invent or cite `EOS-<n>` ids before Plane exists;
  - create Plane structure, automations, tokens or secrets;
  - sign up for a service or configure hosting (Gabriel's account);
  - put restaurant, customer or order data into Plane (added 2026-10-06, now that Plane is hosted by Plane).

## Open questions
- Q-03 row 3.10 ("Plane off the critical path, `docs/roadmap.md` interim"): answered 2026-09-29, accepted.
- Q-83: answered 2026-10-01, b (other): the Plane setup is the first step before Build 1 (Decision). Its Oracle part was replaced on 2026-10-06 by Gabriel's move to Plane Cloud's free plan (Decision).
- HTTPS exposure and off-box backups: closed on 2026-10-06, moot under Plane Cloud.
- Work-item id convention once Plane exists (`EOS-<n>` in commit messages and plan files; no per-item branches or PRs under ADR-0013; pm-tool L27): pending as an answer; it is Claude's default in `docs/ops/plane-setup.md` section 0 (Q-83).
- Whether cloud Claude sessions get Plane access too, and how (a custom connector or the token endpoint), is open: `docs/ops/plane-setup.md` section 6 item 2.

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §3 D-15 (L48), §4 Plane row (L66), §6 S1 (L88)
- `planning/open-questions.md` Q-83: Gabriel's answer of 2026-10-01 (project thread, 2026-10-01T16:25Z) and his change of 2026-10-06 (project thread, 2026-10-06T13:48Z and 13:49Z); `docs/ops/plane-setup.md` (Draft)
- Plane Cloud free plan, read 2026-10-06: https://plane.so/pricing ; API (base `https://api.plane.so/`, `X-API-Key`, 60 requests per minute): https://developers.plane.so/api-reference/introduction ; hosted MCP server (`https://mcp.plane.so/http/mcp`, OAuth or token with `x-workspace-slug`): https://developers.plane.so/dev-tools/mcp-server
- `/mnt/project-files/planning/pm-tool-recommendation.md` L3, L5-29, L31-48, L56-58, L65-74, L133-135
- `/mnt/project-files/planning/ai-ready-development-plan.md` L43, R7 (L54), R15 (L56)
- `/tmp/claude/memory/team/silo/pm-tool-plane.md` L9-14; `/tmp/claude/memory/team/silo/MEMORY.md` L19
