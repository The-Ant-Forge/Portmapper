# TODO

Outstanding work for the modernization fork. CLAUDE.md stays focused on current-state guidance; this file is the roadmap + history. Decisions that have been made stay here in the "completed and decided" section so we don't re-litigate them later.

## Open

- **Manual smoke test on each release.** Connect via jUPnP, list mappings, add a test mapping via a preset, delete it, hit About. Document any router-specific quirks discovered.
- **`Settings` migration-shim removal.** The shim in `Settings.getRouterFactoryClassName()` rewrites both the pre-rename Cling FQCN and the dropped SBBI FQCN to jUPnP. Safe to delete once the maintainer's `settings.xml` has been rewritten by at least one launch cycle.
- **weupnp watch.** `org.bitlet:weupnp:0.1.4` (2017, abandoned). Kept as the manual-locationUrl fallback because some routers respond better to its discovery. Revisit if jUPnP coverage improves to the point weupnp adds no value.
- **Modernization candidates deliberately deferred** (low value vs effort right now):
  - Sealed interface for `IRouter`. Won't fit while we resolve factories by FQCN reflection.
  - Pattern matching on `instanceof`. Codebase has near-zero downcasts.
  - Broader `var` sweep beyond the targeted Swing-builder pass already done.

## Completed and decided

### v3.0.0 — modernization release

This release closes wave 1 (toolchain), wave 2 (BSAF removal), the code-review punch-list, and the SBBI lifecycle decision. See [README.md "What's new in this fork"](../README.md#whats-new-in-this-fork-v300) for the user-facing summary.

**Wave 1 — foundation:**

- `d4952a2` Disconnect from upstream (publishing/release/Sonar infra stripped)
- `cfe5f1b` Rebrand as The Ant Forge fork; preserve GPL-3.0 attribution
- `854db3f` Gradle 8.10.1 → 9.5.1; johnrengelman.shadow → gradleup.shadow
- `cd48902` JUnit 4 → JUnit 5; Mockito bump; `MockitoExtension`
- `eab1c6d` Bump non-abandoned implementation deps; drop deprecated Level.ALL
- `da88027` Test safety net: `TestCommandLineArguments` + `TestDummyRouter`
- `9449a3b` Cling → jUPnP 3.0.4
- `bed506a` Fix shadowJar artifact filename references after Gradle 9 swap
- `81d0b8b` Rename `cling` package and `Cling*` classes to `jupnp` / `JUPnP*`

**Wave 2 — BSAF removal (full plan: [bsaf-plan.md](bsaf-plan.md)):**

- Step 1: `OSXAdapter` → `java.awt.Desktop`
- Step 2: `ResourceMap` → static `Messages` utility (recursive `${...}` resolution + cycle detection)
- Step 3: `LocalStorage` → `XMLEncoder`/`XMLDecoder` via new `SettingsStorage`
- Step 4: `@Action` annotations → `Actions` helper (`create` / `createBound`)
- Step 5: `SingleFrameApplication` + `FrameView` + `Task` + `ExitListener` all removed
- Step 6: drop the `org.jdesktop.bsaf:bsaf:1.9.2` dependency

**Code review (full review: [Code-Review-260527.md](Code-Review-260527.md)):**

- F1/F3: `updateAddresses`, `updatePortMappings`, `removeMappings`, `displayRouterInfo`, and the multi-router `JOptionPane` all moved off the EDT via `RouterWorker<T>`
- F4: atomic `settings.xml` write via `.tmp` + `Files.move(ATOMIC_MOVE)`
- F7: `PortMappingPreset.getCompleteDescription` rebuilt to include port data
- F8: `PresetListModel` listener-lifetime tie documented
- F9: `AbstractRouterFactory.create(FQCN, owner)` deduplicates factory instantiation
- F2/F5/F6/F10: dead-code sweep (4 orphaned `ConnectTask_*.properties`, `SBBIRouter.getUpTime`, stale SBBI cross-link, `SinglePortMapping.copy`)

**Other decisions:**

- ✅ **args4j → picocli** (commit `c131d28`). args4j retired (EOL since 2018); replaced with picocli 4.7.7. CLI contract preserved.
- ✅ **Modern Java idioms.** `PortMapping`, `SinglePortMapping`, `PortMappingPreset` are records. Table models use arrow-syntax switch. Targeted `var` sweep on short-lived Swing builder locals.
- ✅ **SBBI dropped.** Vendored 2008 jar hardcoded acceptance of UPnP UDA 1.0; rejects every modern router that advertises 1.1+. Investigated the triplea-game fork (Debian-packaged `1.0.4+triplea-2`) — not on Maven Central, version check still present. Dropped the jar, the `commons-jxpath:1.1` chain, the `flatDir` repo, the `sbbi/` package, the Settings/About entries, and the `MAPPING_ENTRY_*` constants. Migration shim self-heals legacy `settings.xml`.
- ✅ **JSplitPane between port-mappings table and the bottom panels.** Closes a long-standing UX gripe where a growing log shrank the mappings table.
- ✅ **Discovery noise filter.** jUPnP's chatty "Found service of wrong type" / "Service descriptor retrieval failed" logs for non-router devices on the LAN are suppressed by default via a Logback `TurboFilter`. Toggleable in Settings.
- ✅ **GitHub Actions re-enabled.** `Build` workflow on push/PR to `main`, uploads the fat JAR as an artifact (90-day retention).
- ✅ **Commit-count versioning.** `MAJOR.MINOR.PATCH` where PATCH = `git rev-list --count v<MAJOR>.<MINOR>.0..HEAD`, capped at 99. See [CLAUDE.md → Versioning](../CLAUDE.md#versioning).
- ❌ **offbynull/portmapper backend.** Investigated, skipped. API too narrow — no `listExistingMappings` and no router-level `getExternalIpAddress`, both required by our `IRouter` SPI for the GUI's mappings table and External Address display.

## Operating notes

- After each commit: `./gradlew build` locally before pushing.
- CI ([build workflow](../.github/workflows/gradle.yml)) builds and runs tests on every push to `main`; download the fat JAR artifact from the workflow run.
- Norton TLS interception on the dev machine requires `org.gradle.jvmargs=-Djavax.net.ssl.trustStoreType=Windows-ROOT` in `~/.gradle/gradle.properties` to resolve Maven Central.
