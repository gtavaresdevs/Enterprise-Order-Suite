# ADR-0015: Claude readiness before software development; Jev dropped
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
- Supersedes: `planning/ai-ready-development-plan.md` §4 order of work (L72-80) and §5 readiness gate (L82-91), replaced by MASTER-PLAN §6-§7 (the slimmer gate is a §4 simplification, accepted 2026-09-29, Q-03 row 3.8)
- Related: ADR-0007, ADR-0010, ADR-0011, ADR-0013, ADR-0016, ADR-0017

## Context
- D-13 (MASTER-PLAN L46): Claude readiness is a prerequisite before software development; Jev was evaluated and dropped (`planning/pm-tool-recommendation.md` L143: signups paused, paid per call; memory `jev-claude-plan.md` L8).
- State on the working branches (MASTER-PLAN §2 L27; ai-ready §1 L14-25):
  - backend: 5 skills, 4 agents, 1 PreToolUse hook that needs `pwsh` (`enterprise-order-suite/.claude/settings.json` L1-15);
  - frontend: skills, agents and hook exist only on Gabriel's machine (`order-ui/.gitignore` L16-17 ignores `.claude/`);
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
- Documentation (S0-S2, S4-S5) and readiness tooling (S3: versioning frontend `.claude/`, port or removal of the PowerShell hook, `permissions.deny`, SessionStart setup, frontend lint baseline, Vitest, CI) are not software development and proceed before the gate (MASTER-PLAN §6 L87-92).
- Jev is not used. Tooling is Claude Code only.
- The reduction from the ai-ready 8-point gate to MASTER-PLAN §7 is a Claude simplification (MASTER-PLAN §4 L64), accepted by Gabriel on 2026-09-29 (Q-03 row 3.8). ArchUnit rules, Playwright and the contract drift test are acceptance criteria of Build 1, not gate items.

## Consequences
- Before starting Build 1, an agent lists the §7 items and their state; any unchecked item stops the start.
- Gabriel-owned items on the gate path: accepting ADRs and the architecture amendment; pushing the frontend `.claude/` from his machine or approving a Remote Control session (MASTER-PLAN §6 S1, L88).
- The "no test suite" statements (`order-ui/CLAUDE.md` L7, L94-95; `RESTAURANT-OPS-ROADMAP.md` L25-26) are updated when Vitest lands in S3.
- Small existing-feature fixes from the audit ride alongside Build 2-4 (MASTER-PLAN §6 L94), after the gate.
- Agents must never: start Tenant foundation code before the gate passes; propose Jev again unless Gabriel asks; add a readiness item that guards code which does not exist yet (MASTER-PLAN §4 L64).

## Open questions
- Q-03 row 3.8 (the slimmer gate): answered 2026-09-29, accepted.
- Keep or drop the extra frontend tooling on Gabriel's machine when `.claude/` is versioned (Graphify, ponytail, two wshobson plugins, the audit-requirement skill; `order-ui/docs/superpowers/specs/2026-09-15-dev-tooling-workflow-design.md`). Register: Q-07.
- Whether the two outstanding backend tooling tasks (security-guidance plugin, fresh-session end-to-end verification; `2026-09-20-claude-tooling-install.md` Tasks 2 and 14) are done, dropped or folded into S3. Register: Q-75.
- Frontend `.claude/` push by Gabriel vs a Remote Control session: S1 action (MASTER-PLAN §6 L88). Register: Q-06.

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §2 (L27), §3 D-13 (L46), §4 gate row (L64), §6 (L85-95), §7 (L97-107)
- `/mnt/project-files/planning/ai-ready-development-plan.md` L4-6, §1 (L10-25), §3 (L41-70), §4 (L72-80), §5 (L82-91)
- `/mnt/project-files/planning/pm-tool-recommendation.md` L141-143
- `/mnt/project-files/RESTAURANT-OPS-ROADMAP.md` L25-26, L267-269
- `/mnt/project-files/2026-09-20-claude-tooling-install.md` Task 2 (L122), Task 14 (L1036)
- `/tmp/claude/memory/team/silo/jev-claude-plan.md` L8-12
- Backend `enterprise-order-suite` (`feature/ai-agent` @ `af2634e`): `CLAUDE.md` L7, L94-95; `.claude/settings.json` L1-15
- Frontend `order-ui` (`Claude-Assisted-Development` @ `14a3cfd`): `CLAUDE.md` L7, L9-18, L81-88, L94-95; `.gitignore` L16-17; `package.json`
