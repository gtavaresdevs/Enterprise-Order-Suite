#!/usr/bin/env bash
# backup.sh: nightly backup of a Plane Community Edition install, copied off the server.
#
# Part of docs/ops/plane-setup.md (Enterprise Order Suite), step 9.
# Run as the user that installed Plane (member of the docker group), by hand or from cron:
#     ~/plane-ops/backup.sh
#
# What one run does:
#   1. Runs Plane's own backup (./setup.sh backup). It writes pgdata, uploads, rabbitmq_data and
#      redisdata archives (*.tar.gz) to plane-app/backup/<YYYYMMDD-HHMM>/, the format Plane's
#      restore.sh reads. setup.sh copies the live data directories with docker cp while Plane runs,
#      so pgdata.tar.gz is not guaranteed consistent and is kept only as a fallback.
#   2. Adds a logical Postgres dump (plane.pgdump) to the same folder. It is consistent while Plane
#      runs, can be restored on another CPU architecture, and is what restores the database (restore
#      drill in step 9.3) and what verify-backup.sh checks.
#   3. Copies the folder to an rclone crypt remote that is not on this server (RCLONE_REMOTE), so it
#      leaves the server encrypted, and checks the copy with rclone cryptcheck.
#   4. Keeps the newest KEEP_LOCAL folders on the server and deletes remote copies older than
#      KEEP_REMOTE_DAYS days.
#   5. Pings HEALTHCHECK_URL (optional) only when every step above succeeded.
#
# Safe to re-run: each run adds one dated folder, and pruning keeps the total bounded.
# Contains no secrets: the remote's credentials live in rclone's own config
# (~/.config/rclone/rclone.conf), and Plane's passwords stay in plane-app/plane.env.
set -euo pipefail

PLANE_DIR="${PLANE_DIR:-/opt/plane-selfhost}"          # folder that holds setup.sh
APP_DIR="$PLANE_DIR/plane-app"                         # created by setup.sh (Install)
BACKUP_ROOT="$APP_DIR/backup"                          # where ./setup.sh backup writes
COMPOSE_PROJECT="${COMPOSE_PROJECT:-plane-app}"        # Docker Compose project name of the install
RCLONE_REMOTE="${RCLONE_REMOTE:-offsite-crypt:plane-backups}"   # must be a crypt remote
KEEP_LOCAL="${KEEP_LOCAL:-7}"
KEEP_REMOTE_DAYS="${KEEP_REMOTE_DAYS:-30}"
HEALTHCHECK_URL="${HEALTHCHECK_URL:-}"

log() { printf '%s %s\n' "$(date -Is)" "$*"; }
fail() { log "ERROR: $*"; exit 1; }

# Only one run at a time.
exec 9>/tmp/plane-backup.lock
flock -n 9 || fail "another backup run holds /tmp/plane-backup.lock"

[ -x "$PLANE_DIR/setup.sh" ] || fail "setup.sh not found in $PLANE_DIR"
[ -f "$APP_DIR/plane.env" ] || fail "plane.env not found in $APP_DIR"
command -v rclone >/dev/null 2>&1 || fail "rclone is not installed"
rclone mkdir "$RCLONE_REMOTE" || fail "cannot reach the remote $RCLONE_REMOTE (run 'rclone config')"

# Dated backup folder names (YYYYMMDD-HHMM), oldest first (glob order).
dated_folders() {
  local d
  for d in "$BACKUP_ROOT"/[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]; do
    if [ -d "$d" ]; then basename "$d"; fi
  done
  return 0
}

# Newest dated backup folder name, or nothing.
latest_folder() { dated_folders | tail -n 1; }

# A plane.env value without quotes, or nothing.
env_value() {
  grep -E "^$1=" "$APP_DIR/plane.env" | tail -n 1 | cut -d= -f2- | tr -d "\"'" || true
}

# 1. Plane's own backup.
before="$(latest_folder)"
log "Running Plane's backup (./setup.sh backup)"
(cd "$PLANE_DIR" && ./setup.sh backup </dev/null)
after="$(latest_folder)"
if [ -z "$after" ] || [ "$after" = "$before" ]; then
  fail "setup.sh backup made no new folder in $BACKUP_ROOT (two runs in the same minute? wait and re-run)"
fi
NEW="$BACKUP_ROOT/$after"
ls "$NEW"/*.tar.gz >/dev/null 2>&1 || fail "no .tar.gz archives in $NEW"
log "Plane backup folder: $NEW"

# 2. Logical Postgres dump: the consistent copy (setup.sh copied the data directories while Plane ran).
db_container="$(docker ps -q \
  --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
  --filter "label=com.docker.compose.service=plane-db" | head -n 1)"
[ -n "$db_container" ] || fail "the plane-db container is not running"
pg_user="$(env_value POSTGRES_USER)"; pg_user="${pg_user:-plane}"
pg_db="$(env_value POSTGRES_DB)"; pg_db="${pg_db:-plane}"
for _ in $(seq 1 30); do
  docker exec "$db_container" pg_isready -U "$pg_user" -q && break
  sleep 2
done
log "Dumping database $pg_db"
docker exec "$db_container" pg_dump -U "$pg_user" -d "$pg_db" -Fc > "$NEW/plane.pgdump.partial"
mv "$NEW/plane.pgdump.partial" "$NEW/plane.pgdump"
[ -s "$NEW/plane.pgdump" ] || fail "plane.pgdump is empty"

# 3. Off-box copy, then compare it with the local folder. A crypt remote has no hashes of its own,
#    so cryptcheck compares the local files with the encrypted copies.
log "Copying to $RCLONE_REMOTE/$after"
rclone copy "$NEW" "$RCLONE_REMOTE/$after"
rclone cryptcheck "$NEW" "$RCLONE_REMOTE/$after" --one-way || fail "the remote copy differs from $NEW"

# 4. Retention: newest KEEP_LOCAL folders here, KEEP_REMOTE_DAYS days on the remote.
dated_folders | head -n -"$KEEP_LOCAL" \
  | while read -r old; do
      log "Removing local $old"
      rm -rf -- "${BACKUP_ROOT:?}/$old"
    done
rclone delete "$RCLONE_REMOTE" --min-age "${KEEP_REMOTE_DAYS}d"
rclone rmdirs "$RCLONE_REMOTE" --leave-root

# 5. Optional success ping (for example a free healthchecks.io check that emails you when it goes quiet).
if [ -n "$HEALTHCHECK_URL" ]; then
  curl -fsS -m 10 --retry 3 "$HEALTHCHECK_URL" >/dev/null || log "WARNING: healthcheck ping failed"
fi
log "Backup OK: $after"
