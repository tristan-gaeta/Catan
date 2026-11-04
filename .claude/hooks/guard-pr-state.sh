#!/usr/bin/env bash
# PreToolUse hook on `git push`: blocks a push to a branch whose latest PR has merged or closed, because the commit
# would never reach main. Any error lets the push through, since this is a convenience and not a security boundary.
set -u

# Prints the working directory, then the branch being pushed (empty when the command names none).
parsed=$(python3 -I -c '
import json, shlex, sys

data = json.load(sys.stdin)
print(data.get("cwd", ""))
try:
    tokens = shlex.split(data.get("tool_input", {}).get("command", ""))
except ValueError:
    tokens = []
branch = ""
for i in range(len(tokens) - 1):
    if tokens[i:i + 2] == ["git", "push"]:
        args = []
        for token in tokens[i + 2:]:
            if token in ("&&", "||", ";", "|"):
                break
            args.append(token)
        if "--delete" in args or "-d" in args:
            sys.exit(3)
        positional = [arg for arg in args if not arg.startswith("-")]
        if len(positional) > 1:
            # A refspec with an empty source, such as :branch, deletes the remote branch.
            if positional[1].startswith(":"):
                sys.exit(3)
            branch = positional[1].rsplit(":", 1)[-1]
        break
print(branch)
') || exit 0

cwd=$(printf '%s\n' "$parsed" | sed -n 1p)
branch=$(printf '%s\n' "$parsed" | sed -n 2p)
cd "$cwd" 2>/dev/null || exit 0
case "$branch" in
  "" | HEAD) branch=$(git branch --show-current) ;;
esac
[ -n "$branch" ] || exit 0

state=$(gh pr list --head "$branch" --state all --limit 1 --json number,state -q '.[0] | "\(.number) \(.state)"' 2>/dev/null) || exit 0
case "$state" in
  *MERGED | *CLOSED)
    echo "PR #${state% *} from $branch is ${state#* }. Put this commit on a new branch from origin/main and open a new PR." >&2
    exit 2
    ;;
esac
exit 0
