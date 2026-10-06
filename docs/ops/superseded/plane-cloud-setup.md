# Plane setup runbook (Plane Cloud, free plan) - superseded

> **Superseded on 2026-10-06 (Gabriel, project thread 2026-10-06T14:02Z): "Ok, so lets redesign our plan around github projects instead of plane, much simpler, free and AI can access it."** The project uses GitHub Projects (ADR-0021); nothing on this page is run. The current runbook is `../github-projects-setup.md`. Kept as the record of the 13-minute Plane Cloud plan and of the Plane free-plan facts checked that day.

- Status: Superseded (was Draft)
- Updated: 2026-10-06
- Reviewed: not yet
- Roadmap step: Plane setup, before Build 1 (`docs/roadmap.md`)
- Related: ADR-0017, ADR-0011, ADR-0013, ADR-0016; register Q-83
- Who runs it: Gabriel, on his own Plane account. Agents never sign up for a service and never create Plane structure, automations, tokens or secrets (ADR-0017). Claude wrote this page from Plane's own docs and updates it from what Gabriel reports.
- Replaces: `plane-selfhost-oracle.md` (self-hosted Plane CE on an Oracle Always Free VM), with its tested scripts in `../plane/`. Nothing there is run any more.

Gabriel's answer to Q-83 (2026-10-01) put the Plane setup before Build 1. On 2026-10-06 he changed where Plane runs: Oracle had no Always Free A1 capacity, only a 1 GB `VM.Standard.E2.1.Micro`, which cannot run Plane (its self-host docs ask for 4 GB). After being told the free plan's seat limit does not matter with one user ("bro the only sit I need is mine"), he wrote: "Ok, so lets readjust our plan so that we use the plane free tier in their cloud."

So Plane runs on **Plane Cloud's free plan**. There is no server, no HTTPS setup, no backup job and no maintenance. The whole setup is sign-up, one workspace, one project, states and labels, and a token for the agents. Plan for about 30 minutes.

Never put the token into this repo (it is public), a commit or the project chat. The password manager holds it.

## 0. Claude's defaults (change any of them)

Claude picked these so the setup needs no questions; they are Claude's choices, not Gabriel's answers to Q-83. To change one, say so in the project thread.

| Topic | Claude's default | Why |
|---|---|---|
| Where Plane runs | Plane Cloud free plan, `https://app.plane.so` | Gabriel's decision of 2026-10-06. No server to run or back up |
| Workspace and project | Workspace "Enterprise Order Suite", slug `eos`; project "Enterprise Order Suite", identifier `EOS` | The MCP and the API read the slug |
| Work-item ids | `EOS-<n>` in commit messages and plan files once Plane is in use. No per-item branches or PRs (ADR-0013) | Links commits to work items with no new git process |
| What goes in Plane | Status only: a short title, a link to the doc or commit, and the state. Canonical text stays in git (ADR-0011, ADR-0017) | Plane Cloud is someone else's server; this also makes losing Plane a small loss |
| Restaurant and customer data | Never in Plane. No real order, customer or menu data in a work item, even as an example | It is a third-party service, and the app is pre-launch anyway |
| Off-box backups | None. There is nothing of ours to lose: the docs are in git, and work items hold status only | Replaces the nightly encrypted backup of the self-hosted plan, which existed because the data lived on one free VM |
| Token expiry | 1 year, with a calendar reminder | Plane asks for an expiry when the token is created |
| MCP access for agents | Plane's hosted MCP server, `https://mcp.plane.so/http/mcp`, signed in with OAuth from Claude Code on Gabriel's PC | Nothing to install, and no token in a config file |
| Pages, cycles, modules | Not used. Project Pages are on the free plan, but docs stay in git | ADR-0017: Plane only links to the docs |

## 1. Facts checked on 2026-10-06

