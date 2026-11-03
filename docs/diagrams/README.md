# Diagrams

`generate.py` draws every SVG in [`../images`](../images) from `geometry.json`. It needs only the Python 3 standard
library:

```bash
python3 docs/diagrams/generate.py
```

`geometry.json` is dumped from the real enums, so the vertex and edge numbers in the pictures are the ordinals the
code uses. It holds:

* every hex's coordinates, corners and sides;
* the six frame sides;
* the six number spirals;
* `BoardLayout.generate(42)` as a sample board, and `BoardLayout.fixedSetup`.

Regenerate it whenever the board geometry or the board generator changes. Both changes also trip `VersionGuardTest`.

1. Copy `GeometryDump.kt` into `core/src/jvmTest/kotlin/com/tgaetaprojects/biophilia/game/`. It isn't compiled where
   it is.
2. Run it:

   ```bash
   ./gradlew :core:jvmTest --tests 'com.tgaetaprojects.biophilia.game.GeometryDump'
   ```

3. Delete the copy, then run `generate.py`.

The diagrams use a white background so they stay readable on GitHub's dark theme and in IDE documentation popups.
