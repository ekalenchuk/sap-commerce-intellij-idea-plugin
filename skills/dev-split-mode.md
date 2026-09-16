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

## Phase 1 (current) — convert submodules into content modules

Behaviour in a monolithic IDE must stay identical. Source of truth for migrated modules: `<content>` in `resources/META-INF/plugin.xml`.

### Conventions

- Name: `sap.commerce.toolset.<gradle name with '-' → '.'>` — `:typeSystem-mcp` → `sap.commerce.toolset.typeSystem.mcp`.
- Descriptor: `resources/<name>.xml` (resources root, **not** `META-INF`). Its presence makes the root build package the module via `pluginModule(...)` as `lib/modules/<name>.jar` (see `isContentModule` in `build.gradle.kts`); other submodules stay composed into the main jar.
- Register `<module name="..."/>` in plugin.xml `<content>`; remove the legacy `<xi:include>` / `<depends optional="true" config-file="...">`.
- No `loading` attribute (optional): an optional content module implicitly gets the main plugin class loader as a parent, i.e. all composed submodules and plugins from plugin.xml `<depends>`.
- `<dependencies>` in the descriptor — `<depends>` is not allowed:
  - `<plugin id="..."/>` — every `bundledPlugins`/`compatiblePlugins` entry of the module build that is not a hard `<depends>` of plugin.xml, plus the plugin of the replaced optional `<depends>`
  - `<module name="..."/>` — every `project(":...")` dependency that is a content module, every `bundledModules` entry
- `<resource-bundle>` is not inherited from plugin.xml — declare `<resource-bundle>i18n.HybrisBundle</resource-bundle>` when the descriptor uses `key=` without `bundle=`.

### When a module can be migrated

The main plugin class loader cannot see content modules, so migrate bottom-up:

1. No composed submodule and no root `src` class depends on it (content module dependents are fine).
2. No main descriptor (plugin.xml, xi:included descriptors, `plugin-internal.xml`) references its classes, extension points, action or group ids.
3. The main class loader sees an optional plugin only through a remaining `<depends ...>` in plugin.xml — do not remove the last one while composed code still uses that plugin.
   Blocked this way: `com.intellij.database` (impex-core, flexibleSearch-*, polyglotQuery-ui), `org.jetbrains.kotlin` (shared-core), `com.intellij.spring`/`com.intellij.javaee` (spring-core, root `src`).
4. No Java package-private access into a package that also exists in the main jar (different class loaders = different runtime packages).
5. Resources loaded through a main-jar class or the plugin class loader must stay in a composed submodule.

Modules sharing an optional plugin migrate together (all MCP modules, all diagram modules, ...).

### Verification per migration

1. `./gradlew prepareSandbox` — `lib/modules/<name>.jar` contains `<name>.xml` at its root; the classes are gone from the main jar.
2. `./gradlew verifyPlugin` — it runs `buildSearchableOptions`, a headless IDE with the plugin:
   - sandbox `log/idea.log` plugin set section: no `sap.commerce.toolset.*` module `excluded`/`not resolved` (JRebel is disabled there on purpose), no `PluginException`/`NoClassDefFoundError`
   - `build/reports/pluginVerifier/**/compatibility-problems.txt` and `plugin-structure-warnings.txt`: no new entries compared to the previous run
3. Plugin Verifier does **not** check per-module class loader visibility (a missing `<dependencies>` entry is not reported) — cross-check `<dependencies>` against the module build as described above, then exercise the feature in `runIde`.

## Next phases

- **Required modules.** Once everything is a content module, move plugin.xml `<depends>` into module `<dependencies>`; foundation modules (`shared-core`, ...) become `loading="required"` and declare all their dependencies explicitly (required modules get no implicit main class loader parent).
- **Split.** Per feature follow the migration flow: extract frontend/backend/shared modules, add `intellij.platform.frontend` / `intellij.platform.backend` (+ `intellij.platform.kernel.backend`) dependencies, add the `rpc` and kotlinx serialization Gradle plugins (versions per [compatibility table](https://plugins.jetbrains.com/docs/intellij/configuring-split-mode.html#required-gradle-plugin-and-library-versions)), replace direct UI → service calls with RPC wrapped in `durable {}`, sync settings via `RemoteSettingInfoProvider`, register actions on the frontend unless `update()` needs backend entities.
- **Running.** `./gradlew generateSplitModeRunConfigurations` creates the `Run IDE (Split Mode)` configuration; emulate latency with `-Didea.is.internal=true` → Split Mode widget → Connection Config → Direct Ping.
