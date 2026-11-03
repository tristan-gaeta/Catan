---
paths:
  - "**/src/*Test/**/*.kt"
  - "server/src/test/**/*.kt"
---

# Testing

## Test names

Split a test name into parts with underscores, and write each part in camelCase:
`subject_conditionOrAction_expectedResult`. Use two parts when there is no separate condition.

| Instead of | Write |
|---|---|
| `emptySetCopiesDoNotThrow` | `emptySetCopy_doesNotThrow` |
| `corruptActionsAreRejected` | `corruptAction_isRejected` |
| `setIteratesInOrdinalOrderAndPrintsLikeKotlin` | `enumSet_iterate_visitsInOrdinalOrder` and `enumSet_toString_matchesKotlinSet` |

- Don't use backtick names with spaces: the Android runtime doesn't support them, and `core` runs on Android.
- A name that needs "And" is usually two tests.
- Name new tests this way. Rename an existing test only when you are already changing it.

## Writing tests

- Rules and codec tests live in `core/src/commonTest`, next to the package they cover. Set up exact positions with
  the helpers in `GameFixture.kt` (such as `newGame`), and use `RandomBots` for random play.
- Use fixed seeds so a failure replays exactly.
- When a deliberate change trips `VersionGuardTest`, bump the version and add the new fingerprint under the new
  version. Keep the old entries; never edit a fingerprint in place.
- Keep throwaway probes and dump tests out of the source tree.