| Fact | Value | Source |
|---|---|---|
| Free plan price and seats | "$0 per seat per month", "12 users"; guests "Limited" | Pricing page [P1] |
| On the free plan | Projects and work items, cycles and modules, basic layouts and views, work item intake, basic estimates, project pages, comments, basic roles, importers (Jira, Linear, Asana, ClickUp, CSV, Confluence, Notion), integrations (GitHub, GitLab, Slack, Sentry, Draw.io) | [P1] |
| Not on the free plan | AI credits, work item types and properties, workspace wiki, time tracking and worklogs, templates, dashboards, initiatives, teamspaces, SAML/OIDC/LDAP, advanced automation | [P1] |
| API | Base `https://api.plane.so/`, header `X-API-Key`, 60 requests per minute per client | API reference [P2] |
| Personal access token | Profile settings > Personal Access Tokens > "Add personal access token": title, description, optional expiry | [P2], [M1] |
| Workspace slug | The segment after `app.plane.so/` in the URL | [M1] |
| Hosted MCP server | `https://mcp.plane.so/http/mcp` with OAuth ("your client redirects you to Plane, where you sign in and choose a workspace"), or `https://mcp.plane.so/http/api-key/mcp` with the headers `Authorization: Bearer <token>` and `x-workspace-slug: <slug>` | [M1] |
| MCP and the plan | "The MIT-licensed server is free to use. The Plane features it can access follow your Plane plan." A plan-gated action returns a message naming the unavailable feature | [M1] |

What the free plan's limits mean for us:
- **Work item types and properties are paid.** The labels in section 4 carry that information instead, which is what the earlier plan already did.
- **No dashboards or initiatives.** `docs/roadmap.md` stays the plan-level view; Plane holds the items under it.
- **No workspace wiki.** Docs stay in git either way (ADR-0017).
- **60 API requests per minute** is far above anything an agent does here, so no agent needs to batch or throttle.
- The **GitHub integration is on the free plan**, which contradicts `planning/pm-tool-recommendation.md` ("native GitHub sync is a paid feature", written about Community Edition). It stays unused: ADR-0017 allows no automations, and nothing in the build depends on it.
- Unverified: whether the free plan can export work items to CSV, and whether a work item's attachment size is capped. Neither blocks the setup, because nothing of ours lives only in Plane.

## 2. Sign up

1. Open https://app.plane.so and sign up with the Google account or email Gabriel wants to own the project, and confirm the email if asked.
2. Store the Plane sign-in email and password in the password manager. If it is a Google sign-in, note that instead.
3. Stay on the free plan. Plane may offer a trial of a paid plan: decline it, so nothing starts counting down.

## 3. Workspace and project

The board structure in `planning/pm-tool-recommendation.md` is not applied as written (ADR-0017). This is the minimum to start; Gabriel adjusts it.

1. Create the workspace:
   - Name: `Enterprise Order Suite`.
   - URL or slug: replace the suggested value with `eos`. The MCP and the API read this.
   - Size question: `Just myself`.
2. Plane may create a demo or sample project in a new workspace. Delete it, so it does not take the name of the real one (Project settings > General > Delete).
3. Create the project:
   - Name: `Enterprise Order Suite`.
   - Identifier: `EOS`, so items are numbered `EOS-1`, `EOS-2` and so on.
   - Access: Private.
4. Project settings > Features: leave Cycles, Modules, Views, Intake and Pages off (whatever is on by default, switch off what we do not use). ADR-0017 rules out empty future modules and automations, and docs stay in git.

Console labels on Plane Cloud were not checked in a browser; they follow the self-hosted wording closely, so take the names above as a guide.

## 4. States and labels

Project settings > Work structure (names from `planning/pm-tool-recommendation.md`, trimmed by ADR-0017):

| State | Plane state group | Note |
|---|---|---|
| Backlog | Backlog | default |
| Spec'd | Unstarted | rename the default "Todo" |
| Ready for agent | Unstarted | what agents may pick up |
| In Progress | Started | default; keep Plane's name |
| In review | Started | waiting for Gabriel's review. ADR-0013 has no PRs to merge, so the pm-tool name "In review (PR open)" is shortened |
| Blocked | Started | |
| Done | Completed | default |
| Cancelled | Cancelled | keep Plane's default |

Labels: `area:backend`, `area:frontend`, `area:docs`, `type:feature`, `type:bug`, `type:doc`, `type:chore`, `contract-change`, `needs-human`.

Then create one work item, write a description, reload the page and check the text stayed. That is the whole smoke test.

## 5. Token for the agents

