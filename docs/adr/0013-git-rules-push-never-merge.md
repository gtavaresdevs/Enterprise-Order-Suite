# ADR-0013: Git rules for agents: working branches only, push allowed, never merge
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel (D-11; scope: Q-02 option a; enforcement and extended rules: Q-03 row 3.9; both answered 2026-09-29; branch protection: Q-72 a, answered 2026-10-01, confirmed done 2026-10-06)
- Supersedes: the legacy frontend finishing step "merge to Claude-Assisted-Development locally" (`order-ui/docs/superpowers/plans/RESTAURANT-OPS-ROADMAP.md` L8-14); the ai-ready R14 custom git-guard hook (`planning/ai-ready-development-plan.md` L55), replaced by `permissions.deny` (MASTER-PLAN §4 L65)
- Related: ADR-0011, ADR-0015

## Context
- D-11 (MASTER-PLAN L44): agents may push but never merge; always read the working branches, never `main`. Working branches: backend `feature/ai-agent`, frontend `Claude-Assisted-Development` (`planning/pm-tool-recommendation.md` L63; memory `git-branch-rules.md`).
- Frontend rules today (`order-ui/CLAUDE.md` L129-139): commit on `Claude-Assisted-Development` and push to it; never commit to, merge into or push `main`; explicit-pathspec commits; never `git add -A`; plain push, never force; only the main agent commits or pushes, subagents never run git write commands; never `git stash` (shared `.git` across worktrees).
- The legacy frontend process ran each phase in worktrees and ended with `superpowers:finishing-a-development-branch`, always choosing "merge to Claude-Assisted-Development locally" (`RESTAURANT-OPS-ROADMAP.md` L8-14). That is a merge.
- Backend `CLAUDE.md` has no git section. Backend `.claude/settings.json` (L1-15) holds only a `pwsh` PreToolUse hook, which cannot run on Linux cloud sessions (ai-ready L19).
- Frontend `.claude/` is gitignored (`order-ui/.gitignore` L16-17): frontend settings exist only on Gabriel's machine. Since 2026-10-01 it is versioned: Gabriel committed it in FE commit `c4a7309` (Q-06 a, Q-07).
- MASTER-PLAN §4 (L65) replaced the planned Node git-guard hook with `permissions.deny` rules in each repo's `.claude/settings.json`: "Built-in, cross-platform, no script."
- Gabriel's answers (2026-09-29T16:34Z, project thread): Q-02 "commit to working branch and push."; Q-03 "Yes." (all rows, including 3.9).

## Decision
Accepted (D-11):
1. Agents read the working branches, never `main`, for any reference, diff or doc citation.
2. Agents may commit to and push the working branch of the repo they work in. Plain push only.
3. Agents never merge, anywhere (Q-02 option a): agents commit straight to the working branch and push. No agent runs `git merge` in any form, including local worktree-to-working-branch merges. No merging or auto-merging a pull request, no push to `main`. Work lands on the working branch itself, not on feature branches for Gabriel to merge.
   - Parallel worktree results land on the working branch by `git cherry-pick` (option a as written in the register).

Accepted with D-11's enforcement (Q-03 row 3.9, 2026-09-29; MASTER-PLAN §4 L65):
4. Both repos adopt the frontend's existing rules: explicit-pathspec commits, never `git add -A`, never `git stash`, never force push, only the main agent runs git write commands.
5. Enforcement is `permissions.deny` in each repo's committed `.claude/settings.json`, added in S3. The frontend needs `.claude/` versioned first (Gabriel's S1 push, MASTER-PLAN §6 L88). Done 2026-10-01: Gabriel versioned the frontend `.claude/` (FE commit `c4a7309`), and Claude added these deny rules to `order-ui/.claude/settings.json` and the repo-root `.claude/settings.json` the same day (FE commit `30bc172`).

Accepted (Gabriel, 2026-10-01; Q-72 a, "Q71 - 72 a"):
6. Gabriel turns on GitHub branch protection for `main` in both repos. It is his action in the GitHub settings. Confirmed done on 2026-10-06 (project thread, 2026-10-06T18:12Z: "5 - done"); GitHub REST reports `main` as protected in both repos, and the backend repo has an active ruleset "Request PR for Main".

Q-02 answer (MASTER-PLAN §8a Q2): (a) agents commit straight to the working branch and push, as the frontend already does; no agent runs `git merge` at all. Not chosen: (b) agents push feature branches that Gabriel merges; (c) local worktree-to-working-branch merges allowed.

