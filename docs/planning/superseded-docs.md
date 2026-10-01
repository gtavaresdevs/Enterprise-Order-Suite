# Superseded and stale legacy docs (S2 banner list)

- Status: Draft
- Date: 2026-09-29 (updated after Q-01..Q-03 were answered; updated 2026-10-01 after Gabriel's answers: F1, F2, F3, F4, F5, F9, F10, F11, B4, B6, B11, B12, B13, §5, Open questions)
- Written by: Claude (S0). Follows MASTER-PLAN §4 (L68), §6 S2 (L89-90) and ADR-0000.
- Used in: S2 (banners, both `CLAUDE.md` rewrites) and S3 (skills, agents, tooling).

Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`); FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`). Line numbers are at those heads; re-check before editing.

## 0. Rules for the S2 pass

- Never delete a legacy file. Mark it superseded (MASTER-PLAN §4 L68). The banner is the only edit to a legacy doc; `CLAUDE.md` files are rewritten instead (§6 S2 L89-90); skills and agents are changed in S3.
- `enterprise-order-suite/docs` in the banners is the docs root: the backend repo, folder `docs/`, branch `feature/ai-agent` (ADR-0011, Q-01 answered 2026-09-29). S2 applied the banners with that literal path.
- Placement:
  - Markdown doc: directly under the H1 title line, as a blockquote.
  - `SKILL.md` and agent files: directly after the closing `---` of the YAML frontmatter (the frontmatter must stay first or the file stops loading).
  - YAML files: `#` comment lines at line 1. Comments do not change the parsed document.
  - Section banner: directly under the named heading.
- Priority: High = an agent following it today does the wrong thing on a likely task. Medium = wrong on a less likely task. Low = historical, mostly harmless.
- Per-decision verdicts (Kept / Superseded / Pending) are in ADR-0000; this file is per document and section.

## 1. Frontend repo (`order-ui`)

### F1. `docs/superpowers/plans/RESTAURANT-OPS-ROADMAP.md` (High)

| Lines | Section / text | Why stale | Replaced by |
|---|---|---|---|
| L3-6 | "Read this file first ... single source of truth" | The plan's entry point is MASTER-PLAN, then `enterprise-order-suite/docs/README.md` after S2. | ADR-0007, ADR-0011 (Q-01), MASTER-PLAN §6 S2 |
| L8-14 | Every phase ends with `finishing-a-development-branch`, "merge to Claude-Assisted-Development locally" | Agents never merge; whether local merges are banned is Q-02. | ADR-0013 |
| L25-26 | "No test suite exists in this repo" | Vitest lands in S3. | ADR-0015 |
| L36-38 | `/storefront` and `/kds` (and any public route) render outside the auth shell, no login | Under multi-tenancy the KDS shows customer names and phones; the KDS signs in with a staff login (Q-54 a, 2026-10-01). Keep as a description of today, not as a rule. | ADR-0001; Q-54 a |
| L51-53, L207, L220-221 | Phase 9 "merged locally, not pushed yet" | False: `origin/Claude-Assisted-Development` @ `14a3cfd` contains `src/features/track-order` (Phase 9). | factual |
| L54-55, L213-218 | In-app card payment and automatic WhatsApp "Blocked" until an integration exists | Now Out for this run, not waiting on a dependency. | ADR-0005, ADR-0006 |
| L57-61 | "no more frontend-only backlog left" | This run improves existing features; the audit backlog is open. | ADR-0006, MASTER-PLAN §6 S5 |
| L209-221 | "What's next" | Replaced by MASTER-PLAN §6. | ADR-0007 |
| L223-241 | Audit Stage 2: "go straight to writing-plans"; the pt-BR docs are the deliverables, the English one only if ambiguous | About half of the audit list is already fixed (MASTER-PLAN §2 L28): re-verify and triage in S5 before planning. Agents cite the English file (ADR-0012). | MASTER-PLAN §6 S5, ADR-0012 |
| L256-263 | Graphify notes (Windows `python`) | Kept: the graphify skill is versioned in FE `c4a7309` (Q-07, 2026-10-01); the Windows `python` notes describe Gabriel's machine. | ADR-0015 (Decision), ADR-0000 DT-1 |
| whole file | Omits the 2026-09-25 refresh-cookie and cross-tab auth work | Incomplete status (MASTER-PLAN §2 L23). | FE `docs/superpowers/plans/2026-09-25-auth-refresh-cookie-cross-tab.md` |

Still valid (recommended to carry into the S2 rewrite of `order-ui/CLAUDE.md`): L18-24 (mock menu ids `m1`-`m9` are load-bearing while menu is mock; never show `available === false` in customer views; `features/menu` is the only `MenuItem` source; `@/` alias only), L27-32 (never `git stash`; explicit-pathspec commits), L33-35 (browser verification only to confirm a reasoned fix).

Banner (under the H1):
```markdown
> **SUPERSEDED as the project entry point (2026-09-29). Do not start here.**
> Current plan, phase and order of work: `enterprise-order-suite/docs/README.md` and `enterprise-order-suite/docs/roadmap.md`. ADRs: `enterprise-order-suite/docs/adr/`.
> This file is the historical record of frontend phases 0-9, which are done.
> Superseded below: "Read this file first" (ADR-0007); the local-merge finishing step (ADR-0013); the "Blocked" items and "What's next" (ADR-0005, ADR-0006, ADR-0007); "No test suite" (ADR-0015); the public `/kds` route as a standing rule (ADR-0001; KDS sign-in is an open question).
> Phase 9 is on `origin/Claude-Assisted-Development`; "not pushed" below is stale. The 2026-09-25 auth refresh-cookie work (`2026-09-25-auth-refresh-cookie-cross-tab.md`) is done and not listed here.
> The audit Stage 2 section is input to the S5 audit triage, not a ready plan: re-verify each item first, and cite the English business-rules file (ADR-0012).
```

Update 2026-10-01: Q-54 a (KDS signs in with a staff login) makes the banner's fourth line stale. Replacement for that line, applied 2026-10-01 in the frontend repo:
```markdown
> Superseded below: "Read this file first" (ADR-0007); the local-merge finishing step (ADR-0013); the "Blocked" items and "What's next" (ADR-0005, ADR-0006, ADR-0007); "No test suite" (ADR-0015); the public `/kds` route as a standing rule (ADR-0001; the KDS signs in with a staff login, Q-54 a, 2026-10-01).
```

### F2. `docs/superpowers/specs/2026-09-09-restaurant-ops-redesign-design.md` (High)

