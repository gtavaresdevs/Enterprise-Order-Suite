# Plane setup runbook (Oracle Always Free)

- Status: Draft
- Updated: 2026-10-01
- Reviewed: not yet
- Roadmap step: Plane setup, before Build 1 (`docs/roadmap.md`)
- Related: ADR-0017, ADR-0011, ADR-0013, ADR-0015; register Q-83
- Who runs it: Gabriel, on his Oracle account and VPS. Claude cannot reach the VPS: Claude wrote this page and updates it from what Gabriel reports.

Gabriel's answer to Q-83 (2026-10-01, project thread 2026-10-01T16:25Z): "Q83 - b -> other I already create the oracle free tier with always free account, its fresh and ready to begin the setup, so this is the first thing we do before begin working on the actual app."

This page takes that fresh account to a working Plane Community Edition (CE) with nightly off-box backups and a token for the Plane MCP. Every step is Gabriel's: agents never sign up for or configure hosting, and never create Plane structure, automations, tokens or secrets (ADR-0017). Plan for two to three hours, most of it waiting on downloads.

The repo is public. Never put a token, password, `plane.env` value, IP address or `rclone` config into this file, a commit or the project chat. The password manager holds them.

## 0. Decide before you start

Three things Q-83 left open are asked at the end of this page (Open questions): how Plane is reached over HTTPS, where backups go, and the `EOS-<n>` id convention. Steps 5 and 9 depend on the first two.

How Plane is reached (step 5). CE's built-in Caddy can get its own certificate (`SITE_ADDRESS`, `CERT_EMAIL` in `plane.env` [P8]), but only with inbound ports 80 and 443 open. This runbook keeps `SITE_ADDRESS=:80` and terminates HTTPS at the tunnel, so HTTPS comes from one of the options below. Cloud Claude sessions run outside your network, so they reach Plane only through a public HTTPS address.

| Option | What it needs | Public URL | Cloud Claude sessions can reach it | Inbound ports on the VPS |
|---|---|---|---|---|
| Cloudflare Tunnel | Free Cloudflare account and a domain you own, with its DNS on Cloudflare | `https://plane.<your-domain>` | Yes | none (outbound only) |
| Tailscale Funnel | Free Tailscale account; MagicDNS and HTTPS certificates turned on | `https://<machine>.<tailnet>.ts.net` (no custom domain) | Yes | none |
| Tailscale, private | Free Tailscale account; Tailscale on every device that opens Plane | none (tailnet only) | No: only sessions on your own devices (local or Remote Control) | none |
| Caddy + DuckDNS (pm-tool doc) | Free DuckDNS name; ports 80 and 443 opened in Oracle's security list and on the host | `https://<name>.duckdns.org` | Yes | 80, 443 (not recommended: exposes the box and adds moving parts) |

Cloudflare's Quick Tunnels (`trycloudflare.com`) need no domain but give a random URL and are for testing only, so they are not an option here.

## 1. Facts checked on 2026-10-01

| Fact | Value | Source (read 2026-10-01) |
|---|---|---|
| Plane CE CPU and RAM | 2 cores (x64 or ARM64); 4 GB RAM, 8 GB recommended for production | Plane Docker Compose docs [P1] |
| Plane CE install path | `setup.sh` from the latest GitHub release; its menu option "1) Install" checks that the release has images for the host architecture (x64 or ARM64), pulls them and exits | [P1], [P8] |
| Plane CE features | At par with the Free tier of Plane Cloud; AGPL v3.0 | [P3] |
| Oracle A1 Always Free | 1,500 OCPU hours and 9,000 GB hours per month on `VM.Standard.A1.Flex`, "equivalent to 2 OCPUs and 12 GB of memory" for Always Free tenancies | Oracle [O1] |
| Oracle storage and traffic | 200 GB of block storage, boot and block volumes combined, plus 5 volume backups; 10 TB outbound per month | [O1] |
| Home region | Always Free compute and volumes must be created in the tenancy's home region | [O1] |
| Capacity | "Out of host capacity" means a temporary lack of Always Free shapes: try another availability domain or wait and retry | [O1] |
| Idle reclamation | An instance is idle if, over 7 days, the 95th-percentile CPU is under 20%, network is under 20%, and (A1 only) memory is under 20%; idle Always Free instances "may be reclaimed" | [O1] |

