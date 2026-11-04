#!/usr/bin/env bash
# SessionStart hook: warns when a session starts on a stale main, because its instructions load from that old checkout.
# Stays silent otherwise, so it normally adds nothing to the context. It warns rather than pulling, as main is the user's.
set -u

cwd=$(python3 -I -c 'import json, sys; print(json.load(sys.stdin).get("cwd", ""))') || exit 0
cd "$cwd" 2>/dev/null || exit 0
[ "$(git branch --show-current)" = main ] || exit 0
git fetch --quiet origin main 2>/dev/null || exit 0

behind=$(git rev-list --count HEAD..origin/main) || exit 0
if [ "$behind" -gt 0 ]; then
  echo "Local main is $behind commit(s) behind origin/main, so the project instructions loaded here may be out of date. Base new work on origin/main, and tell the user they can run git pull --ff-only."
fi
exit 0
