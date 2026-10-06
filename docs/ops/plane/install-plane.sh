#!/usr/bin/env bash
# SUPERSEDED 2026-10-06: the project uses Plane Cloud's free plan (ADR-0017), so this script is not run.
# Kept in case Plane is ever self-hosted. See README.md in this folder and ../plane-setup.md.
# install-plane.sh: install Plane Community Edition without questions, with fresh secrets, and start it.
#
# Part of docs/ops/plane-setup.md (Enterprise Order Suite). cloud-init.yaml runs it on the first
# boot (section 2); by hand (Appendix A), run it after bootstrap-host.sh, as root:
#     sudo ./install-plane.sh                               # Plane answers on http://localhost
#     sudo ./install-plane.sh --host plane.tail1234.ts.net  # the public HTTPS name, if already known
#     sudo ./install-plane.sh --port 8080                   # publish Plane on another host port
# Optional settings, passed on the sudo line:
#     sudo PLANE_RELEASE=v1.4.2 START_TIMEOUT=900 ./install-plane.sh
#
#   --host <name>   Sets APP_DOMAIN=<name>, WEB_URL and CORS_ALLOWED_ORIGINS=https://<name>.
#                   Without it, a first install uses http://localhost (plus :<port> when the port is
#                   not 80): Plane then works through an SSH port forward that uses the same port on
#                   your PC (ssh -L 80:localhost:80 ...). Change it later with plane-url.sh.
#   --port <n>      LISTEN_HTTP_PORT, the host port Plane's proxy publishes (default 80).
#   PLANE_DIR       Folder that holds setup.sh and plane-app/ (default /opt/plane-selfhost).
#   PLANE_RELEASE   Plane release tag to install, for example v1.4.2 (default: the latest release).
#   START_TIMEOUT   Seconds to wait for Plane to answer after starting it (default 600).
#
# What it does:
#   1. Checks Docker and the Compose plugin (bootstrap-host.sh installs them).
#   2. Downloads Plane's own setup.sh for the release into PLANE_DIR and runs "./setup.sh install"
#      with no keyboard input. setup.sh writes plane-app/docker-compose.yaml and plane-app/plane.env
#      and pulls the images.
#   3. Before Plane first starts, replaces every shipped default password and secret in plane.env
#      with random values made on this machine, including DATABASE_URL and AMQP_URL. Those two must
#      be written out: when they are empty, Plane's docker-compose.yaml falls back to URLs that
#      contain the default password "plane", and Plane could not reach its own database.
#   4. Sets APP_DOMAIN, WEB_URL, CORS_ALLOWED_ORIGINS and LISTEN_HTTP_PORT.
#   5. Starts Plane ("./setup.sh start") and waits until http://localhost:<port>/ answers.
#
# Safe to re-run. Secrets are generated only before Plane's first start (while the Postgres data
# volume does not exist). Postgres, RabbitMQ and MinIO keep the passwords they were first started
# with, so once Plane has started a re-run never changes a secret: it only makes sure Plane is
# running, and applies --host / --port when given.
#
# Contains no secrets and prints none. The secrets live only in plane-app/plane.env (mode 600,
# folder mode 700, owned by the owner of PLANE_DIR, normally the "ubuntu" user, who also runs the
# nightly backup.sh) and in a root-only copy, /root/plane.env.first-start, written once.
set -euo pipefail

PLANE_DIR="${PLANE_DIR:-/opt/plane-selfhost}"
PLANE_RELEASE="${PLANE_RELEASE:-}"
START_TIMEOUT="${START_TIMEOUT:-600}"
APP_DIR="$PLANE_DIR/plane-app"            # setup.sh always uses this folder name
ENV_FILE="$APP_DIR/plane.env"
COMPOSE_FILE="$APP_DIR/docker-compose.yaml"
COMPOSE_PROJECT="plane-app"               # Docker Compose names the project after APP_DIR
ROOT_COPY="/root/plane.env.first-start"
LATEST_SETUP_URL="https://github.com/makeplane/plane/releases/latest/download/setup.sh"
LOCK_FILE="/run/lock/plane-env.lock"      # shared with plane-url.sh

log() { printf '\n==> %s\n' "$*"; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
usage() { sed -n '2,23p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

# ---------------------------------------------------------------------------------------------
# Arguments
# ---------------------------------------------------------------------------------------------
HOST=""
PORT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --host) [ $# -ge 2 ] || die "--host needs a value"; HOST="$2"; shift 2 ;;
    --host=*) HOST="${1#--host=}"; shift ;;
    --port) [ $# -ge 2 ] || die "--port needs a value"; PORT="$2"; shift 2 ;;
    --port=*) PORT="${1#--port=}"; shift ;;
    -h|--help) usage 0 ;;
    *) printf 'Unknown argument: %s\n\n' "$1" >&2; usage 1 ;;
  esac