So 2 OCPU and 12 GB on A1 meet Plane's minimum CPU and exceed its recommended RAM. For a new account 2 OCPU and 12 GB is the ceiling: do not size the instance above it.

Where sources differ:
- The 2 OCPU / 12 GB figure in `planning/pm-tool-recommendation.md` is confirmed by Oracle's page [O1]. Oracle gives no date for the change. InfoQ [O2] and bex.co [O5] say 2026-06-15; Linuxiac [O3] gives no date; TerminalBytes [O4] says no official date existed and enforcement was uneven in June.
- Instances above the new limit after 2026-08-18: Sudo Security [O6] says "terminated"; bex.co [O5] says "auto-stopping and disabling". This does not affect a new 2/12 instance.
- Pay As You Go: InfoQ [O2] reports Oracle support answers that contradict each other. bex.co [O5] says upgraded accounts could keep 4 OCPU and 24 GB free, while Oracle's page says all tenancies get the same 1,500 OCPU hours free [O1]. This runbook assumes 2/12.
- Plane's Docker Compose page now leads with the Commercial Edition installer (`prime.plane.so`, free plan with 12 seats) [P1], [P3]. ADR-0017 chose Community Edition, so this runbook uses the CE `setup.sh`.
- The reclamation rule in the pm-tool doc matches Oracle's page. Oracle's page does not say whether reclaiming stops or terminates the instance, and does not say whether upgraded accounts are exempt.

## 2. Create the A1 instance

1. On your PC, create an SSH key if you do not have one. Windows 10 and 11 include OpenSSH:
   ```bash
   ssh-keygen -t ed25519 -C "plane-oci"
   ```
   The public key is `~/.ssh/id_ed25519.pub` (on Windows: `C:\Users\<you>\.ssh\id_ed25519.pub`). Keep the private key on your PC.
2. Oracle console, in your home region: Compute > Instances > Create instance. Console labels change over time; the choices are:
   - Name: `plane`.
   - Image: Canonical Ubuntu 24.04 (with an A1 shape the console offers the aarch64 build).
   - Shape: Virtual machine > Ampere > `VM.Standard.A1.Flex`, 2 OCPUs, 12 GB memory. Check that the shape and the image show "Always Free-eligible": only those stay free after the sign-up trial credit ends.
   - Networking: create a new virtual cloud network and a new public subnet; assign a public IPv4 address.
   - SSH keys: paste the contents of `id_ed25519.pub`.
   - Boot volume: custom size 100 GB (the free 200 GB covers boot and block volumes together).
3. If creation fails with "Out of host capacity", pick another availability domain or retry later [O1].
4. Note the public IP in your password manager, not here. Log in:
   ```bash
   ssh ubuntu@<PUBLIC_IP>
   ```

## 3. Network: keep only SSH open

- The new subnet's default security list allows SSH (TCP 22) and ICMP. Add nothing else: Cloudflare Tunnel and Tailscale connect outward, so Plane needs no inbound port.
- Docker-published ports bypass host firewalls such as `ufw`. Oracle's security list is therefore what keeps Plane's port 80 closed to the internet. Do not add a port-80 or port-443 ingress rule.
- Optional hardening if you choose Tailscale: once SSH to the VPS over the tailnet works (`ssh ubuntu@plane`), remove the port-22 ingress rule.

## 4. Prepare the host

The helper scripts live next to this page (`docs/ops/plane/`). They are idempotent, commented and contain no secrets. Read each one before running it. They can be downloaded once this page is pushed to `feature/ai-agent`:

```bash
mkdir -p ~/plane-ops && cd ~/plane-ops
BASE=https://raw.githubusercontent.com/gtavaresdevs/Enterprise-Order-Suite/feature/ai-agent/docs/ops/plane
for f in bootstrap-host.sh backup.sh verify-backup.sh; do curl -fsSLO "$BASE/$f"; done
chmod +x ./*.sh
less bootstrap-host.sh
```

`bootstrap-host.sh` installs OS updates, turns on automatic security updates, adds a 4 GB swap file, installs Docker with the Compose plugin, caps container log size, gives the `ubuntu` user Docker access and creates `/opt/plane-selfhost`. Set your timezone so the nightly backup runs at night:

```bash
sudo TZ_NAME=America/Sao_Paulo ./bootstrap-host.sh
exit    # log out so the docker group applies; reboot first if the script asked for it
```

Log in again and check:

