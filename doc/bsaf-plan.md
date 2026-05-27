# BSAF replacement plan

## Status

**COMPLETED in v3.0.0** (commit `f101d1f`). All BSAF surfaces below have been replaced with plain Java / Swing alternatives; the `org.jdesktop.bsaf:bsaf:1.9.2` dependency is no longer on the classpath and no `org.jdesktop.application.*` imports remain.

This document is retained as a historical record of the migration plan. The actual completed commits are summarised in [TODO.md → v3.0.0 — modernization release](TODO.md#v300--modernization-release).

---

## Original plan (preserved verbatim below)

`org.jdesktop.bsaf:bsaf:1.9.2` (last released ~2012, upstream abandoned) is currently load-bearing across the GUI. Runs clean on Java 21 LTS today without `--add-opens` flags. Replacement is risk-management for future Java versions, not an immediate need.

This is **wave 2's headline task**. Defer until ready for a focused multi-hour session.

## What BSAF actually provides (five distinct jobs)

| # | BSAF surface | Where used | Replacement | Estimate |
|---|--------------|-----------|-------------|----------|
| 1 | `SingleFrameApplication` (app lifecycle) | `PortMapperApp` extends it; `Application.launch()` in `PortMapperCli.startGui()`; `addExitListener` for graceful router disconnect | Plain `main()` + manual JFrame + `Runtime.getRuntime().addShutdownHook(...)` for cleanup | ~50 lines |
| 2 | `LocalStorage` (XML settings persistence) | `PortMapperApp.loadSettings()` / `shutdown()`; reads/writes `settings.xml` | JDK built-in `java.beans.XMLEncoder` / `XMLDecoder` (closest API match), OR Jackson XML (more flexible, new dep) | ~30 lines + format-compat decision |
| 3 | `ResourceMap` (i18n + placeholder substitution) | `PortMapperApp.getResourceMap()`; throughout dialogs for localized strings with `${Application.title}`-style interpolation | Plain `ResourceBundle.getBundle()` + small placeholder-substitution helper | ~10 lines wrapper + call-site updates |
| 4 | `@Action` + `ActionMap` (annotation-driven button/menu bindings) | ~20 methods across `AboutDialog`, `EditPresetDialog`, `SettingsDialog`, `AddPortRangeDialog`, `PortMapperView` carry `@Action(name = ...)` | Direct `AbstractAction` subclasses, OR lambda `ActionListener`s. Optional: a tiny `ResourceActionBuilder` to reduce boilerplate | ~5 lines per action ≈ 100 lines net |
| 5 | `OSXAdapter` (macOS menu integration) | `PortMapperApp.registerMacOSXListeners()` and `MacSetup` invoke via reflection | Java 9+ `java.awt.Desktop` API: `Desktop.getDesktop().setAboutHandler(...)`, `setPreferencesHandler(...)`, `setQuitHandler(...)` | ~20 lines, near drop-in |

## Recommended execution order

Smallest, lowest-risk pieces first to build confidence and shrink BSAF's surface incrementally:

1. **OSXAdapter → `java.awt.Desktop`** — completely isolated, ~30 min, removes the `OSXAdapter` reflection dance in `MacSetup` and `PortMapperApp.registerMacOSXListeners`. Free win.
2. **ResourceMap → ResourceBundle + helper** — touches every dialog (~6 files) but each call-site change is mechanical. Add the placeholder-substitution helper, then sed-replace call sites.
3. **OSXAdapter step opens up `MacSetup`**, but step 2 expands to every dialog. Order matters less between these; pick by mood.
4. **LocalStorage → XMLEncoder/XMLDecoder** — **highest risk**, do this with full attention. Settings file format compatibility is the single biggest failure mode (users lose presets if format mismatches). Recommend: write a one-time migration that detects BSAF-format `settings.xml`, reads it via BSAF one last time, writes via the new encoder, then deletes the old file. Or accept settings reset for the single-user fork (cheaper, less code).
5. **`@Action` removal** — the most repetitive piece. Recommend writing a small `ActionFactory` helper that takes (name, ResourceBundle, ActionListener) and returns a configured `AbstractAction` with text/shortDescription pulled from the bundle. Then convert ~20 `@Action`-annotated methods to call sites.
6. **SingleFrameApplication removal** — last, because it's the orchestrator the other pieces hang off. Once #1-#5 are done, `PortMapperApp` no longer needs to extend `SingleFrameApplication`. Convert to plain class with `main()` driving JFrame setup.
7. **Remove `org.jdesktop.bsaf:bsaf:1.9.2`** from `build.gradle`. Verify nothing imports `org.jdesktop.application.*`.

## Risks and notes

- **Settings format compatibility (#4)** is the single biggest risk. The `LocalStorage.save()` output is BSAF's specific XML schema. `XMLEncoder` output is different. Without a migration path, the first launch after replacement may fail to load existing settings (no crash, just empty presets). For a single-user fork on a Windows machine, the user's existing presets are in `%AppData%\UnknownApplicationVendor\PortMapper\settings.xml` — back this up before testing.
- **Window position persistence**: BSAF's `SingleFrameApplication` auto-saves/restores window position. Plain Swing doesn't. Either replicate (write/read a few ints to settings) or accept the loss.
- **`@Action` discovery**: BSAF's `ActionMap` works via reflection scanning methods with the annotation. Replacement is more verbose but avoids the runtime reflection cost and obscure debugging when an action doesn't fire.
- **`--add-opens` regression check**: after BSAF removal, no `org.jdesktop.application.*` imports should remain. Run with `-Dsun.reflect.debugModuleAccessChecks=true` to surface any leftover internal-API access.
- **Build still must compile clean** with `-Werror -Xlint:all,-classfile`. Watch for new lint warnings during the rewrite — they're easy to accumulate in dialog code.

## Done criteria

- [ ] No `org.jdesktop.application.*` imports anywhere
- [ ] No `org.jdesktop.bsaf:bsaf` in `build.gradle` dependencies
- [ ] `./gradlew build` green, all tests pass
- [ ] Manual smoke test: app launches, connects to router via weupnp (the maintainer's working backend), shows the existing presets from `settings.xml`, can add/remove port mappings, About dialog renders with all five library credits
- [ ] macOS smoke test (if user has access): About/Preferences/Quit menu items still trigger the app's handlers via the new `java.awt.Desktop` integration. **If no Mac access**: skip the macOS validation and flag it in the commit message.

## Out of scope for this work

- Replacing Swing itself with JavaFX or anything else — separate decision, much larger.
- Adding new features (themes, FlatLaf look-and-feel, etc.) — keep this purely structural.
- Refactoring dialog layouts. They use MigLayout already and that's fine.
