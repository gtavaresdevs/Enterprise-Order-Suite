# Self-hosted Plane scripts - superseded, not run

> **Superseded on 2026-10-06 (Gabriel, project thread 2026-10-06T13:49Z): "lets readjust our plan so that we use the plane free tier in their cloud."** The project uses Plane Cloud's free plan (ADR-0017), so there is no server and none of these scripts is run. The current runbook is `../plane-setup.md`; this folder belongs to the superseded one, `../superseded/plane-selfhost-oracle.md`.

Kept in case Plane is ever self-hosted. They were written and tested for Plane Community Edition v1.4.2 on 2026-10-01 and 2026-10-02, on linux/amd64 in a cloud container, and never run on an Oracle VM (the "Tested" section of the superseded runbook lists exactly what was and was not tested). Re-check the sources and the Plane release before using them.

| File | What it does |
|---|---|
| `cloud-init.yaml` | First-boot file for the instance: downloads the four scripts below, then runs `bootstrap-host.sh`, installs Tailscale without logging it in, and runs `install-plane.sh`. Contains no secrets |
| `bootstrap-host.sh` | OS updates, swap, Docker with the Compose plugin, container log cap, timezone |
| `install-plane.sh` | Non-interactive Plane CE install of a pinned release; replaces every shipped default secret before the first start; idempotent |
| `plane-url.sh` | Points Plane at its public host name; refuses to run until the instance admin is claimed and public sign-up is off |
| `backup.sh` | Nightly backup: Plane's own archives plus a logical Postgres dump, copied off the box through an `rclone` crypt remote; refuses a remote that does not encrypt |
| `verify-backup.sh` | Restores the newest off-box backup into a throwaway Postgres container and prints PASS with row counts; never touches the live containers |

`cloud-init.yaml` downloads the scripts from this folder's raw URLs on `feature/ai-agent`, so moving or renaming them breaks it.
