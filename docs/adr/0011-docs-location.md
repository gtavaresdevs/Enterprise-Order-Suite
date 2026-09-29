# ADR-0011: Shared docs live in the backend repo `docs/` folder
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel (Q-01 option a, with his frontend annotation rule, and Q-03 row 3.1; both answered 2026-09-29)
- Supersedes: the docs-hub repo `enterprise-order-suite-docs` proposal (`planning/pm-tool-recommendation.md` L90-106; `planning/ai-ready-development-plan.md` "Docs hub" L45-49)
- Related: ADR-0010, ADR-0012, ADR-0013, ADR-0016, ADR-0017

## Context
- D-9 (MASTER-PLAN L42) requires one shared place for docs that both repos and every agent read. It does not name the place.
- The hub repo was proposed but never created or approved (`planning/pm-tool-recommendation.md` L92 "not created yet"; MASTER-PLAN §4 L57).
- Existing doc folders:
  - backend: `enterprise-order-suite/docs/superpowers/{specs,plans}` and `docs/contracts/`;
  - frontend: `order-ui/docs/superpowers/{specs,plans}`, including the manifest 0.4.0 and the business-rules master.
- A third repo adds a third `CLAUDE.md`, a third working-branch rule and cross-repo PR plumbing (MASTER-PLAN §4 L57, "for no gain").
- The backend owns the contract (ADR-0010). Its drift test compares the running code with a committed `docs/api/openapi.yaml`, which is simplest when both sit in one repo.
- S0 drafts are written to `/mnt/project-files/adr/` using the final layout, so they move unchanged (MASTER-PLAN §6 S0, L87).
- Gabriel's answer to Q-01 (2026-09-29T16:34Z, project thread): "its ok to live in backend repo, frontend should fetch from it, backend keeps updated, frontend can annotate if necessary upon previous agreement with me."

## Decision
Accepted (Gabriel, 2026-09-29; register Q-01 option a and Q-03 row 3.1).

1. **Location.** The shared docs live in the backend repo (`gtavaresdevs/enterprise-order-suite`), folder `docs/`, on the working branch `feature/ai-agent`. There is no separate docs repo (Q-01 option b not chosen).
2. **The frontend fetches them from the backend repo.** Frontend sessions, skills and tools read the shared docs from the backend repo. Frontend sessions attach the backend repo read-only. The rewritten `order-ui/CLAUDE.md` (S2) points to the backend `docs/README.md`. How frontend CI and sessions fetch the committed API spec: Q-76.
3. **The backend keeps them updated.** A shared doc changes only through a commit on the backend working branch (ADR-0013). The frontend repo never holds an edited or diverging version of a shared doc.
4. **Frontend annotations only with Gabriel's prior agreement.** The frontend may annotate a shared doc only when necessary, and only after Gabriel has agreed to that annotation beforehand. Without his agreement, the frontend agent raises the point with Gabriel and does not annotate.
   - Where an agreed annotation lives is **not decided**. Claude's proposal: in the frontend repo, in the frontend file that needs it (for example `order-ui/CLAUDE.md`), as a clearly marked block that names the backend doc it annotates:
     ```markdown
     > **Frontend note (agreed with Gabriel on YYYY-MM-DD):** <note>. Annotates backend `docs/<path>` @ `<commit>`.
     ```
     Reason: only backend commits change shared docs (point 3), and frontend sessions attach the backend repo read-only (point 2). Alternative: the same marked block inside the backend doc, committed on `feature/ai-agent`. Gabriel decides when the first annotation is needed.
5. **Layout** (Claude's proposal; S2 creates it):
   ```text
   docs/
     README.md            where to look, current phase, what exists (read first)
     roadmap.md           interim tracker (ADR-0017)
     adr/NNNN-slug.md     decision records (numbering fixed in S0)
     architecture/        RESTAURANT-OPS-ARCHITECTURE.md + Tenancy & Identity, API conventions,
                          Order Core, Menu & Storefront
     api/openapi.yaml     committed live spec (ADR-0010)
     api/drafts/          design-first contract drafts (ADR-0010)
     glossary.md          domain terms, legacy->new map, PT-BR UI vocabulary
     templates/           doc templates with Status + Open questions (ADR-0016)
   ```
   Existing `docs/superpowers/{specs,plans}` and `docs/contracts/` stay where they are (the latter superseded by ADR-0010).

## Consequences
- S2 creates the `docs/` tree in the backend repo, on `feature/ai-agent`, and commits and pushes it there (ADR-0013).
- Doc links inside `docs/` are relative to `docs/`. ADR filenames keep the S0 names so cross-references hold.
- Shared-doc changes are commits on the backend working branch and follow ADR-0013 (commit straight to the working branch and push; never merge).
- A cloud session that needs the docs attaches the backend repo; a frontend session therefore attaches two repos.
- Plans stay in the repo whose code they change (`docs/superpowers/plans/`), as today.
- This ADR moves no legacy doc. S2 adds superseded banners (MASTER-PLAN §6 L90).
- Agents must: read shared docs from the backend repo `docs/`; make every shared-doc change in the backend repo.
- Agents must never:
  - create a separate docs repo;
  - edit, copy-and-modify or fork a shared doc in the frontend repo;
  - add a frontend annotation without Gabriel's prior agreement to that annotation;
  - pick a location for an agreed annotation before Gabriel decides point 4.

## Open questions
- Q-01 and Q-03 row 3.1: answered 2026-09-29 (see Decision).
- Where an agreed frontend annotation lives (Decision point 4): Gabriel decides when the first annotation is needed. Local to this ADR.
- How frontend CI and local frontend sessions fetch the backend spec: Q-76.
- Whether the business-rules master (`order-ui/docs/superpowers/specs/2026-09-16-business-rules-master-en.md`) moves into `docs/` or stays in the frontend with a pointer: S2. Register: Q-14 (decided by Claude 2026-09-29, Gabriel may override: it stays in the frontend with a pointer until S5 re-verifies it, then the English master moves to `docs/business-rules/`).

## Sources
- Gabriel's S1 answer, project thread, 2026-09-29T16:34Z: "1 - its ok to live in backend repo, frontend should fetch from it, backend keeps updated, frontend can annotate if necessary upon previous agreement with me. 2 - commit to working branch and push. 3 Yes."
- `/mnt/project-files/planning/open-questions.md` Q-01, Q-03 (row 3.1), Q-14, Q-76
- `/mnt/project-files/planning/MASTER-PLAN.md` §3 D-9 (L42), §4 docs row (L57), §6 S0/S2 (L87, L90), §8a Q1 (L112)
- `/mnt/project-files/planning/pm-tool-recommendation.md` "Shared docs hub" L90-106
- `/mnt/project-files/planning/ai-ready-development-plan.md` R1/R3/R4/R5 (L45-49)
- `/tmp/claude/memory/team/silo/project-decisions-2026-09-29.md` L13 (hub proposed, not created)
- Backend `enterprise-order-suite` (`feature/ai-agent` @ `af2634e`): `docs/superpowers/`, `docs/contracts/`
- Frontend `order-ui` (`Claude-Assisted-Development` @ `14a3cfd`): `docs/superpowers/specs/`, `docs/superpowers/plans/`
