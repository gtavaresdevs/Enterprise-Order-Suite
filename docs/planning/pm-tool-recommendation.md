# Project management tool for Enterprise Order Suite

> **Superseded where it differs from `planning/MASTER-PLAN.md` (2026-09-29).** Superseded on 2026-10-06 (ADR-0021): the Plane choice itself, replaced by GitHub Projects. Still useful: the board structure (states and labels, adapted in `ops/github-projects-setup.md`), the tenancy comparison (ADR-0001), the architecture review as background. Superseded on 2026-10-06: self-hosting itself, with the hosting section ("Free VPS: Oracle Cloud Always Free") and the Community Edition notes (CE Pages/Intake 404s, GitHub sync as paid) - Plane then moved to Plane Cloud's free plan (ADR-0017), and was replaced the same day. Also superseded: the per-restaurant Edge in "Scope" (ADR-0003), the docs-hub repo (ADR-0011), the cross-repo export Action (ADR-0010), packages as plans (ADR-0018), board modules, cycles and history import (ADR-0017), revised-order rows 3, 5 and 6 (ADR-0007, ADR-0019, ADR-0003).

Checked 2026-09-29. Research only: nothing installed or configured yet.

## Recommendation: Plane (Community Edition, self-hosted with Docker)

Plane is an open source (AGPL-3.0) Jira/Linear-style tool with board, list, calendar, spreadsheet and Gantt views out of the box. It is the easiest of the candidates to *look at*, and it has the best path for AI agents.

**Why Plane over the others**