```bash
docker run --rm hello-world
free -h          # Swap: 4.0Gi
sudo sshd -T | grep -i '^passwordauthentication'    # expect: passwordauthentication no
```

## 5. Prepare the HTTPS name (do not publish yet)

Set up the connector now and publish Plane only in step 7, after you have claimed the instance admin. Call the chosen hostname `<PLANE_HOST>` below.

Cloudflare Tunnel (needs a domain on Cloudflare) [C1]:
1. Cloudflare dashboard: add your domain on the free plan and switch its nameservers at your registrar until the zone shows Active.
2. Networking > Tunnels (older dashboards: Zero Trust > Networks > Tunnels) > Create tunnel > Cloudflared, name `plane-oci`, OS Debian, architecture arm64.
3. Run the commands the dashboard shows on the VPS. They end with `sudo cloudflared service install <TUNNEL_TOKEN>`. The token is a secret: paste it only into the VPS shell.
4. Wait until the tunnel shows Healthy. Do not add a route yet. `<PLANE_HOST>` will be `plane.<your-domain>`.

Tailscale Funnel or private (no domain) [T1]:
```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up --hostname=plane          # open the printed link and sign in
tailscale status --json | jq -r .Self.DNSName   # e.g. plane.tail1234.ts.net. (drop the final dot)
```
In the Tailscale admin console, under DNS, turn on MagicDNS and HTTPS Certificates. Funnel also needs the `funnel` node attribute in the tailnet policy; the CLI prints a link to add it the first time you run `tailscale funnel`.

## 6. Install Plane Community Edition

Commands from Plane's Docker Compose page [P1]:

```bash
cd /opt/plane-selfhost
curl -fsSL -o setup.sh https://github.com/makeplane/plane/releases/latest/download/setup.sh
chmod +x setup.sh
./setup.sh        # choose 1) Install; the script pulls the arm64 images and exits
```

If it reports that arm64 images are not available and offers to build them locally, answer `N` and post the message in the project thread.

This creates `/opt/plane-selfhost/plane-app/` with `docker-compose.yaml` and `plane.env`. Edit `plane.env` [P7] before the first start: Postgres, RabbitMQ and MinIO keep the passwords they are first started with.

```bash
cd /opt/plane-selfhost/plane-app
cp plane.env plane.env.orig
# Show every line to review: the URL settings and every password, secret or key
grep -nE '^(APP_DOMAIN|WEB_URL|CORS_ALLOWED_ORIGINS|LISTEN_HTTP_PORT|LISTEN_HTTPS_PORT)=' plane.env
grep -nE 'PASSWORD|SECRET|_KEY|_URL=' plane.env
```

```bash
# setenv KEY VALUE: replace an existing KEY= line and print it (prints nothing if the key is absent)
setenv() { sed -i "s|^$1=.*|$1=$2|" plane.env; grep -n "^$1=" plane.env; }
setenv APP_DOMAIN '<PLANE_HOST>'
setenv WEB_URL 'https://<PLANE_HOST>'
setenv CORS_ALLOWED_ORIGINS 'https://<PLANE_HOST>'
PG_PW="$(openssl rand -hex 24)"; MQ_PW="$(openssl rand -hex 24)"
setenv POSTGRES_PASSWORD "$PG_PW"
setenv DATABASE_URL "postgresql://plane:${PG_PW}@plane-db:5432/plane"
setenv RABBITMQ_PASSWORD "$MQ_PW"
setenv AMQP_URL "amqp://plane:${MQ_PW}@plane-mq:5672/plane"
unset PG_PW MQ_PW
setenv AWS_SECRET_ACCESS_KEY "$(openssl rand -hex 24)"
setenv SECRET_KEY "$(openssl rand -hex 32)"
setenv LIVE_SERVER_SECRET_KEY "$(openssl rand -hex 32)"
# No URL may still carry the default password: this must print 0
docker compose -f docker-compose.yaml --env-file plane.env config | grep -c 'plane:plane@'
```

- Leave `LISTEN_HTTP_PORT=80`: only the tunnel and SSH port forwards reach it.
- `DATABASE_URL` and `AMQP_URL` are empty in `plane.env`, and `docker-compose.yaml` then falls back to URLs that contain the password `plane`, so they must be set together with the passwords [P8].
- Replace any other default password or secret the second `grep` showed.
- Copy the final `plane.env` into your password manager. A rebuild needs it, and it must never be committed.

