# Plane setup runbook (Oracle Always Free) - superseded

> **Superseded on 2026-10-06 (Gabriel, project thread 2026-10-06T13:49Z): "lets readjust our plan so that we use the plane free tier in their cloud."** The project uses Plane Cloud's free plan, so no server is set up and nothing on this page is run. The current runbook is `../github-projects-setup.md`: later the same day Plane Cloud was replaced too, by GitHub Projects (ADR-0021). The Plane Cloud page is `plane-cloud-setup.md`. Kept, with its tested scripts in `../plane/`, in case Plane is ever self-hosted: everything below was correct for Plane CE v1.4.2 on 2026-10-02, and the Oracle A1 availability problem that ended this path (only a 1 GB `VM.Standard.E2.1.Micro` was free) is not a defect in it. Treat every statement below as of that date, and re-check the sources before using it.

- Status: Superseded (was Draft)
- Updated: 2026-10-02
- Reviewed: not yet
- Roadmap step: Plane setup, before Build 1 (`docs/roadmap.md`)
- Related: ADR-0017, ADR-0011, ADR-0013, ADR-0015; register Q-83
- Who runs it: Gabriel, on his own Oracle, Tailscale and Google accounts. Claude cannot reach the server. Claude wrote and tested the scripts (section Tested) and updates this page from what Gabriel reports.

Gabriel's answer to Q-83 (2026-10-01, project thread 2026-10-01T16:25Z): "Q83 - b -> other I already create the oracle free tier with always free account, its fresh and ready to begin the setup, so this is the first thing we do before begin working on the actual app."

This page takes that fresh account to a working Plane Community Edition (CE) with nightly encrypted off-box backups and a token for the Plane MCP. Every step is Gabriel's: agents never sign up for or configure hosting, and never create Plane structure, automations, tokens or secrets (ADR-0017). Plan for about two hours, including 15 to 30 minutes of waiting for the first boot. The fast path is section 2's one-paste first-boot file. Appendix A does the same setup by hand.

The repo is public. Never put a token, password, `plane.env` value, IP address, `rclone` config or crypt password into this file, a commit or the project chat. The password manager holds them.

## 0. Claude's defaults (change any of them)

Gabriel asked for this run to go ahead without questions ("Allow all, dont ask me anything for this run"), so Claude picked the defaults below. They are Claude's choices, not Gabriel's answers to Q-83. To change one, say so in the project thread.

| Topic | Claude's default | Why |
|---|---|---|
| HTTPS exposure | Tailscale Funnel: `https://plane.<tailnet>.ts.net` | Free, needs no domain (Gabriel never mentioned owning one) and no inbound port, and cloud Claude sessions can reach it. Alternatives: Cloudflare Tunnel (needs a domain on Cloudflare [C1]), or Tailscale private (`tailscale serve`; cloud sessions cannot reach it) |
| Order | Claim the instance admin and turn off public sign-up before Funnel goes on. `plane-url.sh` refuses to run until both are done | Whoever completes `/god-mode/` first becomes the instance admin |
| Off-box backups | Google Drive through `rclone` (scope `drive.file`) under an `rclone` crypt remote. Nightly at 03:30; 7 copies on the server, 30 days on Drive | Free and outside Oracle. `backup.sh` refuses a remote that does not encrypt |
| Work-item ids | `EOS-<n>` in commit messages and plan files once Plane is in use. No per-item branches or PRs (ADR-0013) | Links commits to work items with no new git process |
| Timezone | `America/Sao_Paulo` | Puts the nightly backup at night |
| Instance | `plane`: `VM.Standard.A1.Flex`, 2 OCPU, 12 GB, Canonical Ubuntu 24.04 aarch64, 100 GB boot volume, home region, Always Free-eligible | The whole A1 allowance; 100 GB of the free 200 GB |
| Plane release | v1.4.2, pinned in `cloud-init.yaml` | The tested release. Upgrade later (section 11) |
| Workspace and project | Workspace "Enterprise Order Suite", slug `eos`; project "Enterprise Order Suite", identifier `EOS`, Private | The MCP reads the slug |
| Telemetry | Off: untick it on the claim form | Nothing here needs it |
| Token expiry | 1 year, with a calendar reminder | Plane requires an expiry unless "Never expires" is switched on |
| Pay As You Go | Upgrade, after setting a US$1 budget alert (section 9). This is billing, so Gabriel decides | Plane's measured memory is under Oracle's idle line |

## 1. Facts checked on 2026-10-01 and 2026-10-02

| Fact | Value | Source (read dates under Sources) |
|---|---|---|
| Plane CE CPU and RAM | 2 cores (x64 or ARM64); 4 GB RAM, 8 GB recommended for production | Plane Docker Compose docs [P1] |
| Plane CE install path | `install-plane.sh` (this repo) runs Plane's own `setup.sh install` for a pinned release without questions. Before the first start it replaces every shipped default password and secret, including `DATABASE_URL` and `AMQP_URL`. Then it starts Plane | [P8]; section Tested |
| Plane CE features | At par with the Free tier of Plane Cloud; AGPL v3.0 | [P3] |
| Oracle A1 Always Free | 1,500 OCPU hours and 9,000 GB hours per month on `VM.Standard.A1.Flex`, "equivalent to 2 OCPUs and 12 GB of memory" for Always Free tenancies | Oracle [O1] |
| Oracle storage and traffic | 200 GB of block storage, boot and block volumes combined, plus 5 volume backups; 10 TB outbound per month | [O1] |
| Home region | Always Free compute and volumes must be created in the tenancy's home region | [O1] |
| Capacity | "Out of host capacity" means a temporary lack of Always Free shapes: try another availability domain or wait and retry | [O1] |
| Idle reclamation | An instance is idle if, over 7 days, the 95th-percentile CPU is under 20%, network is under 20%, and (A1 only) memory is under 20%; idle Always Free instances "may be reclaimed" | [O1] |
| Oracle Ubuntu images | User `ubuntu` with `sudo`. The default firewall rules allow only SSH. Do not use UFW on Ubuntu images; rules live in `/etc/iptables/rules.v4` | [O7], [O8] |
| Initialization script | Create instance > 1. Basic information > Advanced options > Management > Initialization script (browse to the file or drag it in). User data and metadata: 32,000 bytes at most. "Do not include anything in the script that could trigger a reboot" | [O9] |
| Docker and host firewalls | Ports that Docker publishes bypass the host's firewall rules | [D1] |
| Tailscale Funnel | Ports 443, 8443 and 10000 only; `ts.net` names only; needs MagicDNS, HTTPS certificates and the `funnel` node attribute; `--bg` keeps it on across reboots | [T1], [T2] |
| Certificate Transparency | Every Tailscale HTTPS certificate is in the public CT ledger, so the machine name `plane.<tailnet>.ts.net` becomes public. Access does not | [T3] |
| Plane memory (measured) | 1,211 MiB idle and 1,258 MiB after the restore drill, all 12 containers together; about 10% of 12 GB. Measured on amd64 | section Tested |

