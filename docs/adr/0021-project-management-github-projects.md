# ADR-0021: Project management: GitHub Projects, set up before Build 1
- Status: Accepted
- Date: 2026-10-06
- Decided by: Gabriel (project thread, 2026-10-06T14:02Z). The board design below (where issues live, Status values, labels, the reference convention) was Claude's default; Gabriel confirmed it on 2026-10-06 (18:12Z) when he reviewed the runbook, and may still change any of it.
- Supersedes: ADR-0017 (Plane), in full. The parts of ADR-0017 that carry over are restated below.
- Related: ADR-0007, ADR-0011, ADR-0013, ADR-0015, ADR-0016

## Context
- ADR-0017 chose Plane, self-hosted on a free VPS. On 2026-10-06 Oracle had no Always Free A1 capacity and gave Gabriel only a 1 GB `VM.Standard.E2.1.Micro`, which cannot run Plane (4 GB minimum). He then chose Plane's own free cloud plan (2026-10-06T13:49Z, recorded in ADR-0017), and 13 minutes later replaced Plane altogether: "Ok, so lets redesign our plan around github projects instead of plane, much simpler, free and AI can access it."
- Both repos are public, on Gabriel's personal account `gtavaresdevs`, with Issues and Projects enabled and `main` as the default branch. The backend repo has no issues and no milestones yet, only GitHub's nine default labels; its pull requests go up to #25, and issues share that number space, so the first issue will be #26 (GitHub REST API, 2026-10-06).
- GitHub Projects (checked 2026-10-06): a project belongs to a user or an organization, not to a repository; up to 50 fields; built-in workflows, of which "item closed" and "pull request merged" (both set Status to Done) are on by default; GitHub Free allows **one** auto-add workflow per project, each workflow watches one repository, and it does not add existing items. The `gh project` commands need a token with the `project` scope.
- What agents can reach, tested from a cloud session on 2026-10-06: repository endpoints work (issues, comments, labels; the session's GitHub tools also read and write issues). The user-level project does not: GraphQL "is not available through this session's GitHub proxy", and `users/gtavaresdevs/projectsV2` is refused because "sessions are bound to their configured repositories". So cloud sessions can work on the issues but cannot read or move the board's own fields, such as Status. A Claude Code session on Gabriel's PC, with `gh` holding the `project` scope, can do both.
- Closing keywords (`closes`, `fixes`, `resolves` and their forms) in a commit close the issue only "when you merge the commit into the default branch". ADR-0013 never merges into `main`, so on this project they never close anything.

## Decision
- Accepted (Gabriel, 2026-10-06): **GitHub Projects is the project management tool**, replacing Plane (ADR-0017). It is free, needs no server, account or token beyond GitHub, and its work items are GitHub issues, which agents already read and write.
- Carried over from ADR-0017 unchanged (Claude's reading of "redesign our plan around github projects instead of plane": the plan keeps its shape and only the tool changes): canonical text stays in git and work items only link to it (ADR-0011); the setup is the first step before Build 1 (Q-83 b, 2026-10-01) and a readiness-gate item; `docs/roadmap.md` is the tracker until the board is in use; no import of historical phases, no empty future milestones or iterations, no GitHub Action or webhook worker (Q-03 row 3.10). Claude's reading: GitHub's built-in project workflows are switches in the tool, not the automations that row 3.10 excluded, so the ones below are used.
- Claude's defaults for the board (runbook `docs/ops/github-projects-setup.md`):
  - One user-owned project, "Enterprise Order Suite", owned by `gtavaresdevs`, board layout grouped by Status.
  - **Every work item is an issue in the backend repo**, including frontend work (label `area:frontend`). Reasons: the backend repo is the docs home (ADR-0011); GitHub Free allows one auto-add workflow, which watches one repository; and one repo gives one number space.
  - Status: Todo, In Progress, In review, Done (GitHub's three plus "In review" for Gabriel's review). Agent-relevant flags are labels, because cloud sessions cannot read Status: `ready-for-agent`, `blocked`, `needs-human`, plus `area:*`, `type:*` and `contract-change`.
  - Workflows: auto-add every issue from the backend repo; "item added" sets Todo; the two default "Done" workflows stay on.
  - References: commit messages and plan files cite `#<n>` in the backend repo, or `gtavaresdevs/Enterprise-Order-Suite#<n>` from the frontend repo, with `Refs`, never a closing keyword. This replaces the `EOS-<n>` ids of ADR-0017. Issues are closed by hand; closing one moves its card to Done.
- **Agents create issues for build work on their own** (Gabriel, project thread 2026-10-07T01:45Z: "please add that rule to the docs, consider it reviewed and approaved by me."). This answers the open point "what agents may do on issues" for issue creation:
  - When a build step, or a piece of build work with no issue yet, starts, the agent searches the backend repo's open issues for one that covers it. If none does, it creates one in the backend repo (frontend work too, labelled `area:frontend`) with one `area:*` and one `type:*` label, a short title and a body that links the canonical doc (plan, roadmap step, ADR). Canonical text stays in git (ADR-0011).
  - Every commit for that work cites it with `Refs #<n>` (from the frontend repo, `Refs gtavaresdevs/Enterprise-Order-Suite#<n>`). The agent may comment on and label its issues; it closes the issue by hand when the work is done, with a comment naming the commits. Closing moves the card to Done.
  - `docs/roadmap.md` steps move to issues as they start: the step's Status gets the issue number. The roadmap keeps the order of work and the gate until every open step has its issue.
  - Moving cards between Todo, In Progress and In review stays Gabriel's, or a local session's with `gh project` (cloud sessions cannot reach board fields).

## Consequences
- The setup is Gabriel's, about 20 minutes: create the project, add the Status option, switch on the auto-add workflow, and paste one `gh label create` block. No server, no backups, no token for cloud sessions.
- Issues in a public repo are public, whatever the project's own visibility. Agents and people must never put restaurant, customer or order data, secrets, tokens, IP addresses or credentials into an issue, a comment or a project field.
- Cloud sessions see and change issues, not board fields. Anything an agent must know (ready for it, blocked, needs a person) is a label on the issue. Moving cards between Todo, In Progress and In review is Gabriel's, or a local session's with `gh project`.
- A commit with "closes #N" on `feature/ai-agent` leaves the issue open (ADR-0013 keeps `main` untouched); use `Refs #N` and close by hand.
- Every Plane runbook and script (the Plane Cloud runbook, the self-hosted Oracle runbook, and `docs/ops/plane/`) was deleted on 2026-10-06 (Gabriel, 2026-10-06T14:10Z: "u can delete what's related to plane, we wont use it"). They remain in git history up to commit `1092797`. ADR-0017 stays as the superseded decision record: an ADR is never deleted, only superseded (`adr/README.md`, Conventions).
- Set up by Gabriel on 2026-10-06 (project chat, 15:12Z): "Board setup done https://github.com/users/gtavaresdevs/projects/2". The Build 1 gate item for the board is met. Since 2026-10-07 agents create the issues themselves as build steps start (Decision), so `docs/roadmap.md` hands over to the board step by step.
- Agents must never:
  - invent or cite an issue number that does not exist;
  - create the project, its fields, views or workflows, or new labels, before Gabriel says so (issues for build work are the exception, Decision);
  - use a closing keyword in a commit message;
  - put any of the data listed above into an issue.

## Open questions
- Q-83: the 2026-10-06 change is recorded in its row. HTTPS exposure and off-box backups are moot (they were already closed under Plane Cloud). The reference convention (`#<n>` with `Refs`) was Claude's default; Gabriel confirmed it with the runbook on 2026-10-06 (project thread, 18:12Z: "4 - confirmed", "confirms all points as per recommended by claude"), together with the rest of the board design in section 0.
- Answered 2026-10-07 (Gabriel, 01:45Z, recorded as Reviewed at his word): agents create, comment on, label and close issues for build work on their own (Decision).

## Sources
- Gabriel, project thread 2026-10-07T01:45Z (agents create issues for build work).
- Gabriel, project thread 2026-10-06T14:02Z (this decision) and 13:48Z-13:49Z (Plane Cloud, ADR-0017).
- GitHub REST API from this project's cloud session, 2026-10-06: `repos/gtavaresdevs/Enterprise-Order-Suite` and `repos/gtavaresdevs/enterprise-order-suite-frontend` (`default_branch: main`, `visibility: public`, `has_issues`, `has_projects`); the refused GraphQL call and `users/gtavaresdevs/projectsV2` call, with the proxy's messages quoted above.
- GitHub Docs, read 2026-10-06: About Projects (50 fields; projects at the user or organization level): https://docs.github.com/en/issues/planning-and-tracking-with-projects/learning-about-projects/about-projects ; Adding items automatically (GitHub Free: 1 auto-add workflow; one repository per workflow; existing items are not added): https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/adding-items-automatically ; Using the built-in automations (closed and merged set Done, on by default): https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-built-in-automations ; Linking a pull request to an issue (keywords; closed "when you merge the commit into the default branch"): https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue
- GitHub CLI manual, `gh project` (minimum scope `project`; `gh auth refresh -s project`): https://cli.github.com/manual/gh_project
- ADR-0017 (superseded), `planning/open-questions.md` Q-83 and Q-03 row 3.10, `planning/pm-tool-recommendation.md` ("Proposed board structure": states and labels).