| Tool | Visual | AI agent access | Verdict |
|---|---|---|---|
| **Plane CE** | Kanban, list, Gantt, calendar, spreadsheet; cycles (sprints) and modules (epics) | Official MCP server ([makeplane/plane-mcp-server](https://github.com/makeplane/plane-mcp-server)) + REST API with personal access tokens | **Pick** |
| OpenProject | Strong Gantt, heavier UI | Built-in MCP server exists, but only on paid Enterprise plans ([docs](https://www.openproject.org/docs/system-admin-guide/integrations/mcp-server/)); community MCP servers otherwise | Good for waterfall/PMO, heavier than a solo dev needs |
| Taiga | Nice Scrum/Kanban | REST API only, community MCP servers | Development slower, weaker roadmap views |
| Leantime | Friendly, non-dev oriented | Has an MCP server | Aimed at non-technical PMs, weaker for dev + API work |
| Focalboard | Simple boards | Effectively unmaintained | Skip |

**Caveats on Plane CE (verified):**
- The official MCP server runs against CE in local (stdio) mode with a personal access token; OAuth is Commercial-only ([self-host docs](https://developers.plane.so/dev-tools/mcp-server-self-host)).
- On CE, work items, comments, sub-items, cycles, modules, members work; **Pages and Intake tools return 404** and PQL filtering is unsupported ([issue #171](https://github.com/makeplane/plane-mcp-server/issues/171)). This is fine for us because docs stay as markdown in the repos, not in Plane pages.
- Native GitHub sync (PRs/commits moving work items) is a Pro feature ([docs](https://developers.plane.so/self-hosting/govern/integrations/github)). On CE we link by convention instead (below), and can add a small GitHub Action later that calls the Plane API when a PR merges.

## How AI agents read and update it

1. **MCP**: each Claude Code session (local or Remote Control) registers the Plane MCP server with a personal access token. Agents can list "Ready for agent" items, move state, comment progress, create sub-items.
2. **Convention in `CLAUDE.md` of both repos**: every branch, commit and PR title carries the work item id (e.g. `EOS-42`). The PR link is pasted as a comment on the item. The plan file path goes in the item description.
3. **Docs stay in git**: PRD, SDD, API contract, plans and roadmaps live as markdown/OpenAPI in the repos. Plane items *link* to them; Plane never holds the canonical text. This keeps agents reading the same files they already use.
4. **Reachability**: cloud agent sessions cannot reach a Plane running only on your PC's `localhost`. Either host it on a small VPS/public URL, or have agents that touch Plane run on your device via Remote Control. (See question 1.)

## Proposed board structure

One workspace `Enterprise Order Suite`, one project `EOS` (prefix `EOS-`). One project keeps a solo+agents setup simple; backend/frontend split is done with labels.

**Modules (epics)**
- Documentation: `PRD`, `SDD`, `API contract`
- Feature areas: `Auth`, `Menu`, `Tables`, `Orders`, `KDS`, `Storefront (public menu + checkout)`, `Delivery & WhatsApp`, `Payments`, `Administration`, `Home & Analytics`, `i18n`, `Notifications`
- Cross-cutting: `Audit fix pass (business rules Stage 2)`, `Integrations (PSP, WhatsApp provider)`, `Dev tooling & agent workflow`

**Cycles (phases)**
- Historical: frontend phases 0–9 and backend phases 0–1 imported as completed cycles, one work item per phase with its plan file linked. This is the "what exists" record.
- Forward: one cycle per upcoming phase (docs first, then audit fix pass, then backend phases 2+).

**States**: Backlog → Spec'd → Ready for agent → In progress → In review (PR open) → Done, plus Blocked.

**Labels**: `area:backend`, `area:frontend`, `area:docs`; `type:feature|bug|doc|chore`; `contract-change` (touches the OpenAPI manifest); `needs-human` (decision or approval only you can give).

**Views to save**: Board by state (daily driver), Gantt by cycle (roadmap), "Ready for agent" list (what agents pick from), "Blocked / needs-human" list (your inbox).

## Suggested extra threads (besides PRD, SDD, API contract)

1. **Plane setup and import**: stand up Plane, create the structure above, import the existing roadmap and the six known bugs from the business-rules audit.
2. **Agent workflow conventions**: the `CLAUDE.md` rules both repos share (work item ids, where plans go, how agents claim and close items).
3. **Audit fix pass backlog**: turn the "Resumo de prioridades" list from the business-rules audit into work items so it is planned, not lost.

## Decisions (Gabriel, 2026-09-29)

- **Hosting**: Plane on a free VPS.
- **Scope**: multi-restaurant, shared multi-tenant SaaS plus a per-restaurant Restaurant Edge for offline operation. Source: `architecture/RESTAURANT-OPS-ARCHITECTURE.md`.
- **This run**: improve existing features. Payment gateway and automatic WhatsApp are out.
- **Contract owner**: the backend. The frontend manifest stops being canonical.
- **Docs language**: English only.
- **Git rules for agents**: push allowed, never merge. Always read the working branches, not `main`: backend `feature/ai-agent`, frontend `Claude-Assisted-Development`.

## Free VPS: Oracle Cloud Always Free (Ampere A1, ARM)

Plane needs 2 cores and 4 GB RAM minimum, 8 GB recommended, and runs on ARM64 ([Plane docs](https://developers.plane.so/self-hosting/methods/docker-compose)). Oracle's Always Free A1 is the only free tier that fits: 2 OCPU and 12 GB RAM, 200 GB block storage ([Oracle docs](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)). Google's e2-micro and AWS's free t-micro instances have 1 GB RAM and cannot run Plane.

Caveats:
- Oracle halved the A1 allowance on 2026-06-15 without announcement ([InfoQ](https://www.infoq.com/news/2026/07/oracle-cloud-free-tier-limits/)). The free tier can change again, so back up Plane's Postgres and uploads off the box.
- Idle instances are reclaimed if CPU, network and memory all stay under 20% for 7 days. Plane's stack keeps memory above that on a 12 GB box, so this should not trigger, but it is worth watching.
- A1 capacity is sometimes unavailable in popular regions at signup; retrying or choosing a less busy home region helps. The home region cannot be changed later.
- Needs a card for identity verification; Always Free resources are not charged.
- For agents in cloud sessions to reach it, expose it over HTTPS (Caddy with a free DuckDNS subdomain, or a Cloudflare Tunnel).

## Tenancy: shared multi-tenant SaaS with plan-based packages (decided)

Today the design is explicitly **one deployment per restaurant** (`2026-09-09-restaurant-ops-redesign-design.md`, Decisions: Tenancy). Two ways to go multi-restaurant:

| | Instance per restaurant | Shared multi-tenant (recommended) |
|---|---|---|
| What it is | Each restaurant gets its own server + database | One deployment; every row carries `restaurant_id` |
| "Packages" | Different builds/configs per customer | Plans (Basic/Pro/...) = feature flags per tenant |
| Ops cost | Grows with every customer (N servers, N upgrades) | Flat |
| Isolation | Strong by default | Must be enforced in every query |
| Change needed now | Almost none | Tenant context in auth + `restaurant_id` on every table |

Recommendation: shared multi-tenant, with packages as subscription plans that switch features on per restaurant. The audit already flagged that the current tenant boundary is fragile (`2026-09-20-orders-authorization-fix.md`, `2026-09-20-test-failures-and-audit-followups.md`), so tenant scoping should be designed deliberately in the SDD before feature work adds more tables. The backend Phase 0 design already kept settings in a row so they can move to a tenant (`2026-09-24-restaurant-ops-phase-0-foundation-design.md`).

## Shared docs hub: one place both teams and every agent read

New repo `enterprise-order-suite-docs` (not created yet):

```
README.md            <- "where to look" index, read first
prd/                 <- product requirements
sdd/                 <- system design (backend, frontend, tenancy)
api/openapi.yaml     <- the contract, owned by the backend
adr/                 <- one file per architecture decision
business-rules/      <- the business-rules master (EN)
roadmap/             <- phase status, pointing to Plane cycles
```

- The backend owns `api/openapi.yaml`. Changes start as a backend PR; a GitHub Action in the backend exports the spec from springdoc and opens a PR into the docs repo, so the contract always matches the running API.
- Both code repos' `CLAUDE.md` start with the same short "where to look" table pointing at the docs repo, so agents never guess.
- The frontend's current manifest becomes a read-only consumer copy, then is retired.


## Architecture review (Gabriel's Cloud SaaS + Restaurant Edge proposal)

Full text: `architecture/RESTAURANT-OPS-ARCHITECTURE.md`. The direction is sound: one Order Core with `channel` + `source`, one menu, client-generated ULIDs, honest `PENDING` for anything external. Five gaps to settle in the design docs before code:

1. **One core, two runtimes.** If the Edge re-implements order rules, cloud and Edge will drift. The Order/Menu core should be one backend module (plain Java, no web or tenant plumbing inside) deployed twice: in the cloud app (multi-tenant) and in the Edge app (fixed to one restaurant). This is what makes `OrderApplicationService.createOrder(...)` real in both places.
2. **Ownership per entity, not just "who is authoritative when".** Section 16 says the Edge is authoritative while offline, but the cloud keeps taking storefront delivery orders and menu edits during that time. `OFFLINE-SYNC-DESIGN.md` needs a table: menu and configuration are cloud-owned (Edge read-only cache; maybe a local "86 this item" availability override); dine-in orders created at the Edge are Edge-owned; delivery orders are cloud-owned and pushed down. Plus what happens when both sides touched the same order (status transitions should only move forward, so the later valid transition wins).
3. **Event log, not row copying.** Sync should replay domain events (OrderCreated, ItemAdded, StatusChanged) through an outbox with an idempotency key per event. That also feeds KDS and analytics later.
4. **ID migration.** Section 15 needs client-generated ULIDs. If the backend's current tables use database sequences (to check on `feature/ai-agent`), switching is a Flyway migration best done now, while data is small.
5. **Order of work vs this run's goal (accepted by Gabriel, 2026-09-29).** You said this run is "improve what exists". Storefront delivery is cloud-only and never needs the Edge, so step 8 (Storefront ↔ Order Core) can come right after step 4, and the Edge (5–7) comes after, as the gate for waiter/KDS/POS. The existing KDS screen stays cloud-only until then.

### Proposed order (revised)

| # | Step | Runs where | This run? |
|---|---|---|---|
| 1 | Tenant foundation (tenant context in auth, `restaurant_id` everywhere, ULIDs) | Cloud | Yes |
| 2 | Menu domain (categories, items, modifiers, options, availability) | Cloud | Yes |
| 3 | Order Core (channel, source, lifecycle, domain events + outbox) | Core module | Yes |
| 4 | Storefront ↔ Order Core (branding, delivery ordering, order status) | Cloud | Yes |
| 5 | Edge app shell (same core module, local DB, device registry) | Edge | Design only |
| 6 | Offline sync (outbox replay, ownership rules, reconciliation) | Both | Design only |
| 7 | QR/table context | Both | Later |
| 8 | Waiter, KDS on Edge, POS, Tablet | Edge | Later |
| 9 | Payments, fiscal, marketplaces | Cloud + gateways | Out |

### Board changes

Modules become: `Architecture docs`, `PRD`, `API contract`, `Tenancy`, `Menu`, `Order Core`, `Storefront`, `Restaurant Edge`, `Offline Sync`, `Device model`, `Auth`, `Audit fix pass`, `Dev tooling & agent workflow`. Future surfaces (`Waiter`, `KDS`, `POS`, `Tablet`, `QR/table`, `Payments`, `Fiscal`, `Marketplaces`) exist as modules with only backlog items, so ideas land somewhere without being scheduled. Cycles follow the table above. The SDD becomes the six `docs/architecture/` files in the docs hub, with `RESTAURANT-OPS-ARCHITECTURE.md` as its root.

### No production data (Gabriel, 2026-09-29)

The app has no real users, orders or products yet; only a test profile. So schema changes carry no migration risk: ULID primary keys, `restaurant_id` on every table and the Order Core reshape can be done directly (or by re-baselining Flyway) instead of through careful data migrations. Backward compatibility with the current API is not a constraint either; the contract can be redesigned around the Order Core.

## Claude readiness first (Gabriel, 2026-09-29)

Jev was considered and dropped (signups paused, paid per call). Before Tenant foundation, the project gets a **Claude readiness** module so Claude works at its best in every step of the spec-driven cycle: docs hub map, current `CLAUDE.md` in both repos, versioned frontend tooling, cross-platform hooks, git guard, tests and CI in both repos, architecture rules as tests, backend-owned contract pipeline. Full plan and readiness gate: `planning/ai-ready-development-plan.md`. Documentation has no fixed "done" point; it is iterated with Gabriel, and contract design asks him questions as each contract is drafted.
