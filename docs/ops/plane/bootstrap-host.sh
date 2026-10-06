#!/usr/bin/env bash
# SUPERSEDED 2026-10-06: the project uses Plane Cloud's free plan (ADR-0017), so this script is not run.
# Kept in case Plane is ever self-hosted. See README.md in this folder and ../plane-setup.md.
# bootstrap-host.sh: prepare a fresh Ubuntu 24.04 (arm64) Oracle Ampere A1 VM for Plane Community Edition.
#
# Part of docs/ops/plane-setup.md (Enterprise Order Suite). cloud-init.yaml runs it on the first
# boot (section 2); by hand (Appendix A), run it once as the default user, with sudo:
#     sudo ./bootstrap-host.sh
# Optional settings, passed on the sudo line:
#     sudo TZ_NAME=America/Sao_Paulo SWAP_SIZE_GB=4 ./bootstrap-host.sh
#   TZ_NAME        Timezone to set, for example America/Sao_Paulo (default: leave it as it is).
#   SWAP_SIZE_GB   Size of the swap file in GB, 1 to 64 (default 4).
#   TARGET_USER    User that gets Docker access and owns /opt/plane-selfhost (default: the user
#                  that ran sudo, else "ubuntu").
#
# Safe to re-run: every step checks the current state first and only changes what is missing.
# Contains no secrets.
#
# Steps:
#   1. OS updates and the packages the runbook uses (curl, jq, rclone, unattended-upgrades).
#   2. Automatic security updates.
#   3. A swap file, so a memory spike cannot get Postgres killed.
#   4. A cap on container log size, so logs cannot fill the boot volume (written before Docker
#      starts for the first time, so no Docker restart is needed on a fresh VM).
#   5. Docker Engine and the Compose plugin from Docker's own apt repository (the method Docker
#      recommends for servers; the repository line names this machine's CPU type, arm64 here).
#   6. Docker access for TARGET_USER, and the Plane folder /opt/plane-selfhost.
#   7. The timezone (only when TZ_NAME is set), so the nightly backup runs at night.
#
# It needs a real VM booted with systemd. Inside a container it stops before changing anything,
# because swap, kernel settings, services and the clock belong to the host, not to a container.
# For a test run in a container only, BOOTSTRAP_CONTAINER_TEST=1 runs every step a container
# allows, prints "SKIPPED" for each step that needs a VM, and exits with code 3 (never 0), so a
# test run can never be mistaken for a finished bootstrap.
set -euo pipefail

SWAP_SIZE_GB="${SWAP_SIZE_GB:-4}"
PLANE_DIR="${PLANE_DIR:-/opt/plane-selfhost}"
TZ_NAME="${TZ_NAME:-}"
TARGET_USER="${TARGET_USER:-${SUDO_USER:-ubuntu}}"
CONTAINER_TEST="${BOOTSTRAP_CONTAINER_TEST:-0}"

DOCKER_REPO_URL="https://download.docker.com/linux/ubuntu"
DOCKER_KEY=/etc/apt/keyrings/docker.asc
DOCKER_SOURCES=/etc/apt/sources.list.d/docker.sources
DAEMON_JSON=/etc/docker/daemon.json