Start Plane and check it (the first start pulls images and runs migrations; allow a few minutes):

```bash
cd /opt/plane-selfhost
./setup.sh start
docker ps --format 'table {{.Names}}\t{{.Status}}'
curl -sI http://localhost/ | head -n 1     # expect an HTTP 200 or a redirect
free -h
```

Logs: `./setup.sh` > 6) View Logs.

## 7. Claim the instance admin, then publish

Whoever completes `/god-mode/` first on a fresh instance becomes its admin [P4], so claim it before Plane is public.

1. From your PC, forward a local port and open the admin page (keep the trailing slash; without it the page can stay blank [P5]):
   ```bash
   ssh -L 8080:localhost:80 ubuntu@<PUBLIC_IP>
   ```
   Open `http://localhost:8080/god-mode/`. At "Let's secure your instance", enter your email and a password from your password manager.
   If the page redirects to `<PLANE_HOST>` or will not load through the forward:
   - Tailscale (funnel or private): run `sudo tailscale serve --bg 80` (tailnet only), claim `https://<PLANE_HOST>/god-mode/` from a device on your tailnet, then for Funnel run `sudo tailscale serve reset && sudo tailscale funnel --bg 80`.
   - Cloudflare: add the route (step 2), then claim within seconds and check that the admin email shown under God mode is yours. If it is not, remove the route, stop with `./setup.sh stop`, delete the Plane volumes (`docker volume ls -q | grep '^plane-app_' | xargs -r docker volume rm`) and start again with `./setup.sh start` (same `plane.env`).
2. Publish:
   - Cloudflare: open the tunnel > Add route > Published application (older dashboards: Public hostname): subdomain `plane`, your domain, service `HTTP` `localhost:80` [C1].
   - Funnel: `sudo tailscale funnel --bg 80`, then `tailscale funnel status`.
   - Private: `sudo tailscale serve --bg 80`.
3. In God mode [P4]:
   - Authentication: turn off "Allow anyone to sign up without an invite".
   - General: switch off telemetry if you prefer.
   - Email (SMTP): skip for now. Without SMTP, reset emails are not sent, so keep the password in the password manager.
4. Open `https://<PLANE_HOST>/` from your PC and sign in.

## 8. Workspace and project

The board structure in the pm-tool doc is not applied as written (ADR-0017). This is the minimum to start; Gabriel adjusts it.

1. Create the workspace "Enterprise Order Suite" with URL slug `eos`. The MCP reads it as `PLANE_WORKSPACE_SLUG`.
2. Create the project "Enterprise Order Suite", identifier `EOS`, visibility Private. Work items are then numbered `EOS-1`, `EOS-2` and so on.
3. Project settings > Features: leave Cycles, Modules, Pages and Intake off. ADR-0017 rules out history import, empty future modules and automations. Docs stay in git, and on CE the MCP's Pages and Intake tools return 404 (pm-tool doc).
4. Project settings > States, from `planning/pm-tool-recommendation.md` ("States"):

   | State | Plane state group | Note |
   |---|---|---|
   | Backlog | Backlog | default |
   | Spec'd | Unstarted | rename the default "Todo" |
   | Ready for agent | Unstarted | what agents may pick up |
   | In progress | Started | default |
   | In review | Started | waiting for Gabriel's review. ADR-0013 has no PRs to merge, so the pm-tool name "In review (PR open)" is shortened |
   | Blocked | Started | |
   | Done | Completed | default |
   | Cancelled | Cancelled | keep Plane's default |

5. Labels, from the pm-tool doc ("Labels"): `area:backend`, `area:frontend`, `area:docs`, `type:feature`, `type:bug`, `type:doc`, `type:chore`, `contract-change`, `needs-human`.
6. Smoke test: create a work item, edit its description, reload the page and check the text stayed. That exercises the live editing service through your tunnel. Keep the item for the restore drill in step 9.

Which roadmap steps become the first work items, and when Plane replaces `docs/roadmap.md` as the tracker, is Gabriel's call (ADR-0017). Agents never invent or cite `EOS-<n>` ids before then.

## 9. Backups: nightly, off the box, with a restore test