| Lines | Section / text | Why stale | Replaced by |
|---|---|---|---|
| L36-39 | The spec is "the target contract for both the frontend rework and the backend" and "doubles as the backend's roadmap" | The backend owns the contract; the build order changed. | ADR-0010, ADR-0007 |
| L43-45 | Tenancy: single-tenant, one deployment = one restaurant, role mapping | Multi-tenant SaaS. | ADR-0001 |
| L46-47 | One restaurant, not a chain | Kept (Q-22 a, 2026-10-01): one tenant = one restaurant location. | ADR-0000 FS-02 |
| L48-51 | Anonymous customers, name + phone on each order | Kept (Q-51 a, 2026-10-01): a snapshot on each order (name, phone, address); no Customer table this run. | ADR-0000 FS-03 |
| L52-57 | Channel `Online`/`Dine-in`/`Phone`; fulfillment `Pickup`/`Delivery` | Superseded (Q-49 a, Q-34 a, 2026-10-01): `channel` DINE_IN, TAKEAWAY or DELIVERY (fulfillment folds in); `source` STOREFRONT, PHONE, WAITER, POS, QR or TABLET; SCREAMING_SNAKE on the wire. The one-Order-model principle is kept. | ADR-0002, ADR-0000 FS-05/FS-06 |
| L61-65 | Inventory folds into Menu via `stockQuantity` | Superseded (Q-42 c, 2026-10-01): stock counts dropped; the 86 toggle is the only availability control. | ADR-0000 FS-08 |
| L70-72 | Online orders pay in-app before confirmation | Payments are record-only; PSP out. | ADR-0005, ADR-0006 |
| L73-76 | WhatsApp message on every status change | Automatic WhatsApp out; manual `wa.me` stays. | ADR-0006 |
| L89-112 | Target concept diagram ("pays in-app", "WhatsApp status message ... on every status change") | Same as above. | ADR-0005, ADR-0006 |
| L114-155 | Order model: `id: string`, PascalCase statuses, `PayLater` | Ids, statuses, casing and payment fields are decided in ADR-0009 (ULIDs), the API conventions and the Order Core contract. | ADR-0009, MASTER-PLAN §5 L75, §8c |
| L213-219 | KDS/Orders status change triggers WhatsApp | Automatic WhatsApp out. | ADR-0006 |
| L221-226 | Storefront checkout ends in in-app payment | Record-only. | ADR-0005 |
| L264-271 | Preferences as the per-restaurant config hub, later payment provider config | Settings are a typed backend-owned schema; no payment processing. | ADR-0014, ADR-0005 |
| L290-305 | "Backend gaps" as the backend work list (#5 PSP, #6 WhatsApp) | Build order replaced; #5, #6 out. | ADR-0007, ADR-0005, ADR-0006 |
| L307-314 | Out of scope: multi-tenant support | Now in scope. | ADR-0001 |
| L316-321 | Open questions: WhatsApp provider, payment processor | Moot this run. | ADR-0005, ADR-0006 |

Still valid: one Order model and one KDS queue, one Menu served everywhere (ADR-0002); Tables as a list with QR; Team/Roles/Audit Log; keep-and-extend scope (ADR-0000 table E).

Banner (under the H1):
```markdown
> **Partly superseded (2026-09-29). No longer the source of truth for the concept.**
> Superseded: single-tenant deployment and its role mapping (ADR-0001); in-app payment before confirmation (ADR-0005); automatic WhatsApp messages (ADR-0006); this spec as the backend's target contract and roadmap (ADR-0010, ADR-0007).
> Open, decided in the contracts, not here: chains vs single location, the Customer model, channel and fulfillment vocabulary and casing, stock counts, id type (ULIDs, ADR-0009).
> Still valid: one Order model and one KDS queue, one Menu served everywhere (ADR-0002); Tables as a list with QR codes; Team, Roles and Audit Log; keep and extend what works.
> Per-decision verdicts: `enterprise-order-suite/docs/adr/0000-legacy-decisions-triage.md` (table E). Start at `enterprise-order-suite/docs/README.md`.
```

Update 2026-10-01: Gabriel's answers close the banner's "Open" line (the applied banner reads "Open, decided in the contracts, not here: ... stock counts. Decided since: ids are ULIDs (ADR-0009)."). Replacement for that line, applied 2026-10-01 in the frontend repo:
```markdown
> Decided since (Gabriel, 2026-10-01; the contracts record the detail): one tenant = one restaurant location, not a chain (Q-22 a); the customer is a snapshot on each order, with no Customer table (Q-51 a); `channel` DINE_IN, TAKEAWAY or DELIVERY and `source` STOREFRONT, PHONE, WAITER, POS, QR or TABLET, SCREAMING_SNAKE on the wire (Q-49 a, Q-34 a); no stock counts, the 86 toggle only (Q-42 c); ids are ULIDs (ADR-0009).
```

### F3. `docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml`, version 0.4.0 (High)

| Lines | Section / text | Why stale | Replaced by |
|---|---|---|---|
| L5-12 | Generated from frontend mocks; 2026-09-09 spec is "source of truth for the target concept" | The backend owns the contract; F2 is partly superseded. | ADR-0010 |
| L22-33 | Payment processor checkout and WhatsApp integration planned, provider TBD | Record-only payments; automatic WhatsApp out. | ADR-0005, ADR-0006 |
| L74-75, L1005-1015 | `x-maintenance`: canonical copy in the frontend, patch-and-bump rules, source-of-truth order | No longer apply; the file is frozen. | ADR-0010 |
| L976 | `x-out-of-scope`: payment and WhatsApp as external dependencies, TBD | Out this run. | ADR-0005, ADR-0006 |
| L982-985 | `x-open-decisions` `id-type` (int64) | ULIDs everywhere. | ADR-0009 |
| L990-993 | `created-at-format`: zone falls back to deployment config, startup fails | Deployment config cannot hold a restaurant value. | ADR-0001 (ADR-0000 D10a) |
| L994-996 | `payment-sequencing` "depends on the processor" | Closed by Q-55 a, Q-58 a and Q-65 (2026-10-01): an order starts UNPAID and becomes PAID when staff record the payment; storefront PIX prepayment: ADR-0020 (Proposed). | ADR-0005, ADR-0020 |

Still valid as seed: shapes for Menu, Tables, Orders, Delivery zones, RestaurantSettings and `/public/*` seed the backend drafts (ADR-0010 step 1).

Banner (comment lines at line 1, before `openapi: 3.0.3`):
```yaml
# FROZEN at 0.4.0 on 2026-09-29. READ-ONLY. NOT THE API CONTRACT.
# The backend owns the contract (ADR-0010): live spec enterprise-order-suite/docs/api/openapi.yaml,
# design drafts enterprise-order-suite/docs/api/drafts/. This file only seeds those drafts.
# Do not edit it, bump info.version, or add x-changelog or x-open-decisions entries.
# Its x-maintenance rules no longer apply.
# Superseded inside: payment-processor checkout and automatic WhatsApp (ADR-0005, ADR-0006);
# x-open-decisions id-type (ADR-0009: ULIDs) and the created-at-format config fallback (ADR-0001).
# Verdicts per decision: enterprise-order-suite/docs/adr/0000-legacy-decisions-triage.md (table F).
```

### F4. `docs/superpowers/specs/2026-09-15-core-package-br-i18n-ux-design.md` (Medium)

| Lines | Section / text | Why stale | Replaced by |
|---|---|---|---|
| L7-10, L328-351 | Product grows into 10 separately sellable packages | Packages and pricing deferred; the list is reference only. | ADR-0018 |
| L32-36 | Out of scope: "any backend/API change (blocked per roadmap)" | Backend work is now the build order. | ADR-0007 |
| L109-143 | Payment: mock PIX "pay now" QR screen; payment counts as received when staff completes the order | The app records payments, never processes them. A payment record holds method, amount, who and when (Q-58 a), and an order is Paid when staff record the payment, not when it completes (Q-55 a), both 2026-10-01. PIX paid at checkout returns in another form in ADR-0020 (Proposed): a static PIX code with the order total, confirmed by staff recording the payment; no PSP. | ADR-0005, ADR-0006, ADR-0020 |
| L144-152, L348-351 | In-app card payment later through a PSP in "Package 10" | Out; integrate, never build. | ADR-0005, ADR-0006 |
| L185-189 | `PreferencesState` gains `deliveryZones`, `whatsappNumber`, prep and delivery times | Restaurant settings live in a typed backend schema, not the browser. | ADR-0014 |
| L222-226 | Notifications: mock WhatsApp channel | Automatic WhatsApp out; B2B Notifications content is audit item 88. | ADR-0006 |
| L228-287 | Phase 10 comandas, waiter ordering | Later, with QR/table and waiter (Q-81 a, 2026-10-01). | ADR-0007 |
| L288-313 | Phase 11 dashboard and visual polish | Not Later: its small items ride alongside Build 3-4 as existing-feature fixes (Q-82 a, 2026-10-01). | ADR-0007 Decision 4, MASTER-PLAN §6 |

Still valid: i18n architecture (EN/pt-BR), BR formatting, delivery zones as a named neighborhood list with a flat ETA, in-person card and cash, fulfillment-aware status labels, non-goals (no geocoding).

Banner (under the H1):
```markdown
> **Partly superseded (2026-09-29).** Still valid: i18n (EN/pt-BR), BR formatting, delivery zones as a named neighborhood list, in-person card and cash.
> Superseded: the mock PIX "pay now" flow and in-app card payment through a PSP (ADR-0005, ADR-0006); restaurant settings kept in the browser's `PreferencesState` (ADR-0014); "no backend/API change" as a scope rule (ADR-0007).
> Not scheduled: the 10-package product plan (ADR-0018: reference only), Phase 10 comandas and Phase 11 polish (Later, MASTER-PLAN §8d).
> Verdicts: `enterprise-order-suite/docs/adr/0000-legacy-decisions-triage.md` (table G).
```

Update 2026-10-01: Q-82 a moves Phase 11 out of Later, and ADR-0020 (Proposed) brings back PIX paid at checkout in another form. Replacement for the banner's second and third lines, applied 2026-10-01 in the frontend repo:
```markdown
> Superseded: the mock PIX "pay now" flow and in-app card payment through a PSP (ADR-0005, ADR-0006); restaurant settings kept in the browser's `PreferencesState` (ADR-0014); "no backend/API change" as a scope rule (ADR-0007). PIX paid at checkout is proposed again as a static PIX code that staff confirm by recording the payment, with no PSP (ADR-0020, Proposed).
> Not scheduled: the 10-package product plan (ADR-0018: reference only) and Phase 10 comandas (Later, Q-81 a). Phase 11's small polish items ride alongside Build 3-4 (Q-82 a, 2026-10-01).
```

### F5. `docs/superpowers/specs/2026-09-15-dev-tooling-workflow-design.md` (Low)

| Lines | Section / text | Why stale | Replaced by |
|---|---|---|---|
| L3-9 | `.claude/` is git-ignored, so installs are machine-local | S3 versions the frontend `.claude/`. | ADR-0015 |
| whole file | Graphify, ponytail, two wshobson plugins adopted | Kept: versioned with `.claude/` in FE `c4a7309` (Q-07, 2026-10-01). | ADR-0015 (Decision), ADR-0000 DT-1 |

Banner (under the H1):
```markdown
> **Under review (2026-09-29).** The frontend `.claude/` is being versioned in git (ADR-0015, step S3). Whether Graphify, ponytail and the wshobson plugins are kept is an open question. They are installed only on Gabriel's machine; cloud sessions do not have them.
```

Update 2026-10-01: Q-06 a and Q-07 (answered by action, closest option a) settle both rows: Gabriel versioned the whole FE `.claude/` in FE `c4a7309`, including the graphify skill and a `settings.json` that enables ponytail and the two wshobson plugins. The banner above, applied in S2, is now out of date. Replacement text (under the H1, in place of the S2 banner), applied 2026-10-01 in the frontend repo:
```markdown
> **Partly out of date (2026-10-01).** `.claude/` is no longer git-ignored: it is versioned since commit `c4a7309` (Q-06, Q-07), including the graphify skill and a `settings.json` that enables ponytail and the two wshobson plugins. The "git-ignored, local-machine state" statements below describe the setup before that commit.
```

### F6. `docs/superpowers/specs/2026-09-16-business-rules-master-en.md` (Medium)

Why: implementation tags are dated 2026-09-16; about half of the audit list has since been fixed (MASTER-PLAN §2 L28); "[NEW]" rules are unconfirmed by definition (L8); order and menu rules become contract input. Replaced by: the Order Core, Menu and Storefront contracts as they reach Reviewed; S5 audit triage.

Banner (under the H1):
```markdown
> **Status snapshot of 2026-09-16, not current (2026-09-29).** "Implemented" and "Diverges" tags predate later fixes; about half of the audit list is already fixed. Re-verify a rule against the code before acting on it. "[NEW]" rules are proposals Gabriel has not confirmed. Order and menu rules are input to the Order Core, Menu and Storefront contracts in `enterprise-order-suite/docs/`, which win where they differ. This English file wins over the pt-BR versions (ADR-0012).
```

### F7. `docs/superpowers/specs/2026-09-16-regras-de-negocio.md` and `2026-09-16-fluxo-de-dados.md` (Low)

Why: pt-BR docs. D-10: docs in English; the English file wins (ADR-0012, which leaves the banner wording to this list). `fluxo-de-dados.md` has no English counterpart (ADR-0012 Context) and describes a mock/real boundary that moves as features go live (ADR-0007).

Banner for `2026-09-16-regras-de-negocio.md` (under the H1):
```markdown
> **Reference only (2026-09-29).** pt-BR translation of `2026-09-16-business-rules-master-en.md`, which wins where they differ (ADR-0012). Same caveat as the English file: status snapshot of 2026-09-16; re-verify before acting.
```

Banner for `2026-09-16-fluxo-de-dados.md` (under the H1):
```markdown
> **Reference only (2026-09-29).** pt-BR data-flow notes of 2026-09-16 with no English counterpart (ADR-0012). The mock-versus-real boundary described here changes as each feature moves to the backend (ADR-0007). Translate the parts a new doc needs; do not extend this file.
```

### F8. Executed frontend plans (Low)

Files in `docs/superpowers/plans/`: `2026-09-10-restaurant-ops-phase0-foundation-types.md`, `2026-09-10-restaurant-ops-phase1-menu-tables.md`, `2026-09-10-restaurant-ops-phase2-retire-duplicates.md`, `2026-09-10-restaurant-ops-phase3-public-menu-view.md`, `2026-09-12-restaurant-ops-phase4-orders-kds-unified-model.md`, `2026-09-13-restaurant-ops-phase5-administration.md`, `2026-09-13-restaurant-ops-phase6-home-analytics.md`, `2026-09-15-core-package-phase7-i18n-foundation.md`, `2026-09-15-core-package-phase8-payment-method.md`, `2026-09-15-dev-tooling-workflow-design.md`, `2026-09-16-restaurant-ops-phase9-delivery-whatsapp.md`, `2026-09-25-auth-refresh-cookie-cross-tab.md`.

Why: executed (PF `RESTAURANT-OPS-ROADMAP.md` L42-53). They assume the single-restaurant mock world, finish with local worktree merges, and patch the frontend manifest (for example the auth plan, L296).

Banner (under the H1, same text in each):
```markdown
> **Executed plan, historical record (2026-09-29).** Do not re-execute it or copy its process. It predates the multi-tenant and backend-owned-contract decisions (ADR-0001, ADR-0010); its worktree-merge and manifest-patch steps are not current (ADR-0013, ADR-0010). Current work starts at `enterprise-order-suite/docs/README.md`.
```

### F9. `order-ui/CLAUDE.md` (High; rewritten in S2, no banner)

| Lines | Text | What the rewrite must change | Source |
|---|---|---|---|
| L7, L94-95 | No test suite; lint and build are the only safety net | True until S3; afterwards the verification is `yarn lint && yarn build && yarn test` (MASTER-PLAN §7 L104). | ADR-0015 |
| L9-18 | "Active initiative": the 2026-09-09 spec is the concept to read before changes; lists `inventory` and "the new `menu`/`tables` features" | Point at `enterprise-order-suite/docs/README.md`, the architecture and ADRs. `features/inventory` was deleted in phase 2 (PF `RESTAURANT-OPS-ROADMAP.md` L83); menu and tables exist. | ADR-0001, ADR-0002, ADR-0010 |
| L25-28 | Mocks keep target shapes from the spec's "Backend gaps" | Target shapes come from the backend drafts and spec; mocks retire feature by feature in build order. | ADR-0010, ADR-0007 |
| L57 | "Only `auth` and `profile` services call the real API"; lists `administration` and `inventory` as mock | Administration is real (phase 5); inventory is gone; profile avatar upload is a mock (FE `src/features/profile/services/profile.service.ts` L26-27). | MASTER-PLAN §2 L24 |
| L61 | `accessToken`/`refreshToken` stored in `localStorage` | The refresh token is an HttpOnly cookie since 2026-09-25; a leftover `localStorage` value is only sent once for migration (FE `src/api/client.ts` L41-46). | ADR-0000 D18-D19 |
| L67 | `/storefront` and `/kds` are standalone routes outside the shell | Keep as a description of today; do not state it as a rule for the KDS (the KDS signs in with a staff login, Q-54 a, 2026-10-01). | ADR-0001 |
| L75-78 | Confirm endpoints against the local backend's live swagger (`connect-backend`) | Confirm against the committed `docs/api/openapi.yaml` and the generated types. | ADR-0010 |
| L81-88 | Backend integration manifest stays in lockstep | Remove. The frontend never records contract decisions. | ADR-0010 |
| L96-99 | Hook `.claude/hooks/lint-typecheck.cjs` | Exists only on Gabriel's machine until `.claude/` is versioned. | ADR-0015 |
| L104-106 | Real test credentials from the `order-ui-test-login` memory | Machine-local memory; cloud sessions do not have it. S3 decides the source. | ADR-0015 |
| L129-139 | Git workflow | Keep; align with ADR-0013 and the Q-02 answer; mention the `permissions.deny` rules. | ADR-0013 |

### F10. Frontend `.claude/` (not in git; not inspected)

Skills `connect-backend`, `scaffold-feature`, `migrate-shared-type`, `verify-ui`, `audit-requirement`; agents `requirement-auditor`, `ui-behavior-verifier`; hook `lint-typecheck.cjs` exist only on Gabriel's machine (FE `.gitignore` L17; FE `CLAUDE.md` L23-24, L75-78, L96-99). They cannot be checked from here. S3 reviews them after Gabriel's push; `connect-backend` must read the backend-owned spec (ADR-0010 Consequences). No banner.

Update 2026-10-01: in git since FE `c4a7309` (Gabriel, Q-06 a, Q-07), with the `graphify` skill, `settings.json` and `.claude/CLAUDE.md`; `permissions.deny` git rules added in FE `30bc172`. Still to fix: `order-ui/.claude/skills/connect-backend/SKILL.md` checks the local live swagger (L16-30) and its §6 (L74-78) patches the frontend manifest, which ADR-0010 retires. Stopgap applied 2026-10-01 in the frontend repo: the banner below, after the frontmatter. The rewrite of the skill around the vendored spec and generated types (Q-76 a) waits for Build 1, when they exist.
```markdown
> **Partly superseded (2026-10-01). Where this skill and ADR-0010 differ, the ADR wins.** §6 is retired: never patch the frozen frontend manifest. The live swagger of a local backend (§0-§1) shows what that backend runs today; it is not the contract. API shapes come from the backend repo `enterprise-order-suite/docs/api/` (`drafts/` while a contract is designed, `openapi.yaml` once implemented) and, from Build 1, from the frontend's vendored copy of that spec and the types generated from it (Q-76 a). Raise any difference between them to Gabriel and the backend; never settle it in the frontend. §4 predates Vitest: verify with `yarn lint && yarn build && yarn test`.
```

### F11. Public READMEs (Low; applied 2026-10-01, Q-10 a)

Status: applied. Gabriel answered Q-10 a on 2026-10-01 ("Q10- a"). The note is under the H1 of the frontend root README (FE `30bc172`), dated 2026-10-01 and linking to the backend `docs/README.md` on `feature/ai-agent`. Line numbers below are from before the note (it adds two lines).

- `enterprise-order-suite-frontend/README.md` L3, L8, L119: "B2B Order Management System". Public portfolio text.
- `order-ui/README.md` L1-5: stock Vite template. Harmless; no action.

Banner for the frontend root README (proposed in S0; applied 2026-10-01 with the new date and a link):
```markdown
> **Note (2026-09-29):** this README describes the earlier B2B demo. The product is now a multi-tenant restaurant operations SaaS; see `enterprise-order-suite/docs/README.md`.
```

## 2. Backend repo (`enterprise-order-suite`)

### B1. `docs/contracts/README.md` (High)

Whole file: frontend repo named as contract owner; snapshot re-sync rules; source-of-truth order (L5-30). Replaced by ADR-0010. (Flat copy: PF `README.md`.)

Banner (under the H1):
```markdown
> **SUPERSEDED (2026-09-29). Do not re-sync or follow the rules below.**
> The backend owns the API contract (ADR-0010). Live spec: `docs/api/openapi.yaml` in this repo, checked by a drift test. Design drafts: `enterprise-order-suite/docs/api/drafts/`.
> The snapshot next to this file is frozen at 0.3.0 and is never re-synced. The frontend manifest is frozen at 0.4.0 and only seeds the drafts. The `api-contract-sync` skill is retired.
```

### B2. `docs/contracts/backend-integration-manifest.openapi.yaml`, version 0.3.0 (High)

Frozen snapshot, already behind the frontend's 0.4.0 (ADR-0010 Context). (Flat copy: PF `backend-integration-manifest.openapi.yaml`.)

Banner (comment lines at line 1):
```yaml
# FROZEN SNAPSHOT (0.3.0, taken 2026-09-24). SUPERSEDED 2026-09-29. NOT THE API CONTRACT.
# The backend owns the contract (ADR-0010): live spec docs/api/openapi.yaml, drafts enterprise-order-suite/docs/api/drafts/.
# Older than the frontend manifest 0.4.0, which seeds the drafts. Never re-sync or edit this file.
```

### B3. `.claude/skills/api-contract-sync/SKILL.md` (High)

| Lines | Text | Why stale | Replaced by |
|---|---|---|---|
| L3 | Description: check endpoints against "the frontend's backend-integration manifest" | Still triggers on every endpoint change until S3. | ADR-0010 |
| L8-21 | Endpoint shapes are owned by the frontend manifest; never edit the snapshot | Ownership reversed. | ADR-0010 |
| L39-50 | Report divergences back to the canonical frontend copy | No canonical frontend copy. | ADR-0010 |
| L52-67 | `id-type`, `category-identity`, `created-at-format`, `dev-cookie-secure` listed as open | Stale even in legacy terms (decided 2026-09-24); current verdicts in ADR-0000. | ADR-0000 table F |

Action: S2 removes it from the backend `CLAUDE.md` skill table (L20) and adds the banner; S3 deletes the skill or changes its `description` so it stops triggering (ADR-0010). Until S3 the description still triggers it; the banner is the stopgap.

Banner (after the frontmatter):
```markdown
> **RETIRED (2026-09-29). Do not invoke; do not follow.** The backend owns the API contract (ADR-0010). Change API shapes in `enterprise-order-suite/docs/api/drafts/` (design) or in code together with the committed `docs/api/openapi.yaml` (implementation). The frontend manifest and the `docs/contracts/` snapshot are frozen. The open-decisions table below is stale: see `enterprise-order-suite/docs/adr/0000-legacy-decisions-triage.md`.
```

### B4. `.claude/skills/backend-module-development/SKILL.md` (High)

| Lines | Text | Why stale | Replaced by |
|---|---|---|---|
| L69-72 | "Invoke the `api-contract-sync` skill. The endpoint shape is owned by the frontend's manifest" | Ownership reversed. | ADR-0010 |
| L83-95 | "Product direction": new `menu`/`tables`/`settings` modules, anonymous customers, check `docs/contracts/` and the 2026-09-20 design | Target is multi-tenant, built in the ADR-0007 order from the contracts; customers are a snapshot on each order, with no Customer table this run (Q-51 a, 2026-10-01). | ADR-0001, ADR-0002, ADR-0007, ADR-0010 |
| (missing) | Nothing on tenant scoping or ULIDs | Added when ADR-0009 and the Tenancy & Identity contract are settled (S3 / Build 1). | ADR-0001, ADR-0009 |

Still valid: layering and reuse table, L50-60 money derivation and price snapshots (ADR-0000 LR-1, LR-2), scope discipline.

Section banner under `## Before you add or change an endpoint` (L69):
```markdown
> **Superseded (2026-09-29):** the backend owns the contract (ADR-0010). Do not invoke `api-contract-sync`; work from `enterprise-order-suite/docs/api/` as ADR-0010 describes.
```

Section banner under `## Product direction — target, not yet built` (L83):
```markdown
> **Superseded (2026-09-29):** the target is a multi-tenant SaaS with one Order Core and one Menu (ADR-0001, ADR-0002), built in the order of ADR-0007 from the contracts in `enterprise-order-suite/docs/`. `docs/contracts/` and the 2026-09-20 design are not the target. Tenant scoping and id rules are added here when their ADRs and contracts are settled.
```

### B5. `.claude/skills/flyway-migrations/SKILL.md` and `.claude/agents/flyway-migration-author.md` (Medium)

| File, lines | Text | Why stale | Replaced by |
|---|---|---|---|
| SKILL L24 | Highest version is V19 | Actual V21 (BE `src/main/resources/db/migration/`). Factual fix in S3. | factual |
| SKILL L26-30; agent L18 | Never edit an applied migration | ADR-0009 allows one re-baseline before launch, planned for Build 1; the rule stands for every other change and without exception after launch. | ADR-0009 |
| SKILL L46-74 | Enum rename as a data migration; "the mapping ... phase 4 needs"; confirm names in `docs/contracts/` | No legacy data to migrate pre-launch; legacy statuses are replaced, not migrated; contract pointer retired. | ADR-0008, ADR-0010 |

Section banner under `## Never edit an applied migration` (SKILL L26), and the same text after the agent's frontmatter:
```markdown
> **Exception (2026-09-29):** ADR-0009 allows one Flyway re-baseline before launch, planned for Build 1 (Tenant foundation). Outside that single re-baseline, and always after launch, this rule applies without exception.
```

Section banner under `## Renaming an enum value is a data migration` (SKILL L46):
```markdown
> **Not for pre-launch legacy data (2026-09-29).** There is no production data (ADR-0008): the legacy `orders` statuses are replaced by the Order Core contract, not migrated. The "phase 4" mapping and the `docs/contracts/` pointer below are superseded (ADR-0008, ADR-0010). The technique stays valid once real data exists.
```

### B6. `.claude/skills/spring-security-changes/SKILL.md` (Medium)

| Lines | Text | Why stale | Replaced by |
|---|---|---|---|
| L113-116 | "Target model (Phase 1 built / Phase 6 pending)", source `docs/contracts/` | "Phase 6" is now the auth cleanup inside Tenant foundation; source is the backend-owned contract. | MASTER-PLAN §4 L68, ADR-0010 |
| L134-135 | Remaining for Phase 6: drop body `refreshToken`, remove body fallback, universal Origin check | Salvaged into Tenant foundation (§4 below). | MASTER-PLAN §4 L68 |
| L136-140 | `/public/*` obligations; "force `channel=Online`" | Obligations stay; they move into the Storefront ↔ Order Core contract; `Online` becomes source STOREFRONT, forced server-side (Q-49 a, 2026-10-01). | ADR-0000 LR-4, FS-05 |

Section banner under `## Target model (Phase 1 built / Phase 6 pending)` (L113):
```markdown
> **Updated meaning (2026-09-29):** "Phase 6" now means the auth cleanup inside Tenant foundation (MASTER-PLAN §4). Target shapes come from the backend-owned contract (ADR-0010), not `docs/contracts/`. The `/public/*` obligations below stay in force and move into the Storefront ↔ Order Core contract; the `channel=Online` value follows the Order Core vocabulary once decided.
```

### B7. Backend agents (High for one line, Low otherwise)

| File, lines | Text | Why stale | Replaced by |
|---|---|---|---|
| `backend-feature-builder.md` L9 | Step 1: `api-contract-sync`, "confirm the shape against the frontend manifest" | Ownership reversed. | ADR-0010 |
| `backend-feature-builder.md` L17, `backend-test-writer.md` L12, `flyway-migration-author.md` L12, `spring-security-reviewer.md` L17 | "Improving the structure is allowed; breaking what works is not." | Ambiguous pre-launch: schema and API may break (ADR-0008). S3 clarifies the line; no S2 banner. | ADR-0008 |
| `flyway-migration-author.md` L18 | Never edit an applied migration | See B5. | ADR-0009 |

Banner for `backend-feature-builder.md` (after the frontmatter):
```markdown
> **Update (2026-09-29):** step 1 below is retired. Confirm API shapes against the backend-owned contract (`enterprise-order-suite/docs/api/`, ADR-0010), not the frontend manifest or `api-contract-sync`.
```

### B8. `docs/superpowers/specs/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` (High)

| Lines | Section / text | Why stale | Replaced by |
|---|---|---|---|
| L29-33 | The frontend manifest defines the product the backend must build | Backend owns the contract. | ADR-0010 |
| L59-60 | D7 frozen `/orders` matrix; D8 backward-compatible auth | Superseded. | ADR-0008 (ADR-0000 table A) |
| L90-92 | `api-contract-sync` skill definition | Retired. | ADR-0010 |
| L132-148 | Part 3 gap table; back office "one restaurant, all staff see all orders" | Multi-tenant. The gap table stays useful as a delta list (salvage, §4). | ADR-0001 |
| L150-166 | Roadmap ordering, phases 2-6 | Phases 0-1 done; the rest replaced by the build order. Salvage phases 5 and 6. | ADR-0007, MASTER-PLAN §4 L68 |
| L168-173 | Open decisions inherited from the manifest | Current verdicts in ADR-0000 tables B and F. | ADR-0000 |
| L175-179 | Contract ownership (frontend canonical) | Reversed. | ADR-0010 |

Still valid: D1-D6 as triaged (ADR-0000 table A), Part 2 (orders fix, done), testing rules L68-73.

Banner (under the H1):
```markdown
> **Partly superseded (2026-09-29).** Phases 0-1 of this migration are done. Superseded: the migration plan phases 2-6 (Part 3, "Roadmap ordering"), replaced by the build order Tenant foundation → Menu → Order Core → Storefront ↔ Order Core (ADR-0007); the single-restaurant target (ADR-0001); "Contract ownership" by the frontend (ADR-0010); D7 and D8 (ADR-0008).
> Salvaged: phase 5 `/public/*` hardening goes into the Storefront ↔ Order Core contract; phase 6 auth cleanup goes into Tenant foundation.
> Verdicts for D1-D8: `enterprise-order-suite/docs/adr/0000-legacy-decisions-triage.md` (table A). Start at `enterprise-order-suite/docs/README.md`.
```

### B9. `docs/superpowers/specs/2026-09-24-restaurant-ops-phase-0-foundation-design.md` (Medium)

| Lines | Section / text | Why stale | Replaced by |
|---|---|---|---|
| L82, L91-106 | D9 int64 ids | ULIDs everywhere. | ADR-0009 |
| L83, L108-156 | D10 `businessDate` | Pending, Order Core contract. | ADR-0000 D10 |
| L84, L120-146 | D10a zone fallback to `restaurant.timezone`, startup fails; "one restaurant" (L144-146) | Deployment config cannot hold a restaurant value. | ADR-0001 |
| L86, L158-172 | D11 category identity | Pending, Menu contract. | ADR-0000 D11 |
| L268-309 | Contract work: patch the canonical frontend manifest in place, uncommitted | Ownership reversed. | ADR-0010 |
| L403-407 | `RESTAURANT_TIMEZONE` as a required first-run step | Moot: D10a superseded and never built. | ADR-0001 |

Still in force: D12, D13, D14, D15.

Banner (under the H1):
```markdown
> **Partly superseded (2026-09-29).** Still in force: D12, D13, D14, D15 (D13 and D15 are built). Superseded: D9 int64 ids (ADR-0009: ULIDs), D10a timezone fallback to deployment config (ADR-0001), and "Contract work" (ADR-0010). Pending re-examination: D10 `businessDate` (Order Core contract) and D11 category identity (Menu contract).
> Verdicts: `enterprise-order-suite/docs/adr/0000-legacy-decisions-triage.md` (table B).
```

### B10. `docs/superpowers/specs/2026-09-25-restaurant-ops-phase-1-auth-design.md` (Low)

| Lines | Section / text | Why stale | Replaced by |
|---|---|---|---|
| L12 | "every test written in Phases 2-5" | Phases replaced. | ADR-0007 |
| L77-78, L376-379 | "Phase 6" cleanup; revoke on deactivation or role change | Salvaged into Tenant foundation (§4 below). | MASTER-PLAN §4 L68 |
| L215-232 | Contract work: manifest, snapshot, `x-status: target-change` until Phase 6 | Ownership reversed; manifest frozen. | ADR-0010 |
| L363-367 | Launch gate: four frontend follow-ups | Done (FE `2026-09-25-auth-refresh-cookie-cross-tab.md`). | factual |

Banner (under the H1):
```markdown
> **In force (2026-09-29).** D16-D24 are kept (MASTER-PLAN D-16). "Phase 6" below now means the auth cleanup inside Tenant foundation (MASTER-PLAN §4). "Contract work" is superseded (ADR-0010). The four frontend follow-ups are done (frontend `2026-09-25-auth-refresh-cookie-cross-tab.md`).
```

### B11. Executed backend plans (Low)

| File | Stale parts | Replaced by |
|---|---|---|
| `docs/superpowers/plans/2026-09-20-claude-tooling-install.md` | Task 1 contract snapshot; Tasks 2 (security-guidance plugin) and 14 (fresh-session verification) listed as outstanding (L10, L14): Task 14 folded into the SessionStart check, Task 2 dropped (Q-75 a, 2026-10-01) | ADR-0010; ADR-0015 (Decision) |
| `docs/superpowers/plans/2026-09-20-orders-authorization-fix.md` | Done (L5-12); historical | none |
| `docs/superpowers/plans/2026-09-20-test-failures-and-audit-followups.md` | Complete (L8); launch items carried (§5 below) | none |
| `docs/superpowers/plans/2026-09-24-restaurant-ops-phase-0-foundation.md` | L23 edit the frontend repo in place, never commit; Task 3 (L1097) manifest patch; L27 Windows-only worktree path | ADR-0010 |
| `docs/superpowers/plans/2026-09-25-restaurant-ops-phase-1-auth.md` | L20 snapshot rule; L1396 invokes `api-contract-sync` | ADR-0010 |

Banner (under the H1, same text in each):
```markdown
> **Executed plan, historical record (2026-09-29).** Do not re-execute it. Its contract steps (frontend manifest patches, the `docs/contracts/` snapshot, `api-contract-sync`) are superseded by ADR-0010. Current work starts at `enterprise-order-suite/docs/README.md`.
```

Extra line for `2026-09-20-claude-tooling-install.md`, appended inside the same blockquote:
```markdown
> Tasks 2 (security-guidance plugin) and 14 (fresh-session verification) are still open; S3 decides whether they are done, dropped or folded in (ADR-0015).
```

Update 2026-10-01: Q-75 a settles both tasks. Replacement for the extra line, applied 2026-10-01:
```markdown
> Task 14 (fresh-session verification) is folded into the S3 SessionStart check and Task 2 (security-guidance plugin) is dropped (Q-75 a, 2026-10-01; ADR-0015).
```

### B12. Backend `CLAUDE.md` (High; rewritten in S2, no banner)

| Lines | Text | What the rewrite must change | Source |
|---|---|---|---|
| L7 | "Enterprise-grade B2B order management backend" | Multi-tenant restaurant operations SaaS; point at `enterprise-order-suite/docs/README.md`. | ADR-0001, ADR-0002 |
| L20 | `api-contract-sync` row in the skill table | Remove. | ADR-0010 |
| L25 | `PreToolUse` hook warns on security-sensitive edits | The hook needs `pwsh` (BE `.claude/settings.json` L1-15) and does not run on Linux cloud sessions; S3 ports or removes it. | ADR-0015 |
| L76 | New resource-scoped endpoints follow `isOrderOwner`; "Multi-tenant list/search endpoints ... force-filter by the current user's ID" | "Tenant" now means restaurant; per-user order ownership goes with the anonymous-customer model (PF `2026-09-20-...-migration-design.md` L146-148). Tenant scoping is set by Tenancy & Identity. | ADR-0001 |
| L80-81 | "`OrderController` currently violates this and is scheduled for correction" | Fixed: `OrderController` has only `@PreAuthorize` (BE `orders/api/OrderController.java` L36-99; PF `2026-09-20-orders-authorization-fix.md` L5-12). Drop the sentence, keep the D2 rule. | factual |
| L83 | Order create/cancel mutates `Product` stock | Legacy B2B behavior; the new Menu has no stock counts (Q-42 c, 2026-10-01). | ADR-0000 FS-08 |
| L89-105 | "Product direction": snapshot, 2026-09-20 design, single-restaurant target | Replace with the ADRs and build order. | ADR-0001, ADR-0002, ADR-0007, ADR-0010 |
| (missing) | No git section | Add the ADR-0013 rules. | ADR-0013 |

Keep: commands, environment, layering, cross-module dependency inversion, reuse list, D2 paragraph, testing, language rule (L29-30).

### B13. Backend `README.md` (Low; applied 2026-10-01, Q-10 a)

Status: applied. Gabriel answered Q-10 a on 2026-10-01 ("Q10- a"). The note is under the H1 of the backend `README.md` (BE `c8bf761`), dated 2026-10-01 and linking to `docs/README.md` on `feature/ai-agent`. Line numbers below are from before the note.

L3-17 and L240-276 describe a B2B order suite with companies and products. Public portfolio text; `CLAUDE.md` L34 already tells agents to ignore its Maven instructions.

Banner (proposed in S0; applied 2026-10-01 with the new date and a link):
```markdown
> **Note (2026-09-29):** this README describes the earlier B2B order suite. The product is now a multi-tenant restaurant operations SaaS; see `enterprise-order-suite/docs/README.md`.
```

### B14. Tooling, not docs (S3, no banner)

`.claude/settings.json` L1-15 and `.claude/hooks/security-sensitive-file.ps1`: PowerShell-only hook; port or remove (ADR-0015).

### B15. Stale "live defect" references in backend skills and agents (Medium)

The `SUPER_ADMIN` demotion defect these lines call live was fixed by the 2026-09-20 orders authorization fix (PF `2026-09-20-orders-authorization-fix.md` L5-12): `OrderService.isAdmin` now resolves through `RoleHierarchy` (BE `orders/application/service/OrderService.java` L289-303), and `OrderController.java` has 115 lines, all authorization in `@PreAuthorize` (L36-99). The rule "authorization only in `@PreAuthorize`" (legacy D2, kept by D-16) stays; only the "live defect" claims and line references are stale.

| File, lines | Text | Why stale | Replaced by |
|---|---|---|---|
| `.claude/skills/writing-backend-tests/SKILL.md` L72-77 | Mandatory coverage 3: "That is the live defect in `orders/application/service/OrderService.java:209`" | Fixed; L209 is now stock code. Keep the `SUPER_ADMIN` test obligation. | factual |
| `.claude/skills/writing-backend-tests/SKILL.md` L63-77 | Mandatory coverage lists denied-path and leak tests only | No cross-tenant test obligation yet. Added with the Build 1 acceptance criteria (MASTER-PLAN §6 L93), not before (§4 L64). | ADR-0001 |
| `.claude/skills/spring-security-changes/SKILL.md` L55-72 | Hard rule 1: "This is a live defect at the time of writing", `OrderService.java:209 and OrderController.java:124`, and "a `SUPER_ADMIN` sees only their own orders" | Fixed. Keep the rule and the code sample as the anti-pattern. | factual |
| `.claude/agents/spring-security-reviewer.md` L24-26 | "Reference defect: `OrderService.java:209`, `OrderController.java:124`" | Fixed; the lines no longer hold it. | factual |

Action: S3 rewrites these lines (factual fix, no banner). No S2 banner: the rule they teach is still correct.

## 3. Project files (`/mnt/project-files`)

### P1. `planning/pm-tool-recommendation.md` (Medium)

MASTER-PLAN L3-6: where the detail files disagree with it, MASTER-PLAN wins.

| Lines | Section / text | Replaced by |
|---|---|---|
| L22, L26-27 | GitHub Action automation; Plane MCP and `EOS-<n>` ids in every session | ADR-0017 |
| L36-42 | Board modules and cycles, history import, forward cycles "backend phases 2+" | ADR-0017, ADR-0007 |
| L59 | Scope: "a per-restaurant Restaurant Edge for offline operation" | ADR-0003 (Edge optional, D-3) |
| L76-88 | Heading "(decided)" also covers packages as plans | ADR-0001 (tenancy), ADR-0018 (packages) |
| L90-106 | Docs-hub repo `enterprise-order-suite-docs`; cross-repo export Action | ADR-0011 (Q-01), ADR-0010 |
| L113, L115 | Review gaps #1 (core module deployed twice) and #3 (events + outbox) as this-run work | ADR-0019 |
| L119-131 | Revised order: row 3 "Core module", "domain events + outbox"; rows 5-6 "Design only" | ADR-0007, ADR-0019, ADR-0003 |
| L135 | Board modules incl. Edge, Offline Sync, Device | ADR-0017, ADR-0003 |

Banner (under the H1):
```markdown
> **Superseded where it differs from `planning/MASTER-PLAN.md` (2026-09-29).** Still current: the Plane choice and hosting notes (ADR-0017), the tenancy comparison (ADR-0001), the architecture review as background. Superseded: the per-restaurant Edge in "Scope" (ADR-0003), the docs-hub repo (ADR-0011), the cross-repo export Action (ADR-0010), packages as plans (ADR-0018), board modules, cycles and history import (ADR-0017), revised-order rows 3, 5 and 6 (ADR-0007, ADR-0019, ADR-0003).
```

### P2. `planning/ai-ready-development-plan.md` (Medium)

| Lines | Section / text | Replaced by |
|---|---|---|
| L43, L54, L56 | Every R-item a Plane work item; `EOS-<n>` plan ids; `finish-task` updates Plane | ADR-0017 |
| L45-49, L52 | Docs hub repo | ADR-0011 |
| L55 | R14 custom git-guard hook | ADR-0013 (`permissions.deny`) |
| L62 | R12 ArchUnit before code; no `IDENTITY` ids only on offline-capable entities | ADR-0015 (Build 1 acceptance), ADR-0009 |
| L63, L90 | R13a CI PR into the hub; end-to-end pipeline gate item | ADR-0010 |
| L69 | R11 Playwright smoke tests now | ADR-0015 (Vitest now; Playwright with Build 1, MASTER-PLAN §4 L64) |
| L72-80 | Order of work | MASTER-PLAN §6 (ADR-0007, ADR-0015) |
| L82-91 | Readiness gate | MASTER-PLAN §7 (ADR-0015) |

Banner (under the H1):
```markdown
> **Superseded where it differs from `planning/MASTER-PLAN.md` (2026-09-29).** The order of work and the readiness gate are MASTER-PLAN §6-§7 (ADR-0015). Superseded: the docs-hub repo (ADR-0011), Plane-dependent items (ADR-0017), the custom git-guard hook (ADR-0013), ArchUnit and Playwright as gate items (ADR-0015), ULIDs only on offline-capable entities (ADR-0009), the cross-repo contract export (ADR-0010).
```

### P3. `architecture/RESTAURANT-OPS-ARCHITECTURE.md` (no banner)

Gabriel's doc is changed through the architecture amendment proposal (MASTER-PLAN §5 L73), not by a banner. Sections it touches, for cross-reference: status L3; §2 L40-42 and §24 L302-306 Edge at every restaurant (ADR-0003); §4.2 L81 and §17 L229-235 offline scope (ADR-0004); §9 L171 open-ended customization (ADR-0014); §12 L192 "Offline Transaction Queue" and L200 "Payment Device Gateway" (ADR-0004, ADR-0005); derived documents L308-318 (MASTER-PLAN §4 L59); implementation order L320-339 (ADR-0007).

### P4. Flat copies of legacy docs

PF `*.md` and the two PF `*.yaml` files mirror repo files (PF `README.md` = BE `docs/contracts/README.md`; PF `backend-integration-manifest.openapi.yaml` = BE 0.3.0 snapshot; PF `2026-09-14-backend-integration-manifest.openapi.yaml` = FE 0.4.0). The six copies cited in ADR-0000 were checked identical to the repo files, ignoring CRLF. S2 banners go in the repos. What happens to the flat copies afterwards is not decided (Open questions).

### P5. Memory

Handled by the S0 memory cleanup, not this list.

## 4. Salvage: content that moves into new docs

### S-1. Backend phase 5 `/public/*` hardening → Storefront ↔ Order Core contract

- Rate limiting on public endpoints (PF `2026-09-20-...-migration-design.md` L165).
- Phone as a lookup credential without enumeration (L165; BE `spring-security-changes/SKILL.md` L138-139).
- No 86'd items: filter `available == true` server-side (SKILL L137-138; PF `2026-09-16-business-rules-master-en.md` L53, rule 26).
- No table-roster leak: single-table lookup, never an admin-scoped query (SKILL L136-138; PF `RESTAURANT-OPS-ROADMAP.md` L100-102).
- Scoped order lookup; today the mock ships the whole order list to an unauthenticated page (business rules L47, rule 23).
- Server-derived total, ETA and payment status; channel forced server-side, value per the Order Core vocabulary (PF migration design L144; SKILL L139-140).
- A leak test for every `/public/*` endpoint and a denied-access test for every resource-scoped endpoint (PF migration design L72-73; ADR-0000 LR-4).
- Every public endpoint resolves its restaurant from the request (ADR-0001 Consequences).

### S-2. Backend phase 6 auth cleanup → Tenant foundation

- Drop `refreshToken` from `AuthResponse`; remove the body fallback (D18) (PF `2026-09-25-restaurant-ops-phase-1-auth-design.md` L77, L376-377).
- Make the `Origin` check universal (D19) (L78, L376-377).
- Revoke refresh tokens on user deactivation or role change (L378-379); more pressing once roles are per restaurant.
- Frontend side: remove the one-time `localStorage` refresh-token migration (FE `src/api/client.ts` L41-46); move the access token into memory, "the full TARGET" (FE manifest L1025).
- "Flip the Auth paths to `live`" (L376-377) becomes: document the result in the committed `docs/api/openapi.yaml` (ADR-0010).

### S-3. Other salvage

- 2026-09-20 gap table (PF migration design L136-144) → delta list for the Menu and Order Core contracts.
- Manifest 0.4.0 shapes (Menu, Tables, Orders, Delivery zones, RestaurantSettings, `/public/*`) → seeds for `enterprise-order-suite/docs/api/drafts/` (ADR-0010 step 1).
- Backend phase 3 content (Tables, DeliveryZones, RestaurantSettings; PF migration design L163) → placed by the S5 scope page (see Reviewer note).
- D10 `businessDate` rationale (PF phase-0 design L108-156) → Order Core contract input.
- Enum mapping `PENDING→NEW ... DELIVERED→COMPLETED` (BE `flyway-migrations/SKILL.md` L59-65) → reference for the Order Core status discussion only, no data migration (ADR-0008).
- Audit Stage 2 known bugs (PF `RESTAURANT-OPS-ROADMAP.md` L243-254) and business-rule "[NEW]" items → S5 audit triage and contract question batches.
- About 28 old lint errors (PF `RESTAURANT-OPS-ROADMAP.md` L267-269, not re-verified) → S3 frontend lint baseline.
- Roadmap standing constraints still valid (F1 "Still valid") → rewritten frontend `CLAUDE.md` (S2).

## 5. Launch checklist items found in legacy docs (destination: NFR page, S5, pre-pilot)

| Item | Source | Status |
|---|---|---|
| Set `SUPER_ADMIN_EMAIL` in every environment | PF `2026-09-20-test-failures-and-audit-followups.md` L77-81 | Open; the root admin's meaning depends on Tenancy & Identity (ADR-0000 AF-3) |
| Rotate the seeded super-admin password before any deployment with a real credential | same L94-96; PF phase-0 design L430-431 | Open (ADR-0000 AF-4) |
| Behind a proxy: `SERVER_FORWARD_HEADERS_STRATEGY=native` and trusted `server.tomcat.remoteip.internal-proxies` | PF phase-1 auth design L180-188 (D23); BE `application.yml` L10 | Open |
| Keep `REFRESH_COOKIE_SECURE=true` (default); only local dev opts out | PF phase-0 design L181-188 (D12) | Open |
| Set `CORS_ALLOWED_ORIGINS` to the real origins | same L183; BE `application.yml` L89 | Open; addressing decided: path `/r/{slug}` (Q-20 a), app and API on the same registrable domain (Q-79 a), both 2026-10-01 |
| If front and API sit on different sites: `SameSite=None; Secure` plus an anti-CSRF token | PF phase-1 auth design L352-354; FE manifest L1002 | Not needed while app and API share a registrable domain (Q-79 a, 2026-10-01; ADR-0000 D12) |
| The rate limiter is in-memory per instance; revisit before running more than one instance | PF phase-1 auth design L370-372 | Open |
| `org.springframework.security` logs at `DEBUG` | BE `application.yml` L91-93 | Open |
| Delete the verification user `phase5-verify-test-delete-me@example.com` | PF `RESTAURANT-OPS-ROADMAP.md` L144-146 | Open |
| Phase 6 auth cleanup done | S-2 above | Moves to Tenant foundation |
| `RESTAURANT_TIMEZONE` required at first run | PF phase-0 design L403-407 | Dropped (D10a superseded, never built); per-restaurant zone at onboarding is a Tenancy & Identity question |
| Four frontend auth follow-ups (credentials, single-flight, no retry, new claims) | PF phase-1 auth design L229-232, L363-367 | Done (FE `2026-09-25-auth-refresh-cookie-cross-tab.md`) |

## Open questions

- Q-01 (docs location): answered 2026-09-29; the banners use `enterprise-order-suite/docs` (ADR-0011).
- Q-02 (never-merge scope) sets the git wording of both `CLAUDE.md` rewrites and of banner F1 (ADR-0013).
- Banners on the two public READMEs (F11, B13): answered 2026-10-01, Q-10 a ("Q10- a"); applied the same day (BE `c8bf761`, FE `30bc172`).
- F5 and F10 (frontend tooling): Q-06 a and Q-07 answered 2026-10-01 (FE `c4a7309`). The F5 replacement banner and the F10 `connect-backend` stopgap banner were applied 2026-10-01 (frontend repo); the skill's rewrite around the vendored spec (Q-76 a) waits for Build 1.
- Banners made stale by Gabriel's answers of 2026-10-01 (F1, F2, F4 in the frontend repo; the B11 extra line in this repo): replacement lines recorded under each entry and applied 2026-10-01.
- What happens to the flat copies in `/mnt/project-files` after S2 (keep as mirrors, delete, or banner them too). Register: Q-15 (decided by Claude 2026-09-29, Gabriel may override: a, keep them under a pointer README saying the repo wins).
- Wording for pt-BR legacy docs: F7 proposes "Reference only; the English file wins", answering ADR-0012's open question; Gabriel confirms in S1. Register: Q-14 (decided by Claude 2026-09-29, Gabriel may override: the F7 wording, applied in S2).

## Reviewer notes

- Reviewer note: MASTER-PLAN §4 (L68) names salvage only for backend phases 5 and 6. Backend phase 3 (Tables, DeliveryZones, RestaurantSettings; PF `2026-09-20-...-migration-design.md` L163) has no named home in the D-7 build order, although delivery zones and restaurant settings are needed by Storefront ↔ Order Core (step 4) and by ADR-0014. MASTER-PLAN §5 (L76, the scope page) is the place to put them; flagged so S5 does not miss them. Register: Q-30.
- Reviewer note: MASTER-PLAN §4 (L68) lists the frontend roadmap's "read first" and "what's next" as superseded, but its audit section (L230-241) also tells agents to plan from the pt-BR docs, which conflicts with D-10 / ADR-0012. It is included in F1 and in the banner.
