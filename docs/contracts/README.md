# API contracts

> **SUPERSEDED (2026-09-29). Do not re-sync or follow the rules below.**
> The backend owns the API contract (ADR-0010). Live spec: `docs/api/openapi.yaml` in this repo, checked by a drift test. Design drafts: `docs/api/drafts/`.
> The snapshot next to this file is frozen at 0.3.0 and is never re-synced. The frontend manifest is frozen at 0.4.0 and only seeds the drafts. The `api-contract-sync` skill is retired.

## backend-integration-manifest.openapi.yaml

**This is a snapshot. It is not the source of truth.**

| | |
|---|---|
| Canonical location | `order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml` in the **frontend** repository |
| Snapshot of version | 0.3.0 (dated 2026-09-24) |
| Taken on | 2026-09-24 |
| Owner | the frontend repository |

The copy is kept **byte-identical** to the canonical file so re-syncing is a clean diff.

### Rules

- Never edit this file to record a backend decision. It is a mirror.
- When the backend must diverge from the contract, report it back so the canonical
  copy is patched. The manifest's own `x-maintenance` section defines how: patch only
  what the decision touches, bump `info.version`, add an `x-changelog` entry at the top.
- When the canonical file's version changes, re-copy the whole file and update the
  version row above.
- The `api-contract-sync` skill enforces this.

### Source-of-truth order (from the manifest's `x-maintenance`)

1. The live `/api/v3/api-docs` for anything marked `x-status: live` or `target-change`
2. The frontend's `src/types/*` and services for mock-backed target shapes
3. `docs/superpowers/specs/2026-09-09-restaurant-ops-redesign-design.md` in the frontend repo for the concept