done

# Accept a bare host name (optionally with :port). Drop a trailing dot, as "tailscale status" prints.
normalize_host() {
  local h="$1"
  case "$h" in
    *://*) die "give the host name only, without a scheme (plane.tail1234.ts.net, not https://...)" ;;
    */*) die "give the host name only, without a path" ;;
  esac
  h="${h%.}"
  h="$(printf '%s' "$h" | tr '[:upper:]' '[:lower:]')"
  [ "${#h}" -le 253 ] || die "host name too long: $h"
  [[ "$h" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)*[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(:[0-9]{1,5})?$ ]] \
    || die "not a valid host name: $h"
  printf '%s' "$h"
}

if [ -n "$HOST" ]; then HOST="$(normalize_host "$HOST")"; fi
if [ -n "$PORT" ]; then
  [[ "$PORT" =~ ^[0-9]{1,5}$ ]] && [ "$PORT" -ge 1 ] && [ "$PORT" -le 65535 ] \
    || die "--port must be a number from 1 to 65535"
fi
[[ "$START_TIMEOUT" =~ ^[0-9]+$ ]] || die "START_TIMEOUT must be a number of seconds"

# ---------------------------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------------------------
[ "$(id -u)" -eq 0 ] || die "run with sudo: sudo ./install-plane.sh"
for tool in curl openssl awk flock; do
  command -v "$tool" >/dev/null 2>&1 || die "$tool is missing (bootstrap-host.sh installs what is needed)"
done
command -v docker >/dev/null 2>&1 || die "Docker is not installed: run bootstrap-host.sh first"
docker info >/dev/null 2>&1 || die "the Docker daemon is not running (systemctl start docker)"
if ! docker compose version >/dev/null 2>&1 && ! command -v docker-compose >/dev/null 2>&1; then
  die "the Docker Compose plugin is missing: run bootstrap-host.sh first"
fi
case "$(uname -m)" in
  aarch64|arm64|x86_64|amd64) ;;
  *) warn "Plane publishes images for arm64 and amd64 only; found $(uname -m)." ;;
esac

# One run at a time (cloud-init and a person at the keyboard could overlap).
mkdir -p "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
flock -n 9 || die "another install-plane.sh or plane-url.sh is running ($LOCK_FILE)"

# ---------------------------------------------------------------------------------------------
# Helpers for plane.env
# ---------------------------------------------------------------------------------------------

# get_env KEY: the value of the last KEY= line in plane.env, or nothing.
get_env() {
  [ -f "$ENV_FILE" ] || return 0
  awk -v k="$1" 'index($0, k "=") == 1 { v = substr($0, length(k) + 2) } END { print v }' "$ENV_FILE"
}

# apply_env FILE: FILE holds KEY=VALUE lines. Writes them into plane.env in one atomic step: an
# existing KEY= line is replaced, a missing key is appended. Values never pass through sed or the
# shell, and the file keeps its owner and mode.
apply_env() {
  local changes="$1" tmp
  tmp="$(mktemp "$ENV_FILE.XXXXXX")"
  awk '
    NR == FNR { i = index($0, "="); k = substr($0, 1, i - 1); v[k] = substr($0, i + 1); order[++n] = k; next }
    {
      i = index($0, "=")
      if (i > 1 && substr($0, 1, 1) != "#") {
        k = substr($0, 1, i - 1)
        if (k in v) { if (!(k in done)) { print k "=" v[k]; done[k] = 1 }; next }
      }
      print
    }
    END { for (j = 1; j <= n; j++) if (!(order[j] in done)) { print order[j] "=" v[order[j]]; done[order[j]] = 1 } }
  ' "$changes" "$ENV_FILE" > "$tmp"
  chmod --reference="$ENV_FILE" "$tmp"
  chown --reference="$ENV_FILE" "$tmp"
  mv -f "$tmp" "$ENV_FILE"
}

# True when a Docker volume of this Plane install exists (Plane has started at least once).
volume_exists() { docker volume inspect "${COMPOSE_PROJECT}_$1" >/dev/null 2>&1; }

# True when KEY is missing, empty, or still holds a default that Plane ships in variables.env.
is_default() {
  case "$(get_env "$1")" in
    ""|plane|access-key|secret-key|change-this-key-on-deployment) return 0 ;;
    *) return 1 ;;
  esac
}

# run_setup ACTION: Plane's setup.sh with no keyboard (at its only prompt, "build the images
# locally?", it reads no answer and exits). Every key that plane.env sets is removed from the
# environment first: Docker Compose prefers a variable from the calling shell over the env file, so
# an exported AWS_SECRET_ACCESS_KEY (say, for another tool) would silently replace MinIO's
# password. TERM=dumb stops setup.sh's "clear" from printing control codes into logs, and the
# image pull of "install" runs without its very long per-layer progress output.
run_setup() {
  local -a clean=()
  local key
  if [ -f "$ENV_FILE" ]; then
    while IFS= read -r key; do clean+=(-u "$key"); done \
      < <(awk -F= '/^[A-Za-z_][A-Za-z0-9_]*=/ { print $1 }' "$ENV_FILE" | sort -u)
  fi
  if [ "$1" = "install" ]; then clean+=(COMPOSE_PROGRESS=quiet); fi
  (cd "$PLANE_DIR" && env "${clean[@]}" TERM=dumb ./setup.sh "$@" </dev/null)
}

# The release tag behind GitHub's "latest" link, for example v1.4.2 (no GitHub API call needed).
latest_release() {
  local url
  url="$(curl -fsS -o /dev/null -w '%{redirect_url}' --retry 3 "$LATEST_SETUP_URL")" || return 1
  url="${url%/setup.sh}"
  printf '%s' "${url##*/}"
}