Plane's own backup (`./setup.sh backup`) writes `pgdata`, `uploads`, `rabbitmq_data` and `redisdata` archives to `plane-app/backup/<YYYYMMDD-HHMM>/`. Its restore script reads that format [P2], [P6], [P8]. `backup.sh` runs it nightly, adds a logical Postgres dump (`plane.pgdump`), copies the folder off the server with `rclone`, encrypted, and keeps 7 copies locally and 30 days remotely. `pgdata.tar.gz` is copied from the running database and is kept only as a fallback; `plane.pgdump` is what restores the database.

### 9.1 Off-box target

A target outside Oracle survives a closed or changed free tier. The recommended target is Google Drive (Open questions):

1. On the VPS run `rclone config`: `n` (new remote), name `offsite`, storage `drive`, leave client id and secret empty, scope `drive.file` (rclone sees only the files it creates), no service account, no advanced config.
2. At "Use web browser to automatically authenticate?" answer `n`. rclone prints a `rclone authorize "drive" "..."` command. Run it on your PC, which needs rclone (Windows: `winget install Rclone.Rclone`). Sign in in the browser and paste the result back into the VPS prompt. Not a shared drive; confirm.
3. Add an encrypting layer. The backups hold live credentials (Plane stores personal access tokens and session keys in its database as plain text [P9]), so they leave the server encrypted. Run `rclone config` again: `n`, name `offsite-crypt`, storage `crypt`, remote `offsite:`, filename_encryption `standard`, directory_name_encryption `false` (folder names are only backup dates). For the password and for the salt choose `g` (generate), and store both in your password manager: without them the backups cannot be read, and a rebuilt server needs them (step 10).
4. Test it:
   ```bash
   rclone mkdir offsite-crypt:plane-backups && rclone lsd offsite:
   ```

For Cloudflare R2 instead, create `offsite` as an rclone `s3` remote with provider Cloudflare and an R2 API token, and give the crypt remote `offsite:<bucket>` as its remote; the scripts then need no change. A pull to your PC only works while the PC is on, so the scripts do not cover it.

### 9.2 First run and schedule

```bash
~/plane-ops/backup.sh
rclone lsd offsite-crypt:plane-backups
# Nightly at 03:30 server time; output goes to syslog under the tag plane-backup. Re-running this line replaces it.
( crontab -l 2>/dev/null | grep -v 'plane-ops/backup.sh' ; echo '30 3 * * * $HOME/plane-ops/backup.sh 2>&1 | logger -t plane-backup' ) | crontab -
crontab -l
journalctl -t plane-backup --since yesterday    # check the next morning
```

Optional: create a free check at a heartbeat service such as healthchecks.io, which emails you when the pings stop. Put its URL in the cron line as `HEALTHCHECK_URL=<url>` before the script path. Treat the URL like a password: it stays on the server and out of git.

### 9.3 Restore drill (once now, before Plane holds real work)

This is the only test that proves a backup brings Plane back. It overwrites the live data, so do it now while Plane is empty.

1. In Plane, create a work item named "drill: before backup".
2. Run `~/plane-ops/backup.sh` and note the folder name it prints (`Backup OK: <folder>`).
3. Create a work item named "drill: after backup".
4. Restore from the off-box copy. Plane's restore script (steps from [P2]) brings back the uploads, RabbitMQ and Redis volumes; the database comes back from `plane.pgdump`:
   ```bash
   mkdir -p ~/restore-drill
   rclone copy offsite-crypt:plane-backups/<folder> ~/restore-drill/<folder> --exclude pgdata.tar.gz
   cd /opt/plane-selfhost
   curl -fsSL -o restore.sh https://raw.githubusercontent.com/makeplane/plane/refs/heads/preview/deployments/cli/community/restore.sh
   chmod +x restore.sh
   less restore.sh
   ./setup.sh stop
   ./restore.sh ~/restore-drill/<folder>    # if it reports a permission error, run it with sudo
   cd /opt/plane-selfhost/plane-app
   C='docker compose -f docker-compose.yaml --env-file plane.env'
   $C up -d plane-db
   until $C exec -T plane-db pg_isready -U plane -q; do sleep 2; done
   $C exec -T plane-db dropdb -U plane --if-exists plane
   $C exec -T plane-db createdb -U plane plane
   $C exec -T plane-db pg_restore -U plane -d plane --no-owner < ~/restore-drill/<folder>/plane.pgdump
   cd .. && ./setup.sh start
   ```
5. Pass: you can sign in, "drill: before backup" exists, and "drill: after backup" is gone. Delete both items and `~/restore-drill`.

### 9.4 Non-destructive check (monthly)