log() { printf '\n==> %s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
SKIPPED=()
skip() { printf 'SKIPPED (needs a VM booted with systemd): %s\n' "$*"; SKIPPED+=("$*"); }

# apt without questions: wait for the apt lock (Ubuntu's own update timers hold it for a while
# after boot), keep existing config files when a package ships a new one, and restart services
# whose libraries were updated without asking (needrestart).
export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a
apt_get() {
  apt-get -o DPkg::Lock::Timeout=600 \
    -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold "$@"
}

# ---------------------------------------------------------------------------------------------
# Checks. Nothing is changed before all of them pass.
# ---------------------------------------------------------------------------------------------
[ "$(id -u)" -eq 0 ] || die "run with sudo: sudo ./bootstrap-host.sh"

# shellcheck disable=SC1091  # /etc/os-release exists on every Ubuntu
. /etc/os-release
[ "${ID:-}" = "ubuntu" ] || die "this script is for Ubuntu (found ${ID:-unknown}); Docker's apt repository below is Ubuntu's"
CODENAME="${UBUNTU_CODENAME:-${VERSION_CODENAME:-}}"
[ -n "$CODENAME" ] || die "cannot read the Ubuntu release name from /etc/os-release"

if [ "$(uname -m)" != "aarch64" ]; then
  echo "Warning: expected aarch64 (Ampere A1), found $(uname -m). Continuing." >&2
fi

[[ "$SWAP_SIZE_GB" =~ ^[0-9]+$ ]] && [ "$SWAP_SIZE_GB" -ge 1 ] && [ "$SWAP_SIZE_GB" -le 64 ] \
  || die "SWAP_SIZE_GB must be a whole number from 1 to 64 (got '$SWAP_SIZE_GB')"
id "$TARGET_USER" >/dev/null 2>&1 || die "user '$TARGET_USER' does not exist (set TARGET_USER)"

# systemd is PID 1 on a real VM; containers normally have no systemd and say they are containers.
HAS_SYSTEMD=0
[ -d /run/systemd/system ] && HAS_SYSTEMD=1
IN_CONTAINER=0
if [ -f /.dockerenv ] || [ -f /run/.containerenv ] \
  || { command -v systemd-detect-virt >/dev/null 2>&1 && systemd-detect-virt --container --quiet; }; then
  IN_CONTAINER=1
fi
if [ "$HAS_SYSTEMD" -eq 0 ] || [ "$IN_CONTAINER" -eq 1 ]; then
  if [ "$CONTAINER_TEST" != "1" ]; then
    die "this is a container or a machine not booted with systemd (systemd=$HAS_SYSTEMD, container=$IN_CONTAINER).
Nothing was changed. Run this script on the Oracle VM itself. (Test runs only: BOOTSTRAP_CONTAINER_TEST=1.)"
  fi
  echo "CONTAINER TEST MODE: the steps that need a VM are skipped and the script exits with code 3."
else
  CONTAINER_TEST=0
fi

if [ -n "$TZ_NAME" ] && [ "$HAS_SYSTEMD" -eq 1 ]; then
  # grep without -q reads the whole list: with -q it can stop early, timedatectl then dies of
  # SIGPIPE, and pipefail reports a valid timezone as unknown (seen in testing).
  timedatectl list-timezones | grep -xF -- "$TZ_NAME" >/dev/null \
    || die "unknown timezone '$TZ_NAME' (see: timedatectl list-timezones)"
fi

# ---------------------------------------------------------------------------------------------
log "1/7 OS updates and packages"
apt_get update -y
apt_get upgrade -y
apt_get install -y ca-certificates curl jq rclone unattended-upgrades

# ---------------------------------------------------------------------------------------------
log "2/7 Automatic security updates"
# Same two lines on every run, so re-running changes nothing.
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

# ---------------------------------------------------------------------------------------------
log "3/7 Swap file (${SWAP_SIZE_GB} GB)"
if [ "$CONTAINER_TEST" = "1" ]; then
  # Swap and vm.swappiness are kernel-wide: a container shares the host's kernel.
  skip "swap file /swapfile and vm.swappiness"
elif swapon --show=NAME --noheadings | grep -x /swapfile >/dev/null; then
  echo "Swap already active on /swapfile."
else
  if [ ! -f /swapfile ]; then
    # Built under a temporary name and renamed when complete, so an interrupted run never leaves
    # a /swapfile without a swap signature behind.
    rm -f /swapfile.partial
    fallocate -l "${SWAP_SIZE_GB}G" /swapfile.partial
    chmod 600 /swapfile.partial
    mkswap /swapfile.partial
    mv /swapfile.partial /swapfile
  fi
  swapon /swapfile
fi
if [ "$CONTAINER_TEST" != "1" ]; then
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  # Prefer RAM; swap only under memory pressure.
  echo 'vm.swappiness=10' > /etc/sysctl.d/99-plane-swap.conf
  sysctl -q -p /etc/sysctl.d/99-plane-swap.conf
fi

# ---------------------------------------------------------------------------------------------
log "4/7 Container log size cap (10 MB x 3 files per container)"
LOG_CAP_CHANGED=0
if [ -s "$DAEMON_JSON" ] && jq -e '."log-opts"."max-size"' "$DAEMON_JSON" >/dev/null 2>&1; then
  echo "Log cap already set in $DAEMON_JSON."
else
  install -d -m 755 /etc/docker
  existing='{}'
  if [ -s "$DAEMON_JSON" ]; then existing="$(cat "$DAEMON_JSON")"; fi
  printf '%s' "$existing" \
    | jq '. + {"log-driver": "json-file", "log-opts": {"max-size": "10m", "max-file": "3"}}' \
    > "$DAEMON_JSON.tmp"
  mv "$DAEMON_JSON.tmp" "$DAEMON_JSON"
  LOG_CAP_CHANGED=1
  echo "Wrote $DAEMON_JSON. It applies to containers created from now on."
fi

# ---------------------------------------------------------------------------------------------
log "5/7 Docker Engine and Compose plugin (Docker's apt repository)"
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  echo "Already installed: $(docker --version)"
else
  # Docker's documented apt setup (docs.docker.com/engine/install/ubuntu). The architecture comes
  # from dpkg, so the same lines give arm64 packages on Ampere A1 and amd64 packages elsewhere.
  install -d -m 755 /etc/apt/keyrings
  curl -fsSL --retry 5 --retry-delay 5 "$DOCKER_REPO_URL/gpg" -o "$DOCKER_KEY.tmp"
  chmod a+r "$DOCKER_KEY.tmp"
  mv "$DOCKER_KEY.tmp" "$DOCKER_KEY"
  cat > "$DOCKER_SOURCES" <<EOF
Types: deb
URIs: $DOCKER_REPO_URL
Suites: $CODENAME
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: $DOCKER_KEY
EOF
  apt_get update -y
  apt_get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  echo "Installed: $(docker --version); $(docker compose version)"
fi
if [ "$HAS_SYSTEMD" -eq 1 ]; then
  systemctl enable --now docker
  if [ "$LOG_CAP_CHANGED" -eq 1 ]; then
    # Docker reads daemon.json only when it starts. On a fresh VM this restart costs seconds; on
    # a host where Docker already ran, it puts the cap in force (existing containers keep their
    # old setting until they are recreated).
    systemctl restart docker
  fi
  docker info --format 'Docker is running: version {{.ServerVersion}}, logs {{.LoggingDriver}}'
else
  skip "start Docker and enable it at boot (systemctl enable --now docker)"
fi

# ---------------------------------------------------------------------------------------------
log "6/7 Docker access for $TARGET_USER and the Plane folder $PLANE_DIR"
if id -nG "$TARGET_USER" | tr ' ' '\n' | grep -x docker >/dev/null; then
  echo "$TARGET_USER is already in the docker group."
else
  usermod -aG docker "$TARGET_USER"
  echo "Added $TARGET_USER to the docker group: log out and back in before running docker."
fi
install -d -o "$TARGET_USER" -g "$TARGET_USER" -m 755 "$PLANE_DIR"

# ---------------------------------------------------------------------------------------------
log "7/7 Timezone"
if [ "$HAS_SYSTEMD" -eq 0 ]; then
  skip "timezone (timedatectl needs systemd)"
else
  if [ -n "$TZ_NAME" ]; then
    timedatectl set-timezone "$TZ_NAME"
  fi
  echo "Timezone: $(timedatectl show -p Timezone --value)"
fi

# ---------------------------------------------------------------------------------------------
if [ "$CONTAINER_TEST" = "1" ]; then
  log "CONTAINER TEST MODE finished. NOT a finished bootstrap: ${#SKIPPED[@]} step(s) skipped:"
  printf '  - %s\n' "${SKIPPED[@]}"
  exit 3
fi
if [ -f /var/run/reboot-required ]; then
  log "Updates need a reboot: run 'sudo reboot', wait a minute, then SSH in again."
fi
log "Done."