So 2 OCPU and 12 GB on A1 meet Plane's minimum CPU and exceed its recommended RAM. For a new account 2 OCPU and 12 GB is the ceiling: do not size the instance above it.

Where sources differ:
- The 2 OCPU / 12 GB figure in `planning/pm-tool-recommendation.md` is confirmed by Oracle's page [O1]. Oracle gives no date for the change. InfoQ [O2] and bex.co [O5] say 2026-06-15; Linuxiac [O3] gives no date; TerminalBytes [O4] says no official date existed and enforcement was uneven in June.
- Instances above the new limit after 2026-08-18: Sudo Security [O6] says "terminated"; bex.co [O5] says "auto-stopping and disabling". This does not affect a new 2/12 instance.
- Pay As You Go: InfoQ [O2] reports Oracle support answers that contradict each other. bex.co [O5] says upgraded accounts could keep 4 OCPU and 24 GB free, while Oracle's page says all tenancies get the same 1,500 OCPU hours free [O1]. This runbook assumes 2/12.
- Plane's Docker Compose page now leads with the Commercial Edition installer (`prime.plane.so`, free plan with 12 seats) [P1], [P3]. ADR-0017 chose Community Edition, so this runbook uses the CE `setup.sh`.
- The reclamation rule in the pm-tool doc matches Oracle's page. Oracle's page does not say whether reclaiming stops or terminates the instance, and does not say whether upgraded accounts are exempt. Re-read 2026-10-02: unchanged.

## 2. Create the instance (one paste: the first-boot file)

`docs/ops/plane/cloud-init.yaml` sets up the whole server on its first boot. It runs OS updates, adds swap, installs Docker, installs Tailscale without logging it in, and installs Plane v1.4.2 with new random passwords made on the server. It never reboots the server and contains no secrets. It downloads the helper scripts in `docs/ops/plane/` from this repo's `feature/ai-agent` branch. Its first line, `#cloud-config`, tells cloud-init what the file is [CI1].

1. On your PC, download the file. Open https://github.com/gtavaresdevs/Enterprise-Order-Suite/blob/feature/ai-agent/docs/ops/plane/cloud-init.yaml and use the "Download raw file" button (GitHub button label from memory). Keep the name `cloud-init.yaml`. Change nothing unless you change a default from section 0.
2. SSH key, in PowerShell (Windows 10 and 11 include OpenSSH). Skip this if `C:\Users\<you>\.ssh\id_ed25519.pub` already exists:
   ```powershell
   ssh-keygen -t ed25519 -C "plane-oci"
   ```
   Give the key a passphrase and store the passphrase in your password manager. The private key stays on your PC.
3. In the Oracle console, in your home region, go to Compute > Instances > Create instance. The wizard steps are 1. Basic information, 2. Security, 3. Networking, 4. Storage, Review [O9]. Console labels change over time.
   - Step 1, Basic information:
     - Name: `plane`.
     - Image: Canonical Ubuntu 24.04. With an Ampere shape, the console offers the aarch64 build.
     - Shape: Ampere > `VM.Standard.A1.Flex`, 2 OCPUs, 12 GB memory. The shape and the image must both show "Always Free-eligible".
     - Advanced options > Management > Initialization script: choose `cloud-init.yaml` (browse to it, or drag it into the box). If the console offers "Paste cloud-init script" instead, paste the whole file (not tested).
   - Step 2, Security: keep the defaults.
   - Step 3, Networking:
     - Create a new virtual cloud network and a new public subnet, and assign a public IPv4 address.
     - SSH keys: upload `id_ed25519.pub`, or paste the output of `Get-Content $env:USERPROFILE\.ssh\id_ed25519.pub`.
   - Step 4, Storage: set a custom boot volume size of 100 GB.
   - Review > Create.
4. If creation fails with "Out of host capacity", pick another availability domain or retry later [O1].
5. Copy the public IP from the instance page into your password manager. Do not put it here or in the chat.

## 3. Network: keep only SSH open

- Keep the security list that Oracle creates with the new subnet (inbound SSH and ICMP only), and add no ingress rule. Docker-published ports bypass the host firewall [D1]. That security list is the only thing keeping Plane's port 80, and its unused port 443, off the internet while Plane is still unclaimed. Tailscale only connects outward, so Plane needs no inbound port.
- Do not use `ufw` on Oracle's Ubuntu images [O7].
- Check from your PC once the first boot has finished (section 4, item 4 sends you back here). Before that, nothing listens on port 80, so a `False` proves nothing. In PowerShell, each check takes about 20 seconds (cmdlet output from memory):
  ```powershell
  Test-NetConnection <PUBLIC_IP> -Port 80     # expect: TcpTestSucceeded : False
  Test-NetConnection <PUBLIC_IP> -Port 22     # expect: TcpTestSucceeded : True
  ```
  If port 80 shows `True`, stop and remove the extra ingress rule before section 5.
- Not a default: once `ssh ubuntu@plane` works over Tailscale, the port-22 rule could go. If Tailscale then breaks, only Oracle's console connection gets you back in.

## 4. First boot: wait, check, save plane.env

1. Log in from PowerShell. During setup, always log in with the port forward that section 5 uses:
   ```powershell
   ssh -L 8080:127.0.0.1:80 ubuntu@<PUBLIC_IP>
   ```
   The first time, answer `yes` to the host-key question.
2. Wait for the first boot to finish. Expect roughly 15 to 30 minutes (not measured on Oracle).
   ```bash
   cloud-init status --wait                  # ends with: status: done
   sudo cat /var/lib/plane-bootstrap.done    # the finish time
   ```
   - If you see `status: error`, or `/var/lib/plane-bootstrap.failed` exists:
     - `sudo cat /var/lib/plane-bootstrap.failed` names the failed step.
     - `sudo tail -n 50 /var/log/plane-bootstrap.log` shows why.
     - Fix the cause, then run `sudo /usr/local/sbin/plane-firstboot.sh`. It is safe to re-run.
   - If the log shows "Failed to pull the images" or `toomanyrequests`, Docker Hub has limited pulls from this address. Wait an hour, then re-run the same command (not tested).
   - To watch live: `sudo tail -f /var/log/plane-bootstrap.log`. Ctrl+C stops the watching, not the setup.
