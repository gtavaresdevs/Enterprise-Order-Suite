<!--
Template: SDD section (software design: a section of the architecture root, API conventions, or a design note
that is not an endpoint contract; for an endpoint contract use contract-draft.md).
Copy into docs/architecture/<name>.md. Keep every heading; write "none" when empty. Delete this comment.
Status (ADR-0016): Draft by default; only Gabriel sets Reviewed; a build step uses only Reviewed or Ready.
Check every ADR the section touches first (docs/adr/README.md); do not contradict an Accepted ADR.
-->
# <Design area>: <section title>

- Status: Draft | Reviewed | Ready
- Updated: YYYY-MM-DD
- Reviewed: <"Reviewed by Gabriel on YYYY-MM-DD" or "not yet">
- Roadmap step: <S4 | Build N> (`docs/roadmap.md`)
- Related: <ADR ids>; register <Q-ids>

## Context
(problem; current code with paths on the working branches; constraints from ADRs)

## Decisions
| # | Decision | Decided by | Source |
|---|---|---|---|
(Decided by: "Gabriel YYYY-MM-DD" with his words quoted in the register, or "Claude (Q-xx, Decided by Claude YYYY-MM-DD), Gabriel may override")

## Design
(model, flows, invariants; ids are ULIDs (ADR-0009); restaurant-owned data carries the restaurant id (ADR-0001))

## Tenancy and security
(restaurant scoping; who may call what; authorization only in `@PreAuthorize`; cross-tenant and leak tests)

## Edge-ready constraints check
(ADR-0003 C1-C6: which apply here and how; "none apply" is a valid answer)

## Consequences for code
(what agents must / must never do in code that follows this section)

## Acceptance criteria
(testable; required before the doc is Ready)

## Open questions
- Q-xx: <question>. Blocks: <step or "none">.

## Sources