download_setup_sh() {
  local release="$1"
  [[ "$release" =~ ^v[0-9][0-9A-Za-z.-]*$ ]] || die "unexpected Plane release tag: '$release'"
  log "Downloading Plane's setup.sh ($release)"
  curl -fsSL --retry 3 -o "$PLANE_DIR/setup.sh.download" \
    "https://github.com/makeplane/plane/releases/download/$release/setup.sh"
  chmod 755 "$PLANE_DIR/setup.sh.download"
  mv -f "$PLANE_DIR/setup.sh.download" "$PLANE_DIR/setup.sh"
}

# plane.env holds secrets: owner-only file inside an owner-only folder. The folder mode also covers
# the copies setup.sh makes on upgrade (plane.env.bak, archive/*.env), which it writes as 644.
fix_permissions() {
  local owner group
  owner="$(stat -c %U "$PLANE_DIR")"
  group="$(id -gn "$owner")"
  chown -R "$owner:$group" "$PLANE_DIR"
  chmod 700 "$APP_DIR"
  find "$APP_DIR" -maxdepth 2 -type f \( -name 'plane.env*' -o -name '*.env' \) -exec chmod 600 {} +
}

# ---------------------------------------------------------------------------------------------
# 1. Folder and Plane's setup.sh
# ---------------------------------------------------------------------------------------------
if [ ! -d "$PLANE_DIR" ]; then
  # bootstrap-host.sh normally creates it for the login user; fall back to the sudo user.
  install -d -m 755 -o "${SUDO_USER:-root}" -g "$(id -gn "${SUDO_USER:-root}")" "$PLANE_DIR"
fi
log "Plane folder: $PLANE_DIR (owner $(stat -c %U "$PLANE_DIR"))"

install_complete() {
  [ -s "$COMPOSE_FILE" ] && [ -f "$ENV_FILE" ] && grep -q '^POSTGRES_PASSWORD=' "$ENV_FILE"
}

if install_complete; then
  log "1/5 Plane is already installed ($(get_env APP_RELEASE)); skipping the download"
  if [ ! -x "$PLANE_DIR/setup.sh" ]; then
    release="$(get_env APP_RELEASE)"
    [[ "$release" =~ ^v[0-9] ]] || release="$(latest_release)" || die "cannot find Plane's latest release"
    download_setup_sh "$release"
  fi
else
  log "1/5 Installing Plane with ./setup.sh install"
  if [ -n "$PLANE_RELEASE" ]; then
    release="$PLANE_RELEASE"
  else
    release="$(latest_release)" || die "cannot reach GitHub to find Plane's latest release"
  fi
  download_setup_sh "$release"
  # Seed plane.env with the release tag. setup.sh then installs exactly this release instead of
  # asking GitHub's API (which it calls only when it has no plane.env yet), and carries the value
  # over into the plane.env it writes, as it does on an upgrade.
  install -d -m 700 "$APP_DIR"
  if [ ! -f "$ENV_FILE" ]; then
    (umask 077 && printf 'APP_RELEASE=%s\n' "$release" > "$ENV_FILE")
  fi
  printf 'APP_RELEASE=%s\n' "$release" > "$APP_DIR/.release.tmp"
  apply_env "$APP_DIR/.release.tmp"
  rm -f "$APP_DIR/.release.tmp"
  echo "Pulling Plane's images: several minutes, without progress output."
  run_setup install
  # setup.sh exits 0 even when it gave up (no images for this CPU), so check its result.
  install_complete || die "setup.sh install did not produce plane.env and docker-compose.yaml (see its output above)"
  [ "$(get_env APP_RELEASE)" = "$release" ] || warn "plane.env says APP_RELEASE=$(get_env APP_RELEASE), expected $release"