```bash
~/plane-ops/verify-backup.sh
```

It downloads the newest off-box backup, checks every archive, and restores `plane.pgdump` into a throwaway Postgres container with no network, using the same image as the running database. It never touches the live containers. It ends with `PASS` and counts of workspaces, projects and work items.

## 10. Keep the instance from idle reclamation

- The rule [O1]: over 7 days, 95th-percentile CPU under 20%, network under 20% and (A1) memory under 20%, all three at once. A one-person Plane keeps CPU and network low, so memory decides. 20% of 12 GB is 2.4 GB.
- After Plane has run for a day, check memory: run `free -m` (the `used` column) and open the instance's Metrics page in the Oracle console (Memory utilization). The metrics come from the Oracle Cloud Agent monitoring plugin, which is on by default; leave it on.
- If memory stays well above 2.4 GB, re-check monthly.
- If memory stays under 2.4 GB: the clean fix is upgrading the account to Pay As You Go. Oracle states it does not charge for Always Free resources after the upgrade [O1]. Set a budget alert (for example US$1) first. Community posts say upgraded accounts are not reclaimed for idleness, but Oracle's page does not say so; treat that as unverified. This is a hosting decision, so it is Gabriel's. Artificial load generators are not recommended.
- If the instance is reclaimed anyway: create a new one (steps 2-6) with the same `plane.env` from your password manager, recreate both rclone remotes (step 9.1; for `offsite-crypt` choose `y` and enter the stored password and salt), then restore the newest off-box backup with the procedure in step 9.3, item 4 (database from `plane.pgdump`).

## 11. Personal access token and the Plane MCP

On CE the Plane MCP server runs locally over stdio with a personal access token; OAuth is not available on CE [M1]. It needs Python 3.10+ and `uv`, and is started as `uvx plane-mcp-server stdio` with `PLANE_API_KEY`, `PLANE_WORKSPACE_SLUG` and `PLANE_BASE_URL` [M2].

1. In Plane: Profile settings > Personal access tokens > create `claude-mcp` [M2]. If Plane offers an expiry, pick one and put a reminder in your calendar. Copy the token once, into your password manager. Agents act with your permissions, and their comments appear under your name.
2. Test the token from your PC (Git Bash or WSL). `read -rs` keeps it out of shell history:
   ```bash
   read -rs PLANE_API_KEY && export PLANE_API_KEY
   curl -fsS -H "X-API-Key: $PLANE_API_KEY" "https://<PLANE_HOST>/api/v1/workspaces/eos/projects/" | head -c 300; echo
   ```
   You should see JSON containing the `EOS` project.
3. Local Claude Code (your PC, including Remote Control sessions). Install `uv` with its standard installer (macOS/Linux: `curl -LsSf https://astral.sh/uv/install.sh | sh`; Windows: `powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"`), then register the server at user scope. User scope is stored in your home folder, outside every repo:
   ```bash
   claude mcp add plane --scope user \
     -e PLANE_API_KEY="$PLANE_API_KEY" \
     -e PLANE_WORKSPACE_SLUG=eos \
     -e PLANE_BASE_URL=https://<PLANE_HOST> \
     -- uvx plane-mcp-server stdio
   claude mcp list
   ```
   Never use `--scope project`: it writes `.mcp.json` into the repo, and the repo is public.
4. Cloud Claude sessions (this claude.ai project): add `PLANE_API_KEY`, `PLANE_WORKSPACE_SLUG` and `PLANE_BASE_URL` as environment variables in the project's environment settings. If the network policy blocks the Plane host (or PyPI, which `uvx` downloads from), add it under Network access. Registering the server for cloud sessions takes a committed `.mcp.json` that reads those variables, with no value inside it, plus `uv` in the session setup. Claude proposes that as a separate change once Plane works; it is not part of this runbook.
5. Never paste the token into the chat, a doc or a commit. To rotate it, revoke it in Plane, create a new one, and update the places in items 3 and 4.

What agents may do in Plane (read, comment, move state) is set when Plane replaces `docs/roadmap.md`. Agents never create Plane structure, automations or tokens (ADR-0017).

## 12. Maintenance

