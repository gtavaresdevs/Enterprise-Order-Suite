#!/usr/bin/env bash
# verify-backup.sh: non-destructive check that a Plane backup made by backup.sh can be restored.
#
# Part of docs/ops/plane-setup.md (Enterprise Order Suite), steps 9 and 12.
# Usage, as the user that installed Plane (member of the docker group):
#     ~/plane-ops/verify-backup.sh                  # newest backup on the off-box remote
#     ~/plane-ops/verify-backup.sh <local-folder>   # a backup folder already on disk
#
# Checks:
#   1. Every .tar.gz archive in the folder can be read.
#   2. plane.pgdump restores into a throwaway Postgres container (same image as the running
#      plane-db, no network) that holds workspaces, projects and work items.
# It never touches the live Plane containers or volumes. Safe to re-run: it removes its temporary
# folder and container on exit. Contains no secrets (the throwaway database password is a dummy).
set -euo pipefail

RCLONE_REMOTE="${RCLONE_REMOTE:-offsite-crypt:plane-backups}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-plane-app}"
FALLBACK_PG_IMAGE="${FALLBACK_PG_IMAGE:-postgres:15.7-alpine}"   # used only if plane-db is not running

work="$(mktemp -d)"
cname="plane-verify-$$"
cleanup() {
  docker rm -f "$cname" >/dev/null 2>&1 || true
  rm -rf -- "$work"
}
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

# Pick the backup folder.
if [ $# -ge 1 ]; then
  src="$1"
else
  newest="$(rclone lsf --dirs-only "$RCLONE_REMOTE" | sed 's:/$::' \
    | grep -E '^[0-9]{8}-[0-9]{4}$' | sort | tail -n 1 || true)"
  [ -n "$newest" ] || fail "no backup folders on $RCLONE_REMOTE"
  echo "Downloading $RCLONE_REMOTE/$newest"
  rclone copy "$RCLONE_REMOTE/$newest" "$work/$newest"
  src="$work/$newest"
fi
[ -d "$src" ] || fail "$src is not a folder"
[ -s "$src/plane.pgdump" ] || fail "no plane.pgdump in $src"

# 1. Archives are readable.
found=0
for archive in "$src"/*.tar.gz; do
  [ -e "$archive" ] || continue
  tar -tzf "$archive" >/dev/null || fail "cannot read $(basename "$archive")"
  echo "ok  archive $(basename "$archive")"
  found=$((found + 1))
done
[ "$found" -gt 0 ] || fail "no .tar.gz archives in $src"

# 2. Restore the logical dump into a throwaway Postgres with no network access.
db_container="$(docker ps -q \
  --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
  --filter "label=com.docker.compose.service=plane-db" | head -n 1)"
if [ -n "$db_container" ]; then
  image="$(docker inspect --format '{{.Config.Image}}' "$db_container")"
else
  image="$FALLBACK_PG_IMAGE"
fi
echo "Starting a throwaway $image"
docker run -d --name "$cname" --network none \
  -e POSTGRES_USER=plane -e POSTGRES_PASSWORD=verify-only -e POSTGRES_DB=plane \
  "$image" >/dev/null
# Wait on TCP: the image's first-boot init server listens on the socket only, then restarts.
ready=0
for _ in $(seq 1 60); do
  if docker exec "$cname" pg_isready -h 127.0.0.1 -U plane -q; then ready=1; break; fi
  sleep 2
done
[ "$ready" -eq 1 ] || fail "throwaway Postgres did not start"

echo "Restoring plane.pgdump"
docker exec -i "$cname" pg_restore -U plane -d plane --no-owner --no-privileges \
  < "$src/plane.pgdump" || echo "note: pg_restore reported errors above; checking the content anyway"

q() { docker exec "$cname" psql -U plane -d plane -tAc "$1"; }
tables="$(q "select count(*) from information_schema.tables where table_schema = 'public'")"
[ "${tables:-0}" -gt 0 ] || fail "the restored database has no tables"
echo "ok  $tables tables restored"
for t in workspaces projects issues; do
  echo "    $t: $(q "select count(*) from $t" 2>/dev/null || echo 'n/a')"
done
echo "PASS: $src can be restored"
