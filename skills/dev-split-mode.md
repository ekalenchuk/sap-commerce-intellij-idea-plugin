# Split Mode (Remote Development) — Migration Reference

Docs: [Split Mode](https://plugins.jetbrains.com/docs/intellij/split-mode-and-remote-development.html) ·
[Modular Plugins](https://plugins.jetbrains.com/docs/intellij/modular-plugins.html) ·
[Feature migration flow](https://plugins.jetbrains.com/docs/intellij/split-mode-feature-development.html) ·
[RPC](https://plugins.jetbrains.com/docs/intellij/remote-procedure-calls.html) ·
[API placement](https://plugins.jetbrains.com/docs/intellij/frontend-backend-shared-apis.html) ·
[Settings sync](https://plugins.jetbrains.com/docs/intellij/persistent-state-in-split-mode.html)

## Target architecture

Split plugin = Plugin Model v2 content modules loaded per process: frontend (UI, typing assistance) and backend (PSI analysis, indexes, project model, execution).
A module is frontend/backend by its `<module name="intellij.platform.frontend|backend"/>` dependency; no such dependency — loaded on both sides.
Frontend ↔ backend only via RPC (`@Rpc` suspend interfaces with `@Serializable` DTOs in a shared module, backend `RemoteApiProvider` via `platform.rpc.backend.remoteApiProvider`) or remote topics (backend → frontend events).

Layer mapping (guideline): language/PSI (lexer, parser, file types) → shared · inspections, references, completion, indexes, meta-models, `exec`, `mcp`, `project` → backend · tool windows, dialogs, editors, configurables, notifications → frontend.

## Phase 1 (done) — every submodule is a content module

Behaviour in a monolithic IDE is unchanged. `resources/META-INF/plugin.xml` holds plugin metadata, hard `<depends>` and `<content>`; the root module has no sources.

### Conventions

- Name: `sap.commerce.toolset.<gradle name with '-' → '.'>` — `:typeSystem-mcp` → `sap.commerce.toolset.typeSystem.mcp`.
- Descriptor: `resources/<name>.xml` (resources root, **not** `META-INF`). Its presence makes the root build package the module via `pluginModule(...)` as `lib/modules/<name>.jar` (see `isContentModule` in `build.gradle.kts`).
- Register `<module name="..."/>` in plugin.xml `<content>`.
- No `loading` attribute (optional): an optional content module implicitly gets the main plugin class loader as a parent — plugins from plugin.xml `<depends>` and the root `resources` (`extensions/`, `icons/`, `prs.json`, ...).
- `<dependencies>` — `<depends>` is not allowed:
  - `<plugin id="..."/>` — plugins the module really uses (not hard `<depends>` of plugin.xml). **Every dependency gates loading**: a module (and all modules depending on it) is skipped when the plugin is disabled — never declare unused plugins, especially in foundation modules.
  - `<module name="..."/>` — `project(":...")` dependencies and platform modules (`bundledModules`).
- `<plugin id>` exposes only the plugin's main jar, **not its content modules**: classes from `plugins/<plugin>/lib/modules/*.jar` need `<module name>` + Gradle `bundledModules` (e.g. `intellij.spring`, `intellij.grid.impl`, `intellij.javascript.parser`). Modules with `visibility="internal"` cannot be depended on — use public API, extension points or ids instead (see `AngularConfigurator`, `JavaeeExcludeFrameworkDetectionConfigurator`).
- Soft optional dependencies (`Plugin.X.isActive()` guards) must not reference the plugin classes — resolve by id (`Language.findLanguageByID("SQL")`) or move the code into the module gated by that plugin (see `database-core`).
- `<resource-bundle>` is not inherited — declare `<resource-bundle>i18n.HybrisBundle</resource-bundle>` when the descriptor uses `key=` without `bundle=` or action texts from the bundle. plugin.xml has no bundle: the plugin class loader cannot see `shared-core` resources.
- Resources resolved by the plugin class loader (e.g. `ExtensionsService` → `resources/extensions`) belong to the root module resources.
- Java package-private access between modules sharing a package fails at runtime (different class loaders).

### Verification

1. `./gradlew verifyPlugin` — it runs `buildSearchableOptions`, a headless IDE with the plugin:
   - sandbox `log/idea.log` plugin set section: no `sap.commerce.toolset.*` module `excluded`/`not resolved` (JRebel is disabled there on purpose), no `PluginException`/`NoClassDefFoundError`/bundle errors
   - `build/reports/pluginVerifier/**/compatibility-problems.txt`: no new entries compared to `main`
2. `./gradlew verifyContentModules` (part of `check`) — Plugin Verifier and the headless start do **not** check per-module class loader visibility, a missing `<module>`/`<plugin>` dependency surfaces only when the class is used at runtime. The task resolves every class referenced by the plugin jars against the class loaders configured by the platform and fails with the missing dependencies. `build/reports/verifyContentModules/report.txt` also lists declared dependencies without class references — review them when adding dependencies, they gate module loading. Implementation: `gradle/build-logic` (`CxVerifyContentModulesGradleTask`, `ContentModulesVerifier`).
3. Exercise the changed features in `runIde`.

## Next phases

- **Required modules.** Move plugin.xml `<depends>` into module `<dependencies>` and `plugin-internal.xml` extensions into modules; foundation modules (`shared-core`, ...) become `loading="required"` and declare all their dependencies explicitly (required modules get no implicit main class loader parent).
- **Split.** Per feature follow the migration flow: extract frontend/backend/shared modules, add `intellij.platform.frontend` / `intellij.platform.backend` (+ `intellij.platform.kernel.backend`) dependencies, add the `rpc` and kotlinx serialization Gradle plugins (versions per [compatibility table](https://plugins.jetbrains.com/docs/intellij/configuring-split-mode.html#required-gradle-plugin-and-library-versions)), replace direct UI → service calls with RPC wrapped in `durable {}`, sync settings via `RemoteSettingInfoProvider`, register actions on the frontend unless `update()` needs backend entities.
- **Running.** `./gradlew generateSplitModeRunConfigurations` creates the `Run IDE (Split Mode)` configuration; emulate latency with `-Didea.is.internal=true` → Split Mode widget → Connection Config → Direct Ping.
