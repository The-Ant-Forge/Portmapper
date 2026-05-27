# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

UPnP PortMapper is a Java 21 LTS Swing desktop application for managing port forwardings on UPnP-enabled routers.

This repository is **The Ant Forge's private modernization fork** of the upstream [kaklakariada/portmapper](https://github.com/kaklakariada/portmapper). Upstream stopped active development at v2.2.4.

**Operating rules for this fork:**

- **Never submit work upstream.** Do not open PRs against `kaklakariada/portmapper`. Treat upstream as historical-only.
- **Never publish to Maven Central.** The publishing/signing/Sonatype infrastructure was deliberately stripped; do not reintroduce it.
- **Java 21 LTS is the floor**, not a ceiling. The toolchain defaults to Java 21; older JDKs are not a target.
- **GPL-3.0 attribution is non-negotiable.** Keep the `Copyright (C) 2015 Christoph Pirkl` headers in every source file. The LICENSE file is the upstream license and must remain in place.
- The group is `org.theantforge.portmapper`; the version is `3.0.0-SNAPSHOT` (signals major break from upstream's 2.x line).

## Common commands

All commands use the Gradle wrapper. On Windows shells use `.\gradlew.bat`; everywhere else `./gradlew`.

| Task | Command |
|------|---------|
| Full build + fat JAR | `./gradlew build` (shadowJar is wired into `build`) |
| Run the GUI from sources | `./gradlew run` |
| Run all tests | `./gradlew test` |
| Run a single test class | `./gradlew test --tests org.chris.portmapper.router.dummy.TestDummyRouter` |
| Run one test method | `./gradlew test --tests '*TestDummyRouter.<methodName>'` |
| Apply license headers to new files | `./gradlew licenseFormat` (required — see below) |
| Check dependency updates | `./gradlew dependencyUpdates` |
| Build against a non-default JDK | `./gradlew build -PjavaVersion=24` (default is 21; CI only tests 21) |

Built artifact location: `build/libs/portmapper-<version>-all.jar`. The end-user invocation is `java -jar build/libs/portmapper-<version>-all.jar`.

## Build gotchas worth knowing

- **`-Werror` + `-Xlint:all,-classfile` + `javadoc.failOnError = true`** ([build.gradle](build.gradle)). A missing `@param`, unused import, or any javac warning fails the build. Keep new code lint-clean and javadoc-clean. `-classfile` is disabled to silence jUPnP's OSGi-annotation metadata warnings; that's the only category we suppress.
- **License header enforcement** — the Hierynomus license plugin checks every source file against `gradle/license-header.txt`. Run `./gradlew licenseFormat` after adding new files or `build` will fail.
- **`processResources` rewrites property filenames at build time**: `*_en.properties` becomes the default (no suffix), and `*_zh_CN.properties` becomes `*_zh.properties`. The token `@VERSION_NUMBER@` inside resources is substituted with `project.version`. If you reference a property bundle by name, remember the source filename ≠ the runtime filename for English/Chinese.
- **Java 21 LTS is the language baseline.** All Java 21 features (records, pattern matching, sealed classes, virtual threads, etc.) are fair game.
- **Norton TLS interception fix is required on this dev machine.** `~/.gradle/gradle.properties` contains `org.gradle.jvmargs=-Djavax.net.ssl.trustStoreType=Windows-ROOT` to make Gradle trust Norton's local root CA when resolving Maven Central. Without it, all `mavenCentral()` lookups fail with PKIX errors. See the `windows-norton-tls-mitm` memory entry for full context.

## Architecture

### Entry flow

```
PortMapperStarter.main
  └── PortMapperCli.start(args)
        ├── CLI flags present → connect → add/delete/list/info → exit
        └── else → Application.launch(PortMapperApp.class, args)   // BSAF Swing app
                    └── PortMapperApp.startup()  → PortMapperView (Swing)
```

`PortMapperCli` is the single decision point for GUI vs headless. Headless mode constructs a *throwaway* `PortMapperApp` solely to satisfy the router factory constructor — **CLI mode does not read or write `settings.xml`** (see [PortMapperCli.java:174-194](src/main/java/org/chris/portmapper/PortMapperCli.java#L174-L194)). Every option in CLI mode must be passed as an arg.

### Router abstraction (the main extension surface)

[IRouter](src/main/java/org/chris/portmapper/router/IRouter.java) is the SPI. Three implementations live under `org.chris.portmapper.router.*`:

| Package | Factory class | Purpose |
|---------|---------------|---------|
| `jupnp/` | `JUPnPRouterFactory` | **Default**. Uses jUPnP 3.0.4 (active fork of the abandoned 4thline Cling). Requires Jetty 9.4 on the runtime classpath — see [build.gradle](build.gradle) for the rationale. Single-router only. |
| `weupnp/` | `WeUPnPRouterFactory` | Supports `-Dportmapper.locationUrl=...` for manual router URL. Kept as a fallback because some routers respond better to weupnp's discovery than jUPnP's. |
| `dummy/` | `DummyRouterFactory` | Testing only; do not ship as default. |

Factories are resolved by **fully-qualified class name string** via reflection — not `ServiceLoader`. The class name comes from either the `-lib` CLI flag or `Settings.getRouterFactoryClassName()`. The constructor contract is `public RouterFactory(PortMapperApp app)`. To add a new UPnP library: subclass `AbstractRouterFactory`, implement `findRoutersInternal()` and `connect(locationUrl)`, expose the `(PortMapperApp)` constructor, and register it in the Settings dialog dropdown.

`Settings.getRouterFactoryClassName()` carries migration shims that rewrite obsolete factory FQCNs on read so legacy `settings.xml` files self-heal on the next save: the pre-rename Cling FQCN and the dropped SBBI FQCN both rewrite to jUPnP.

### GUI framework

The Swing UI is built on **BSAF** (Better Swing Application Framework — `org.jdesktop.application`, the maintained fork of the old JSR-296 reference impl). `PortMapperApp extends SingleFrameApplication`. Settings persist as XML via BSAF's `LocalStorage` under `%AppData%\UnknownApplicationVendor\PortMapper\` on Windows. Layout uses **MigLayout**. macOS About/Preferences menu integration uses Java 9+ `java.awt.Desktop` API in `PortMapperApp.registerSystemMenuHandlers` (BSAF's `OSXAdapter` was replaced as the first step of wave 2 BSAF removal).

### Logging

SLF4J + Logback. `PortMapperStarter` installs `SLF4JBridgeHandler` so `java.util.logging` (used by some UPnP libs) is routed through SLF4J. The GUI captures log output via a custom `LogMessageOutputStream` attached to Logback in `LogbackConfiguration` — that's how the in-app log viewer works.

## Tests

JUnit 5 (junit-bom:6.1.0) + Mockito 5.23 via `MockitoExtension` (strictness LENIENT to preserve legacy stubbing semantics). Test classes:

- `org.chris.portmapper.TestCommandLineArguments` — picocli CLI parsing (17 tests)
- `org.chris.portmapper.router.dummy.TestDummyRouter` — IRouter contract via DummyRouter (11 tests)
- `org.chris.portmapper.TestMessages` — ResourceBundle + `${...}` placeholder resolution
- `org.chris.portmapper.TestSettingsStorage` — XMLEncoder/XMLDecoder roundtrip + atomic-write
- `org.chris.portmapper.TestActions` — `Actions.create` / `createBound` helper

Tests run with `-enableassertions` and heap-dump-on-OOM. When adding tests, mirror the `src/main/java/org/chris/portmapper/...` package path under `src/test/java/`.

## Outstanding work

See [doc/TODO.md](doc/TODO.md) for the current modernization backlog. The headline in-progress task is BSAF replacement (detailed plan at [doc/bsaf-plan.md](doc/bsaf-plan.md)). Code reviews use [doc/code-review.md](doc/code-review.md) as the checklist.
