# Settledown

A game in the style of CATAN, written in Kotlin Multiplatform for Android, desktop (JVM) and a Ktor server. It is an
independent fan project, early in development. The rules engine is plain Kotlin in `core/src/commonMain`, built for a
small memory footprint on the JVM. Follow the 6th-edition base-game rulebook exactly. If you have a copy, keep it in
`docs/local/`, which git ignores; it is copyrighted, so never commit it or quote it at length.

Start with [docs/README.md](../docs/README.md) for the geometry, board setup, rules engine and serialization formats.

## Modules

- `core`: the game, shared by every target (`jvm()` and `android`). Packages `game` (board graph and layout),
  `game.engine` (rules), `game.codec` (bit-packed formats and replayable records), `game.random` (seeded RNG),
  `utils` (enum-keyed collections).
- `app/shared`: Compose Multiplatform UI. `app/androidApp` and `app/desktopApp` are thin launchers.
- `server`: the Ktor server, which runs the authoritative game.

## Building and testing

The run and test commands for each module are in the [README](../README.md). Test conventions are in
[rules/testing.md](rules/testing.md). Machine-specific build guidance, if any, lives in a gitignored
`rules/*.local.md` file.

## Conventions

- **Serialized enums are the wire format.** Enums are written as their ordinal, so reordering, adding or removing a
  constant in a serialized enum (listed in the KDoc of `FORMAT_VERSION` in `game/codec/Bits.kt`) is a format change.
  Bump `FORMAT_VERSION` for any change to what a codec writes, and `Game.RULES_VERSION` for any change to how a seeded
  game unfolds (board generation, rules, or the order of random draws). `VersionGuardTest` fails until you do.
- Write enums with `.ordinal` and decode with exhaustive `when`s; don't add hand-maintained code tables.
- Keep the rules engine free of floating point where integer geometry works.
- KDoc links to diagrams and docs are absolute links to `https://github.com/tristan-gaeta/Catan/blob/main/...`.
  Android Studio's quick documentation can't show images or follow relative links.
- Use American spelling (harbor, behavior, color) in code, identifiers, KDoc and docs.
- Diagrams in `docs/images` are generated from the real enums; see `docs/diagrams/README.md` to redraw them rather
  than editing the SVGs by hand.

## Git

- Commit subjects are imperative and capitalized ("Add seeded serialization and 6th-edition setup"). The body explains
  what changed and why, wrapped at about 76 columns.
- Before committing a non-trivial change, have the `reviewer` subagent review the diff, then fix what it confirms.
  Changes to only docs or comments, and one-line fixes, don't need it.
- Work on `claude/*` branches. On those branches, push and open a PR without asking once the work is committed. Name
  the branch when pushing (`git push -u origin <branch>`).
- Before pushing to a branch that has a PR, check the PR is still open (`gh pr view --json state`). If it has merged
  or closed, put the commit on a new branch from `origin/main` and open a new PR.
- Merge only when the user asks, with `gh pr merge --merge --match-head-commit <sha>`. Add `--auto` if a required check
  is pending. Never use `--admin`.
- Auto-merge only waits for required checks, currently just `host-tests`. Until `instrumented-tests` is required, don't
  merge or enable auto-merge on a PR until that job has passed or been skipped. Rerun it once if it looks like a flake,
  and report it if it still fails.
- Pushing to `main`, force-pushing and rewriting pushed history need the user's approval.
