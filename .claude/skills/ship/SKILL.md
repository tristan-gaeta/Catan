---
name: ship
description: Commit finished work on a claude/* branch, review it, push it, open a PR and hand CI to Auto-fix. Also covers CI failures and merging. Use when the work is ready to commit or the user asks to ship or merge it.
allowed-tools: Bash(git status *) Bash(git log *) Bash(gh pr view *) Bash(echo *)
---

## Current state

Branch and changes:
!`git status --short --branch`

Commits ahead of `origin/main`:
!`git log --oneline origin/main..HEAD`

Pull request:
!`gh pr view --json number,state,url,headRefOid 2>/dev/null || echo "None for this branch"`

## Commit and push

1. Work on a `claude/*` branch. If you are on `main`, run `git fetch origin main` and create one from `origin/main`.
2. Run the narrowest tests that cover the change (see `rules/builds.md`). Skip them for docs-only changes.
3. For a non-trivial change, have the `reviewer` subagent review the diff, then fix what it confirms. Changes to only
   docs or comments, and one-line fixes, don't need it. Pass `model: opus` when the change touches `game/engine` or
   `game/codec`.
4. Commit, then name the branch when pushing: `git push -u origin <branch>`. A hook blocks the push if the branch's PR
   has merged or closed. If it does, put the commit on a new branch from `origin/main` and open a new PR.
5. Open a PR with `gh pr create` if the branch has none.

## Wait for CI

Waiting must not spend tokens, so never poll with `/loop`, `sleep` loops or repeated `gh pr checks` calls.

- In the desktop app, turn on Auto-fix for a PR this session opened, then end your turn. The app wakes the session on
  CI failures, merge conflicts and review comments.
- Elsewhere, run `gh pr checks <number> --watch --fail-fast` as a background command, then end your turn.

## When CI fails

1. Run `.claude/scripts/ci-digest.sh <number>`. Open full logs only if the digest doesn't explain the failure.
2. If it looks like an infrastructure flake, such as an emulator that failed to boot, run `gh run rerun <run-id> --failed`
   once.
3. Otherwise fix the cause and push. If the second attempt also fails, report it to the user.

## Merge

Merge only when the user asks: `gh pr merge --merge --match-head-commit <sha>`. Add `--auto` if a required check is
pending. Desktop's "Merge when ready" requests a squash merge, so don't use it.
