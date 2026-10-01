# ADR-0015: Claude readiness before software development; Jev dropped
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel. Changes of 2026-10-01: his register answers (Q-06, Q-07, Q-08, Q-73..Q-77, Q-83)
- Supersedes: `planning/ai-ready-development-plan.md` §4 order of work (L72-80) and §5 readiness gate (L82-91), replaced by MASTER-PLAN §6-§7 (the slimmer gate is a §4 simplification, accepted 2026-09-29, Q-03 row 3.8)
- Related: ADR-0007, ADR-0010, ADR-0011, ADR-0013, ADR-0016, ADR-0017

## Context
- D-13 (MASTER-PLAN L46): Claude readiness is a prerequisite before software development; Jev was evaluated and dropped (`planning/pm-tool-recommendation.md` L143: signups paused, paid per call; memory `jev-claude-plan.md` L8).
- State on the working branches (MASTER-PLAN §2 L27; ai-ready §1 L14-25):
  - backend: 5 skills, 4 agents, 1 PreToolUse hook that needs `pwsh` (`enterprise-order-suite/.claude/settings.json` L1-15);
  - frontend: skills, agents and hook exist only on Gabriel's machine (`order-ui/.gitignore` L16-17 ignores `.claude/`). Since 2026-10-01 they are versioned (FE commit `c4a7309`, Q-06 a, Q-07);
  - no CI in either repo; frontend has no test runner (`order-ui/package.json` scripts: dev, build, lint, preview) and about 28 old lint errors (`RESTAURANT-OPS-ROADMAP.md` L267-269, not re-verified);
  - both `CLAUDE.md` files steer agents to the old direction (backend `CLAUDE.md` L7 "B2B", L94-95 frontend-owned contract; `order-ui/CLAUDE.md` L9-18 single-restaurant redesign, L81-88 manifest lockstep).
- The ai-ready plan had 16 actions and an 8-point gate, all before code (ai-ready L41-91). MASTER-PLAN §4 (L64) slims it; §7 is the gate.

## Decision
Accepted (D-13).
- No software development (Build 1, Tenant foundation, onward) starts until every item of the readiness gate in MASTER-PLAN §7 is checked:
  - ADRs and the amended architecture are Accepted by Gabriel.
  - Docs live in one agreed place with a "where to look" map; legacy docs carry superseded banners.
  - Both `CLAUDE.md` files match the current decisions.
  - Frontend `.claude/` is in git; no Windows-only hooks; git rules enforced by `permissions.deny`.
  - A fresh cloud session can run the full verification in both repos.
  - CI runs `./gradlew test` and `yarn lint && yarn build && yarn test` on the working branches, green.
  - API conventions + Tenancy & Identity contract are Reviewed.
- Added by Gabriel on 2026-10-01 (Q-08 b, "Q8- b"): S5 (scope of this run, NFR page, audit triage) must be Reviewed before Build 1. S5 joins the gate.
- Added by Gabriel on 2026-10-01 (Q-83 b, "this is the first thing we do before begin working on the actual app"): Plane set up on his Oracle Always Free account (ADR-0017; runbook `docs/ops/plane-setup.md`). The Plane setup joins the gate. Docs and readiness tooling do not wait for it.
- Documentation (S0-S2, S4-S5) and readiness tooling (S3: versioning frontend `.claude/`, port or removal of the PowerShell hook, `permissions.deny`, SessionStart setup, frontend lint baseline, Vitest, CI) are not software development and proceed before the gate (MASTER-PLAN §6 L87-92).
- Jev is not used. Tooling is Claude Code only.
- Decided later (Gabriel, 2026-10-01):
  - Q-06 a, Q-07 (answered by action; closest option a, commit it all): the frontend `.claude/` is versioned. Gabriel removed it from `.gitignore` and committed the whole folder in FE commit `c4a7309` on `Claude-Assisted-Development`: the agents, the skills (including graphify), the Node lint/typecheck hook, `settings.json` (which enables the ponytail and wshobson plugins) and `.claude/CLAUDE.md`. Four machine-only files stay ignored: `.tsc-hook-cache`, `scheduled_tasks.lock`, `settings.json.graphify-bak`, `settings.local.json`.
  - Q-73..Q-77 a ("Q73 - 77 a") confirm the S3 tooling choices: the backend hook is ported to Node (Q-73); the frontend lint baseline is a plain `yarn lint` (Q-74); backend tooling Task 14 is the SessionStart check and Task 2 is dropped (Q-75); the frontend gets the API spec as a vendored copy synced from a pinned backend commit (Q-76, ADR-0010 Decision 7); the SessionStart script checks Docker and JDK 17 so a cloud session runs the full `./gradlew test`; without Docker a session runs the unit tests, says so, and CI stays the full check (Q-77).