3. If the end of the log says updates need a reboot, or the file `/var/run/reboot-required` exists (`ls /var/run/reboot-required`; "No such file" means no reboot is needed), run `sudo reboot`, wait a minute, and log in again as in item 1. Plane starts again by itself.
4. Log out and in once (`exit`, then item 1 again) so the `docker` group applies to `ubuntu`. Then check:
   ```bash
   docker ps --format 'table {{.Names}}\t{{.Status}}'   # 12 plane-app-* containers, all Up
   free -h                                             # Swap: 4.0Gi
   timedatectl show -p Timezone --value                # America/Sao_Paulo
   sudo sshd -T | grep -i '^passwordauthentication'    # passwordauthentication no
   ```
   Then run the two port checks of section 3 from your PC. Plane now listens on port 80, so only now does `False` for port 80 prove that the security list blocks it.
5. Save the server's Plane passwords now:
   ```bash
   sudo cat /opt/plane-selfhost/plane-app/plane.env
   ```
   Copy the whole output into a password-manager note named "Plane plane.env". No other copy of these passwords exists off the server. Never paste it into a chat, a commit or this repo.

## 5. Claim the instance admin and close sign-up (before anything is public)

Whoever completes `/god-mode/` first becomes the instance admin [P4]. At this point Plane answers only on the server and through your SSH port forward.

