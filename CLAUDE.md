# Settledown

A game in the style of CATAN, written in Kotlin Multiplatform for Android, desktop (JVM) and a Ktor server. It is an
independent fan project, early in development. The rules engine is plain Kotlin in `core/src/commonMain`, built for a
small memory footprint on the JVM. Follow the 6th-edition base-game rulebook exactly. If you have a copy, keep it in
`docs/local/`, which git ignores; it is copyrighted, so never commit it or quote it at length.

Start with [docs/README.md](docs/README.md) for the geometry, board setup, rules engine and serialization formats.

## Modules

- `core`: the game, shared by every target (`jvm()` and `android`). Packages `game` (board graph and layout),
  `game.engine` (rules), `game.codec` (bit-packed formats and replayable records), `game.random` (seeded RNG),
  `utils` (enum-keyed collections).
- `app/shared`: Compose Multiplatform UI. `app/androidApp` and `app/desktopApp` are thin launchers.
- `server`: the Ktor server, which runs the authoritative game.

## Building and testing

The dev machine is a 2-core laptop, so every Gradle run is slow. Run the narrowest task that checks the change.

| Change in | Run |
|---|---|
| `core` (inner loop) | `./gradlew :core:jvmTest`, or one class with `--tests '*BoardLayoutTest'` |
| `core`, before committing | `./gradlew :core:allTests` |
| `app/shared` | `./gradlew :app:shared:jvmTest :app:shared:testAndroidHostTest` |
| `server` | `./gradlew :server:test` |
| Android build | `./gradlew :app:androidApp:assembleDebug` |

- Never start an Android emulator or a Gradle Managed Device task on this machine; they belong in CI. Run instrumented
  tests on the USB-connected phone (`connectedDebugAndroidTest`) and ask before installing anything onto it.
- To check a file you edited, prefer the Android Studio MCP server's `get_file_problems` over a Gradle build: the IDE
  has already analyzed it. It serves the project open in Android Studio (the main checkout), so in a worktree its
  results describe `main`, not your branch.
- Don't add throwaway probes or dump tests to the source tree; use the scratchpad.

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
- The user pushes. Don't push or open PRs unless asked.
