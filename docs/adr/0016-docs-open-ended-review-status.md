# ADR-0016: Docs are open-ended; Draft / Reviewed / Ready statuses
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel. How Reviewed is signalled: Q-09 a, answered 2026-10-01
- Supersedes: none (defines the statuses that `planning/ai-ready-development-plan.md` R4 L48 named without defining)
- Related: ADR-0010, ADR-0011, ADR-0012, ADR-0015

## Context
- D-14 (MASTER-PLAN L47): documentation has no fixed "done"; it is iterated with Gabriel, who is asked questions as each contract is drafted.
- MASTER-PLAN §7 (L107): "Reviewed" = Gabriel read it and no open question blocks the next build step. Only Gabriel sets it.
- MASTER-PLAN §9 (L124-126): the docs stay open-ended and grow contract by contract; each only has to reach Reviewed before the build step that needs it. §7 (L105) requires API conventions and the Tenancy & Identity contract to be Reviewed before Build 1.
- ai-ready R4 (L48) planned templates with a "Status: Draft / Reviewed / Ready" line and an "Open questions" block, without defining the statuses or who sets them.

## Decision
Accepted (D-14). The Reviewed definition and "only Gabriel sets it" come from MASTER-PLAN §7. The Draft and Ready definitions and the demotion rule are Claude's proposals (see Open questions).

Every doc (architecture, contract and its draft, scope, NFR, glossary, roadmap) has:
- a `Status:` line near the top;
- an `## Open questions` block; each question says which step it blocks, or "none", and cites its register id when it has one.

Statuses:

| Status | Meaning | Who sets it | May a build step use it? |
|---|---|---|---|
| **Draft** | Default. Being written or changed. May contain blocking open questions. | Any agent or Gabriel | No. Read for context only. |
| **Reviewed** | Gabriel read it and no open question blocks the next build step. Non-blocking questions stay listed. | Only Gabriel. The agent records it as "Reviewed by Gabriel on YYYY-MM-DD". | Yes. This is what the gate (§7) and each build step require. |
| **Ready** | Reviewed, and every requirement the next build step implements has testable acceptance criteria, so a plan can cite them without new questions to Gabriel. | The agent writing the implementation plan, after checking. | Yes. Informational; never a substitute for Reviewed. |

- Demotion: a change to a Reviewed or Ready doc that alters a decision or adds a blocking question sets it back to Draft. Editorial fixes (typos, links, wording that changes no decision) keep the status.
- How Gabriel signals Reviewed (Gabriel, 2026-10-01; Q-09 a, "Q9-a"): he posts "Reviewed: <doc>" in the project thread. Claude records it in the doc as "Reviewed by Gabriel on YYYY-MM-DD", with the date of his message. No git work is needed from him.
- There is no Final, Done or Approved status. Docs keep growing.
- ADR statuses (Proposed, Accepted, Superseded by ADR-XXXX) are separate: they record decisions, not doc maturity. Only Gabriel moves an ADR to Accepted (MASTER-PLAN §7 L99).
- Legacy docs get a superseded banner instead (MASTER-PLAN §6 S2 L90); they are outside this lifecycle.

## Consequences
- Agents must:
  - ask Gabriel questions while drafting each contract, in his language (ADR-0012), and record the answers in the doc in English;
  - move answered questions out of the Open questions block into the doc body;
  - check a doc's status before building from it.
- Agents must never:
  - set Reviewed without a "Reviewed: <doc>" message from Gabriel in the project thread, or treat silence, a push, or their own review as Reviewed;
  - start a build step from a Draft;
  - keep Reviewed on a doc after changing one of its decisions.
- The S2 templates implement the Status line and the Open questions block (MASTER-PLAN §6 L90).
- The open-questions register holds cross-doc questions (Q-01 onward); doc-level questions reference its ids.

## Open questions
- Confirm the Draft and Ready definitions and the demotion rule (Claude proposals). Register: Q-12, which also asks whether the architecture root carries a decision status ("Accepted with amendments") next to its doc status (AMD Reviewer note 2). Decided by Claude 2026-09-29 (option a: yes to both); Gabriel may override.
- Q-09: answered 2026-10-01, a: a "Reviewed: <doc>" message from Gabriel in the project thread, which Claude records (Decision).

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §3 D-14 (L47), §6 S2 (L90), §7 (L97-107), §9 (L122-126)
- `planning/open-questions.md` Q-09: Gabriel's answer of 2026-10-01 (project thread, 2026-10-01T16:25Z)
- `/mnt/project-files/planning/ai-ready-development-plan.md` R4 (L48), gate L91
- `/tmp/claude/memory/team/silo/MEMORY.md` L12