1. With the section 4 login still open, open `http://localhost:8080/god-mode/` on your PC. Keep the trailing slash [P5].
2. Fill in the page "Setup your Plane Instance":
   - First name, Last name, Email and Company name.
   - "Set a password" and "Confirm password": use a password generated by your password manager. Plane needs at least 8 characters with upper case, lower case, a number and a symbol. Use 24 or more, because this sign-in page becomes public in section 6.
   - Untick "Allow Plane to anonymously collect usage events." (Claude's default).
   - Click "Continue".
3. The browser then shows an error page at `http://localhost/god-mode/general/`, without `:8080`. That is expected: the claim is already saved. Open `http://localhost:8080/god-mode/general/` again.
4. In the God mode sidebar, open "Authentication". Switch off "Allow anyone to sign up even without an invite". It saves at once and shows "Configuration saved successfully".
5. Skip "Email" (SMTP). Without it, Plane sends no password-reset emails, so keep the password in your password manager. Section 11 shows a reset without email.

Later God-mode sign-ins at `http://localhost:8080/god-mode/` ("Manage your Plane instance", Email, Password, "Sign in") can end on the same error page. Re-open the URL as in item 3.

## 6. Publish with Tailscale Funnel

1. Log Tailscale in, on the server:
   ```bash
   sudo tailscale up --hostname=plane
   ```
   Open the printed `https://login.tailscale.com/...` link on your PC, and sign up or sign in. The command ends once the machine is added.
2. In the Tailscale admin console (https://login.tailscale.com/admin/machines), open the machine `plane` > "..." menu > "Disable key expiry". Without this, the server drops off Tailscale when its key expires (180 days by default), and Plane goes offline. These labels and the default are from memory.
3. Point Plane at its Tailscale name. The script first checks that the admin is claimed and sign-up is off, and it stops if either is not. It then sets `APP_DOMAIN`, `WEB_URL` and `CORS_ALLOWED_ORIGINS` in `plane.env` [P7]:
   ```bash
   sudo ~/plane-ops/plane-url.sh "$(tailscale status --json | jq -r .Self.DNSName)"
   ```
   It restarts Plane (about a minute) and prints the address, `https://plane.<tailnet>.ts.net/`. Below, `<PLANE_HOST>` means the host name in it, `plane.<tailnet>.ts.net`.
4. Turn on Funnel:
   ```bash
   sudo tailscale funnel --bg 80
   tailscale funnel status
   ```
   The first time, it prints a link to enable HTTPS certificates and Funnel for your tailnet. Open it and approve, and the command continues (not tested). `--bg` keeps Funnel on across reboots [T2].
5. On your PC, open `https://<PLANE_HOST>/` and sign in with the admin email and password.
   - To take Plane off the internet at any time: `sudo tailscale funnel --https=443 off`.
   - The name `<PLANE_HOST>` appears in public Certificate Transparency logs [T3]. That makes the name public, not access to Plane.

## 7. Workspace and project

The board structure in the pm-tool doc is not applied as written (ADR-0017). This is the minimum to start; Gabriel adjusts it.

1. After the first sign-in, Plane shows "Create your profile." (Name, "Continue"), then "Create your workspace":
   - "Name your workspace": `Enterprise Order Suite`.
   - "Set your workspace's URL": replace the suggested `enterprise-order-suite` with `eos`. The MCP reads this as `PLANE_WORKSPACE_SLUG`.
   - "How many people will use this workspace?": `Just myself`. The button stays disabled until this is answered.
   - Click "Create workspace".
2. Plane creates every new workspace with a demo project named after it (identifier `ENTER`, Public, 7 work items, 2 cycles, 3 modules), plus a bot member "Plane" with the Admin role. Leave the bot. Delete the demo project, because its name blocks the real one ("The project name is already taken"):
   - Open the demo project > Project settings > General > "Delete".
   - Type the project name, then `delete my project`.
   - Click "Delete project".
3. Projects > "Add Project":
   - "Project name": `Enterprise Order Suite`.
   - "Project ID": `EOS`. It is pre-filled with `Enterprise`.
   - Change "Public" to "Private" ("Accessible only by invite").
   - Click "Create project". Work items are then numbered `EOS-1`, `EOS-2` and so on.
4. Project settings > Features:
   - Pages starts switched on in a new project. Open "Pages" and switch off "Enable pages".
   - Cycles, Modules, Views and Intake start off. Leave them off.
   - Why: ADR-0017 rules out history import, empty future modules and automations, and docs stay in git. On CE the MCP's `page` tools return 404; its `intake` tools work.
5. Project settings > Work Structure > States, from `planning/pm-tool-recommendation.md` ("States"):

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

6. Project settings > Work Structure > Labels, from the pm-tool doc ("Labels"): `area:backend`, `area:frontend`, `area:docs`, `type:feature`, `type:bug`, `type:doc`, `type:chore`, `contract-change`, `needs-human`.
7. Smoke test through the public address. Create a work item, edit its description, reload the page and check that the text stayed. This proves sign-in, a write and a read over Funnel. Descriptions are saved by a normal API call; Plane's live editing service is used only by Pages, which are off. Keep the item for the restore drill.

Which roadmap steps become the first work items, and when Plane replaces `docs/roadmap.md` as the tracker, is Gabriel's call (ADR-0017). Agents never invent or cite `EOS-<n>` ids before then.

## 8. Backups: nightly, off the box, encrypted, with a restore test

Plane's own backup (`./setup.sh backup`) writes `pgdata`, `uploads`, `rabbitmq_data` and `redisdata` archives to `plane-app/backup/<YYYYMMDD-HHMM>/`. Its restore script reads that format [P2], [P6], [P8]. `backup.sh` runs it nightly, adds a logical Postgres dump (`plane.pgdump`), copies the folder off the server through an `rclone` crypt remote, checks the copy, and keeps 7 copies locally and 30 days remotely. It stops before copying anything if the remote does not encrypt. `pgdata.tar.gz` is copied from the running database and is kept only as a fallback; `plane.pgdump` is what restores the database.

### 8.1 Off-box target: Google Drive, encrypted

First, on your PC (PowerShell): `winget install Rclone.Rclone`, then open a new PowerShell window so that `rclone` is found. (The winget id comes from the earlier draft and was not tested.)

On the server, as `ubuntu` and without `sudo` (`backup.sh` reads this user's rclone config), run `rclone config` and answer as below. These prompts were recorded from Ubuntu's rclone 1.60.1, which `bootstrap-host.sh` installs.

| Prompt | Answer |
|---|---|
| `n/s/q>` | `n` |
| `name>` | `offsite` |
| `Storage>` | `drive` |
| `client_id>` | Enter |
| `client_secret>` | Enter |
| `scope>` | `drive.file` (rclone sees only the files it creates) |
| `service_account_file>` | Enter |
| `Edit advanced config?` | `n` |
| `Use auto config?` | `n` |

1. rclone prints `rclone authorize "drive" "eyJzY29wZSI6ImRyaXZlLmZpbGUifQ"`. Run that exact line in the PowerShell window on your PC.
2. A browser opens. Sign in with the Google account for backups and allow access.
3. PowerShell prints a token between `Paste the following into your remote machine --->` and `<---End paste`. That token is a secret. Copy it and paste it at `config_token>` on the server.
4. Answer `Configure this as a Shared Drive (Team Drive)?` with `n`, and `Keep this "offsite" remote?` with `y`. Steps 2 to 4 were not seen in a test, because Google sign-in cannot run there.
5. rclone notes "same rclone version recommended". Your PC's rclone is newer than the server's 1.60.1. If the server rejects the token, get rclone v1.60.1 for Windows from https://downloads.rclone.org/v1.60.1/ and run `.\rclone.exe authorize ...` from its folder (not tested).

Then add the encrypting layer in the same `rclone config` session. The backups hold live credentials: Plane stores personal access tokens and session keys in its database as plain text [P9].

| Prompt | Answer |
|---|---|
| `e/n/d/r/c/s/q>` | `n` |
| `name>` | `offsite-crypt` |
| `Storage>` | `crypt` |
| `remote>` | `offsite:` |
| `filename_encryption>` | `standard` |
| `directory_name_encryption>` | `false` (folder names are only backup dates) |
| `y/g>` (password) | `g` |
| `Bits>` | `128`: type it, because 1.60.1 has no default and Enter gives "Bad number" |
| `Your password is: …` | save it in the password manager as "rclone crypt password" |
| `y/n>` | `y` |
| `y/g/n>` (password2, the salt) | `g` |
| `Bits>` | `128` |
| `Your password is: …` | save it as "rclone crypt salt" |
| `y/n>` | `y` |
| `Edit advanced config?` | `n` |
| `Keep this "offsite-crypt" remote?` | `y` |
| `e/n/d/r/c/s/q>` | `q` |

Without the password and the salt, nobody can read the backups, including a rebuilt server. Section 8.4 checks the stored copies. Test the remote:
```bash
rclone mkdir offsite-crypt:plane-backups && rclone lsd offsite:      # lists plane-backups
```

For Cloudflare R2 instead, create `offsite` as an rclone `s3` remote with provider Cloudflare and an R2 API token, and give the crypt remote `offsite:<bucket>` as its remote; the scripts then need no change. A pull to your PC only works while the PC is on, so the scripts do not cover it.

### 8.2 First run and schedule

As `ubuntu`, without `sudo`:
```bash
~/plane-ops/backup.sh                         # ends with: Backup OK: <folder>
rclone lsd offsite-crypt:plane-backups
# Nightly at 03:30 server time; output goes to syslog under the tag plane-backup. Re-running this line replaces it.
( crontab -l 2>/dev/null | grep -v 'plane-ops/backup.sh' ; echo '30 3 * * * $HOME/plane-ops/backup.sh 2>&1 | logger -t plane-backup' ) | crontab -
crontab -l
journalctl -t plane-backup --since yesterday  # the next morning: the last line says Backup OK
```
- "permission denied ... docker.sock": log out and in once (section 4, item 4).
- "is not an rclone crypt remote ... Nothing was copied": fix section 8.1. The script refuses on purpose.
- "run this as ubuntu ..., without sudo": run it without `sudo`.
- About 60 Compose warnings that a variable "is not set" appear in the log. They are harmless and come from Plane's own `setup.sh`.

Optional: a free heartbeat check, such as healthchecks.io, emails you when the pings stop. Add `HEALTHCHECK_URL=<url>` in front of `$HOME/plane-ops/backup.sh` using `crontab -e`, not on a command line, so the URL stays out of shell history. Treat the URL like a password: keep it on the server and out of git.

### 8.3 Restore drill (once now, before Plane holds real work)

This is the test that proves a backup brings Plane back. It overwrites the live data, so do it now while Plane is empty.

1. In Plane, create a work item named "drill: before backup" and attach a small image to it. A project cover image works too.
2. Run `~/plane-ops/backup.sh` and note the folder name it prints (`Backup OK: <folder>`).
3. Create a work item named "drill: after backup".
4. Restore from the off-box copy. Plane's `restore.sh` [P2] brings back the uploads, RabbitMQ and Redis volumes. The database comes back from `plane.pgdump` through the container's local socket (`-h /var/run/postgresql`); without it, the tools ask for a password and the restore fails.
   ```bash
   mkdir -p ~/restore-drill
   rclone copy offsite-crypt:plane-backups/<folder> ~/restore-drill/<folder> --exclude pgdata.tar.gz
   cd /opt/plane-selfhost
   REL="$(grep '^APP_RELEASE=' plane-app/plane.env | cut -d= -f2)"
   curl -fsSL -o restore.sh "https://github.com/makeplane/plane/releases/download/$REL/restore.sh"
   chmod +x restore.sh
   less restore.sh          # read it; q quits
   ./setup.sh stop
   ./restore.sh ~/restore-drill/<folder>
   cd /opt/plane-selfhost/plane-app
   C='docker compose -f docker-compose.yaml --env-file plane.env'
   H='-h /var/run/postgresql'
   $C up -d plane-db
   until $C exec -T plane-db pg_isready $H -U plane -q; do sleep 2; done
   $C exec -T plane-db dropdb $H -U plane --if-exists plane </dev/null
   $C exec -T plane-db createdb $H -U plane plane </dev/null
   $C exec -T plane-db pg_restore $H -U plane -d plane --no-owner < ~/restore-drill/<folder>/plane.pgdump
   cd .. && ./setup.sh start
   ```
   - `restore.sh` needs `jq`, which `bootstrap-host.sh` installs, and pulls `busybox` from Docker Hub.
   - It prints "Restore completed successfully." even when one volume failed. Check that it printed ".....Successfully restored volume" for `uploads`, `rabbitmq_data` and `redisdata`.
   - It skips hidden files, so MinIO's `.minio.sys` and RabbitMQ's `.erlang.cookie` are not restored. MinIO rebuilds its own on start; in the test, uploaded files were still served.
5. It passes when all of these hold:
   - You can sign in at `https://<PLANE_HOST>/`.
   - "drill: before backup" exists, and its image opens.
   - "drill: after backup" is gone.
   - God mode > Authentication still shows sign-up off.

   Then delete both items, and run `rm -rf ~/restore-drill`: it holds a decrypted copy of the database.

### 8.4 Check that the password manager can read the backups (once, right after the drill)

The drill used the server's own `rclone` config. This check uses only the password and salt stored in your password manager, as a rebuilt server would. Run `rclone config` and add a temporary remote:
- `n`; name `drill-crypt`; Storage `crypt`; remote `offsite:`; `standard`; `false`.
- Password: `y`, then type the stored "rclone crypt password" twice. Nothing appears on screen while you type.
- Password2: `y`, then type the stored "rclone crypt salt" twice.
- `Edit advanced config?` `n`; `Keep?` `y`; `q`.

Then:
```bash
RCLONE_REMOTE=drill-crypt:plane-backups ~/plane-ops/verify-backup.sh    # must end with PASS
rclone config delete drill-crypt
```
If it fails, the stored values are wrong. While the server still exists, show the right ones and store them again. They print on screen only:
```bash
for k in password password2; do rclone reveal "$(rclone config dump | jq -r --arg k "$k" '."offsite-crypt"[$k]')"; done
```

### 8.5 Non-destructive check (monthly)

```bash
~/plane-ops/verify-backup.sh
```

It downloads the newest off-box backup, checks every archive, and restores `plane.pgdump` into a throwaway Postgres container with no network, using the same image as the running database. It never touches the live containers. It ends with `PASS` and counts of workspaces, projects and work items.

## 9. Keep the instance from idle reclamation

- The rule [O1]: an instance counts as idle when, over 7 days, all three of these hold:
  - 95th-percentile CPU under 20%.
  - Network under 20%.
  - (A1 only) memory under 20%.

  20% of 12 GB is 2.4 GB.
- Measured in the test (amd64, Plane v1.4.2): Plane's 12 containers use about 1.2 GiB, which is about 10% of 12 GB (1,211 MiB idle, 1,258 MiB with data). A one-person Plane also keeps CPU and network low. So expect the instance to count as idle.
- Check on the real server after a day:
  ```bash
  docker stats --no-stream --format '{{.Name}} {{.MemUsage}}'
  free -m
  ```
  Also open the instance's Metrics page in the Oracle console ("Memory utilization"). The metrics come from the Oracle Cloud Agent monitoring plugin, which is on by default; leave it on.
- Claude's default: upgrade the account to Pay As You Go, after setting a budget alert of US$1.
  - Oracle states it does not charge for Always Free resources after the upgrade [O1].
  - Community posts say upgraded accounts are not reclaimed for idleness. Oracle's page does not say so (re-read 2026-10-02), so treat that as unverified.
  - It is a billing decision, so it is Gabriel's. If he declines, the nightly off-box backup is the way back.
- Artificial load generators are not recommended.
- If the instance is reclaimed anyway, rebuild it. This was not tested end to end; the restore itself was (section 8.3).
  1. In Tailscale, delete the old `plane` machine, so the new one gets the same name.
  2. Create a new instance with the same `cloud-init.yaml` (sections 2 to 4). First boot makes new passwords: save the new `plane.env`. Skip section 5, because the restored database brings back the admin and the sign-up setting.
  3. Recreate `offsite` (section 8.1). Recreate `offsite-crypt` with `y` and the stored password and salt instead of `g`.
  4. Restore with section 8.3, item 4, but copy only the uploads and the database:
     ```bash
     rclone copy offsite-crypt:plane-backups/<folder> ~/restore-drill/<folder> --include uploads.tar.gz --include plane.pgdump
     ```
     The RabbitMQ and Redis archives hold only queue and cache state, which is tied to the old passwords.
  5. Run section 6, items 1 to 4. If `plane-url.sh` says the admin is not claimed right after the restore, Plane is still serving its cached answer from before the restore: it caches `/api/instances/` for 2 hours, and a direct database restore does not clear that cache. Wait it out, or clear the cache with `docker exec plane-app-plane-redis-1 valkey-cli FLUSHALL` (not tested; Redis holds only cache here).
  6. Add the nightly backup again (section 8.2: the new server has an empty crontab), then run `~/plane-ops/verify-backup.sh` (section 8.5) and `rm -rf ~/restore-drill`.

  If you ever store a secret in a God-mode setting (SMTP, OAuth; this runbook sets none), it is encrypted with the old `SECRET_KEY`. Copy that key from the saved `plane.env` into the new one before the restore.

## 10. Personal access token and the Plane MCP

On CE the Plane MCP server runs locally over stdio with a personal access token; OAuth is not available on CE [M1]. It needs Python 3.10+ and `uv`, and is started as `uvx plane-mcp-server stdio` with `PLANE_API_KEY`, `PLANE_WORKSPACE_SLUG` and `PLANE_BASE_URL` [M2].

1. In Plane at `https://<PLANE_HOST>/`, open the top-right avatar > Settings > Developer > Personal Access Tokens, then "Add access token". In "Create token":
   - Title: `claude-mcp`.
   - "Set expiration date": 1 year (Claude's default). Plane requires an expiry unless "Never expires" is switched on.
   - Click "Generate token".

   The "Key created" dialog shows the token once: copy it into your password manager. Plane also downloads a CSV file that contains the key. Delete that file from Downloads and from the Recycle Bin (the download was not checked in the test). Put a calendar reminder two weeks before the expiry. Agents act with your permissions, and their comments appear under your name.
2. Test the token from your PC in Git Bash (installed with Git for Windows; PowerShell has no read -s). `read -rs` keeps it out of shell history:
   ```bash
   read -rs PLANE_API_KEY && export PLANE_API_KEY
   curl -fsS -H "X-API-Key: $PLANE_API_KEY" "https://<PLANE_HOST>/api/v1/workspaces/eos/projects/" | head -c 300; echo
   ```
   You should see JSON containing the `EOS` project.
3. Local Claude Code (your PC, including Remote Control sessions). Install `uv` with its standard installer (macOS/Linux: `curl -LsSf https://astral.sh/uv/install.sh | sh`; Windows: `powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"`), then register the server at user scope, in the same Git Bash window as item 2 (the command reads `$PLANE_API_KEY` from it). User scope is stored in your home folder, outside every repo:
   ```bash
   claude mcp add plane --scope user \
     -e PLANE_API_KEY="$PLANE_API_KEY" \
     -e PLANE_WORKSPACE_SLUG=eos \
     -e PLANE_BASE_URL=https://<PLANE_HOST> \
     -- uvx plane-mcp-server stdio
   claude mcp list
   ```
   Never use `--scope project`: it writes `.mcp.json` into the repo, and the repo is public.
   In the test, `plane-mcp-server` 0.3.3 listed 30 tools. On CE, `page` and `initiative` return 404.
4. Cloud Claude sessions (this claude.ai project): add `PLANE_API_KEY`, `PLANE_WORKSPACE_SLUG` and `PLANE_BASE_URL` as environment variables in the project's environment settings. If the network policy blocks the Plane host (or PyPI, which `uvx` downloads from), add it under Network access. Registering the server for cloud sessions takes a committed `.mcp.json` that reads those variables, with no value inside it, plus `uv` in the session setup. Claude proposes that as a separate change once Plane works; it is not part of this runbook.
5. Never paste the token into the chat, a doc or a commit. To rotate it, revoke it in Plane, create a new one, and update the places in items 3 and 4.

What agents may do in Plane (read, comment, move state) is set when Plane replaces `docs/roadmap.md`. Agents never create Plane structure, automations or tokens (ADR-0017).

## 11. Maintenance

| When | What |
|---|---|
| Weekly, and the morning after any server change | `journalctl -t plane-backup --since yesterday` ends with `Backup OK` (or the heartbeat service stays quiet) |
| Monthly | In order: 1) `~/plane-ops/backup.sh`; 2) `cd /opt/plane-selfhost && ./setup.sh upgrade` (answer `y`; it stops Plane and does not start it again [P8]); 3) `diff plane-app/plane.env.bak plane-app/plane.env`. A new key that still holds a shipped default (`plane`, `access-key`, `secret-key`, `change-this-key-on-deployment`) needs a decision before the start: post it in the project thread; 4) `sudo ~/plane-ops/install-plane.sh` (it keeps every secret, warns if a known secret still holds a default, starts Plane and waits until it answers; re-run tested on v1.4.2 only); 5) sign in and open a work item; 6) `docker image prune -a -f` (only once Plane runs again, so its current images are in use); 7) `~/plane-ops/verify-backup.sh`; 8) check memory against the 2.4 GB line (section 9) and `df -h /`; 9) `docker volume prune -f` while Plane runs (removes the empty unnamed volumes that each Plane restart leaves behind; named volumes are kept) |
| When `/var/run/reboot-required` exists | `sudo reboot`; afterwards `docker ps`, and `cd /opt/plane-selfhost && ./setup.sh start` if Plane did not come back |
| When Oracle emails about the free tier or an idle instance | Read it the same day; the off-box backup is the fallback |
| Forgot the Plane admin password (no SMTP) | `docker exec -it plane-app-api-1 python manage.py reset_password <email>` (the command exists in v1.4.2; not run in the test) |
| Plane unreachable from outside | `tailscale funnel status`; `docker ps`; in the Tailscale admin console, check that `plane` is connected and that its key expiry is disabled |