- The reduction from the ai-ready 8-point gate to MASTER-PLAN §7 is a Claude simplification (MASTER-PLAN §4 L64), accepted by Gabriel on 2026-09-29 (Q-03 row 3.8). ArchUnit rules, Playwright and the contract drift test are acceptance criteria of Build 1, not gate items.

## Consequences
- Before starting Build 1, an agent lists the §7 items and their state; any unchecked item stops the start.
- Gabriel-owned items on the gate path: accepting ADRs and the architecture amendment; marking the gate docs and S5 Reviewed (Q-08 b); the Plane setup (Q-83 b, ADR-0017). Pushing the frontend `.claude/` from his machine (MASTER-PLAN §6 S1, L88) is done (2026-10-01, FE commit `c4a7309`).
- The "no test suite" statements (`order-ui/CLAUDE.md` L7, L94-95; `RESTAURANT-OPS-ROADMAP.md` L25-26) are updated when Vitest lands in S3.
- Small existing-feature fixes from the audit ride alongside Build 2-4 (MASTER-PLAN §6 L94), after the gate.
- Agents must never: start Tenant foundation code before the gate passes; propose Jev again unless Gabriel asks; add a readiness item that guards code which does not exist yet (MASTER-PLAN §4 L64).

## Open questions
- Q-03 row 3.8 (the slimmer gate): answered 2026-09-29, accepted.
- Q-07: answered 2026-10-01 by action, closest option a (commit it all): Gabriel: "I committed and pushed c4a7309 to Claude-Assisted-Development." The graphify and audit-requirement skills are committed, and the committed `settings.json` enables ponytail and the two wshobson plugins (Decision).
- Q-75: answered 2026-10-01, a: Task 14 is the SessionStart check; Task 2 is dropped. Q-73, Q-74, Q-76, Q-77: answered 2026-10-01, a (Decision).
- Q-06: answered 2026-10-01, a: Gabriel: "removed it from .gitignore already pushed to branch".
- Q-08: answered 2026-10-01, b: S5 joins the gate (Decision).

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §2 (L27), §3 D-13 (L46), §4 gate row (L64), §6 (L85-95), §7 (L97-107)
- `planning/open-questions.md` Q-06, Q-07, Q-08, Q-73..Q-77, Q-83: Gabriel's answers of 2026-10-01 (project thread, 2026-10-01T16:25Z)
- `/mnt/project-files/planning/ai-ready-development-plan.md` L4-6, §1 (L10-25), §3 (L41-70), §4 (L72-80), §5 (L82-91)
- `/mnt/project-files/planning/pm-tool-recommendation.md` L141-143
- `/mnt/project-files/RESTAURANT-OPS-ROADMAP.md` L25-26, L267-269
- `/mnt/project-files/2026-09-20-claude-tooling-install.md` Task 2 (L122), Task 14 (L1036)
- `/tmp/claude/memory/team/silo/jev-claude-plan.md` L8-12
- Backend `enterprise-order-suite` (`feature/ai-agent` @ `af2634e`): `CLAUDE.md` L7, L94-95; `.claude/settings.json` L1-15
- Frontend `order-ui` (`Claude-Assisted-Development` @ `14a3cfd`): `CLAUDE.md` L7, L9-18, L81-88, L94-95; `.gitignore` L16-17; `package.json`; @ `c4a7309`: `.claude/` (20 files), `.claude/settings.json`, `.gitignore`
