#!/usr/bin/env bash
# bootstrap-host.sh: prepare a fresh Ubuntu (arm64) Oracle Ampere A1 VM for Plane Community Edition.
#
# Part of docs/ops/plane-setup.md (Enterprise Order Suite), step 4.
# Run once as the default user, with sudo:
#     sudo ./bootstrap-host.sh
# Optional settings, passed on the sudo line:
#     sudo TZ_NAME=America/Sao_Paulo SWAP_SIZE_GB=4 ./bootstrap-host.sh
#
# Safe to re-run: every step checks the current state first and only changes what is missing.
# Contains no secrets.
#
# Steps:
#   1. OS updates and the packages the runbook uses (curl, jq, rclone, unattended-upgrades).
#   2. Automatic security updates.
#   3. A swap file, so a memory spike cannot get Postgres killed.
#   4. Docker Engine and the Compose plugin (Docker's convenience script, as Plane's install docs use).
#   5. A cap on container log size, so logs cannot fill the boot volume.
#   6. Docker access for the invoking user, and the Plane folder /opt/plane-selfhost.
#   7. The timezone (only when TZ_NAME is set), so the nightly backup runs at night.
set -euo pipefail

SWAP_SIZE_GB="${SWAP_SIZE_GB:-4}"
PLANE_DIR="${PLANE_DIR:-/opt/plane-selfhost}"
TZ_NAME="${TZ_NAME:-}"
TARGET_USER="${SUDO_USER:-ubuntu}"

log() { printf '\n==> %s\n' "$*"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "Run with sudo: sudo ./bootstrap-host.sh" >&2
  exit 1
fi

if [ "$(uname -m)" != "aarch64" ]; then
  echo "Warning: expected aarch64 (Ampere A1), found $(uname -m). Continuing." >&2
fi

log "1/7 OS updates and packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get upgrade -y
apt-get install -y ca-certificates curl jq rclone unattended-upgrades

log "2/7 Automatic security updates"
# Same two lines on every run, so re-running changes nothing.
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

log "3/7 Swap file (${SWAP_SIZE_GB} GB)"
if swapon --show=NAME --noheadings | grep -qx /swapfile; then
  echo "Swap already active on /swapfile."
else
  if [ ! -f /swapfile ]; then
    fallocate -l "${SWAP_SIZE_GB}G" /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
  fi
  swapon /swapfile
fi
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
# Prefer RAM; swap only under memory pressure.
echo 'vm.swappiness=10' > /etc/sysctl.d/99-plane-swap.conf
sysctl -q -p /etc/sysctl.d/99-plane-swap.conf

log "4/7 Docker Engine and Compose plugin"
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  echo "Already installed: $(docker --version)"
else
  curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
  sh /tmp/get-docker.sh
  rm -f /tmp/get-docker.sh
fi
systemctl enable --now docker

log "5/7 Container log size cap (10 MB x 3 files per container)"
DAEMON_JSON=/etc/docker/daemon.json
if [ -s "$DAEMON_JSON" ] && jq -e '."log-opts"."max-size"' "$DAEMON_JSON" >/dev/null 2>&1; then
  echo "Log cap already set in $DAEMON_JSON."
else
  existing='{}'
  if [ -s "$DAEMON_JSON" ]; then existing="$(cat "$DAEMON_JSON")"; fi
  printf '%s' "$existing" \
    | jq '. + {"log-driver": "json-file", "log-opts": {"max-size": "10m", "max-file": "3"}}' \
    > "$DAEMON_JSON.tmp"
  mv "$DAEMON_JSON.tmp" "$DAEMON_JSON"
  # Applies to containers created after this point; Plane is installed later (runbook step 6).
  systemctl restart docker
fi

log "6/7 Docker access for $TARGET_USER and the Plane folder $PLANE_DIR"
if id -nG "$TARGET_USER" | tr ' ' '\n' | grep -qx docker; then
  echo "$TARGET_USER is already in the docker group."
else
  usermod -aG docker "$TARGET_USER"
  echo "Added $TARGET_USER to the docker group: log out and back in before running docker."
fi
install -d -o "$TARGET_USER" -g "$TARGET_USER" -m 755 "$PLANE_DIR"

log "7/7 Timezone"
if [ -n "$TZ_NAME" ]; then
  timedatectl set-timezone "$TZ_NAME"
fi
echo "Timezone: $(timedatectl show -p Timezone --value)"

if [ -f /var/run/reboot-required ]; then
  log "Updates need a reboot: run 'sudo reboot', wait a minute, then SSH in again."
fi
log "Done."
