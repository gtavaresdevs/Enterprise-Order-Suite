# ADR-0010: The backend owns the API contract
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
- Supersedes: legacy "Contract ownership" (frontend manifest canonical, `2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L175-179); manifest `x-maintenance` canonical-copy and source-of-truth order (manifest 0.4.0 L1005-1007); frontend lockstep rule (`order-ui/CLAUDE.md` L81-88); backend `api-contract-sync` skill; backend `docs/contracts/` snapshot; ai-ready R13a cross-repo export PR and gate item L90
- Related: ADR-0007, ADR-0008, ADR-0009, ADR-0011, ADR-0012, ADR-0015, ADR-0016

## Context
- D-9 (MASTER-PLAN L42): the backend owns the API contract; one shared place for docs that both repos and every agent read. `planning/pm-tool-recommendation.md` L61: "The frontend manifest stops being canonical."
- Today the frontend owns it:
  - Canonical manifest `order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml`, version 0.4.0 (L4). Its `x-maintenance.canonical-copy` names the frontend file (L1006); its source-of-truth order puts frontend `src/types/*` second (L1007).
  - `order-ui/CLAUDE.md` L81-88: any change the backend must provide patches that file in the same commit.
- The backend mirrors it:
  - `enterprise-order-suite/docs/contracts/backend-integration-manifest.openapi.yaml`, a byte-identical snapshot of 0.3.0 (`docs/contracts/README.md` L5-14). It has already drifted from the canonical 0.4.0.
  - `.claude/skills/api-contract-sync/SKILL.md` L8-21: endpoint shapes "are owned by the frontend's OpenAPI manifest"; never edit the snapshot.
  - Backend `CLAUDE.md` L20 (skill table) and L94-95 (snapshot pointer).
- springdoc is configured: `build.gradle` L77 (`springdoc-openapi-starter-webmvc-ui:2.8.15`), `src/main/java/com/enterprise/ordersuite/api/OpenApiConfig.java`, `application.yml` L123-128 (`api-docs.path: /v3/api-docs`, served under the `/api` context path, so `/api/v3/api-docs`). springdoc describes only implemented endpoints, but the Tenancy, Menu and Order Core contracts must be designed before code.
- Nothing exists yet for the target flow: no committed live spec, no drift test, no `openapi-typescript` (frontend `package.json` scripts: `dev`, `build`, `lint`, `preview`).
- Manifest 0.4.0 already carries most target shapes (Menu, Tables, Orders, Delivery zones, Restaurant settings, `/public/*`) and the open decisions `payment-sequencing` (L994-996) and `settings-images` (L997-999).
- MASTER-PLAN §4 (L58) replaced the planned GitHub Action that opened PRs into a docs-hub repo (`planning/ai-ready-development-plan.md` R13a L63; pm-tool L104) with one backend test plus frontend generation.

## Decision
Accepted (D-9). The mechanism is MASTER-PLAN §4 L58, a Claude simplification that Gabriel accepted on 2026-09-29 (Q-03 row 3.2). Paths follow ADR-0011 (backend repo `docs/`, Q-01 answered 2026-09-29).

Target flow:
1. **Draft (design-first).** A contract under design lives in `docs/api/drafts/` as OpenAPI 3 YAML, next to its prose contract doc in the docs tree. Drafts are seeded from manifest 0.4.0 (paths, schemas, `x-open-decisions`), never from the 0.3.0 snapshot. Only backend-repo commits change drafts.
2. **Review.** A build step implements a draft only after its contract doc is Reviewed (ADR-0016). Gabriel's answers are recorded in the contract doc and draft, not in the frontend manifest.
3. **Implementation.** The backend implements the Reviewed endpoints. The same commit regenerates `docs/api/openapi.yaml` and removes the implemented parts from the draft (or marks them graduated).
4. **Committed live spec + drift test.** `docs/api/openapi.yaml` is springdoc's output for the running app, committed in git. One backend integration test boots the app, reads `/v3/api-docs` (YAML), and fails if it differs from the committed file. It runs inside `./gradlew test`, so the D5 full-suite rule and CI enforce it.
5. **Frontend generation + CI drift check.** The frontend generates TypeScript types from the backend's committed `docs/api/openapi.yaml` with `openapi-typescript` (for example `yarn gen:api`) and commits the generated file. Frontend CI regenerates and fails on any diff. Hand-written API types in `src/types/*` are deleted feature by feature as each feature moves from mock to the real backend (ai-ready R13b L70).
6. **Frozen manifest.** The frontend manifest stays at 0.4.0, read-only, with a superseded banner (S2). No version bump, no `x-changelog` entry, no new `x-open-decisions`.

Retired (marked superseded, not deleted; MASTER-PLAN §4 L68):
- `order-ui/CLAUDE.md` L81-88 "Backend integration manifest stays in lockstep too": removed in the S2 CLAUDE.md rewrite.
- `enterprise-order-suite/.claude/skills/api-contract-sync/SKILL.md`: removed from the backend `CLAUDE.md` skill table (L20) in S2; the skill is deprecated or deleted in S3. A contract-design skill replaces it later, written from how the first contract batch actually went (ai-ready R6 L53).
- `enterprise-order-suite/docs/contracts/` (0.3.0 snapshot + README): superseded banner; never re-synced to 0.4.0; backend `CLAUDE.md` L94-95 pointer removed.
- Manifest `x-maintenance` rules (canonical copy, source-of-truth order, patch-and-bump): no longer apply.
- ai-ready R13a CI PR into a docs hub (L63) and gate item "backend change -> exported spec -> docs hub -> regenerated frontend types" (L90): dropped.

## Consequences
- Every bullet here is Accepted: ownership (D-9), the drift-test mechanism and generated frontend types (Q-03 row 3.2) and the `docs/` paths (Q-01).
- Agents must:
  - change API shape only in the backend repo (draft or code);
  - regenerate and commit `docs/api/openapi.yaml` in the same commit as any endpoint change;
  - run `./gradlew test` (includes the drift test) before claiming done;
  - regenerate and commit frontend types after the backend spec changes.
- Agents must never:
  - edit the frontend manifest or the backend snapshot to record a decision;
  - hand-edit `docs/api/openapi.yaml` to make the drift test pass;
  - hand-edit generated frontend types;
  - design a request or response shape in the frontend repo.
- The frontend `connect-backend` skill (on Gabriel's machine only; `order-ui/CLAUDE.md` L75-78) must read the committed spec and generated types (ai-ready R8f L67).
- `flyway-migrations/SKILL.md` L73-74 points to the `docs/contracts/` manifest for target enum names; repoint to the drafts (see ADR-0009).
- The drift test, the committed `openapi.yaml` and frontend generation are acceptance criteria of Build 1 (MASTER-PLAN §4 L64, §6 L93), not readiness-gate items. The first spec committed will describe today's live endpoints (auth, users, roles, admin, legacy `/orders`, `/products`).
- Draft graduation rules, money type, enum casing, ids, pagination, `Idempotency-Key` and the error envelope are set by the API conventions doc (MASTER-PLAN §5 L75); drafts follow it once it is Reviewed.
- Q-01 chose the backend repo, so drafts, prose contract docs and `docs/api/openapi.yaml` sit in the same repo as the code the drift test checks (ADR-0011).

## Open questions
- Q-01 (docs location) and Q-03 row 3.2 (drift-test flow instead of the cross-repo export): answered 2026-09-29, accepted.
- How frontend CI and local frontend sessions obtain the backend spec (raw file from the public backend repo on `feature/ai-agent`, a pinned commit, or an attached checkout): S3 / Build 1. Gabriel's Q-01 answer says the frontend fetches the docs from the backend repo (ADR-0011). Register: Q-76.
- How the committed spec is regenerated (a Gradle task, or a test mode that rewrites the file) and how the drift test normalizes output (semantic YAML compare vs byte compare): Build 1 detail.
- Whether the frontend may generate types from drafts to keep building mock-backed features before endpoints exist: API conventions (S4), Q-40.
- Draft granularity (one file per contract area or one file) and the exact graduation rule: API conventions (S4), Q-40.
- Versioning policy for the live spec before and after the first real restaurant or first Edge client: API conventions (S4), Q-32.

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §3 D-9 (L42), §4 contract row (L58), ArchUnit row (L64), superseded row (L68), §5 API conventions (L75), §6 S2/S3/Build 1 (L90-93)
- `/mnt/project-files/planning/pm-tool-recommendation.md` L61, L98, L104, L106
- `/mnt/project-files/planning/ai-ready-development-plan.md` R6 (L53), R13a (L63), R8f (L67), R13b (L70), gate L90
- `/mnt/project-files/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` "Contract ownership" L175-179
- `/mnt/project-files/2026-09-14-backend-integration-manifest.openapi.yaml` (= `order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml`, 0.4.0) L4, L978-1007
- `/mnt/project-files/backend-integration-manifest.openapi.yaml` (= backend `docs/contracts/` snapshot, 0.3.0) L4; `/mnt/project-files/README.md` (= backend `docs/contracts/README.md`) L5-24
- Backend `enterprise-order-suite` (`feature/ai-agent` @ `af2634e`): `CLAUDE.md` L20, L94-95; `.claude/skills/api-contract-sync/SKILL.md` L8-21, L69-72; `.claude/skills/flyway-migrations/SKILL.md` L73-74; `build.gradle` L77; `src/main/java/com/enterprise/ordersuite/api/OpenApiConfig.java`; `src/main/resources/application.yml` L123-128
- Frontend `order-ui` (`Claude-Assisted-Development` @ `14a3cfd`): `CLAUDE.md` L75-78, L81-88; `package.json` scripts
