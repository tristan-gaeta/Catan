#!/usr/bin/env bash
# Prints a short summary of a CI run, so Claude can diagnose a failure without reading the full log.
# Usage: ci-digest.sh [run-id | pr-number | branch]. With no argument, uses the current branch's latest run.
set -u

MAX_LINES=40
NOT_PASSED='select(.conclusion != "success" and .conclusion != "skipped")'
arg=${1:-}

latest_run() {
  run=$(gh run list --branch "$1" --limit 1 --json databaseId -q '.[0].databaseId') || {
    echo "gh failed to list runs for $1." >&2
    exit 1
  }
}

case "$arg" in
  "") latest_run "$(git branch --show-current)" ;;
  *[!0-9]*) latest_run "$arg" ;;
  *)
    # PR numbers are short and run IDs are long.
    if [ "${#arg}" -ge 6 ]; then
      run=$arg
    else
      branch=$(gh pr view "$arg" --json headRefName -q .headRefName) || exit 1
      latest_run "$branch"
    fi
    ;;
esac

if [ -z "$run" ]; then
  echo "No CI run found." >&2
  exit 1
fi

status=$(gh run view "$run" --json status -q .status) || exit 1
gh run view "$run" --json url,conclusion,displayTitle,jobs --jq "
  \"\(.displayTitle)\n\(.url)\nRun ID: $run\nStatus: $status \(.conclusion)\",
  (.jobs[] | $NOT_PASSED | select(.conclusion != \"\") |
    \"Not passed: \(.name) (\(.conclusion)), step: \([.steps[] | $NOT_PASSED | select(.conclusion != \"\") | .name] | join(\", \"))\")
" || exit 1

if [ "$status" != completed ]; then
  echo "The run hasn't finished, so it has no failure log yet."
  exit 0
fi

# Strip the job, step and timestamp columns, color codes and carriage returns, then keep only what explains the failure.
# LC_ALL=C stops BSD sed rejecting non-UTF-8 bytes in the log.
gh run view "$run" --log-failed 2>/dev/null |
  LC_ALL=C sed -E -e 's/^[^	]*	[^	]*	[0-9T:.Z-]+ //' -e "s/$(printf '\033')\[[0-9;]*m//g" -e "s/$(printf '\r')$//" |
  awk '
    /^\* What went wrong:/ { section = 1 }
    /^\* Try:/ { section = 0 }
    / FAILED$/ { after = 4 }
    section || after > 0 || /^e: / || /AssertionError/ || /tests? completed,/ || /^##\[error\]/ {
      if (NF > 0) print
    }
    after > 0 { after-- }
  ' |
  head -n "$MAX_LINES"

if gh run view "$run" --json jobs --jq ".jobs[] | $NOT_PASSED | .name" | grep -q instrumented; then
  echo "Device test results: gh run download $run -n android-test-results"
fi
