---
name: api-contract-sync
description: Use before creating or changing any REST endpoint, request body or response shape - checks it against the frontend's backend-integration manifest and defines how to report a necessary divergence back to the canonical copy.
---

# Keeping the API in sync with the contract

The endpoint shapes this backend serves are owned by the frontend's OpenAPI manifest, not by
this repository. The frontend is already running against them on mock data — an endpoint that
does not match is an endpoint that will not be consumed.

## The snapshot is a mirror, not the truth

| | |
|---|---|
| Snapshot here | `docs/contracts/backend-integration-manifest.openapi.yaml` |
| Canonical copy | the **frontend** repository — see `docs/contracts/README.md` |

The snapshot is kept byte-identical so re-syncing is a clean diff. **Never edit it to record
a backend decision.** An edit here does not reach the frontend; it just makes the next
re-sync look like a conflict.

## Before adding or changing an endpoint

Find it in the manifest. Match all five: the path, the method, the request shape, the
response shape, and the status codes. Then read its `x-status`:

| `x-status` | Meaning |
|---|---|
| *(no marker)* | Mock-backed target contract. Not built yet — this is the default. |
| `live` | Verified against the running backend. |
| `target-change` | Exists today, but the frontend needs it changed. |
| `proposed` | New resource; confirm the shape with the user before building. |

**The base path is `/api`, with no version segment.** `server.servlet.context-path` is
`${SERVER_CONTEXT_PATH}` in `application.yml`. The manifest records this under
`x-open-decisions` as `base-path`, `decided-2026-09-19` — do not reintroduce `/v1`.

## When the backend must diverge

**Stop and tell the user.** Then report the divergence back so the canonical copy is patched.
The manifest's own `x-maintenance` rules define how:

- Patch **only** what the decision touches. Never rewrite the file.
- Bump `info.version` — patch for a clarification, minor for something additive, major for a
  breaking change.
- Add an `x-changelog` entry **at the top**, with the date, the version, what changed and why.
- Never delete a superseded decision; supersede it in place.

Then re-copy the whole file into `docs/contracts/` and update the version row in its README.

## Unresolved decisions belong in `x-open-decisions`

Not silently in a schema. These are open right now — if your work depends on one, raise it
rather than deciding it in code:

| id | The question |
|---|---|
| `id-type` | int64 ids (live) vs string ids (frontend types). Blocks every schema. |
| `category-identity` | Categories addressed by name; a stable id may be added later. |
| `created-at-format` | `Order.createdAt` date-only vs datetime + restaurant timezone. |
| `payment-sequencing` | Pay-then-create vs create-then-pay; depends on a processor still TBD. |
| `settings-images` | Storefront logo/cover need an upload endpoint before real URLs. |
| `dev-cookie-secure` | `Secure` on the refresh cookie must be configurable for local HTTP. |

The first four block schemas and are resolved in migration phase 0. The last two are external
dependencies.

## When a mock-backed endpoint goes live

Verify the built endpoint against the running `/api/v3/api-docs`, then report the `x-status`
flip to `live` — noting any drift you found in the `x-changelog`.

## Red flags — stop

- You are about to design a request or response shape from scratch. Check the manifest first;
  it probably already exists.
- You are about to edit the snapshot to make it agree with the code.
- You are about to pick an id type, a date format or a category key. Those are open decisions,
  not implementation details.
