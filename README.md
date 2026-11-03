## Settledown

Settledown is a game in the style of CATAN, written in Kotlin Multiplatform for Android, desktop (JVM) and a Ktor
server. The rules engine is Kotlin-first common code, built for a small memory footprint on the JVM.

![A generated board](docs/images/sample-board.svg)

**Documentation:** start at [docs/README.md](docs/README.md). It covers the hex geometry, board setup, rules engine and
serialization formats, with diagrams and references.

*Settledown is an independent fan project. It is not affiliated with or endorsed by CATAN GmbH. CATAN is a trademark of
CATAN GmbH.*

### Modules

* [/core](./core/src/commonMain/kotlin) holds the game itself, shared by every target:
    - `game`: the board graph (`Hex`, `Vertex`, `Edge`) and layout generation;
    - `game.engine`: the rules engine (`Game`, `Action`, `Phase`, `PlayerView`);
    - `game.codec`: compact binary encodings and replayable game records;
    - `game.random`: the portable, seeded random number generator;
    - `utils`: enum-keyed collections.
* [/app/shared](./app/shared/src) holds the Compose Multiplatform UI shared by the apps:
    - [commonMain](./app/shared/src/commonMain/kotlin) for every target;
    - [jvmMain](./app/shared/src/jvmMain/kotlin) and the other platform folders for platform-specific code.
* [/server](./server/src/main/kotlin) is the Ktor server, which runs the authoritative game.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Android app: `./gradlew :app:androidApp:assembleDebug`
- Desktop app:
    - Hot reload: `./gradlew :app:desktopApp:hotRun --auto`
    - Standard run: `./gradlew :app:desktopApp:run`
- Server: `./gradlew :server:run`

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Rules engine and codec tests (JVM and Android host): `./gradlew :core:allTests`
- Android tests: `./gradlew :app:shared:testAndroidHostTest`
- Desktop tests: `./gradlew :app:shared:jvmTest`
- Server tests: `./gradlew :server:test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…