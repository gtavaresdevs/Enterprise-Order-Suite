# Claude readiness plan

> **Superseded where it differs from `planning/MASTER-PLAN.md` (2026-09-29).** The order of work and the readiness gate are MASTER-PLAN §6-§7 (ADR-0015). Superseded: the docs-hub repo (ADR-0011), Plane-dependent items (ADR-0017; since 2026-10-06 the tool is GitHub Projects, ADR-0021, so R7's `EOS-42` becomes `Refs #<n>` and R15 updates the issue, not a Plane item), the custom git-guard hook (ADR-0013), ArchUnit and Playwright as gate items (ADR-0015), ULIDs only on offline-capable entities (ADR-0009), the cross-repo contract export (ADR-0010).

Status: Draft, iterated with Gabriel until he calls it done. Updated 2026-09-29.
Jev was evaluated and **dropped** (new signups paused, paid per call). This plan uses Claude Code only.

**Rule:** no software development (Tenant foundation onward) starts until the readiness gate in section 5 passes. Documentation work (architecture, PRD, contracts) runs in parallel with readiness, because readiness mostly *consumes* those docs.

Companions: `planning/pm-tool-recommendation.md` (Plane, docs hub, build order), `architecture/RESTAURANT-OPS-ARCHITECTURE.md`.

## 1. What exists today (checked on the working branches, 2026-09-29)

Backend `feature/ai-agent` (`af2634e`), frontend `Claude-Assisted-Development` (`14a3cfd`).

| Area | Backend | Frontend |
|---|---|---|
| `CLAUDE.md` | Yes, 113 lines. Still describes a B2B order suite and a frontend-owned contract. | Yes, 139 lines (`order-ui/CLAUDE.md`). Still says the frontend owns the manifest; describes single-restaurant redesign. |
| Skills | 5, versioned: `writing-backend-tests`, `spring-security-changes`, `backend-module-development`, `flyway-migrations`, `api-contract-sync` | Referenced in `CLAUDE.md` (`migrate-shared-type`, `scaffold-feature`, `connect-backend`, `verify-ui`) but **`.claude/` is gitignored**, so they exist only on Gabriel's machine |
| Agents | 4, versioned: `spring-security-reviewer`, `backend-test-writer`, `backend-feature-builder`, `flyway-migration-author` | `requirement-auditor`, `ui-behavior-verifier`: same problem, not in git |
| Hooks | 1 `PreToolUse` security-file warning, **PowerShell only** (`pwsh`), so it does nothing on Linux/cloud sessions | `lint-typecheck.cjs`, not in git |
| Tests | JUnit 5 + Testcontainers, `./gradlew test` | **None.** Only `yarn build` + `yarn lint` |
| CI | **None** (no `.github/`) | **None** |
| IDs | Database `IDENTITY` sequences (conflicts with client-generated ULIDs) | n/a |
| Specs/plans | `docs/superpowers/{specs,plans}` + `docs/contracts/` snapshot | `docs/superpowers/{specs,plans}` + the canonical manifest |

**What this means:** Claude works well today only on Gabriel's Windows machine, and even there it is steered toward the *previous* product direction (single restaurant, frontend-owned contract, B2B leftovers). A cloud agent, a new machine, or a parallel subagent gets a partial or wrong picture.

## 2. The spec-driven cycle and what Claude needs at each step

The project already runs this cycle (superpowers skills). Each step below lists what Claude needs to do its best work there, and the readiness actions that provide it.

| Step | Claude's job | What it needs | Readiness actions |
|---|---|---|---|
| **1. Orient** | Know the product, current phase and where everything is | One map, current decisions, no stale guidance | R1 docs hub + `where-to-look`, R2 rewrite both `CLAUDE.md`, R3 decisions log (ADRs) |
| **2. Spec** (brainstorm → PRD/SDD/contract section) | Draft with Gabriel, ask the right questions | Templates, glossary, a "questions to ask" habit, the architecture as the root | R4 doc templates, R5 domain glossary, R6 contract-design skill |
| **3. Plan** (`writing-plans`) | Break a spec into small verifiable tasks | Plans that cite spec sections and acceptance criteria; Plane item per plan | R7 plan template + Plane link convention |
| **4. Implement** (`subagent-driven-development`, worktrees) | Write code in the target architecture | Skills that teach the **new** model; subagents with the same guidance; no Windows-only pieces | R8 update/replace skills and agents, R9 version the frontend `.claude/`, R10 cross-platform hooks |
| **5. Verify** | Prove it works before claiming done | Fast, automated red/green in both repos; architecture rules as tests | R11 frontend test setup, R12 ArchUnit rules, R13 contract drift check |
| **6. Finish** | Push to the working branch, never merge | Git rules enforced, not just written | R14 git guard hook |
| **7. Record** | Leave the next session ready | Roadmap/Plane status updated, decisions captured, docs kept in lockstep | R15 end-of-task checklist skill, R16 CI running the same checks |

## 3. Readiness actions

Grouped by where they land. Each becomes a Plane work item under the **Claude readiness** module.

### Docs hub (`enterprise-order-suite-docs`, new repo)
- **R1. Map.** `README.md` + `where-to-look.md`: for each kind of task (menu change, order rule, contract change, tenancy, Edge), which docs and which code paths to read, in order.
- **R3. Decisions log.** `adr/NNNN-title.md`, one per decision, starting with the ones already made: multi-tenant SaaS + Edge, backend owns the contract, ULIDs, EN-only docs, push-never-merge, storefront before Edge. Claude checks ADRs before proposing anything that contradicts them.
- **R4. Templates.** `templates/{prd-section,sdd-section,contract-change,adr,plan}.md`, each with a "Status: Draft / Reviewed / Ready" line and an "Open questions" block, so docs can stay open-ended and still show where they stand.
- **R5. Glossary.** `glossary.md` with the domain words used in code and docs (Restaurant/tenant, Channel vs Source, Order Core, Edge, Menu item vs Product, Modifier vs Option). Stops agents inventing synonyms.

### Both repos
- **R2. Rewrite `CLAUDE.md`.** Short, current, and identical in its first section: where to look (docs hub), the git rules, the architecture principles (one Order Core, tenant scoping, ULIDs, adapters call application services). Remove the B2B description, the single-tenant direction and the frontend-owned manifest rule.
- **R6. Contract-design skill** (`contract-design`, shared text in both repos): how to propose a contract change, which questions to ask Gabriel (the ones only he can answer), and where the change lands. Replaces `api-contract-sync` in the backend and the manifest lockstep rule in the frontend.
- **R7. Plan convention.** Plans stay in `docs/superpowers/plans/` of the repo they change, start with the Plane id (`EOS-42`) and link the spec section they implement.
- **R14. Git guard hook** (Node, cross-platform, blocking): refuses `git merge`, any push to `main`, `git stash`, `git add -A`/`git commit` without pathspec. Rules become enforcement, not advice.
- **R15. `finish-task` skill.** Before declaring done: run the repo's verification, update the Plane item, update the roadmap line, add an ADR if a decision was made, note open questions in the doc.
- **R16. CI (GitHub Actions)** running the same checks as local verification on every push to the working branches. Gives Claude a signal it can read on PRs and in cloud sessions.

### Backend (`feature/ai-agent`)
- **R8b. Skills and agents to the new architecture.** Update `backend-module-development` (core module + adapters, tenant scoping, ULIDs), `flyway-migrations` (ULID keys, `restaurant_id` everywhere; re-baselining allowed since there is no data), `writing-backend-tests` (tenant-isolation test per repository). Add `order-core` skill (channel/source, lifecycle, events). Keep `spring-security-changes` and the security reviewer.
- **R10b. Hook to Node.** Port `security-sensitive-file.ps1` so it runs on Linux, macOS and Windows.
- **R12. ArchUnit rules**: core module has no web/Spring MVC imports; controllers call application services only; every repository in a tenant-owned module goes through the tenant filter; no `IDENTITY` ids on offline-capable entities.
- **R13a. Contract export**: springdoc writes `openapi.yaml`; the build fails if the committed copy differs; CI opens a PR into the docs hub when it changes.

### Frontend (`Claude-Assisted-Development`)
- **R9. Version `.claude/`.** Un-ignore it (keep `settings.local.json` ignored), commit the skills, agents and hook that already exist on Gabriel's machine, pin `model:` on agents as the current `CLAUDE.md` already requires.
- **R8f. Skills to the new architecture.** Update `connect-backend` to read the backend-owned contract; update `scaffold-feature` for tenant-aware routes and storefront branding; retire the manifest-editing guidance.
- **R10f. Hook check.** `lint-typecheck.cjs` is already Node; confirm it runs outside Windows.
- **R11. Test setup.** Vitest + Testing Library for hooks/services/pure logic; Playwright smoke test per public route (`/storefront`, order status). `yarn test` joins `yarn build` + `yarn lint` as the definition of done.
- **R13b. Generated API types** from the docs hub contract (`openapi-typescript`), with a CI check that fails on drift. Hand-written API types are removed as each feature moves to the real backend.

## 4. Order of work

1. R1, R3, R5, R2 (map, decisions, glossary, `CLAUDE.md`): everything else points at these.
2. R9, R10, R14 (version frontend tooling, cross-platform hooks, git guard): makes every session equal.
3. R4, R6, R7, R15 (templates and workflow skills): used immediately for the PRD and contract work.
4. R11, R12, R16 (tests and CI): must exist before feature code.
5. R8b, R8f, R13a, R13b (architecture-specific skills, contract pipeline): done as the architecture docs and first contracts reach "Reviewed", since they encode them.

Docs work (architecture set, PRD, first contracts) runs alongside steps 1–4 and feeds step 5.

## 5. Readiness gate (all true before Tenant foundation starts)

- [ ] A fresh cloud session on either repo, with no local files, can answer "what are we building, what phase, where is the contract" from the repo alone.
- [ ] Both `CLAUDE.md` files match the current architecture and decisions; no stale direction left.
- [ ] Frontend `.claude/` is in git; every hook runs on Linux.
- [ ] Git guard hook blocks merge, push to `main`, stash, commit without pathspec.
- [ ] `yarn test` exists and passes; `./gradlew test` passes; both run in CI.
- [ ] ArchUnit rules for the core/adapters split and tenant scoping exist (they may start mostly empty, but they run).
- [ ] Contract pipeline works end to end once: backend change → exported spec → docs hub → regenerated frontend types.
- [ ] Architecture docs and the Tenant + Menu + Order Core contracts are at least "Reviewed".

## 6. Contract design questions

Asked in the contract thread as each contract is drafted, not up front. First batch will cover Tenant foundation: how a user belongs to restaurants (one or many), how the storefront identifies its restaurant (subdomain, path or custom domain), and what a "package" switches on.
