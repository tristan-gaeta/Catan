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
- `server`: a Ktor skeleton; the authoritative game isn't built yet.

## Building and testing

The run and test commands for each module are in the [README](../README.md). Test conventions are in
[rules/testing.md](rules/testing.md), and the narrowest task for each change is in [rules/builds.md](rules/builds.md).
Machine-specific guidance, if any, lives in a gitignored `rules/*.local.md` file.

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
- Work on `claude/*` branches. Once the work is ready, use the `ship` skill to review, commit, push without asking, open
  a PR and wait for CI.
- Merge only when the user asks. Pushing to `main`, force-pushing and rewriting pushed history need the user's approval.
