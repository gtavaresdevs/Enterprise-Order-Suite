#!/usr/bin/env bash
# SUPERSEDED 2026-10-06: the project uses Plane Cloud's free plan (ADR-0017), so this script is not run.
# Kept in case Plane is ever self-hosted. See README.md in this folder and ../plane-setup.md.
# plane-url.sh: point an installed Plane at its public HTTPS name and restart it.
#
# Part of docs/ops/plane-setup.md (Enterprise Order Suite), section 6. Run it as root once you know
# the name Plane will be reached at, for example the Tailscale name:
#     tailscale status --json | jq -r .Self.DNSName        # plane.tail1234.ts.net.
#     sudo ./plane-url.sh plane.tail1234.ts.net
#
#   <host>      The host name only: no https://, no path. A trailing dot is dropped and upper case is
#               lowered. An explicit port is allowed (plane.example.com:8443).
#   PLANE_DIR   Folder that holds setup.sh and plane-app/ (default /opt/plane-selfhost).
#   START_TIMEOUT  Seconds to wait for Plane to answer after the restart (default 600).
#   PLANE_URL_SKIP_CHECK=1  Skips the safety check below. Only for a throwaway test instance.
#
# Safety check first: it refuses to run until the instance admin is claimed and public sign-up is
# off (read from Plane's own /api/instances/ on this machine), because the next step publishes Plane.
#
# It sets, in plane-app/plane.env:
#     APP_DOMAIN=<host>   WEB_URL=https://<host>   CORS_ALLOWED_ORIGINS=https://<host>
# then restarts Plane (./setup.sh restart) and waits until it answers on its host port again.
# Plane builds links, redirects, upload URLs and cookie and CSRF rules from these values, so they
# must match the address in the browser.
#
# Safe to re-run: when the values are already set it changes nothing and does not restart.
# Never touches a password or secret, contains none and prints none.
set -euo pipefail

PLANE_DIR="${PLANE_DIR:-/opt/plane-selfhost}"
START_TIMEOUT="${START_TIMEOUT:-600}"
APP_DIR="$PLANE_DIR/plane-app"
ENV_FILE="$APP_DIR/plane.env"
LOCK_FILE="/run/lock/plane-env.lock"      # shared with install-plane.sh

