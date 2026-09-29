<!--
Template: contract doc for one contract area (for example Tenancy & Identity, Menu, Order Core, Storefront).
The prose lives in docs/architecture/ (ADR-0011 layout); the OpenAPI draft lives in docs/api/drafts/ (ADR-0010;
see docs/api/drafts/README.md). File names are fixed when each contract starts.
Keep every heading; write "none" when empty. Delete this comment.
Rules: change shapes only in the backend repo; seed from the frontend manifest 0.4.0 (frozen), never from the
docs/contracts/ 0.3.0 snapshot; follow the API conventions doc once it is Reviewed, and until then leave
money, casing, ids, pagination, idempotency and errors marked open (Q-33..Q-40).
Ask Gabriel his questions in his language while drafting; record answers in English with the date (ADR-0012, ADR-0016).
The build step for this area starts only after this doc is Reviewed (ADR-0007).
-->
# <Area> contract

- Status: Draft | Reviewed | Ready
- Updated: YYYY-MM-DD
- Reviewed: <"Reviewed by Gabriel on YYYY-MM-DD" or "not yet">
- Roadmap step: <Build N> (`docs/roadmap.md`)
- Draft spec: `docs/api/drafts/<area>.yaml`
- Seeded from: frontend manifest 0.4.0 <paths and schemas>, or "none"
- API conventions: <doc path and its Status>
- Related: <ADR ids>; register <Q-ids>

## Scope
(what this contract covers and what it leaves to other contracts)

## Decisions
| # | Decision | Decided by | Source |
|---|---|---|---|

## Resources
| Resource | Field | Type | Required | Rule | Source |
|---|---|---|---|---|---|

## Operations
| Method | Path | Caller (role or public) | Restaurant resolved from | Idempotent | Draft state |
|---|---|---|---|---|---|
(Draft state: "draft", or "graduated YYYY-MM-DD" once live in docs/api/openapi.yaml)

## Lifecycle and rules
(status transitions, server-derived values, snapshots; cite ADR-0002, ADR-0003, D-16)

## Tenancy and security
(scoping, roles, public-endpoint obligations, cross-tenant and leak tests)

## Errors
| Code (SCREAMING_SNAKE) | HTTP | When |
|---|---|---|

## Offline and retries
(ADR-0003 C1-C3, ADR-0004; "not applicable" when the area creates no orders)

## Acceptance criteria
(testable; required before the doc is Ready)

## Graduation log
| Date | Operations | Commit |
|---|---|---|

## Open questions
- Q-xx: <question>. Owner: Gabriel | Claude decides. Blocks: <step or "none">.

## Sources
