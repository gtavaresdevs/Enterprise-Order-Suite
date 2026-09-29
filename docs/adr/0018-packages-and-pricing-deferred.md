# ADR-0018: Packages, pricing and billing deferred
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel (accepted Claude's proposal; Q-03 row 3.7, 2026-09-29)
- Supersedes: "Plans (Basic/Pro/...) = feature flags per tenant" as part of Tenant foundation (`planning/pm-tool-recommendation.md` L83, L88)
- Related: ADR-0001, ADR-0005, ADR-0006, ADR-0007, ADR-0014

## Context
- `planning/pm-tool-recommendation.md` L76-88 is headed "shared multi-tenant SaaS with plan-based packages (decided)". The decided part is multi-tenancy (D-1, ADR-0001); the package part is a recommendation (L88: "packages as subscription plans that switch features on per restaurant").
- The frontend core-package spec uses "package" for separately sellable product modules: 1 Operação (Core), 2 Fiscal/NFC-e, 3 Financeiro, 4 Estoque & Compras, 5 Delivery & Marketplace, 6 CRM & Fidelização, 7 Equipe, 8 Relatórios & BI, 9 Cardápio Inteligente, 10 Integrações & Plugins (`order-ui/docs/superpowers/specs/2026-09-15-core-package-br-i18n-ux-design.md` L7-10, L328-351). This conflicts with packages as subscription plans.
- `planning/ai-ready-development-plan.md` §6 (L95) planned to ask "what a package switches on" in the Tenant batch.
- There are no customers (D-8, MASTER-PLAN L41). SaaS billing is Out this run (MASTER-PLAN §6 L95). "Pricing and packages" is a Later question (§8d L120).
- MASTER-PLAN §4 (L63): packages as Basic/Pro feature flags in Tenant foundation → deferred; "No customers; the old spec had 10 sellable modules, which conflicts anyway."

## Decision
Accepted (Gabriel, 2026-09-29; Q-03 row 3.7, "3 Yes"). Proposed by Claude in MASTER-PLAN §4 L63.
- This run has no pricing, no plans, no per-tenant entitlements or feature flags, and no billing.
- The restaurant (tenant) record carries no plan, package or entitlement field. The answer to "what does a package switch on?" is "nothing yet".
- Every restaurant gets the same feature set this run.
- When the first paid tier is defined, a new ADR introduces the entitlement mechanism.

## Consequences
- Tenant foundation acceptance criteria contain no entitlement or plan work.
- The core-package spec's package list stays a product-module roadmap reference; it schedules nothing.
- Agents must never add plan tables, feature-flag or entitlement machinery, pricing, subscription billing, or collection of SaaS fees (collecting money is also excluded by ADR-0005 and ADR-0006).
- Agents avoid the word "package" for pricing tiers in new docs until the glossary settles the vocabulary.

## Open questions
- Q-03 row 3.7 (this deferral): answered 2026-09-29, accepted. Q-11 was dropped: Gabriel had not confirmed it before.
- Pricing and packages: MASTER-PLAN §8d (L120), Later. Register: Q-78.
- Vocabulary: "package" as a sellable product module (core-package spec) vs "plan" as a subscription tier (pm-tool): glossary. Register: Q-78.
- Reviewer note: memory `MEMORY.md` L13 lists "Packages/pricing deferred" under "Decisions (Gabriel, 2026-09-29)", while MASTER-PLAN §4 (L63) marks it as a Claude proposal and §8a Q3 (L114) still asks Gabriel to accept it. This ADR follows the master plan (Proposed). If Gabriel already confirmed it, it becomes Accepted at S1; if not, the memory line should be corrected in the S0 memory cleanup. Register: Q-11. Resolved: Q-11 dropped (not confirmed earlier); Gabriel accepted the deferral with Q-03 on 2026-09-29.

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §3 D-1 (L34), D-8 (L41), §4 packages row (L63), §6 Later/Out (L95), §8a Q3 (L114), §8d (L120)
- `/mnt/project-files/planning/pm-tool-recommendation.md` L76-88
- `/mnt/project-files/planning/ai-ready-development-plan.md` §6 (L93-95)
- Frontend `order-ui/docs/superpowers/specs/2026-09-15-core-package-br-i18n-ux-design.md` L7-10, L328-351 (`Claude-Assisted-Development` @ `14a3cfd`)
- `/tmp/claude/memory/team/silo/MEMORY.md` L13