## 12. Final checklist

- [ ] A1 instance: `VM.Standard.A1.Flex`, 2 OCPU, 12 GB, Ubuntu 24.04 aarch64, Always Free-eligible, home region, created with `cloud-init.yaml`
- [ ] Security list: only SSH (and ICMP) inbound; `Test-NetConnection <PUBLIC_IP> -Port 80` is False
- [ ] First boot done (`cloud-init status`: done); 12 containers Up; swap 4.0Gi; timezone America/Sao_Paulo
- [ ] `plane.env` in the password manager
- [ ] Instance admin claimed by Gabriel; public sign-up off; telemetry off
- [ ] Tailscale: machine `plane`, key expiry disabled; Funnel on; `https://<PLANE_HOST>` signs in; the description edit survives a reload
- [ ] Workspace `eos`; demo project deleted; project `EOS` Private; Pages off; states and labels as in section 7
- [ ] `rclone` remotes `offsite` (Drive, `drive.file`) and `offsite-crypt`; crypt password and salt in the password manager
- [ ] Nightly `backup.sh` in cron; the first off-box copy exists
- [ ] Restore drill passed (8.3); the password-manager check passed (8.4); `verify-backup.sh` prints PASS
- [ ] Memory checked against the 2.4 GB line; Pay As You Go decided (section 9)
- [ ] Token created (1 year, calendar reminder), CSV deleted; `curl` test returns the EOS project; `claude mcp list` shows `plane` connected locally
- [ ] Gabriel posted the outcome in the project thread (section 13)

