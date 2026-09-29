# Roadmap (interim tracker)

- Status: Draft
- Updated: 2026-09-29
- Role: the project tracker until Gabriel sets up Plane (ADR-0017). Order of work and gate: `planning/MASTER-PLAN.md` §6-§7 (ADR-0007, ADR-0015). Where this file and MP disagree, MP wins; fix this file in the same commit.
- Related: `README.md`, `planning/open-questions.md`, ADR-0007, ADR-0015, ADR-0016, ADR-0017

## Order of work

| Step | What | Owner | Blocks on | Status |
|---|---|---|---|---|
| S0 | Decisions baseline: ADRs (new + legacy triage), architecture amendment proposal, open-questions register, superseded-docs list, memory cleanup | Claude | none | Done 2026-09-29 |
| S1 | Gabriel's review: accept the ADRs and the architecture amendments; answer register Q-01..Q-10; push the frontend `.claude/` (Q-06, Q-07); Plane signup whenever convenient | Gabriel | S0 | Partially answered: Q-01..Q-03 answered 2026-09-29; Q-04..Q-10 open |
| S2 | Docs skeleton in the backend repo `docs/` (ADR-0011): `README.md`, `roadmap.md`, ADRs, glossary seed, templates, superseded banners on legacy docs; rewrite both `CLAUDE.md` files | Claude | S1 Q-01..Q-03 (answered). Parts wait on Q-04, Q-05, Q-09, Q-10 | Partially done: skeleton, ADRs, banners and both `CLAUDE.md` rewrites pushed 2026-09-29; waiting on Q-04 (apply amendments), Q-05 (legacy triage), Q-09 (Reviewed signal), Q-10 (public README notes) |
| S3 | Readiness tooling: version the frontend `.claude/`, port or remove the PowerShell hook, `permissions.deny` git rules, SessionStart setup, frontend lint baseline, Vitest, CI running today's checks in both repos | Claude (+ Gabriel for the S1 push) | S2 | Not started |
| S4 | Cross-cutting design: API conventions doc, finalize the Edge-ready constraints (drafted in ADR-0003), then the Tenancy & Identity contract with Gabriel's question batch (register group 2) | Claude + Gabriel | S2 | Not started |
| S5 | Scope of this run (short PRD) + NFR page + audit backlog triage | Claude + Gabriel | S4 | Not started |
| Build 1 | Tenant foundation. Acceptance includes: ArchUnit tenant rule, cross-tenant tests, contract drift test, dev/test seed data, tenant id in logs | Claude | Readiness gate below. Whether it also waits for S5: Q-08 | Not started |
| Build 2 | Menu | Claude | Menu contract Reviewed | Not started |
| Build 3 | Order Core | Claude | Order Core contract Reviewed | Not started |
| Build 4 | Storefront <-> Order Core | Claude | Storefront contract Reviewed | Not started |
| Later | QR/table, waiter, KDS on Edge, POS, tablets, Edge build | none | Not scheduled this run (ADR-0007) | Not scheduled |
| Out | PSP, automatic WhatsApp, fiscal, marketplaces, billing | none | Not this run (ADR-0005, ADR-0006, ADR-0007) | Out |

Small existing-feature fixes from the audit ride alongside Build 2-4, as triaged in S5 (MP §6).

## Readiness gate (before Build 1)

From MP §7. Tick an item only when it holds (repo items: on the working branches of both repos). Items that need Gabriel (Accepted, Reviewed) are ticked only on his word.

- [ ] ADRs and the amended architecture are Accepted by Gabriel.
- [ ] Docs live in one agreed place with a "where to look" map; legacy docs carry superseded banners.
- [x] Both `CLAUDE.md` files match the current decisions. Evidence: S2 commits of 2026-09-29 on both working branches (`CLAUDE.md`, `order-ui/CLAUDE.md`); re-check after S3 changes tooling.
- [ ] Frontend `.claude/` is in git; no Windows-only hooks; git rules enforced by `permissions.deny`.
- [ ] A fresh cloud session can run the full verification in both repos.
- [ ] CI runs `./gradlew test` and `yarn lint && yarn build && yarn test` on the working branches, green.
- [ ] API conventions + Tenancy & Identity contract are Reviewed. (Menu and Order Core contracts gate their own steps, not this one.)

"Reviewed" = Gabriel read it and no open question blocks the next build step. Only Gabriel sets it (ADR-0016).

## Later list

Ideas that are not scheduled this run. Add new ones here with date and source; never schedule, design in detail or build them this run (ADR-0007).

| Item | Source | Register |
|---|---|---|
| Pricing and packages | MP §8d; ADR-0018 | Q-78 |
| App hosting and domain | MP §8d | Q-79 |
| Edge build criteria (also schedules the outbox and the core-module split) | MP §8d; ADR-0003, ADR-0019 | Q-80 |
| Phase 10 comandas (placement open: this run or Later) | MP §8d | Q-81 |
| Phase 11 polish (placement open: this run or Later) | MP §8d | Q-82 |
| Plane setup details | ADR-0017 | Q-83 |

## How to update this file

- Update a step's Status in the same commit as the work that changes it (ADR-0017). Status values: `Not started`, `In progress (started YYYY-MM-DD)`, `Partially done: <what>`, `Blocked: <Q-id or reason>`, `Done YYYY-MM-DD`, `Not scheduled`, `Out`.
- When a gate item is ticked, add the evidence on the same line (commit, doc and its status, or Gabriel's message date).
- Keep this file in line with MP §6-§7. The order of work and the gate are changed in MP §6-§7 and ADR-0007 (ADR-0007 Consequences), then mirrored here.
- Never invent or cite `EOS-<n>` ids before Plane exists (ADR-0017).
- Plane replaces this file only when Gabriel sets Plane up (ADR-0017; Q-83). No history import, no empty future modules, no automations.

## Open questions

- Q-08: does Build 1 wait until S5 is Reviewed? Blocks the Build 1 start rule.
- Q-13 (decided by Claude 2026-09-29, Gabriel may override): gate item 1's "amended architecture Accepted" is met when every amendment checklist line is ticked (accept, change or reject).
- Q-04..Q-10: open S1 questions (MP §8a); each blocks the item named in the register.
