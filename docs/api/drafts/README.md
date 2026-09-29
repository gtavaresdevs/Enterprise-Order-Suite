# API contract drafts

- Status: Draft
- Updated: 2026-09-29 (S2; no draft files yet)
- Related: ADR-0010 (the backend owns the API contract), ADR-0011, ADR-0016, ADR-0007; register Q-32..Q-40, Q-76

This folder holds design-first OpenAPI 3 drafts: API shapes designed before the code exists (ADR-0010 step 1). The springdoc live spec only describes implemented endpoints, so Tenancy & Identity, Menu, Order Core and Storefront are designed here first.

## How drafts work

1. **One file per contract area** (Proposed: Q-40 option a, open until the API conventions doc settles it). Each YAML draft sits next to its prose contract doc in `docs/architecture/` (template: `docs/templates/contract-draft.md`). File names are fixed when each contract starts.
2. **Seeded from the frontend manifest 0.4.0.** Copy into the area's draft only the paths, schemas and `x-open-decisions` entries of that area. Do not copy the whole manifest here. The manifest is frozen and read-only: never edit it, bump its version or add changelog or open-decision entries (ADR-0010 step 6).
   - Manifest: FE `order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml` on `Claude-Assisted-Development` ([GitHub](https://github.com/gtavaresdevs/enterprise-order-suite-frontend/blob/Claude-Assisted-Development/order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml)).
   - Still-valid seeds: Menu, Tables, Orders, Delivery zones, RestaurantSettings and `/public/*` shapes (`docs/planning/superseded-docs.md` F3). Open manifest decisions to carry: `payment-sequencing`, `settings-images`.
   - Never seed from the backend `docs/contracts/` 0.3.0 snapshot (superseded, frozen).
   - Parts of the manifest are superseded (payment-processor checkout, automatic WhatsApp, the int64 `id-type`, the `created-at-format` config fallback): check `docs/adr/0000-legacy-decisions-triage.md` table F before seeding a shape.
3. **Status per ADR-0016.** A draft has the same Status as its prose contract doc (Draft, Reviewed or Ready); the prose doc holds the Status line and the `## Open questions` block. Only Gabriel sets Reviewed. Gabriel's answers are recorded in the contract doc and the draft, never in the frontend manifest.
4. **Conventions.** Money, enum casing, ids, pagination, idempotency, the error envelope and localized text come from the API conventions doc (S4) once it is Reviewed (register Q-33..Q-39). Until then a draft marks each of these as open and does not pick one.
5. **Implementation.** A build step implements a draft only after its contract doc is Reviewed (ADR-0007, ADR-0016).
6. **Graduation.** When an operation is implemented, the same backend commit regenerates `docs/api/openapi.yaml` and removes the operation from the draft or marks it graduated (ADR-0010 step 3). The contract doc's graduation log records the date and commit. The exact rule: Q-40.
7. **Live spec and drift test.** `docs/api/openapi.yaml` is springdoc's output for the running app, committed in git. One backend test fails when it differs from the live `/v3/api-docs` and runs inside `./gradlew test` (ADR-0010 step 4). Both are built in Build 1, as acceptance criteria; the first committed spec describes today's live endpoints. `docs/api/openapi.yaml` does not exist yet: do not create it or hand-edit it.
8. **Frontend.** The frontend generates its TypeScript types from the committed `docs/api/openapi.yaml` and never designs a request or response shape (ADR-0010 step 5). How frontend CI and sessions fetch the spec: Q-76. Whether the frontend may generate mock-backed types from a draft: Q-40.

## Rules

- Change drafts only by commits in this repo on `feature/ai-agent` (ADR-0011, ADR-0013).
- Every operation in a draft cites its seed (manifest path) or says "new".
- Never hand-edit `docs/api/openapi.yaml` to make the drift test pass (ADR-0010).
- Do not invoke the retired `api-contract-sync` skill.

## Open questions

- Q-40: draft granularity, graduation rule, frontend use of drafts. Blocks the API conventions doc.
- Q-32: when breaking API changes stop being free. Blocks the API conventions doc.
- Q-76: how the frontend gets the backend spec. Blocks Build 1 acceptance (generated types).
- How the committed spec is regenerated and how the drift test compares it (semantic vs byte compare): Build 1 detail (ADR-0010).