## 13. After setup

Post in the project thread: "Plane setup done", plus any of Claude's defaults (section 0) you changed. Leave out the hostname, IP and tokens. Claude then updates, in one commit: the Q-83 row in `planning/open-questions.md` (recording only what Gabriel said, with the defaults still marked as Claude's), ADR-0017, `docs/roadmap.md` (the Plane setup step and its readiness-gate item) and this page. Only Gabriel sets this page to Reviewed.

## Appendix A. Manual path (if the first-boot file cannot be used)

Create the instance as in section 2, but leave out the Initialization script. Log in as in section 4, item 1. Then:
```bash
mkdir -p ~/plane-ops && cd ~/plane-ops
BASE=https://raw.githubusercontent.com/gtavaresdevs/Enterprise-Order-Suite/feature/ai-agent/docs/ops/plane
for f in bootstrap-host.sh install-plane.sh plane-url.sh backup.sh verify-backup.sh; do curl -fsSLO "$BASE/$f"; done
chmod +x ./*.sh
less bootstrap-host.sh install-plane.sh   # read both; :n shows the next file, q quits
sudo TZ_NAME=America/Sao_Paulo ./bootstrap-host.sh
curl -fsSL https://tailscale.com/install.sh | sh     # installs Tailscale; it is logged in only in section 6
sudo PLANE_RELEASE=v1.4.2 ./install-plane.sh
```
Then continue with section 4, item 3; on this path there is no first-boot log, so check for `/var/run/reboot-required`. These are the same scripts first boot runs, in the same order (Tailscale before Plane). `BASE` is the same address as the `BASE_URL` default in `cloud-init.yaml`.