| When | What |
|---|---|
| Weekly, and the morning after any server change | `journalctl -t plane-backup --since yesterday` ends with `Backup OK` (or the heartbeat service stays quiet) |
| Monthly | In order: 1) `~/plane-ops/backup.sh`; 2) `cd /opt/plane-selfhost && ./setup.sh upgrade` (answer `y`; it stops Plane and does not start it again [P8]); 3) `diff plane-app/plane.env.bak plane-app/plane.env`, and set any new password, secret or key as in step 6 (the upgrade keeps only non-empty old values, so new keys arrive with their defaults); 4) `./setup.sh start`; 5) sign in and open a work item; 6) `docker image prune -a -f` (only once Plane runs again, so its current images are in use); 7) `~/plane-ops/verify-backup.sh`; 8) check memory against the 2.4 GB line (step 10) and `df -h /` |
| When `/var/run/reboot-required` exists | `sudo reboot`; afterwards `docker ps`, and `./setup.sh start` if Plane did not come back |
| When Oracle emails about the free tier or an idle instance | Read it the same day; the off-box backup is the fallback |

## 13. Final checklist

- [ ] A1 instance: `VM.Standard.A1.Flex`, 2 OCPU, 12 GB, Ubuntu 24.04 aarch64, Always Free-eligible, home region
- [ ] Security list: only SSH (and ICMP) inbound
- [ ] `bootstrap-host.sh` ran: swap on, Docker works without sudo, timezone set, automatic security updates on
- [ ] `plane.env`: `APP_DOMAIN`, `WEB_URL`, `CORS_ALLOWED_ORIGINS` set; every default password and secret replaced, including `DATABASE_URL`, `AMQP_URL` and `LIVE_SERVER_SECRET_KEY` next to `SECRET_KEY`; the `plane:plane@` check prints 0; a copy in the password manager
- [ ] Instance admin claimed by Gabriel; public sign-up off
- [ ] Plane reachable at `https://<PLANE_HOST>`; the description edit survives a reload
- [ ] Workspace `eos`, project `EOS`, states and labels as in step 8; no cycles, modules or automations
- [ ] Nightly `backup.sh` in cron; the first off-box copy exists, encrypted through `offsite-crypt`; crypt password and salt in the password manager
- [ ] Restore drill passed (step 9.3); `verify-backup.sh` prints PASS
- [ ] Memory checked against the reclamation line (step 10)
- [ ] Token created and stored; `curl` test returns the EOS project; `claude mcp list` shows `plane` connected locally
- [ ] Gabriel posted the outcome in the project thread (step 14)

## 14. After setup

Post in the project thread: "Plane setup done", plus your answers to the three Open questions below. Leave out the hostname, IP and tokens. Claude then updates, in one commit: the Q-83 row in `planning/open-questions.md`, ADR-0017, `docs/roadmap.md` (the Plane setup step and its readiness-gate item) and this page. Only Gabriel sets this page to Reviewed.

## Sources

