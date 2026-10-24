# Repository Guidelines

## Project Structure & Module Organization

This repository is a Kotlin Multiplatform app with a shared Compose UI. `shared/src/commonMain` contains shared UI and logic; `androidMain` and `iosMain` contain platform implementations. Shared tests live in `shared/src/commonTest`, `androidHostTest`, and `iosTest`. `androidApp` is the Android application shell. `iosApp` contains the Xcode project, Swift entry point, and assets. Gradle configuration is at the root and in each module; dependency and plugin versions are catalogued in `gradle/libs.versions.toml`. Do not edit generated files under `build/`.
 
## Build, Test, and Development Commands

Run these from the repository root using the checked-in wrapper:

- `./gradlew build` (Windows: `gradlew.bat build`) compiles and checks all configured modules.
- `./gradlew :shared:allTests` runs the shared module’s supported tests; platform-specific tasks depend on the host platform.
- `./gradlew :androidApp:assembleDebug` builds the Android debug APK.
- `./gradlew tasks` lists available tasks. Open `iosApp/iosApp.xcodeproj` in Xcode to build and run the iOS app; iOS builds require macOS and Xcode.
- Use the version catalog and convention plugins (`build-logic/`) for shared configuration. Do not copy-paste `kotlin { ... }` blocks between modules.
- Keep Kotlin, Compose Multiplatform, AGP, and Gradle versions aligned with the official compatibility tables.
- Enable `org.gradle.configuration-cache=true` and `org.gradle.caching=true` when the repo supports it.
- Treat warnings as errors in CI (`allWarningsAsErrors`) if the project does; fix warnings rather than suppressing them.
 

## Coding Style & Naming Conventions

Follow Kotlin conventions: four-space indentation, `UpperCamelCase` for types and composables, `lowerCamelCase` for functions and properties, and descriptive test names ending in `Test`. Keep platform-specific code in the matching source set and shared behavior in `commonMain`. Use Swift’s standard four-space indentation and `UpperCamelCase` type names. No separate formatter or linter configuration is present; keep imports tidy and follow surrounding code.

## Testing Guidelines

Use `kotlin.test` for shared tests. Place common behavior tests in `commonTest`; use `androidHostTest` or `iosTest` when a check depends on that platform. Name test classes and methods for the behavior under test, and run `./gradlew :shared:allTests` before submitting when the host supports the targets.

- Shared logic: `commonTest` + `kotlin.test`, fakes over mocks. (Mocking libraries that depend on reflection don't work on Native.)
- Use `Mokkery` or hand-written fakes for multiplatform mocking.
- Platform tests: `androidUnitTest`, `androidInstrumentedTest`, `iosTest` only for platform-specific behavior.
- New logic requires tests. Bug fixes require a regression test.
- Tests must be deterministic: inject clocks and dispatchers, no real network, no `delay` based timing on real dispatchers.


## Commit & Pull Request Guidelines

Git history is not available in this checkout, so no established commit convention could be confirmed. Use short imperative commit subjects, for example `Add client navigation placeholder`. Pull requests should describe the user-visible change, list validation commands and results, link any relevant issue, and include screenshots for UI changes. Note platform-specific limitations or follow-up work.

## Configuration & Generated Files

Keep machine-specific SDK paths and secrets out of version control. Configure local SDK settings in `local.properties` as required by Gradle. Edit source resources under `shared/src/commonMain/composeResources` or the relevant app asset directory, not generated outputs.


----------------------------------------------------------------
 
## Architecture

- Layers: `ui` → `presentation` (ViewModel/state holder) → `domain` (use cases, optional) → `data` (repositories, data sources). Dependencies point inward.
- Business logic, state holders, repositories, and networking live in shared code. Platform code only hosts UI and provides platform capabilities.
- Expose UI state as immutable `data class`/sealed types via `StateFlow`. Expose one-off events through a dedicated channel or as state, not shared mutable fields.
- Use `androidx.lifecycle.ViewModel` (multiplatform) or an equivalent state holder consistently; do not mix approaches within a feature.
- Prefer many small feature modules over one large `shared` module once the project grows. Use explicit `api`/`implementation` visibility to limit what each module exposes.

## Dependency injection

- Use Koin (or `kotlin-inject`/Metro, whichever the repo uses); do not introduce a second DI framework.
- Declare modules in `commonMain`; platform-specific bindings (e.g., database driver, settings) go in per-platform modules.
- Inject interfaces, not concrete classes, so tests can substitute fakes.

## Coroutines and Flow

- Inject `CoroutineDispatcher`s (or a `DispatcherProvider`) rather than hardcoding `Dispatchers.IO`. Note `Dispatchers.IO` is available on all targets in current coroutines versions, but injection is still better for tests.
- Never use `GlobalScope`. Use structured scopes (`viewModelScope`, injected `CoroutineScope`).
- Always rethrow `CancellationException`; do not swallow it in `catch (e: Exception)`.
- Test with `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`) and Turbine for Flow assertions.
- On iOS, exposing `Flow`/`suspend` to Swift needs care. Use SKIE (or Swift export where stable) or wrap in small adapter APIs rather than leaking raw `Flow` generics.