`permissions.deny` patterns to add (both repos; `Bash(<prefix> *)` is a prefix wildcard, `Bash(<cmd>)` an exact match, a bare MCP tool name denies that tool):
```json
{
  "permissions": {
    "deny": [
      "Bash(git merge *)",
      "Bash(git stash *)",
      "Bash(git add -A *)",
      "Bash(git add --all *)",
      "Bash(git add .)",
      "Bash(git commit -a *)",
      "Bash(git commit -am *)",
      "Bash(git commit --all *)",
      "Bash(git push origin main *)",
      "Bash(git push origin HEAD:main *)",
      "Bash(git push -u origin main *)",
      "Bash(git push --set-upstream origin main *)",
      "Bash(git push --force *)",
      "Bash(git push --force-with-lease *)",
      "Bash(git push -f *)",
      "Bash(gh pr merge *)",
      "mcp__github__merge_pull_request",
      "mcp__github__enable_pr_auto_merge"
    ]
  }
}
```
- Core set from MASTER-PLAN §4 L65: merge, stash, push to main, `add -A`.
- Added from existing rules: `git add --all`, `git add .` and `git commit -a/-am/--all` (explicit-pathspec rule, `order-ui/CLAUDE.md` L134-136); force push (L137); PR merge and auto-merge through `gh` or the GitHub MCP tools (D-11 "never merge").
- `Bash(git merge *)` stays: under Q-02 option a no agent merges.

## Consequences
- S3 merges these patterns into the existing backend `.claude/settings.json` (keeping or replacing the `pwsh` hook per the S3 port/remove decision) and into the frontend `.claude/settings.json` once it is versioned (versioned since 2026-10-01, FE commit `c4a7309`).
- S2 rewrites both `CLAUDE.md` files with the same git section: working branch name, the rules above, and `git pull --ff-only` (a plain `git pull` can create a merge commit; prefix rules cannot deny "pull without a flag", so this stays a written rule).
- The legacy worktree finishing step (merge locally) is not used by agents.
- Deny rules match the command text by prefix. They do not catch every spelling (for example flags placed after the branch name, `git -C <dir> merge`, or a push through another tool). They are a guardrail, not a security boundary.
- Until Gabriel confirmed branch protection on 2026-10-06 (Decision 6), the deny rules were the only guard against a push to `main`, and they cover Claude sessions only. Branch protection is now the hard guard; the deny rules stay as the agents' guardrail.
- Before S3 writes them, the pattern syntax is checked against the Claude Code version in use (older docs show a `:*` suffix form).
- The rules apply to any Claude session in the repo, including Gabriel's own.
- Agents must never: merge; push to `main`; force push; stash; commit without an explicit pathspec; let a subagent run a git write command; read `main` as the reference branch.

## Open questions
- Q-02 (never-merge scope): answered 2026-09-29, option a (Decision 3). Parallel worktree work lands by `git cherry-pick`; `git merge` in any form, including `--ff-only`, is out. Whether `git rebase` counts as allowed is not decided; ask Gabriel if an S3 workflow needs it. Local to this ADR.
- Q-72: answered 2026-10-01, a: Gabriel turns on GitHub branch protection for `main` in both repos (Decision 6); confirmed done 2026-10-06 ("5 - done").
- Q-03 row 3.9 (`permissions.deny` over the custom hook; rules extended to the backend): answered 2026-09-29, accepted.

## Sources
- Gabriel's S1 answer, project thread, 2026-09-29T16:34Z ("2 - commit to working branch and push. 3 Yes."); PF `planning/open-questions.md` Q-02, Q-03 row 3.9
- Gabriel's answers, project thread, 2026-10-01T16:25Z ("Q71 - 72 a"; Q6 and Q7 lines); `planning/open-questions.md` Q-06, Q-07, Q-72; Gabriel's confirmation, project thread, 2026-10-06T18:12Z ("5 - done"); GitHub REST `repos/gtavaresdevs/<repo>/branches/main` and `.../rulesets`, read 2026-10-06
- `/mnt/project-files/planning/MASTER-PLAN.md` §3 D-11 (L44), §4 git-guard row (L65), §6 S1/S3 (L88, L90), §7 (L102), §8a Q2 (L113)
- `/mnt/project-files/planning/pm-tool-recommendation.md` L63
- `/mnt/project-files/planning/ai-ready-development-plan.md` §1 hooks row (L19), R14 (L55), gate L87
- `/mnt/project-files/RESTAURANT-OPS-ROADMAP.md` (= `order-ui/docs/superpowers/plans/RESTAURANT-OPS-ROADMAP.md`) L8-14, L27-31
- `/tmp/claude/memory/team/silo/git-branch-rules.md` L8-11
- Backend `enterprise-order-suite` (`feature/ai-agent` @ `af2634e`): `.claude/settings.json` L1-15; `CLAUDE.md` (no git section)
- Frontend `order-ui` (`Claude-Assisted-Development` @ `14a3cfd`): `CLAUDE.md` L129-139; `.gitignore` L16-17; commit `c4a7309` (versions `.claude/`)