All read 2026-10-01.
- [P1] Plane, Docker Compose install: https://developers.plane.so/self-hosting/methods/docker-compose (mirror read the same day: https://planesoftwareinc.mintlify.app/self-hosting/methods/docker-compose)
- [P2] Plane, Backup and restore (CE section: `./setup.sh` option 7, `restore.sh`): https://developers.plane.so/self-hosting/manage/backup-restore
- [P3] Plane, Editions and versions: https://developers.plane.so/self-hosting/editions-and-versions
- [P4] Plane, Instance admin and God mode: https://developers.plane.so/self-hosting/govern/instance-admin
- [P5] Plane issue #9068, "/god-mode without trailing slash renders a blank/loading page" (search result title only): https://github.com/makeplane/plane/issues/9068
- [P6] Plane, Upgrade from Community to Commercial Edition (uses `./setup.sh backup`; archive names `pgdata.tar.gz`, `redisdata.tar.gz`, `uploads.tar.gz`): https://developers.plane.so/self-hosting/upgrade-from-community
- [P7] Plane, Environment variables (CE: `APP_DOMAIN`, `WEB_URL`, `CORS_ALLOWED_ORIGINS`, `LISTEN_HTTP_PORT`, `POSTGRES_PASSWORD`, `SECRET_KEY`; TLS variables are listed for the Commercial Edition only): https://developers.plane.so/self-hosting/govern/environment-variables
- [P8] Plane CE release v1.4.2 files, the ones `setup.sh` installs: `setup.sh` (menu, `install`, `upgrade`, `backupData`, `syncEnvFile`), `variables.env` (becomes `plane.env`: empty `DATABASE_URL` and `AMQP_URL`, `LIVE_SERVER_SECRET_KEY` placeholder, `SITE_ADDRESS`, `CERT_EMAIL`, `CERT_ACME_CA`, `CERT_ACME_DNS`, `TRUSTED_PROXIES`) and `docker-compose.yml` (default URLs with the password `plane`): https://github.com/makeplane/plane/releases/latest ; CE proxy `apps/proxy/Caddyfile.ce` (`reverse_proxy /live/*`, certificate settings) and `deployments/cli/community/restore.sh` in the repo
- [P9] Plane source at v1.4.2: `apps/api/plane/db/models/api.py` (`APIToken.token` is a `CharField`) and `apps/api/plane/db/models/session.py` (`session_key`): https://github.com/makeplane/plane/tree/v1.4.2/apps/api/plane/db/models
- [M1] Plane, MCP server self-host (CE uses local stdio mode with a personal access token): https://developers.plane.so/dev-tools/mcp-server-self-host
- [M2] Plane, MCP server (`uvx plane-mcp-server stdio`, environment variables, token location): https://developers.plane.so/dev-tools/mcp-server
- [O1] Oracle, Always Free Resources: https://docs.oracle.com/en-us/iaas/Content/FreeTier/resourceref.htm
- [O2] InfoQ, "Oracle Quietly Halves Free Tier Ampere A1 Compute Limits", 2026-07-03: https://www.infoq.com/news/2026/07/oracle-cloud-free-tier-limits/
- [O3] Linuxiac, "Oracle Quietly Cuts Free Tier Ampere A1 Resources in Half", 2026-06-14: https://linuxiac.com/oracle-quietly-cuts-free-tier-ampere-a1-resources-in-half/
- [O4] TerminalBytes, "Oracle Cloud free tier 2026", June 2026: https://terminalbytes.com/oracle-cloud-free-tier-changes-2026/
- [O5] bex.co, "Oracle Halved Its Free ARM Cloud", 2026-09-26: https://bex.co/blog/2026/09/26/oracle-always-free-cut-free-tier-k8s-vs-hetzner
- [O6] Sudo Security, "Oracle Free Tier ARM Cutoff", 2026-08-10: https://sudosecurity.org/oracle-free-tier-arm-cutoff-2026/
- [C1] Cloudflare, Set up Cloudflare Tunnel (needs a Cloudflare account and a domain on Cloudflare; outbound-only; `sudo cloudflared service install <TUNNEL_TOKEN>`): https://developers.cloudflare.com/tunnel/get-started/ ; Quick Tunnels limits: https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/
- [T1] Tailscale, Funnel (MagicDNS, HTTPS certificates, `funnel` node attribute; ports 443, 8443, 10000; `ts.net` names only; all plans): https://tailscale.com/kb/1223/funnel
- Repo: ADR-0017; `planning/pm-tool-recommendation.md` ("Proposed board structure", "Free VPS: Oracle Cloud Always Free"); `planning/open-questions.md` Q-83.
- Not read today, used as written: the `uv` installer commands; `claude mcp add` syntax from the local `claude mcp add --help`.

## Inputs needed from Gabriel

- Oracle home region, chosen at signup and permanent. The instance must be created there.
- Do you own a domain you can use for `plane.<domain>`? Is its DNS on Cloudflare, or do you have a Cloudflare account?
- The Google account (or other storage) for off-box backups.
- Your timezone for the nightly backup (the runbook assumes `America/Sao_Paulo`).
- Only if step 10 shows memory under 20%: would you upgrade the Oracle account to Pay As You Go, with a budget alert?
- When done: the outcome message in step 14 (no hostname, IP or tokens).

## Open questions

Local to this runbook. They complete Q-83, which Gabriel answered b on 2026-10-01 without these three details.
1. HTTPS exposure: `cloudflare`, `funnel` or `private`? Recommended: `cloudflare` if you already own a domain, otherwise `funnel` (free and needs no domain; switching later takes a few minutes). `private` keeps Plane off the internet, but cloud Claude sessions cannot reach it.
2. Off-box backup target: `drive`, `r2` or `pc`? Recommended: `drive` (free, independent of Oracle, already supported by the scripts through rclone).
3. Work-item ids: put `EOS-<n>` in commit messages and plan files once Plane exists, `yes` or `no`? Recommended: `yes`. Under ADR-0013 work lands straight on the working branch, so there are no per-item branches or PRs to name.