1. Avatar or profile menu > Settings > Personal Access Tokens > "Add personal access token" [P2]:
   - Title: `claude-mcp`.
   - Expiry: 1 year (Claude's default). Put a calendar reminder two weeks before it.
2. Copy the token into the password manager when it is shown; Plane shows it once. If Plane also downloads a CSV copy, delete that file from Downloads and from the Recycle Bin.
3. Check it, in Git Bash so the token stays out of shell history (PowerShell has no `read -s`):
   ```bash
   read -rs PLANE_API_KEY && export PLANE_API_KEY
   curl -fsS -H "X-API-Key: $PLANE_API_KEY" "https://api.plane.so/api/v1/workspaces/eos/projects/" | head -c 300; echo
   ```
   You should see JSON containing the `EOS` project. The exact path may differ by API version; the API reference [P2] has the current one.
4. Agents act with Gabriel's permissions and their comments appear under his name. To rotate the token: revoke it in Plane, create a new one, and redo section 6.

## 6. Give Claude access

1. **Claude Code on Gabriel's PC** (including Remote Control sessions). Add Plane's hosted MCP server at user scope, so it stays out of every repo:
   ```bash
   claude mcp add --transport http plane https://mcp.plane.so/http/mcp
   claude mcp list
   ```
   The first call signs in through the browser and asks which workspace to use [M1]. Never use `--scope project`: that writes `.mcp.json` into a public repo. The flag spelling is from memory; `claude mcp add --help` has the current form.
2. **This claude.ai project** (cloud sessions, where most of the work happens). Plane is not in Claude's connector directory, so the options are a custom connector pointed at `https://mcp.plane.so/http/mcp`, or the token endpoint `https://mcp.plane.so/http/api-key/mcp` with the `Authorization` and `x-workspace-slug` headers [M1]. Neither was tried. Claude proposes the exact steps once the workspace exists and it can see which of the two the project settings accept.
3. Nothing in the repos needs the token. If a committed `.mcp.json` ever becomes the right answer, it reads the token from an environment variable and never contains it.

What agents may do in Plane (read, comment, move state) is decided when Plane replaces `docs/roadmap.md` as the tracker. Until then agents never invent or cite `EOS-<n>` ids (ADR-0017).

## 7. Checklist

- [ ] Signed up at `app.plane.so`, free plan, sign-in saved in the password manager
- [ ] Workspace `eos`; any demo project deleted
- [ ] Project `EOS`, Private; Cycles, Modules, Views, Intake and Pages off
- [ ] States and labels as in section 4
- [ ] One work item created, description survived a reload
- [ ] Token `claude-mcp` created (1 year, calendar reminder), saved, any CSV copy deleted; the `curl` check returns the `EOS` project
- [ ] `claude mcp list` shows `plane` on Gabriel's PC
- [ ] Gabriel posted the outcome in the project thread (section 8)

## 8. After setup

Post in the project thread: "Plane setup done", plus any of Claude's defaults (section 0) you changed. Leave out the token. Claude then updates, in one commit: the Q-83 row in `planning/open-questions.md` (recording only what Gabriel said), ADR-0017, `docs/roadmap.md` (the Plane setup step and its readiness-gate item) and this page. Only Gabriel sets this page to Reviewed (ADR-0016).

## Sources

Read 2026-10-06. Nothing on this page was tested: it has no scripts, and Claude has no Plane account.
- [P1] Plane, Pricing (free plan: "$0 per seat per month", "12 users"; the included and excluded feature lists): https://plane.so/pricing
- [P2] Plane, API reference: base URL `https://api.plane.so/`, the `X-API-Key` header, 60 requests per minute, and creating a personal access token under Profile settings: https://developers.plane.so/api-reference/introduction
- [M1] Plane, MCP server: the hosted endpoints `https://mcp.plane.so/http/mcp` (OAuth) and `https://mcp.plane.so/http/api-key/mcp` (`Authorization: Bearer`, `x-workspace-slug`), where the token is created, how the workspace slug is read, and "The MIT-licensed server is free to use. The Plane features it can access follow your Plane plan": https://developers.plane.so/dev-tools/mcp-server
- Repo: ADR-0017; `planning/pm-tool-recommendation.md` ("Proposed board structure"); `planning/open-questions.md` Q-83; `ops/superseded/plane-selfhost-oracle.md` (the self-hosted path, with everything that was tested on 2026-10-02).
- Not read today, used from memory: `claude mcp add` syntax (local `claude mcp add --help` has the current form); Plane Cloud's sign-up and workspace-creation screens.

## Inputs needed from Gabriel

Only Gabriel can do these (ADR-0017).

- Sign up at `app.plane.so` and keep the free plan (section 2).
- Create the workspace, delete any demo project, create the project, switch off the unused features (section 3).
- Set up the states and labels, and run the one-item smoke test (section 4).
- Create the token, store it, delete any CSV copy, and run the `curl` check (section 5).
- Register the MCP server on his PC (section 6, item 1), and say whether he wants cloud sessions to reach Plane too (item 2).
- Password manager: the Plane sign-in and the token.
- When done: the outcome message in section 8 (no token).
