# Roadmap (interim tracker)

- Status: Draft
- Updated: 2026-10-06 (Gabriel's message of 18:12Z: Q-30 and Q-86 answered, ADR-0020 Accepted, the settings proposal and the board runbook Reviewed, branch protection confirmed). Earlier the same day: Gabriel set up the board, 15:12Z: board setup step Done, its gate item ticked. Earlier the same day: board setup row: GitHub Projects replaces Plane, ADR-0021, Plane runbooks deleted; earlier the same day Plane Cloud had replaced the Oracle VPS). Earlier: 2026-10-02 (Plane setup row: runbook ready; its defaults are Claude's). Earlier: 2026-10-01 (Gabriel's register answers of 2026-10-01: S1, S2, S3 done; Plane setup step added; gate items 1, 2 and 4 ticked, two items added; item 3 unticked until the `CLAUDE.md` corrections are committed)
- Role: the project tracker until its open steps move onto the GitHub Project as issues (ADR-0021). Gabriel set the project up on 2026-10-06; the move has not happened yet. Order of work and gate: `planning/MASTER-PLAN.md` §6-§7 (ADR-0007, ADR-0015). Where this file and MP disagree, MP wins; fix this file in the same commit.
- Related: `README.md`, `planning/open-questions.md`, ADR-0007, ADR-0015, ADR-0016, ADR-0021 (was ADR-0017)

## Order of work

| Step | What | Owner | Blocks on | Status |
|---|---|---|---|---|
| S0 | Decisions baseline: ADRs (new + legacy triage), architecture amendment proposal, open-questions register, superseded-docs list, memory cleanup | Claude | none | Done 2026-09-29 |
| S1 | Gabriel's review: accept the ADRs and the architecture amendments; answer register Q-01..Q-10; push the frontend `.claude/` (Q-06, Q-07). (The project board setup is now its own step below, Q-83 b.) | Gabriel | S0 | Done 2026-10-01: Q-01..Q-03 answered 2026-09-29; Q-04..Q-10 answered 2026-10-01, in a batch that answered every other open register question except Q-30 (not in the batch); Q-70 partly answered, clarification asked as Q-86; Q-30 and Q-86 answered 2026-10-06 (both a) |
| S2 | Docs skeleton in the backend repo `docs/` (ADR-0011): `README.md`, `roadmap.md`, ADRs, glossary seed, templates, superseded banners on legacy docs; rewrite both `CLAUDE.md` files | Claude | S1 Q-01..Q-03 (answered). Parts waited on Q-04, Q-05, Q-09, Q-10 (answered 2026-10-01) | Done 2026-10-01: skeleton, ADRs, banners and both `CLAUDE.md` rewrites pushed 2026-09-29; on 2026-10-01 the amendments were applied to the architecture doc (Q-04), ADR-0000 became Accepted (Q-05), the Reviewed signal was defined (Q-09 a) and both public READMEs got their note (Q-10 a; BE `c8bf761`, FE `30bc172`) |
| S3 | Readiness tooling: version the frontend `.claude/`, port or remove the PowerShell hook, `permissions.deny` git rules, SessionStart setup, frontend lint baseline, Vitest, CI running today's checks in both repos | Claude (+ Gabriel for the S1 push) | S2 | Done 2026-10-01: everything except the frontend `.claude/` (hook port, git rules, SessionStart check, Vitest, CI in both repos, skills aligned) on 2026-09-29; the frontend `.claude/` versioned by Gabriel on 2026-10-01 (FE `c4a7309`, Q-06 a, Q-07) and its `permissions.deny` git rules added the same day (FE `30bc172`) |
| S4 | Cross-cutting design: API conventions doc, finalize the Edge-ready constraints (drafted in ADR-0003), then the Tenancy & Identity contract with Gabriel's question batch (register group 2) | Claude + Gabriel | S2 | Not started |
| S5 | Scope of this run (short PRD) + NFR page + audit backlog triage. Input also: `planning/proposals/settings-and-notifications.md` (Q-24). Must be Reviewed before Build 1 (Q-08 b) | Claude + Gabriel | S4 | Not started |
| Board setup | GitHub Projects (Gabriel, 2026-10-06; ADR-0021; the step itself is Q-83 b, 2026-10-01), following the runbook `ops/github-projects-setup.md` (Reviewed 2026-10-06): one project on his account, an "In review" Status, the auto-add workflow and the labels. Work items are issues in this repo, cited as `Refs #<n>`. Then the board replaces this file as the tracker. Docs and readiness work do not wait for it | Gabriel (+ Claude: runbook) | none | Done 2026-10-06: Gabriel set up the project (project chat, 15:12Z: "Board setup done https://github.com/users/gtavaresdevs/projects/2") and ran the smoke test (issues #26 to #29, all closed); Claude created the eleven labels at his request the same day. The Plane runbooks it replaced were deleted on 2026-10-06 at Gabriel's request. Next board step: move this file's open steps onto the board as issues; until then this file stays the tracker |
| Build 1 | Tenant foundation. Acceptance includes: ArchUnit tenant rule, cross-tenant tests, contract drift test, dev/test seed data, tenant id in logs | Claude | Readiness gate below, which includes S5 Reviewed (Q-08 b) and the board setup (Q-83 b, done 2026-10-06) | Not started |
| Build 2 | Menu | Claude | Menu contract Reviewed | Not started |
| Build 3 | Order Core | Claude | Order Core contract Reviewed | Not started |
| Build 4 | Storefront <-> Order Core | Claude | Storefront contract Reviewed | Not started |
| Later | QR/table, waiter, Phase 10 comandas (Q-81 a), KDS on Edge, POS, tablets, Edge build (when Gabriel decides; an optional paid feature, Q-80 b) | none | Not scheduled this run (ADR-0007) | Not scheduled |
| Out | PSP, automatic WhatsApp, fiscal, marketplaces, billing | none | Not this run (ADR-0005, ADR-0006, ADR-0007) | Out |

Small existing-feature fixes from the audit ride alongside Build 2-4, as triaged in S5 (MP §6). Phase 11's small polish items ride alongside Build 3-4 (Q-82 a, 2026-10-01).

## Readiness gate (before Build 1)

From MP §7. Tick an item only when it holds (repo items: on the working branches of both repos). Items that need Gabriel (Accepted, Reviewed) are ticked only on his word.

- [x] ADRs and the amended architecture are Accepted by Gabriel. (ADR-0000..ADR-0019. An ADR added later, such as ADR-0020, gates the build step that depends on it, here Build 4, not Build 1; Claude's reading, 2026-10-01, Gabriel may override; MP §7.) Evidence (Gabriel, 2026-10-01): Q-04, every line of the AMD checklist ticked (commit `6a60b6d`; "Done, edited the file in github repo with my confirmations for the checklist."), amendments applied to `architecture/RESTAURANT-OPS-ARCHITECTURE.md` (decision status "Accepted with amendments"); Q-05 "Yes" (ADR-0000 Accepted); ADR-0001..ADR-0019 Accepted. ADR-0020 (storefront PIX prepayment, Q-65) was new on 2026-10-01 and Proposed; Gabriel accepted it on 2026-10-06 (project thread, 18:12Z). It is Build 4 work.
- [x] Docs live in one agreed place with a "where to look" map; legacy docs carry superseded banners. Evidence: backend repo `docs/` on `feature/ai-agent` (Q-01, ADR-0011) with `README.md` as the map, and the superseded banners on legacy docs (S2, 2026-09-29); notes on both public READMEs added 2026-10-01 (Q-10 a; BE `c8bf761`, FE `30bc172`).
- [x] Both `CLAUDE.md` files match the current decisions. Evidence: S2 commits of 2026-09-29 on both working branches (`CLAUDE.md`, `order-ui/CLAUDE.md`); re-check after S3 changes tooling. Re-checked 2026-10-01: the frontend tooling section, which still called `.claude/` machine-local, was updated after Q-06 and Q-07 (FE `30bc172`). Unticked 2026-10-01: after Gabriel's answer batch both files still called answered questions open (backend: the D-16 kept list without the Q-05 a additions, the Menu stock policy despite Q-42 c; frontend: Q-76, Q-54 and Q-29). Re-ticked 2026-10-01: corrected in BE `633bb23` and FE `c479b55`, then in a second review pass (FE `e4f60ef`; BE: the commit that re-ticks this item); the shared "Start here" sections are identical.
- [x] Frontend `.claude/` is in git; no Windows-only hooks; git rules enforced by `permissions.deny`. Evidence: backend part 2026-09-29 (Node hooks, `permissions.deny` in `.claude/settings.json`); frontend `.claude/` versioned by Gabriel on 2026-10-01 (FE `c4a7309`, Q-06 a, Q-07), its only hook `lint-typecheck.cjs` runs on Node; `permissions.deny` git rules added to `order-ui/.claude/settings.json` and the repo-root `.claude/settings.json` on 2026-10-01 (FE `30bc172`).
- [x] A fresh cloud session can run the full verification in both repos. Evidence: cloud session of 2026-09-29, backend `./gradlew test` 283 tests, 0 failures, with the Docker daemon started by `.claude/hooks/session-start.mjs` (which then also set the Gradle JDK path, a step BE `c8bf761` removed with `gradle.properties`, Q-84 a); frontend `yarn install && yarn lint && yarn build && yarn test` green. Re-run 2026-10-01 on BE `c8bf761` in a cloud session, from a clean export of the commit with an empty Gradle user home (no `org.gradle.java.home` anywhere; Gradle on `JAVA_HOME`): `./gradlew test` 283 tests, 0 failures. The frontend part was not re-run; FE `c4a7309` and `30bc172` change no app code, and frontend CI is green on `30bc172` (item 6).
- [x] CI runs `./gradlew test` and `yarn lint && yarn build && yarn test` on the working branches, green. Evidence (2026-09-29): backend CI run 36604877944 on `72cef45`; frontend CI run 36603662582 on `06de42e`. Re-checked 2026-10-01 after BE `c8bf761` removed the CI JDK override and `gradle.properties` (Q-84 a): backend CI run 36894501564 on `c8bf761`, frontend CI run 36894477010 on `30bc172`, both green.
- [ ] API conventions + Tenancy & Identity contract are Reviewed. (Menu and Order Core contracts gate their own steps, not this one.)
- [ ] S5 (scope of this run, NFR page, audit triage) Reviewed. Added 2026-10-01 (Q-08 b).
- [x] Project board set up (GitHub Projects since 2026-10-06, ADR-0021). Added 2026-10-01 (Q-83 b: "the first thing we do before begin working on the actual app"); runbook `ops/github-projects-setup.md` (Reviewed 2026-10-06). Evidence: Gabriel, project chat 2026-10-06T15:12Z: "Board setup done https://github.com/users/gtavaresdevs/projects/2"; his smoke-test issues #26 to #29 in the backend repo, all closed.

"Reviewed" = Gabriel read it and no open question blocks the next build step. Only Gabriel sets it (ADR-0016), with a message in the project thread ("Reviewed: <doc>") that Claude records (Q-09 a).

## Later list

Ideas that are not scheduled this run. Add new ones here with date and source; never schedule, design in detail or build them this run (ADR-0007).

| Item | Source | Register (Gabriel's answer, 2026-10-01) |
|---|---|---|
| Pricing and packages | MP §8d; ADR-0018 | Q-78 a: decided with the first paid tier |
| App hosting and domain | MP §8d | Q-79 a: chosen before the first pilot, after Q-20 (path `/r/{slug}`), with app and API on the same registrable domain |
| Edge build (also schedules the outbox and the core-module split) | MP §8d; ADR-0003, ADR-0019 | Q-80 b: built when Gabriel decides; an optional paid feature. Until then the work makes everything functional and keeps the Edge-ready constraints (ADR-0003) |
| Phase 10 comandas | MP §8d | Q-81 a: Later, with QR/table and waiter |
| Phase 11 polish | MP §8d | Q-82 a: moved out of Later; its small items ride alongside Build 3-4 as existing-feature fixes |
| Board setup details | ADR-0021 (was ADR-0017, Plane) | Q-83 b: moved out of Later into the plan; the "Board setup" step before Build 1 (Order of work) |

## How to update this file

- Update a step's Status in the same commit as the work that changes it (ADR-0021, carried over from ADR-0017). Status values: `Not started`, `In progress (started YYYY-MM-DD)`, `Partially done: <what>`, `Blocked: <Q-id or reason>`, `Done YYYY-MM-DD`, `Not scheduled`, `Out`.
- When a gate item is ticked, add the evidence on the same line (commit, doc and its status, or Gabriel's message date).
- Keep this file in line with MP §6-§7. The order of work and the gate are changed in MP §6-§7 and ADR-0007 (ADR-0007 Consequences), then mirrored here.
- Never invent or cite an issue number that does not exist; cite issues as `Refs #<n>`, never with a closing keyword (ADR-0021).
- The GitHub Project replaces this file once the open steps are moved onto it as issues (ADR-0021; Q-83). Gabriel set it up on 2026-10-06; the move has not happened yet. No history import, no empty future milestones, no automation code.

## Open questions

- Q-04..Q-10 (S1, MP §8a): answered 2026-10-01; Q-08 b put S5 in the gate, so Build 1 waits for S5 Reviewed.
- Q-13 (decided by Claude 2026-09-29, Gabriel may override): gate item 1's "amended architecture Accepted" is met when every amendment checklist line is ticked (accept, change or reject). Met on 2026-10-01 (commit `6a60b6d`).
- Nothing is open in the register since 2026-10-06: Gabriel answered Q-30 a (Build 1 creates the restaurant, the typed settings schema and the timezone; the other settings and delivery zones land in Build 4) and Q-86 a (pickup only with PIX prepayment inside a delivery zone; rules 54 and 56 stay in Build 4).
- Nothing waits for Gabriel's review since 2026-10-06 (project thread, 18:12Z): ADR-0020 Accepted; `planning/proposals/settings-and-notifications.md` (Q-24) and `ops/github-projects-setup.md` (Q-83, ADR-0021) Reviewed.