log() { printf '\n==> %s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
usage() { sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

# ---------------------------------------------------------------------------------------------
# Argument: the host name
# ---------------------------------------------------------------------------------------------
case "${1:-}" in
  -h|--help) usage 0 ;;
esac
[ $# -eq 1 ] || usage 1
# An empty name usually means "tailscale status" had no name yet (Tailscale not logged in).
[ -n "$1" ] || die "the host name is empty. If it came from 'tailscale status', is Tailscale logged in? (tailscale status)"
host="$1"
case "$host" in
  *://*) die "give the host name only, without a scheme (plane.tail1234.ts.net, not https://...)" ;;
  */*) die "give the host name only, without a path" ;;
esac
host="${host%.}"
host="$(printf '%s' "$host" | tr '[:upper:]' '[:lower:]')"
[ "${#host}" -le 253 ] || die "host name too long: $host"
[[ "$host" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)*[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(:[0-9]{1,5})?$ ]] \
  || die "not a valid host name: $host"
if [[ "$host" == *:* ]]; then
  p="${host##*:}"
  [ "$p" -ge 1 ] && [ "$p" -le 65535 ] || die "port out of range: $p"
fi
[[ "$START_TIMEOUT" =~ ^[0-9]+$ ]] || die "START_TIMEOUT must be a number of seconds"
url="https://$host"

# ---------------------------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------------------------
[ "$(id -u)" -eq 0 ] || die "run with sudo: sudo ./plane-url.sh $host"
[ -x "$PLANE_DIR/setup.sh" ] || die "no setup.sh in $PLANE_DIR: install Plane first (install-plane.sh)"
if [ ! -f "$ENV_FILE" ] || ! grep -q '^POSTGRES_PASSWORD=' "$ENV_FILE"; then
  die "no complete plane.env in $APP_DIR: install Plane first (install-plane.sh)"
fi
docker info >/dev/null 2>&1 || die "the Docker daemon is not running (systemctl start docker)"

mkdir -p "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
flock -n 9 || die "another install-plane.sh or plane-url.sh is running ($LOCK_FILE)"

# get_env KEY: the value of the last KEY= line in plane.env, or nothing.
get_env() {
  awk -v k="$1" 'index($0, k "=") == 1 { v = substr($0, length(k) + 2) } END { print v }' "$ENV_FILE"
}
port="$(get_env LISTEN_HTTP_PORT)"
port="${port:-80}"

# ---------------------------------------------------------------------------------------------
# Safety check: publish only a claimed instance with public sign-up off. Whoever completes
# /god-mode/ first becomes the instance admin, and with sign-up on anyone can create an account.
# ---------------------------------------------------------------------------------------------
if [ "${PLANE_URL_SKIP_CHECK:-0}" != "1" ]; then
  command -v jq >/dev/null 2>&1 || die "jq is missing (bootstrap-host.sh installs it)"
  state="$(curl -s -m 10 "http://localhost:$port/api/instances/" \
    | jq -r '"\(.instance.is_setup_done) \(.config.enable_signup)"' 2>/dev/null || true)"
  case "$state" in
    "true false") echo "Check passed: the instance admin is claimed and public sign-up is off." ;;
    "true true") die "public sign-up is still on. In God mode > Authentication, turn off \"Allow anyone to sign up even without an invite\", then run this again." ;;
    "false "*|"null "*) die "the instance admin is not claimed yet. Claim it at /god-mode/ through the SSH port forward (docs/ops/plane-setup.md), turn off public sign-up, then run this again." ;;
    *) die "cannot read Plane's state from http://localhost:$port/api/instances/ (got '${state:-nothing}'). Is Plane running? (docker ps)" ;;
  esac
fi

# ---------------------------------------------------------------------------------------------
# Update plane.env (one atomic rewrite that keeps the file's owner and mode)
# ---------------------------------------------------------------------------------------------
if [ "$(get_env APP_DOMAIN)" = "$host" ] && [ "$(get_env WEB_URL)" = "$url" ] \
   && [ "$(get_env CORS_ALLOWED_ORIGINS)" = "$url" ]; then
  echo "Already set: WEB_URL=$url. Nothing to change."
  exit 0
fi

log "Setting APP_DOMAIN=$host, WEB_URL=$url, CORS_ALLOWED_ORIGINS=$url"
echo "Before: WEB_URL=$(get_env WEB_URL)"
tmp="$(mktemp "$ENV_FILE.XXXXXX")"
trap 'rm -f "$tmp"' EXIT
H="$host" U="$url" awk '
  BEGIN { v["APP_DOMAIN"] = ENVIRON["H"]; v["WEB_URL"] = ENVIRON["U"]; v["CORS_ALLOWED_ORIGINS"] = ENVIRON["U"] }
  {
    i = index($0, "=")
    if (i > 1 && substr($0, 1, 1) != "#") {
      k = substr($0, 1, i - 1)
      if (k in v) { if (!(k in done)) { print k "=" v[k]; done[k] = 1 }; next }
    }
    print
  }
  END { for (k in v) if (!(k in done)) print k "=" v[k] }
' "$ENV_FILE" > "$tmp"
chmod --reference="$ENV_FILE" "$tmp"
chown --reference="$ENV_FILE" "$tmp"
mv -f "$tmp" "$ENV_FILE"

# ---------------------------------------------------------------------------------------------
# Restart and wait
# ---------------------------------------------------------------------------------------------
log "Restarting Plane (./setup.sh restart)"
# Remove every plane.env key from the environment first: Docker Compose prefers a variable from
# the calling shell over the env file (an exported AWS_SECRET_ACCESS_KEY would replace MinIO's).
clean=()
while IFS= read -r key; do clean+=(-u "$key"); done \
  < <(awk -F= '/^[A-Za-z_][A-Za-z0-9_]*=/ { print $1 }' "$ENV_FILE" | sort -u)
(cd "$PLANE_DIR" && env "${clean[@]}" TERM=dumb ./setup.sh restart </dev/null) \
  || die "./setup.sh restart failed (exit $?). Logs: cd $PLANE_DIR && ./setup.sh logs api"
# setup.sh may have created files as root; give them back to the folder's owner.
owner="$(stat -c %U "$PLANE_DIR")"
chown -R "$owner:$(id -gn "$owner")" "$PLANE_DIR"

log "Waiting for http://localhost:$port/ (up to ${START_TIMEOUT}s)"
start=$SECONDS
while :; do
  code="$(curl -s -o /dev/null -m 5 -w '%{http_code}' "http://localhost:$port/" || true)"
  case "$code" in 2??|3??) break ;; esac
  elapsed=$((SECONDS - start))
  [ "$elapsed" -lt "$START_TIMEOUT" ] \
    || die "Plane did not answer within ${START_TIMEOUT}s (last HTTP status ${code:-none})"
  printf '  still waiting: HTTP %s after %ss\n' "${code:-none}" "$elapsed"
  sleep 10
done

cat <<EOF

Plane now expects to be opened at $url/
It still listens on host port $port; HTTPS comes from what you put in front of it, for example:
  sudo tailscale serve --bg $port     (your tailnet only)
  sudo tailscale funnel --bg $port    (public; only after the admin is claimed and sign-up is off)
Sign in again at $url/god-mode/ : sessions made under the old address do not carry over.
EOF