fi
fix_permissions

# ---------------------------------------------------------------------------------------------
# 2. Secrets: only before Plane's first start
# ---------------------------------------------------------------------------------------------
changes="$(mktemp "$APP_DIR/.changes.XXXXXX")"   # mode 600, inside the 700 folder
trap 'rm -f "$changes"' EXIT
generated=()
first_start=1
if volume_exists pgdata; then first_start=0; fi

if [ "$first_start" -eq 1 ]; then
  log "2/5 Plane has not started yet: replacing every default secret left in plane.env"
  # Random hex values: no characters that need quoting in plane.env, in a URL or in setup.sh,
  # which reads plane.env with "cut -d= -f2" and "source".
  hex() { openssl rand -hex "$1"; }

  # Postgres keeps its first password. DATABASE_URL must carry the same one.
  pg_pass="$(get_env POSTGRES_PASSWORD)"
  if is_default POSTGRES_PASSWORD; then
    pg_pass="$(hex 24)"
    echo "POSTGRES_PASSWORD=$pg_pass" >> "$changes"; generated+=(POSTGRES_PASSWORD)
  fi
  db_url="postgresql://$(get_env POSTGRES_USER):$pg_pass@$(get_env PGHOST):$(get_env POSTGRES_PORT)/$(get_env POSTGRES_DB)"
  if [ "$(get_env DATABASE_URL)" != "$db_url" ]; then
    echo "DATABASE_URL=$db_url" >> "$changes"; generated+=(DATABASE_URL)
  fi

  # RabbitMQ keeps its first password. AMQP_URL must carry the same one.
  mq_pass="$(get_env RABBITMQ_PASSWORD)"
  if is_default RABBITMQ_PASSWORD; then
    mq_pass="$(hex 24)"
    echo "RABBITMQ_PASSWORD=$mq_pass" >> "$changes"; generated+=(RABBITMQ_PASSWORD)
  fi
  amqp_url="amqp://$(get_env RABBITMQ_USER):$mq_pass@$(get_env RABBITMQ_HOST):$(get_env RABBITMQ_PORT)/$(get_env RABBITMQ_VHOST)"
  if [ "$(get_env AMQP_URL)" != "$amqp_url" ]; then
    echo "AMQP_URL=$amqp_url" >> "$changes"; generated+=(AMQP_URL)
  fi

  # MinIO's root user and password (Plane uses them as its S3 key pair). Plane's proxy forwards
  # /uploads/ to MinIO, so the shipped access-key/secret-key pair would be usable from outside.
  # Lengths stay within MinIO's classic limits (user up to 20, password up to 40 characters).
  if is_default AWS_ACCESS_KEY_ID; then
    echo "AWS_ACCESS_KEY_ID=plane$(hex 6)" >> "$changes"; generated+=(AWS_ACCESS_KEY_ID)
  fi
  if is_default AWS_SECRET_ACCESS_KEY; then
    echo "AWS_SECRET_ACCESS_KEY=$(hex 20)" >> "$changes"; generated+=(AWS_SECRET_ACCESS_KEY)
  fi

  # Django's signing key and the live (collaborative editing) server's key.
  for key in SECRET_KEY LIVE_SERVER_SECRET_KEY; do
    if is_default "$key"; then
      echo "$key=$(hex 32)" >> "$changes"; generated+=("$key")
    fi
  done
else
  log "2/5 Plane has started before: keeping every secret in plane.env as it is"
  for key in POSTGRES_PASSWORD RABBITMQ_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY SECRET_KEY LIVE_SERVER_SECRET_KEY; do
    if is_default "$key"; then
      warn "$key still holds Plane's default value. It cannot be changed safely by this script now."
    fi
  done
  if [ -z "$(get_env DATABASE_URL)" ] && ! is_default POSTGRES_PASSWORD; then
    warn "DATABASE_URL is empty, so Plane connects with the default password 'plane', not POSTGRES_PASSWORD."
  fi
fi

