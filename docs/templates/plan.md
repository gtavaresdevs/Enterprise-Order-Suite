<!--
Template: implementation plan. Copy to docs/superpowers/plans/YYYY-MM-DD-<slug>.md in the repo whose code it
changes (ADR-0011). Keep every heading; write "none" when empty. Delete this comment.
A plan implements a spec section that is Reviewed or Ready (ADR-0016); it never starts from a Draft.
Build 1 plans also require every readiness-gate item in docs/roadmap.md to be checked (ADR-0015).
-->
# Plan <roadmap step id>: <title>

- Roadmap step: <S3 | Build 1 | Build 2 | Build 3 | Build 4> (`docs/roadmap.md`)
- Implements: [`docs/<path>.md` §<section>](<link>), Status at plan time: <Reviewed | Ready>
- Status: Draft | Reviewed | Ready
- Updated: YYYY-MM-DD
- Repo and branch: <backend `feature/ai-agent` | frontend `Claude-Assisted-Development`>
- Related: <ADR ids>; register <Q-ids>

## Acceptance criteria
(copied from the spec section with its numbering; the plan is done only when every row passes)
| # | Criterion | Spec ref | Verified by (test or command) |
|---|---|---|---|

## Goal
(one or two sentences)

## Out of scope
(what this plan does not do, and where it goes instead)

## Tasks
### Task 1: <name>
- Files:
- Steps: (write the failing test first where it applies)
- Verify:

## Verification
- Backend: `./gradlew test` (full suite; legacy D5).
- Frontend: `yarn lint && yarn build` (plus `yarn test` once S3 adds Vitest).

## Updates in the same commit as the work
- `docs/roadmap.md`: the step's Status line (ADR-0017).
- `docs/api/openapi.yaml` and the draft's graduation, if an endpoint changed (ADR-0010).
- Register rows answered, glossary terms settled, ADRs that cite them.

## Git
Commit straight to the working branch with explicit pathspecs and push; never merge, stash or force push (ADR-0013).

## Open questions
- Q-xx: <question>. Blocks: <task or "none">.

## Sources
