---
paths:
  - "**/*.kt"
  - "**/*.kts"
  - "gradle/**"
---

# Builds

Run the narrowest task that checks the change:

| Change in | Run |
|---|---|
| `core` (inner loop) | `./gradlew :core:jvmTest`, or one class with `--tests '*BoardLayoutTest'` |
| `core`, before committing | `./gradlew :core:allTests` |
| `app/shared` | `./gradlew :app:shared:jvmTest :app:shared:testAndroidHostTest` |
| `server` | `./gradlew :server:test` |
| Android build | `./gradlew :app:androidApp:assembleDebug` |

The managed-device tasks (`atd30`, `atd34`, and the `ci` group) are for CI only.