# ---------------------------------------------------------------------------------------------
# 3. Address: APP_DOMAIN, WEB_URL, CORS_ALLOWED_ORIGINS, LISTEN_HTTP_PORT
# ---------------------------------------------------------------------------------------------
port="${PORT:-$(get_env LISTEN_HTTP_PORT)}"
port="${port:-80}"
current_domain="$(get_env APP_DOMAIN)"
if [ -n "$HOST" ]; then
  domain="$HOST"; url="https://$HOST"
elif [ -z "$current_domain" ] || [ "$current_domain" = "localhost" ]; then
  # Local default (also what Plane ships): the address a browser uses through an SSH forward
  # that keeps the port number, for example ssh -L 80:localhost:80.
  domain="localhost"; url="http://localhost"
  [ "$port" = "80" ] || url="http://localhost:$port"
else
  # A public name set earlier (by --host or plane-url.sh) stays as it is.
  domain="$current_domain"; url="$(get_env WEB_URL)"
fi
log "3/5 Address: $url (host port $port)"
[ "$current_domain" = "$domain" ] || echo "APP_DOMAIN=$domain" >> "$changes"
[ "$(get_env WEB_URL)" = "$url" ] || echo "WEB_URL=$url" >> "$changes"
[ "$(get_env CORS_ALLOWED_ORIGINS)" = "$url" ] || echo "CORS_ALLOWED_ORIGINS=$url" >> "$changes"
[ "$(get_env LISTEN_HTTP_PORT)" = "$port" ] || echo "LISTEN_HTTP_PORT=$port" >> "$changes"

if [ -s "$changes" ]; then
  apply_env "$changes"
fi
rm -f "$changes"

if [ "$first_start" -eq 1 ] && [ "${#generated[@]}" -eq 0 ]; then
  echo "No default secret left: every secret in plane.env kept as it is."
fi
if [ "${#generated[@]}" -gt 0 ]; then
  echo "Generated: ${generated[*]}"
  if [ ! -e "$ROOT_COPY" ]; then
    install -m 600 -o root -g root "$ENV_FILE" "$ROOT_COPY"
    echo "Saved a root-only copy: $ROOT_COPY"
  fi
fi
fix_permissions

# ---------------------------------------------------------------------------------------------
# 4. Start Plane
# ---------------------------------------------------------------------------------------------
proxy_running="$(docker ps -q --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
  --filter "label=com.docker.compose.service=proxy")"
if [ -z "$proxy_running" ] && command -v ss >/dev/null 2>&1 && [ -n "$(ss -ltnH "( sport = :$port )")" ]; then
  die "host port $port is already in use by another program; pick another with --port"
fi
log "4/5 Starting Plane (./setup.sh start: database migrations, then the API check)"
run_setup start || die "./setup.sh start failed (exit $?). Logs: cd $PLANE_DIR && ./setup.sh logs api"
fix_permissions   # setup.sh may have created files as root

# ---------------------------------------------------------------------------------------------
# 5. Wait until Plane answers on the host port
# ---------------------------------------------------------------------------------------------
log "5/5 Waiting for http://localhost:$port/ (up to ${START_TIMEOUT}s)"
start=$SECONDS
while :; do
  code="$(curl -s -o /dev/null -m 5 -w '%{http_code}' "http://localhost:$port/" || true)"
  case "$code" in 2??|3??) break ;; esac
  elapsed=$((SECONDS - start))
  [ "$elapsed" -lt "$START_TIMEOUT" ] \
    || die "Plane did not answer within ${START_TIMEOUT}s (last HTTP status ${code:-none}). Check: docker ps; cd $PLANE_DIR && ./setup.sh logs proxy"
  printf '  still waiting: HTTP %s after %ss\n' "${code:-none}" "$elapsed"
  sleep 10
done
echo "Plane answers: HTTP $code from http://localhost:$port/"

admin_state="unknown"
if command -v jq >/dev/null 2>&1; then
  admin_state="$(curl -s -m 10 "http://localhost:$port/api/instances/" \
    | jq -r 'if .instance.is_setup_done == true then "claimed" elif .instance.is_setup_done == false then "NOT claimed yet" else "unknown" end' 2>/dev/null || echo unknown)"
fi

cat <<EOF

Plane is running.
  Release:        $(get_env APP_RELEASE)
  Address:        $(get_env WEB_URL)   (host port $(get_env LISTEN_HTTP_PORT))
  Instance admin: $admin_state
  Settings file:  $ENV_FILE
  Root-only copy: $ROOT_COPY

Copy plane.env into your password manager now: a rebuild or restore needs exactly these
passwords. Show it once with:  sudo cat $ENV_FILE
Never paste it into a chat, a commit or the repo.

Next (docs/ops/plane-setup.md, section 5): claim the instance admin at /god-mode/ and turn off
public sign-up before Plane is public.
EOF
