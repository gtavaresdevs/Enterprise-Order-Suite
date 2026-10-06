# Project board setup runbook (GitHub Projects)

- Status: Reviewed
- Updated: 2026-10-06 (Reviewed by Gabriel, 18:12Z; earlier the same day: board set up by Gabriel, labels created, smoke-test note on workflow order; Gabriel ticked the optional `gh` item himself, commit `b709078`)
- Reviewed: Reviewed by Gabriel on 2026-10-06 (project thread, 2026-10-06T18:12Z: "4 - confirmed", "confirms all points as per recommended by claude", answering item 4 of Claude's 15:20Z list, which asked him to read and mark Reviewed ADR-0020, the settings proposal and this runbook). Claude's defaults in section 0 stand, `Refs #<n>` included.
- Roadmap step: Project board setup, before Build 1 (`docs/roadmap.md`)
- Related: ADR-0021 (the decision), ADR-0011, ADR-0013, ADR-0016; register Q-83
- Who runs it: Gabriel, on his GitHub account. Agents do not create the project, its fields, views, workflows or the labels until he says so (ADR-0021). Claude wrote this page from GitHub's docs and from what a cloud session could reach on 2026-10-06; it updates the page from what Gabriel reports.
- Replaces: the Plane runbooks (Plane Cloud, and self-hosted Plane on Oracle), deleted on 2026-10-06 at Gabriel's request; they remain in git history up to commit `1092797`.

Gabriel, 2026-10-06T14:02Z: "Ok, so lets redesign our plan around github projects instead of plane, much simpler, free and AI can access it."

**Set up on 2026-10-06.** Gabriel, project chat 15:12Z: "Board setup done https://github.com/users/gtavaresdevs/projects/2". It is project number 2 on his account. His smoke-test issues are #26 to #29 in the backend repo, all closed. His message named no change to Claude's defaults (section 0), so they stand.

The board is a GitHub Project on Gabriel's account. Its cards are issues in the backend repo. There is no server, no extra account, no backup and no token for cloud sessions. Plan for about 20 minutes.

## 0. Claude's defaults (change any of them)

These were Claude's choices; Gabriel confirmed them when he reviewed this page on 2026-10-06. To change one, say so in the project thread.

| Topic | Claude's default | Why |
|---|---|---|
| Project | "Enterprise Order Suite", owned by `gtavaresdevs`, Board layout, Private, linked to both repos | A project belongs to a user or an organization, never to a repo. Private hides the board; the issues stay public because the repos are public |
| Where work items live | Issues in the **backend repo** only, frontend work included (label `area:frontend`) | GitHub Free allows one auto-add workflow, and each watches one repo. The backend repo is the docs home (ADR-0011). One repo means one number space |
| Status | Todo, In Progress, **In review**, Done | GitHub's three plus a column for Gabriel's review (ADR-0016) |
| Agent signals | Labels, not Status: `ready-for-agent`, `blocked`, `needs-human` | Cloud sessions can read and change issues and labels, but not the board's Status field (section 1) |
| Other labels | `area:backend`, `area:frontend`, `area:docs`, `type:feature`, `type:bug`, `type:doc`, `type:chore`, `contract-change` | From `planning/pm-tool-recommendation.md` ("Labels"). GitHub's nine default labels stay; agents use only these |
| Workflows | Auto-add every issue from the backend repo; "Item added to project" sets Todo; the default "Item closed" and "Pull request merged" (both set Done) stay on | Cards appear and finish without anyone dragging them. These are switches in GitHub, not the GitHub Actions that Q-03 row 3.10 rules out |
| References | `Refs #<n>` in commit messages and plan files (from the frontend repo: `Refs gtavaresdevs/Enterprise-Order-Suite#<n>`). Never `closes`/`fixes`/`resolves` | Those keywords close an issue only when the commit reaches `main`, which ADR-0013 never does. Issues are closed by hand, and closing moves the card to Done |
| What goes in an issue | A title, a short goal, a link to the doc or commit. Never restaurant, customer or order data, secrets, tokens or IP addresses | The repos are public, so every issue is public. Canonical text stays in git (ADR-0011) |
| Milestones and iterations | None up front | ADR-0021 carries over "no empty future modules" |

## 1. Facts checked on 2026-10-06

| Fact | Value | Source |
|---|---|---|
| Repos | Both public, owner `gtavaresdevs` (a personal account), Issues and Projects enabled, default branch `main` | GitHub REST API from a cloud session |
| Backend repo today | No issues, no milestones, GitHub's nine default labels. Pull requests go up to #25 and share the number space, so **the first issue will be #26** | same |
| Project limits | Up to 50 fields; projects belong to a user or an organization | [G1] |
| Auto-add | GitHub Free: 1 auto-add workflow per project. Each targets one repository. Existing items are not added, only items created or updated after it is switched on | [G2] |
| Built-in workflows | "Item closed" and "Pull request merged" set Status to Done and are on by default; "Item added to project" can set Todo | [G3] |
| Closing keywords | `close`, `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves`, `resolved`; the issue closes "when you merge the commit into the default branch" | [G4] |
| `gh project` | Needs a token with the `project` scope: `gh auth refresh -s project` | [G5] |
| Cloud sessions (this claude.ai project) | Repo endpoints work: issues, comments, labels, through `gh api` and the GitHub tools. The user-level project does not: GraphQL "is not available through this session's GitHub proxy", and `users/gtavaresdevs/projectsV2` is refused because "sessions are bound to their configured repositories" | tried 2026-10-06 |

So "AI can access it" holds for the issues, which is where the work is described, discussed and closed. Cloud sessions cannot read or move the board's own fields, which is why the agent signals are labels. A Claude Code session on Gabriel's PC can do both, through `gh` (section 6).

## 2. Create the project

1. On github.com, open your profile > **Projects** > **New project**.
2. Pick the **Board** layout (a blank board, not a template with sample items), name it `Enterprise Order Suite`, and create it.
3. Project settings (the `...` menu at the top right > Settings):
   - Visibility: **Private**.
   - Short description: "Work items for Enterprise Order Suite. Canonical docs: backend repo `docs/` on `feature/ai-agent`."
4. Link it to the repos, so it shows on their Projects tabs: in each repo, open **Projects** > **Link a project** > `Enterprise Order Suite`. Do this for both repos.

GitHub's menu labels change over time; take the names above as a guide.

## 3. Status column

1. On the board, open the Status field's options (column header menu, or Settings > Fields > Status).
2. Add **In review** between "In Progress" and "Done". Leave "Todo", "In Progress" and "Done" as they are.

## 4. Workflows

Project `...` menu > **Workflows**:
1. **Auto-add to project**: repository `Enterprise-Order-Suite`, filter `is:issue`, then **Save and turn on workflow**.
2. **Item added to project**: when an issue is added, set Status to **Todo**. Turn it on.
3. **Item closed** and **Pull request merged**: already on (both set Done). Leave them.

Leave every other workflow off.

## 5. Labels

**Done on 2026-10-06:** Claude created all eleven labels below in the backend repo at Gabriel's request ("create the board labels", project thread 14:39Z), and read them back: names, colours and descriptions match this block. Skip this section unless a label is missing or was changed.

Pick one of these:
- **Ask Claude.** Say "create the board labels" in the project thread. A cloud session can create them in the backend repo (it has repo access); it will not do so unprompted (ADR-0021).
- **Paste it yourself.** With the GitHub CLI installed and logged in (`gh auth login`), paste these lines into PowerShell or Git Bash. `--force` updates a label that already exists, so the block is safe to re-run.

```bash
gh label create "area:backend" --repo gtavaresdevs/Enterprise-Order-Suite --color 1D76DB --description "Backend repo work" --force
gh label create "area:frontend" --repo gtavaresdevs/Enterprise-Order-Suite --color 5319E7 --description "Frontend repo work (tracked here)" --force
gh label create "area:docs" --repo gtavaresdevs/Enterprise-Order-Suite --color 0E8A16 --description "Docs, ADRs, register" --force
gh label create "type:feature" --repo gtavaresdevs/Enterprise-Order-Suite --color A2EEEF --description "New behaviour" --force
gh label create "type:bug" --repo gtavaresdevs/Enterprise-Order-Suite --color D73A4A --description "Something is wrong" --force
gh label create "type:doc" --repo gtavaresdevs/Enterprise-Order-Suite --color C5DEF5 --description "Documentation only" --force
gh label create "type:chore" --repo gtavaresdevs/Enterprise-Order-Suite --color EDEDED --description "Tooling, CI, upkeep" --force
gh label create "contract-change" --repo gtavaresdevs/Enterprise-Order-Suite --color FBCA04 --description "Changes the API contract" --force
gh label create "ready-for-agent" --repo gtavaresdevs/Enterprise-Order-Suite --color 0052CC --description "Spec is clear; an agent may pick it up" --force
gh label create "blocked" --repo gtavaresdevs/Enterprise-Order-Suite --color B60205 --description "Waiting on something outside this issue" --force
gh label create "needs-human" --repo gtavaresdevs/Enterprise-Order-Suite --color F9D0C4 --description "Needs Gabriel's decision or action" --force
```

## 6. Smoke test, and Claude Code on your PC

1. Check that the section 4 workflows are on first. Then, in the backend repo, create an issue titled `Board smoke test`. It should appear on the board under **Todo** within a few seconds.
   - If it does not: auto-add ignores issues that existed before it was switched on and adds them only when they are next edited [G2]. Edit the issue (add a label, for example) or add it by hand from its right sidebar (Projects > `Enterprise Order Suite`), then create one fresh issue to test auto-add on its own.
2. Close it. Its card should move to **Done**. Leave it closed.
3. Optional, for Claude Code on your PC to read and move cards (cloud sessions cannot, section 1):
   ```bash
   gh auth refresh -s project
   gh project list --owner gtavaresdevs
   gh project item-list 2 --owner gtavaresdevs --format json
   ```
   `2` is the project number, from its URL (`github.com/users/gtavaresdevs/projects/2`). No MCP server is needed: Claude Code runs `gh` directly.

Cloud sessions need no setup: they already reach the repo's issues.

How agents use issues (read, comment, label, close) is decided when the board replaces `docs/roadmap.md` as the tracker (ADR-0021). Until then agents never invent or cite an issue number.

## 7. Checklist

Ticked on Gabriel's word (project chat, 2026-10-06T15:12Z, "Board setup done https://github.com/users/gtavaresdevs/projects/2"); a cloud session cannot see the project itself (section 1).

- [x] Project "Enterprise Order Suite" on `gtavaresdevs`, Board layout, Private, linked to both repos
- [x] Status: Todo, In Progress, In review, Done
- [x] Workflows: auto-add (`Enterprise-Order-Suite`, `is:issue`) on; "Item added" sets Todo; "Item closed" and "Pull request merged" on
- [x] The eleven labels in section 5 exist in the backend repo (created by Claude 2026-10-06)
- [x] Smoke-test issue appeared under Todo and moved to Done when closed (issues #26 to #29, all closed)
- [x] Optional: `gh project list --owner gtavaresdevs` works on your PC
- [x] Gabriel posted the outcome in the project thread (section 8): 2026-10-06T15:12Z

## 8. After setup

Post in the project thread: "Board setup done", the project's URL, and any of Claude's defaults (section 0) you changed. The URL is not a secret. Claude then updates, in one commit: the Q-83 row in `planning/open-questions.md` (recording only what Gabriel said), ADR-0021, `docs/roadmap.md` (the board setup step and its readiness-gate item) and this page. Only Gabriel sets this page to Reviewed (ADR-0016).

## Sources

Read 2026-10-06. Nothing on this page was run against a real project: Claude has no access to Gabriel's account-level projects.
- [G1] GitHub Docs, About Projects (up to 50 fields; user or organization level): https://docs.github.com/en/issues/planning-and-tracking-with-projects/learning-about-projects/about-projects
- [G2] GitHub Docs, Adding items automatically (auto-add workflows per plan: Free 1, Pro and Team 5, Enterprise 20; one repository per workflow; existing items are not added; qualifiers `is`, `label`, `reason`, `assignee`, `no`): https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/adding-items-automatically
- [G3] GitHub Docs, Using the built-in automations (closed and merged set Done, on by default; added sets Todo): https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-built-in-automations
- [G4] GitHub Docs, Linking a pull request to an issue (closing keywords; "The issue will be closed when you merge the commit into the default branch"): https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue
- [G5] GitHub CLI manual, `gh project` (minimum scope `project`; `gh auth refresh -s project`; `list`, `item-list`, `item-edit` and the rest): https://cli.github.com/manual/gh_project
- Repo: ADR-0021; ADR-0017 (superseded); `planning/pm-tool-recommendation.md` ("Proposed board structure"); `planning/open-questions.md` Q-83 and Q-03 row 3.10.
- Not read today, used from memory: GitHub's menu labels for creating and linking a project, and the `gh label create` flags (`gh label create --help` has the current form).

## Inputs needed from Gabriel

- ~~Sections 2 to 4, the smoke test and the outcome message.~~ Done by Gabriel on 2026-10-06. ~~The labels (section 5).~~ Done by Claude on 2026-10-06.
- ~~Optional: give `gh` the `project` scope on your PC (section 6).~~ Done by Gabriel on 2026-10-06 ("6-done").
- ~~Mark this page Reviewed when you have read it (ADR-0016).~~ Done on 2026-10-06.