## Tested

Run on 2026-10-01 and 2026-10-02 (UTC) by Claude in a cloud container. **Everything that ran Plane ran on linux/amd64 (x86_64), not on the Oracle Ampere (arm64) VM.** arm64 was checked only for image availability, and for `bootstrap-host.sh` under qemu emulation.

| What | Versions | Result |
|---|---|---|
| Plane CE install (`install-plane.sh`) | Plane v1.4.2: `makeplane/plane-{frontend,space,admin,live,backend,proxy}:v1.4.2`, `postgres:15.7-alpine`, `valkey/valkey:7.2.11-alpine`, `rabbitmq:3.13.6-management-alpine`, `pgsty/minio:RELEASE.2026-08-04T00-00-00Z`; Docker 29.3.1, Compose v5.1.1 | Fresh install: exit 0 in 3 min 18 s (images already cached); 8 secrets generated; no `plane:plane@` left. Re-run: exit 0 in 11.8 s; no secret or container changed |
| arm64 images | the 10 images above | Every one has a linux/arm64 build (Docker Hub API and the local image index; the digests match). Not run on arm64 |
| `bootstrap-host.sh` | docker-ce 5:29.8.2, containerd.io 2.3.6, compose plugin 5.5.1, buildx 0.37.1, rclone 1.60.1+dfsg-3ubuntu0.24.04.6, jq 1.7.1 (Ubuntu 24.04 packages) | amd64 and emulated arm64 containers, plus a privileged systemd container (amd64): Docker enabled, log cap applied, `ubuntu` in the docker group, timezone set; a second run changed nothing. Swap and a real VM boot not tested (a container shares the host kernel) |
| `cloud-init.yaml` | cloud-init 26.1-0ubuntu1~24.04.1 | `cloud-init schema`: "Valid schema". A real cloud-init run (NoCloud seed, stand-in helper scripts and Tailscale) wrote the done marker. A failing step wrote the failed marker and gave `status: error`. A re-run after done changes nothing |
| Admin claim, sign-up off | Chromium through Playwright 1.56.1 | The claim is saved even when the redirect after "Continue" lands on another address; re-opening the forwarded address works. The switch in Authentication saves at once |
| Workspace, project, token, MCP | `plane-mcp-server` 0.3.3 via `uvx` | `eos` and `EOS` created; the demo project had to be deleted first. The token works with `curl`; a wrong key gets 403. MCP: 30 tools; `page` and `initiative` return 404 on CE, `intake` returns 200 |
| `backup.sh`, `verify-backup.sh` | rclone 1.60.1; a local folder behind a crypt remote stood in for Drive | Backup: exit 0 in 3.8 s; cryptcheck "0 differences found, 5 matching files"; local and remote retention work. Verify: PASS. `backup.sh` refuses a remote that is not crypt before copying anything |
| Restore drill (8.3) | `restore.sh` v1.4.2 | With `-h /var/run/postgresql`: the "before" item is back, the "after" item is gone, app and God-mode sign-in work, sign-up is still off, and the uploaded image is served. Without `-h`, `dropdb` waits for a password and the drill fails |
| Password-manager check (8.4) | rclone 1.60.1 | A crypt remote rebuilt from the typed password and salt decrypts the backup. The `rclone reveal` recovery line returns both values |
| Memory | `docker stats`, summed | 1,211 MiB idle; 1,258 MiB after the drill |
| Scripts | shellcheck 0.11.0, `bash -n` | All five scripts clean. `plane-url.sh`'s safety check was tested against a stand-in endpoint |

Not tested:
- Anything on the Oracle VM: arm64 memory, swap, the first-boot duration, Docker together with Oracle's `/etc/iptables/rules.v4`, and the console labels.
- Tailscale login, HTTPS and Funnel end to end (including the redirect behaviour over Funnel).
- Google Drive with rclone 1.60.1 and `rclone authorize` from a newer rclone on Windows.
- Windows itself: PowerShell, OpenSSH, `Test-NetConnection`, winget, and browsers other than Chromium.
- The exact claim redirect with the 8080-to-80 forward. The same behaviour was tested with a different mismatched address.
- The token CSV download, `manage.py reset_password`, a Plane upgrade, and a rebuild on a new instance.

## Sources