## Libraries (use what the repo already has)

| Concern | Preferred multiplatform option |
|---|---|
| Networking | Ktor client (engine per platform: OkHttp, Darwin, CIO/Java) |
| Serialization | kotlinx.serialization |
| Database | SQLDelight or Room (KMP) |
| Key-value settings | DataStore (KMP) or multiplatform-settings |
| Date/time | kotlinx-datetime |
| Images | Coil 3 (Compose Multiplatform) |
| Navigation | Jetpack Navigation (KMP) or Voyager/Decompose, as already used |
| Logging | Kermit or Napier |

- Before adding a library, confirm it supports **all** project targets. Check Kotlin Multiplatform Gradle plugin compatibility.
- Add dependencies through the version catalog (`gradle/libs.versions.toml`), never as inline version strings.

## Compose Multiplatform UI

- Write UI in `commonMain` using `@Composable` functions; keep composables stateless and hoist state.
- Use `Res` / `compose.components.resources` for strings, images, and fonts. Do not hardcode user-facing strings.
- Access platform features via injected interfaces or `expect`/`actual` functions, not by reaching into `LocalContext` from common code.
- Provide `@Preview` functions (where supported) and keep previews free of real network or DI dependencies.
- Respect platform conventions: system back handling, safe-area insets, dark theme, dynamic type/font scaling, accessibility labels.

## iOS interoperability

- Keep the public API surface exported to Swift small and intentional. Mark internals `internal`.
- Prefer simple types at the boundary (data classes, sealed interfaces, enums). Avoid generics-heavy APIs, default arguments, and extension-function-heavy APIs that map poorly to Swift/Objective-C.
- Use `@ObjCName`, `@HiddenFromObjC`, or `@Throws` where needed. Suspend functions called from Swift need `@Throws(CancellationException::class)`-style handling for errors.
- Do not use Kotlin/Native features that require the old memory model assumptions. The new memory manager is the default; still avoid sharing mutable state across threads without synchronization.
- Never edit generated framework headers or `build/` outputs.

## Gradle and build

- Use the version catalog and convention plugins (`build-logic/`) for shared configuration. Do not copy-paste `kotlin { ... }` blocks between modules.
- Keep Kotlin, Compose Multiplatform, AGP, and Gradle versions aligned with the official compatibility tables.
- Enable `org.gradle.configuration-cache=true` and `org.gradle.caching=true` when the repo supports it.
- Treat warnings as errors in CI (`allWarningsAsErrors`) if the project does; fix warnings rather than suppressing them.
- When editing `build.gradle.kts`, run a sync-equivalent (`./gradlew help` or `./gradlew build`) to verify it configures.

## Testing

- Shared logic: `commonTest` + `kotlin.test`, fakes over mocks. (Mocking libraries that depend on reflection don't work on Native.)
- Use `Mokkery` or hand-written fakes for multiplatform mocking.
- Platform tests: `androidUnitTest`, `androidInstrumentedTest`, `iosTest` only for platform-specific behavior.
- New logic requires tests. Bug fixes require a regression test.
- Tests must be deterministic: inject clocks and dispatchers, no real network, no `delay` based timing on real dispatchers.

## Code style

- Follow the official Kotlin coding conventions; formatting is enforced by `ktlint`/`detekt`.
- Prefer immutable types (`val`, `List`, `data class`), explicit visibility (`internal` by default for module-private code), and sealed hierarchies for state/results.
- Use `Result`/sealed result types for expected failures; reserve exceptions for programmer errors.
- Keep functions small and name them for what they do. Add KDoc to public APIs.
- No wildcard imports, no unused code, no commented-out code.

## Boundaries

**Always**
- Keep shared code free of platform-specific imports.
- Run `./gradlew build` (or the relevant subset) and tests before finishing.
- Update tests and docs alongside code changes.

**Ask first**
- Adding or upgrading major dependencies, the Kotlin version, or AGP/Gradle versions.
- Adding a new target or module.
- Changing public APIs consumed from Swift or other modules.
- Changing DI, navigation, or persistence approaches.

**Never**
- Commit secrets, API keys, `local.properties`, or signing credentials.
- Edit generated files or `build/` directories.
- Disable tests, lint rules, or warnings to make a build pass.
- Duplicate logic across `androidMain` and `iosMain` instead of sharing it in `commonMain`.
- Put business logic in `actual` implementations or in Swift/Android UI code.

## Pull request checklist

- [ ] Builds on all configured targets (or notes which couldn't be verified)
- [ ] Tests added/updated and passing
- [ ] No new platform imports in `commonMain`
- [ ] Dependencies added via version catalog and verified multiplatform
- [ ] Public API changes documented