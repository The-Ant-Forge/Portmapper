# TODO

Outstanding work for the modernization fork. Read this when planning the next slice of work; CLAUDE.md should stay focused on current-state guidance.

## Wave 2 — BSAF replacement (in progress)

Full plan: [bsaf-plan.md](bsaf-plan.md). Seven steps total.

**Done:**

- **Step 1: `OSXAdapter` → `java.awt.Desktop` API** (commit `9181568`). Renamed `registerMacOSXListeners` → `registerSystemMenuHandlers`; dropped reflection-based `getMethod` helper; dropped `AppHelper.getPlatform()` check (Desktop API self-guards via `isSupported`).
- **Step 2: `ResourceMap` → static `Messages` utility** (commit `1a737d3`). New `org.chris.portmapper.Messages` class wraps a `ResourceBundle` plus recursive `${...}` placeholder substitution with cycle detection. 27 call sites migrated across 6 files. `app` field became orphaned in `PortMappingsTableModel` and `PortsTableModel` — removed along with the constructor parameter (cleaner signatures). 8 new tests in `TestMessages` covering live-bundle and synthetic-bundle scenarios. **Inventory caveat**: the earlier BSAF inventory subagent missed the table-model files; a direct grep before doing mechanical changes caught them. Lesson logged.
- **Step 3: `LocalStorage` → `XMLEncoder`/`XMLDecoder`** (commit `df1cca9`). New `org.chris.portmapper.SettingsStorage` utility replicates BSAF's directory-selection precedence (system property → portable dir → per-OS app-data) and uses JDK `java.beans.XMLEncoder`/`XMLDecoder` for the actual read/write. Decision: **accept settings reset** on first launch rather than guarantee BSAF-format compatibility (single-user fork, low cost). Real `settings.xml` backed up to `settings.xml.bsaf-backup` before the swap landed; can be restored manually if the old format turns out to be readable after all. 6 new tests in `TestSettingsStorage` (roundtrip + edge cases). **Smoke test needed**: launch the app, confirm settings round-trip, then on success delete the `.bsaf-backup` file.

**Remaining (steps 4–7):**
4. **`@Action` → direct `AbstractAction`/`ActionListener`** — 25 `@Action`-annotated methods across `AboutDialog`, `AddPortRangeDialog`, `EditPresetDialog`, `PortMapperView`, `SettingsDialog`. 8 `getActionMap` call sites. Recommend writing a small `ActionFactory` helper to avoid boilerplate.
5. **`SingleFrameApplication` removal** — `PortMapperApp` stops extending it; replace `Application.launch` in `PortMapperCli` with manual JFrame setup; replicate or drop the window-position-persistence feature; convert the `addExitListener(...)` hook in `PortMapperApp.startup()` and `JUPnPRouterFactory`.
6. **Remove `org.jdesktop.bsaf:bsaf:1.9.2`** from `build.gradle`. Confirm zero `org.jdesktop.application.*` imports remain.
7. **Manual smoke test** — app launches, connects via weupnp (the maintainer's working backend), shows existing presets from `settings.xml`, can add/remove port mappings, About dialog renders correctly.

**Surprises found by the BSAF inventory** (not in the original 5-pillar plan in `bsaf-plan.md`):

- **`PortMapperView` extends BSAF's `FrameView`** — an additional Pillar-1 concern. Replacement: convert to plain `JPanel` + `JFrame` orchestration in `PortMapperApp`.
- **`org.jdesktop.application.Task`** used in `PortMapperView` for async UI work (connection task with progress feedback). Replacement: `SwingWorker` (standard Java).
- **`org.jdesktop.application.Application.ExitListener`** used in `JUPnPRouterFactory:74-85` to register a shutdown hook for the UpnpService. Surprising coupling between router lifecycle and BSAF Application. Replacement: pass a shutdown-hook registration mechanism into the factory (or use `Runtime.getRuntime().addShutdownHook(...)` directly).

## Wave 2 — other candidates (post-BSAF)

- **SBBI lifecycle decision.** Vendored `lib/sbbi-upnplib-1.0.4.jar` from 2008. Options: drop, keep, replace. If dropped, `commons-jxpath:1.1` (pinned only for SBBI compat) can come out too. **Current decision: keep**, revisit after jUPnP coverage is better understood.
- **weupnp evaluation.** `org.bitlet:weupnp:0.1.4` from 2017, abandoned. **Important**: weupnp is currently the only backend known to work against this maintainer's specific router; do **not** drop until jUPnP can reliably connect to that router and supports the equivalent of `-Dportmapper.locationUrl`. **Current decision: keep.**

## Backlog

- **Evaluate <https://github.com/offbynull/portmapper>** as another UPnP backend candidate. User-requested.
- **args4j replacement.** `args4j:2.37` is end-of-life (last release 2018). Modern alternatives: **picocli** (preferred for subcommand support, colored help, completion scripts) or jcommander. Self-contained change; would touch `CommandLineArguments.java` only. Tests in `TestCommandLineArguments` provide a safety net for the swap.
- **Code review pass.** Use [code-review.md](code-review.md) as the checklist. Produces a dated `Code-Review-YYMMDD.md` deliverable.
- **Modern Java idioms.** Records for `PortMapping`/`Protocol`/`SinglePortMapping`/`PortMappingPreset`, pattern matching where useful, judicious `var`, sealed interface for `IRouter` if it adds clarity.
- **Re-enable GitHub Actions** when the codebase stabilizes (currently disabled at repo level; re-enable via Settings → Actions → "Allow all actions" or `gh api -X PUT repos/The-Ant-Forge/Portmapper/actions/permissions -F enabled=true`).
- **`Settings` migration shim removal.** The one-line shim in `Settings.getRouterFactoryClassName()` that rewrites the pre-rename Cling FQCN can be deleted once the maintainer's `settings.xml` has been rewritten (one launch cycle).

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
