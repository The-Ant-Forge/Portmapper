# TODO

Outstanding work for the modernization fork. Read this when planning the next slice of work; CLAUDE.md should stay focused on current-state guidance.

## Wave 2 — BSAF replacement (in progress)

Full plan: [bsaf-plan.md](bsaf-plan.md). Seven steps total.

**Done:**

- **Step 1: `OSXAdapter` → `java.awt.Desktop` API** (commit `9181568`). Renamed `registerMacOSXListeners` → `registerSystemMenuHandlers`; dropped reflection-based `getMethod` helper; dropped `AppHelper.getPlatform()` check (Desktop API self-guards via `isSupported`).
- **Step 2: `ResourceMap` → static `Messages` utility** (commit `1a737d3`). New `org.chris.portmapper.Messages` class wraps a `ResourceBundle` plus recursive `${...}` placeholder substitution with cycle detection. 27 call sites migrated across 6 files. `app` field became orphaned in `PortMappingsTableModel` and `PortsTableModel` — removed along with the constructor parameter (cleaner signatures). 8 new tests in `TestMessages` covering live-bundle and synthetic-bundle scenarios. **Inventory caveat**: the earlier BSAF inventory subagent missed the table-model files; a direct grep before doing mechanical changes caught them. Lesson logged.
- **Step 3: `LocalStorage` → `XMLEncoder`/`XMLDecoder`** (commit `df1cca9`). New `org.chris.portmapper.SettingsStorage` utility replicates BSAF's directory-selection precedence (system property → portable dir → per-OS app-data) and uses JDK `java.beans.XMLEncoder`/`XMLDecoder` for the actual read/write. Decision: accept settings reset on first launch rather than guarantee BSAF-format compatibility — but the smoke test confirmed the BSAF format **did** round-trip cleanly through `XMLDecoder`, so no reset actually occurred. The `.bsaf-backup` file is now safe to delete. 6 new tests in `TestSettingsStorage` (roundtrip + edge cases).
- **Step 4: `@Action` annotations → `Actions` helper** (commit `dcfa6a9`). New `org.chris.portmapper.Actions` static factory: `create(name, listener)` builds an `AbstractAction` with bundle-driven text/tooltip/mnemonic; `createBound(...)` adds the BSAF `enabledProperty` binding via reflective `addPropertyChangeListener` subscription. 14 `@Action` annotations across `AboutDialog`, `AddPortRangeDialog`, `SettingsDialog`, `EditPresetDialog`, `PortMapperView` replaced; 8 `actionMap.get(...)` lookups replaced with inline `Actions.create` / `createBound`. Connect/Disconnect dynamic button swap preserved. `connectRouter()` refactored from `Task<Void, Void>` return to void with explicit `app.getContext().getTaskService().execute(task)`. Orphan `app` field removed from `AddPortRangeDialog`. 10 new tests in `TestActions`.
- **Step 5: `SingleFrameApplication` + `FrameView` + `Task` + `ExitListener` removal** (commit `b818a38`). The big one. `PortMapperApp` no longer extends `SingleFrameApplication` — explicit `startup()` method, EDT-scheduled GUI launch, JVM shutdown hook for settings save + router disconnect. `PortMapperView` no longer extends `FrameView` — composition over inheritance, has-a `JFrame`, has-a `PropertyChangeSupport` with explicit delegate methods. `ConnectTask` extends `SwingWorker<Void,Void>` (BSAF's `Task` is gone); doInBackground only does the worker-thread work, EDT-only UI updates moved to `done()` — fixes a latent off-EDT bug. `Application.ExitListener` in `JUPnPRouterFactory` is now a `Runtime.addShutdownHook` on a dedicated thread. Bundle auto-injection (BSAF walked the component tree and populated `<name>.text`/`<name>.label`/etc. from properties) replaced by explicit `Messages.get` calls in `URLLabel`, every dialog's `createLabel`, dialog `setTitle`, and three `JCheckBox` constructors. `PortMapperCli.startGui` calls `new PortMapperApp().startup()` instead of `Application.launch(class, args)`. **Lost feature, accepted**: BSAF's `*.session.xml` window-position persistence — windows default on next launch; the orphaned `.session.xml` files in `%AppData%\UnknownApplicationVendor\PortMapper\` are harmless leftovers.
- **Step 6: drop `org.jdesktop.bsaf:bsaf:1.9.2` dependency** (commit `f101d1f`). One line out of `build.gradle`. Bundled cleanup: removed BSAF from the About-dialog "libraries used" credits (the credit was both wrong and unnecessary once the dep was gone) — `app_framework_label` URLLabel removed from `AboutDialog.java`, and the three `about_dialog.app_framework_label.{label,url,toolTipText}` keys removed from each locale `.properties` file. Fat JAR shrinks; only historical javadoc references to BSAF remain (intentional, in `Actions`, `Messages`, `SettingsStorage` — they explain what each utility replaces).

**Remaining (step 7):**

7. **Final whole-app smoke test** — confirm nothing was lost in the dep drop (e.g. transitive classes BSAF was secretly providing). Same surface as the step-5 smoke test plus an About-dialog check that BSAF/"Better Swing Application Framework" no longer appears.
4. **`@Action` → direct `AbstractAction`/`ActionListener`** — 25 `@Action`-annotated methods across `AboutDialog`, `AddPortRangeDialog`, `EditPresetDialog`, `PortMapperView`, `SettingsDialog`. 8 `getActionMap` call sites. Recommend writing a small `ActionFactory` helper to avoid boilerplate.
5. **`SingleFrameApplication` removal** — `PortMapperApp` stops extending it; replace `Application.launch` in `PortMapperCli` with manual JFrame setup; replicate or drop the window-position-persistence feature; convert the `addExitListener(...)` hook in `PortMapperApp.startup()` and `JUPnPRouterFactory`.
6. **Remove `org.jdesktop.bsaf:bsaf:1.9.2`** from `build.gradle`. Confirm zero `org.jdesktop.application.*` imports remain.
7. **Manual smoke test** — app launches, connects via weupnp (the maintainer's working backend), shows existing presets from `settings.xml`, can add/remove port mappings, About dialog renders correctly.

**Surprises found by the BSAF inventory** (not in the original 5-pillar plan in `bsaf-plan.md`):

- **`PortMapperView` extends BSAF's `FrameView`** — an additional Pillar-1 concern. Replacement: convert to plain `JPanel` + `JFrame` orchestration in `PortMapperApp`.
- **`org.jdesktop.application.Task`** used in `PortMapperView` for async UI work (connection task with progress feedback). Replacement: `SwingWorker` (standard Java).
- **`org.jdesktop.application.Application.ExitListener`** used in `JUPnPRouterFactory:74-85` to register a shutdown hook for the UpnpService. Surprising coupling between router lifecycle and BSAF Application. Replacement: pass a shutdown-hook registration mechanism into the factory (or use `Runtime.getRuntime().addShutdownHook(...)` directly).

## Wave 2 — other candidates (post-BSAF)

- **weupnp evaluation.** `org.bitlet:weupnp:0.1.4` from 2017, abandoned. Kept as a fallback because some routers respond better to weupnp's discovery than jUPnP's. **Current decision: keep.**

## Backlog

- **Modern Java idioms — done** (commits `38e5cdc`, `e9f7238`). All three model types (`PortMapping`, `SinglePortMapping`, `PortMappingPreset`) are records; table models use arrow-syntax switch expressions; light `var` sweep applied to short-lived Swing builder locals in `EditPresetDialog` (value-type declarations kept explicit on purpose). **Not done — deliberately deferred**:
  - Sealed interface for `IRouter`. Skipped because the factory-by-FQCN-reflection pattern in `Settings` means subclasses aren't strictly enumerable. Would only make sense if we dropped dynamic factory loading.
  - Pattern matching on `instanceof`. Codebase has near-zero downcasts; the readability win is marginal.
  - Broader `var` sweep beyond Swing builders. Cosmetic; can land any time but provides little value over the targeted pass already done.
- **Code review pass.** Use [code-review.md](code-review.md) as the checklist. Produces a dated `Code-Review-YYMMDD.md` deliverable.
- **README polish: regenerate the `-h` help block** with picocli's actual output (commit `c131d28` changed the CLI help format). Low priority — flags listed still all work, only the literal rendering differs.
- **Re-enable GitHub Actions** when the codebase stabilizes (currently disabled at repo level; re-enable via Settings → Actions → "Allow all actions" or `gh api -X PUT repos/The-Ant-Forge/Portmapper/actions/permissions -F enabled=true`).
- **`Settings` migration shim removal.** The one-line shim in `Settings.getRouterFactoryClassName()` that rewrites the pre-rename Cling FQCN can be deleted once the maintainer's `settings.xml` has been rewritten (one launch cycle).

## Backlog — completed and decided

- ✅ **SBBI backend dropped.** The vendored `sbbi-upnplib-1.0.4.jar` (2008-vintage SuperBonBon Industries) rejects every modern router during discovery because it hardcodes `UDA version 1.0` and modern IGDs advertise `1.1`. Investigated the triplea-game fork (Debian-packaged `1.0.4+triplea-2`) but it's not published to Maven Central and the version check sits in `RootDevice.java` rather than the patched files. Dropped: `lib/sbbi-upnplib-1.0.4.jar`, `:sbbi-upnplib:1.0.4` impl dep, `commons-jxpath:1.1` runtime dep (only there for SBBI), the `flatDir { dirs 'lib' }` repository declaration, the `sbbi/` package, the `SBBI UPnP lib` entry in the Settings dropdown and About dialog, and the `MAPPING_ENTRY_*` constants + `PortMapping.create(ActionResponse)` factory. `Settings.getRouterFactoryClassName()` migration shim rewrites the dropped SBBI FQCN to jUPnP on first read, so any saved `settings.xml` self-heals.
- ✅ **args4j → picocli** (commit `c131d28`). `args4j:2.37` retired (end-of-life since 2018); replaced with `info.picocli:picocli:4.7.7`. CLI contract preserved; `TestCommandLineArguments` (17 tests) passes unchanged.
- ❌ **offbynull/portmapper as additional UPnP backend** — investigated, **skipped**. Library (`com.offbynull.portmapper:portmapper:2.0.6`) is Apache-2.0 and on Maven Central but is functionally abandoned (last commit Jan 2023) and the deal-breaker is its API shape: the `PortMapper` interface only exposes 4 mapping primitives (`mapPort` / `unmapPort` / `refreshPort` / `getSourceAddress`) with **no `listExistingMappings()`** and **no router-level `getExternalIpAddress()`** — both required by our `IRouter` SPI for the GUI's mappings table and External Address display. Adopting would require either degrading the GUI or reimplementing the queries beneath offbynull, defeating the point. Revisit only if a future *mapping-only headless* mode is wanted (no GUI), where the narrow API would be a fit.

## Wave 1 — done (reference)

Wave 1 was the foundation modernization, all on `origin/main` as of 2026-05-26:

- `d4952a2` Disconnect from upstream (publishing/release/Sonar infra stripped)
- `cfe5f1b` Rebrand as The Ant Forge fork; preserve GPL-3.0 attribution
- `854db3f` Bump Gradle 8.10.1 → 9.5.1; swap johnrengelman.shadow for gradleup.shadow
- `cd48902` Migrate JUnit 4 → JUnit 5; bump Mockito; switch to MockitoExtension
- `eab1c6d` Bump non-abandoned implementation deps; drop deprecated Level.ALL
- `da88027` Add test safety net: CommandLineArguments + DummyRouter contract
- `9449a3b` Replace abandoned Cling with jUPnP 3.0.4
- `bed506a` Fix shadowJar artifact filename references after Gradle 9 swap
- `81d0b8b` Rename cling package and Cling* classes to jupnp / JUPnP*
- `21549dd` Add BSAF replacement plan in doc/bsaf-plan.md

## Operating notes

- After each modernization commit: `./gradlew build` locally; smoke-test the GUI against the user's actual router before depending on the change.
- Push cadence is "as we go" for wave 2 (wave 1 was batch-at-end). Each commit on `main` is independently verifiable.
- Norton TLS interception on the dev machine requires `org.gradle.jvmargs=-Djavax.net.ssl.trustStoreType=Windows-ROOT` in `~/.gradle/gradle.properties` to resolve Maven Central dependencies.