Read 2026-10-01; [O1] re-read and [O7]-[O9], [T2], [T3], [D1], [CI1] read 2026-10-02.
- [P1] Plane, Docker Compose install: https://developers.plane.so/self-hosting/methods/docker-compose (mirror read the same day: https://planesoftwareinc.mintlify.app/self-hosting/methods/docker-compose)
- [P2] Plane, Backup and restore (CE section: `./setup.sh` option 7, `restore.sh`): https://developers.plane.so/self-hosting/manage/backup-restore . `restore.sh` is used from the release assets, `https://github.com/makeplane/plane/releases/download/<release>/restore.sh` (identical to the v1.4.2 tag copy).
- [P3] Plane, Editions and versions: https://developers.plane.so/self-hosting/editions-and-versions
- [P4] Plane, Instance admin and God mode: https://developers.plane.so/self-hosting/govern/instance-admin
- [P5] Plane issue #9068, "/god-mode without trailing slash renders a blank/loading page" (search result title only): https://github.com/makeplane/plane/issues/9068
- [P6] Plane, Upgrade from Community to Commercial Edition (uses `./setup.sh backup`; archive names `pgdata.tar.gz`, `redisdata.tar.gz`, `uploads.tar.gz`): https://developers.plane.so/self-hosting/upgrade-from-community
- [P7] Plane, Environment variables (CE: `APP_DOMAIN`, `WEB_URL`, `CORS_ALLOWED_ORIGINS`, `LISTEN_HTTP_PORT`, `POSTGRES_PASSWORD`, `SECRET_KEY`; TLS variables are listed for the Commercial Edition only): https://developers.plane.so/self-hosting/govern/environment-variables
- [P8] Plane CE release v1.4.2 files, the ones `setup.sh` installs: `setup.sh` (menu, `install`, `upgrade`, `backupData`, `syncEnvFile`), `variables.env` (becomes `plane.env`: empty `DATABASE_URL` and `AMQP_URL`, `LIVE_SERVER_SECRET_KEY` placeholder, `SITE_ADDRESS`, `CERT_EMAIL`, `CERT_ACME_CA`, `CERT_ACME_DNS`, `TRUSTED_PROXIES`) and `docker-compose.yml` (default URLs with the password `plane`): https://github.com/makeplane/plane/releases/latest ; CE proxy `apps/proxy/Caddyfile.ce` (`reverse_proxy /live/*`, certificate settings) and `deployments/cli/community/restore.sh` in the repo
- [P9] Plane source at v1.4.2: `apps/api/plane/db/models/api.py` (`APIToken.token` is a `CharField`) and `apps/api/plane/db/models/session.py` (`session_key`): https://github.com/makeplane/plane/tree/v1.4.2/apps/api/plane/db/models
- [M1] Plane, MCP server self-host (CE uses local stdio mode with a personal access token): https://developers.plane.so/dev-tools/mcp-server-self-host
- [M2] Plane, MCP server (`uvx plane-mcp-server stdio`, environment variables, token location): https://developers.plane.so/dev-tools/mcp-server
- [O1] Oracle, Always Free Resources: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
- [O2] InfoQ, "Oracle Quietly Halves Free Tier Ampere A1 Compute Limits", 2026-07-03: https://www.infoq.com/news/2026/07/oracle-cloud-free-tier-limits/
- [O3] Linuxiac, "Oracle Quietly Cuts Free Tier Ampere A1 Resources in Half", 2026-06-14: https://linuxiac.com/oracle-quietly-cuts-free-tier-ampere-a1-resources-in-half/
- [O4] TerminalBytes, "Oracle Cloud free tier 2026", June 2026: https://terminalbytes.com/oracle-cloud-free-tier-changes-2026/
- [O5] bex.co, "Oracle Halved Its Free ARM Cloud", 2026-09-26: https://bex.co/blog/2026/09/26/oracle-always-free-cut-free-tier-k8s-vs-hetzner
- [O6] Sudo Security, "Oracle Free Tier ARM Cutoff", 2026-08-10: https://sudosecurity.org/oracle-free-tier-arm-cutoff-2026/
- [O7] Oracle, Ubuntu platform images (user `ubuntu` with sudo; default firewall rules allow only SSH; do not use UFW): https://docs.oracle.com/en-us/iaas/Content/Compute/References/images.htm ; known issue, edit `/etc/iptables/rules.v4`: https://docs.oracle.com/en-us/iaas/Content/Compute/known-issues.htm#ufw
- [O8] Oracle, Connecting to a Linux instance (username `ubuntu`): https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/connect-to-linux-instance.htm
- [O9] Oracle, Creating an instance (wizard steps; Advanced options > Management > Initialization script; 32,000-byte limit; no reboot from the script; boot volume 50 GB to 32 TB): https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/launchinginstance.htm
- [C1] Cloudflare, Set up Cloudflare Tunnel (needs a Cloudflare account and a domain on Cloudflare; outbound-only; `sudo cloudflared service install <TUNNEL_TOKEN>`): https://developers.cloudflare.com/tunnel/get-started/ ; Quick Tunnels limits: https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/
- [T1] Tailscale, Funnel (MagicDNS, HTTPS certificates, `funnel` node attribute; ports 443, 8443, 10000; `ts.net` names only; all plans): https://tailscale.com/kb/1223/funnel
- [T2] Tailscale CLI, serve and funnel (`--bg`, `--https=443 off`, `reset`, `status`): https://tailscale.com/docs/reference/tailscale-cli/serve , https://tailscale.com/docs/reference/tailscale-cli/funnel
- [T3] Tailscale, HTTPS certificates (Certificate Transparency publishes machine names): https://tailscale.com/docs/how-to/set-up-https-certificates
- [D1] Docker, Install Docker Engine on Ubuntu (apt repository; published ports bypass firewall rules). Read from the docs source `docker/docs` `content/manuals/engine/install/ubuntu.md`, because docs.docker.com was blocked in the test environment
- [CI1] cloud-init, user-data formats (cloud-config "Begins with: `#cloud-config`"): https://docs.cloud-init.io/en/24.1/explanation/format.html
- Repo: ADR-0017; `planning/pm-tool-recommendation.md` ("Proposed board structure", "Free VPS: Oracle Cloud Always Free"); `planning/open-questions.md` Q-83.
- Not read today, used as written or from memory: the `uv` installer commands; `claude mcp add` syntax (local `--help`); Tailscale key expiry (180 days, "Disable key expiry"); Docker Hub pull limits; GitHub's "Download raw file"; `winget install Rclone.Rclone`; PowerShell `Test-NetConnection`; rclone's `authorize` output lines.

## Inputs needed from Gabriel

Only Gabriel can do these. Claude cannot reach the server or his accounts (ADR-0017).

- Password manager: entries for the SSH key passphrase, the public IP, the Plane admin email and password, the whole `plane.env`, the rclone crypt password and salt, and the Plane token.
- Oracle console:
  - Create the instance with `cloud-init.yaml` (section 2).
  - Keep the security list as created (section 3).
  - Decide on Pay As You Go and the budget alert (section 9; Claude's default is upgrade).
- PowerShell on his PC:
  - The SSH key and the `ssh -L` login (sections 2 and 4).
  - `Test-NetConnection` (section 3).
  - `winget install Rclone.Rclone` and `rclone authorize` (section 8.1).
- Browser logins and clicks:
  - Claim the Plane admin, untick telemetry, switch off sign-up (section 5).
  - Tailscale sign-up or login, "Disable key expiry", and approving HTTPS and Funnel (section 6).
  - Google sign-in for rclone (section 8.1).
  - Workspace, demo-project deletion, project, Pages off, states and labels (section 7).
  - The token, then deleting its CSV (section 10).
- Server terminal: paste the commands of sections 4 to 8 as written.
- When done: the outcome message in section 13 (no hostname, IP or tokens).
