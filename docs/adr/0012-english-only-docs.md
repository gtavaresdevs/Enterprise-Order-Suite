# ADR-0012: Documentation in English only; questions to Gabriel in his language
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
- Supersedes: none (restates legacy D6, which is kept; see ADR-0000)
- Related: ADR-0000, ADR-0011, ADR-0016

## Context
- D-10 (MASTER-PLAN L43): docs in English only; questions to Gabriel in his language.
- Legacy D6: "Questions are asked in the user's language; code and docs are always English" (`2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` L58). Backend `CLAUDE.md` L29-30 says the same.
- Portuguese docs exist in the frontend repo:
  - `order-ui/docs/superpowers/specs/2026-09-16-regras-de-negocio.md` (business rules). Its English counterpart `2026-09-16-business-rules-master-en.md` calls itself the "English source" (L1).
  - `order-ui/docs/superpowers/specs/2026-09-16-fluxo-de-dados.md` (data flow). No English counterpart was found; it describes itself as the technical complement of `regras-de-negocio.md` (L3).
- The product UI is bilingual: react-i18next, 15 namespaces per locale, EN and pt-BR (MASTER-PLAN §2 L22).

## Decision
Accepted (D-10).
- Written in English: docs, ADRs, contracts and drafts, OpenAPI descriptions, code identifiers, code comments, commit messages, PR text, skills, agent prompts, `CLAUDE.md` files, the open-questions register.
- Questions to Gabriel are written in the language he writes in. His answers are recorded in the docs in English.
- Out of scope (product content, not docs): i18n resource files (EN and pt-BR), UI copy, restaurant-entered content, demo/seed data. The glossary may list PT-BR UI terms as mappings (MASTER-PLAN §5 L78).
- Decided later (Gabriel, 2026-10-01; Q-39 a, "Q33  - Q64- Recommended"): the API returns codes and raw values, never localized text; the frontend translates them. Restaurant content (menu names, descriptions) is returned as the owner typed it.

## Consequences
- Agents write every new doc in English and translate any Portuguese content they reuse.
- When a doc exists in both languages, agents read and cite the English one (`business-rules-master-en.md` over `regras-de-negocio.md`).
- PT-BR-only docs (`fluxo-de-dados.md`) are translated only when a new doc needs their content; translation is not a readiness task.
- Agents must never:
  - put Portuguese prose into ADRs, contracts, code or comments;
  - translate or remove the pt-BR i18n resources (they are product content);
  - ask Gabriel questions in English when he writes in another language.

## Open questions
- Banner wording for PT-BR legacy docs (superseded, or "reference only; the English file wins"): S2 superseded-docs list. Register: Q-14 (decided by Claude 2026-09-29, Gabriel may override: "Reference only; the English file wins", applied in S2).
- Q-39: answered 2026-10-01, a: the API returns codes and raw values and the frontend translates (Decision). The API conventions doc (S4) writes out the detail.

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §2 (L22), §3 D-10 (L43), §5 glossary (L78)
- `planning/open-questions.md` Q-39: Gabriel's answer of 2026-10-01 (project thread, 2026-10-01T16:25Z)
- `/mnt/project-files/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` D6 (L58)
- `/mnt/project-files/2026-09-16-business-rules-master-en.md` L1
- Backend `enterprise-order-suite/CLAUDE.md` L29-30 (`feature/ai-agent` @ `af2634e`)
- Frontend `order-ui/docs/superpowers/specs/2026-09-16-regras-de-negocio.md`, `2026-09-16-fluxo-de-dados.md` L1-3 (`Claude-Assisted-Development` @ `14a3cfd`)
