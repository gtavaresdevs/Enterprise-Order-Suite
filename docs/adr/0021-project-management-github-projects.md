# ADR-0021: Project management: GitHub Projects, set up before Build 1
- Status: Accepted
- Date: 2026-10-06
- Decided by: Gabriel (project thread, 2026-10-06T14:02Z). The board design below (where issues live, Status values, labels, the reference convention) is Claude's default; Gabriel may change any of it.
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
- What agents may do on issues (read, comment, label, close) is decided when the board replaces `docs/roadmap.md`, as ADR-0017 had it for Plane.

## Consequences
- The setup is Gabriel's, about 20 minutes: create the project, add the Status option, switch on the auto-add workflow, and paste one `gh label create` block. No server, no backups, no token for cloud sessions.
- Issues in a public repo are public, whatever the project's own visibility. Agents and people must never put restaurant, customer or order data, secrets, tokens, IP addresses or credentials into an issue, a comment or a project field.
- Cloud sessions see and change issues, not board fields. Anything an agent must know (ready for it, blocked, needs a person) is a label on the issue. Moving cards between Todo, In Progress and In review is Gabriel's, or a local session's with `gh project`.
- A commit with "closes #N" on `feature/ai-agent` leaves the issue open (ADR-0013 keeps `main` untouched); use `Refs #N` and close by hand.
- `docs/ops/plane-setup.md` (Plane Cloud) moves to `docs/ops/superseded/plane-cloud-setup.md`; the self-hosted runbook and scripts stay superseded where they are (`docs/ops/superseded/plane-selfhost-oracle.md`, `docs/ops/plane/`).
- Agents must never:
  - invent or cite an issue number that does not exist;
  - create the project, its fields, views or workflows, or the labels, before Gabriel says so;
  - use a closing keyword in a commit message;
  - put any of the data listed above into an issue.

## Open questions
- Q-83: the 2026-10-06 change is recorded in its row. HTTPS exposure and off-box backups are moot (they were already closed under Plane Cloud). The reference convention (`#<n>` with `Refs`) is Claude's default, still pending as an answer, like the `EOS-<n>` default it replaces.
- What agents may do on issues once the board is the tracker: decided then (carried over from ADR-0017).

## Sources
- Gabriel, project thread 2026-10-06T14:02Z (this decision) and 13:48Z-13:49Z (Plane Cloud, ADR-0017).
- GitHub REST API from this project's cloud session, 2026-10-06: `repos/gtavaresdevs/Enterprise-Order-Suite` and `repos/gtavaresdevs/enterprise-order-suite-frontend` (`default_branch: main`, `visibility: public`, `has_issues`, `has_projects`); the refused GraphQL call and `users/gtavaresdevs/projectsV2` call, with the proxy's messages quoted above.
- GitHub Docs, read 2026-10-06: About Projects (50 fields; projects at the user or organization level): https://docs.github.com/en/issues/planning-and-tracking-with-projects/learning-about-projects/about-projects ; Adding items automatically (GitHub Free: 1 auto-add workflow; one repository per workflow; existing items are not added): https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/adding-items-automatically ; Using the built-in automations (closed and merged set Done, on by default): https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-built-in-automations ; Linking a pull request to an issue (keywords; closed "when you merge the commit into the default branch"): https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue
- GitHub CLI manual, `gh project` (minimum scope `project`; `gh auth refresh -s project`): https://cli.github.com/manual/gh_project
- ADR-0017 (superseded), `planning/open-questions.md` Q-83 and Q-03 row 3.10, `planning/pm-tool-recommendation.md` ("Proposed board structure": states and labels).
