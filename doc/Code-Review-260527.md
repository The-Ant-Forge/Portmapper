# Code Review — 2026-05-27

Scope: full codebase review against the [`doc/code-review.md`](code-review.md) 21-category checklist.

Commit context: wave 2 BSAF removal complete (step 6, commit `f101d1f`); picocli migration done (`c131d28`); records refactor done (`e9f7238`). Wave 2 step 7 (final whole-app smoke test) is still outstanding.

---

## Summary table

| # | Category | Description | Action | Impact | Effort | Risk |
|---|----------|-------------|--------|--------|--------|------|
| F1 | 12 — Swing EDT | `updateAddresses()`, `updatePortMappings()`, `removeMappings()`, `displayRouterInfo()` all make blocking router calls on the EDT | Wrap each in a `SwingWorker` analogous to `ConnectTask` | High | Medium | Medium |
| F2 | 1 — Dead code | `ConnectTask_*.properties` (4 locale files) are entirely orphaned; no code references any of their keys | Delete the 4 files | Medium | Low | Low |
| F3 | 5 / 12 — Error + EDT | `PortMapperApp.connectRouter()` raises a `JOptionPane.showInputDialog` from the `ConnectTask` worker thread (multi-router path) | Collect candidates in `doInBackground`, prompt in `done()` | High | Low | Low |
| F4 | 14 — File I/O | `SettingsStorage.save()` writes non-atomically; a crash mid-write corrupts `settings.xml` permanently | Write to `.tmp`, then `Files.move(..., ATOMIC_MOVE)` | Medium | Low | Low |
| F5 | 1 — Dead code | `SBBIRouter.getUpTime()` is a public method not declared in `IRouter`; nothing calls it | Delete method | Low | Trivial | Low |
| F6 | 9 — Doc drift | `JUPnPPortMappingExtractor` class-level javadoc says it operates on an `InternetGatewayDevice`, with `@link` resolving to the SBBI type — wrong subject for a jUPnP class | Rewrite the javadoc to reference jUPnP's `Service`/`ActionService` and drop the SBBI import | Low | Trivial | Low |
| F7 | 1 — Dead code | `PortMappingPreset.getCompleteDescription()` builds a structurally broken string (no port numbers, just separators) | Fix the format or delete the method and its one TRACE caller | Medium | Low | Low |
| F8 | 13 — Listener lifecycle | `PresetListModel` registers itself as a `PropertyChangeListener` on `Settings` in its constructor but never removes it | Track the registration; deregister on view disposal | Low | Low | Low |
| F9 | 3 — Duplication | `PortMapperCli` has its own `createRouterFactory()`/`getClassForName()` duplicating logic also in `PortMapperApp` | Extract to a shared static helper or `AbstractRouterFactory` | Low | Medium | Low |
| F10 | 1 — Dead code | `SinglePortMapping.copy()` is documented as a no-op return-self; records are immutable and no call sites remain | Delete method | Low | Trivial | Low |

---

## Detailed findings

### Critical

#### F1 — Blocking router calls on the Event Dispatch Thread

**Category 12 — Swing concurrency.** Confidence: high.

`PortMapperView` exposes several public methods that are wired directly to button `ActionListener` lambdas (via `Actions.createBound(...)` in [`getMappingsPanel`](src/main/java/org/chris/portmapper/gui/PortMapperView.java#L245) and elsewhere) and therefore always run on the EDT. Each makes synchronous SOAP/HTTP calls to the router:

- [`PortMapperView.java:262-278`](src/main/java/org/chris/portmapper/gui/PortMapperView.java#L262-L278) — `updateAddresses()` calls `router.getExternalIPAddress()` and `router.getInternalHostName()`. SOAP calls; the UI freezes for the duration.
- [`PortMapperView.java:358-371`](src/main/java/org/chris/portmapper/gui/PortMapperView.java#L358-L371) — `updatePortMappings()` calls `router.getPortMappings()`, which is an enumeration loop bounded by `MAX_NUM_PORTMAPPINGS` (500) and can issue many SOAP calls back-to-back.
- [`PortMapperView.java:316-331`](src/main/java/org/chris/portmapper/gui/PortMapperView.java#L316-L331) — `removeMappings()` calls `router.removeMapping()` inside a loop; the UI freezes per-mapping on slow routers.
- [`PortMapperView.java:333-344`](src/main/java/org/chris/portmapper/gui/PortMapperView.java#L333-L344) — `displayRouterInfo()` calls `router.logRouterInfo()`.

The `ConnectTask` introduced during BSAF step 5 correctly offloads `connectRouter()` to a `SwingWorker`, but these four action paths were not converted. They were already broken pre-BSAF and remain broken — the BSAF migration only addressed the connect path.

`updatePortMappings()` is additionally called from `ConnectTask.done()` ([line 483](src/main/java/org/chris/portmapper/gui/PortMapperView.java#L483)) — that is fine because `done()` runs on the EDT *after* the worker completes, and the call is deliberate. The problem is the button-triggered path where the same method runs while the EDT is the caller.

**Fix:** Wrap `updateAddresses()`, `updatePortMappings()`, `removeMappings()`, `displayRouterInfo()` in `SwingWorker` subclasses analogous to `ConnectTask`. The pattern is already established in the file. Results and UI updates go in `done()`.

---

#### F3 — `JOptionPane` raised from a worker thread (multi-router path)

**Category 5 — Error handling / Category 12 — Swing concurrency.** Confidence: high.

[`PortMapperApp.java:257-265`](src/main/java/org/chris/portmapper/PortMapperApp.java#L257-L265):

```java
final IRouter selectedRouter = (IRouter) JOptionPane.showInputDialog(this.getView().getFrame(),
        Messages.get("messages.select_router.message"),
        Messages.get("messages.select_router.title"), ...);
```

This is inside `connectRouter()`, which is invoked from `ConnectTask.doInBackground()` — a worker thread. `JOptionPane.showInputDialog` creates and shows a modal dialog, and Swing components must only be touched on the EDT. The code comment at [`PortMapperApp.java:219`](src/main/java/org/chris/portmapper/PortMapperApp.java#L219) acknowledges this as a "latent issue" deferred from step 5. With jUPnP doing real discovery that can return multiple IGDs (e.g. on networks with cascaded NATs), this path is reachable and will produce subtle Swing threading violations.

**Fix:** Return the candidate list from `doInBackground()` via the `SwingWorker` result type, then handle the multi-router prompt in `done()` before completing the connection.

---

### Important

#### F4 — Non-atomic `settings.xml` write

**Category 14 — File I/O correctness.** Confidence: high.

[`SettingsStorage.java:157-167`](src/main/java/org/chris/portmapper/SettingsStorage.java#L157-L167):

```java
final File file = new File(directory, filename);
try (FileOutputStream out = new FileOutputStream(file); ...
     XMLEncoder encoder = new XMLEncoder(bout)) {
    encoder.writeObject(object);
}
```

`new FileOutputStream(file)` truncates the existing file *before* writing begins. If the JVM crashes, the OS kills the process, or a flush fails partway through, `settings.xml` is left partially written. `XMLDecoder` then fails to parse it on next launch, and `SettingsStorage.load()` falls back to a fresh `Settings` — silently discarding all user presets.

The checklist category 14 explicitly flags atomic writes as "worth it for settings persistence."

**Fix:**

```java
final Path tmp = directory.toPath().resolve(filename + ".tmp");
final Path target = directory.toPath().resolve(filename);
try (OutputStream out = Files.newOutputStream(tmp); ...) {
    encoder.writeObject(object);
}
Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
```

`ATOMIC_MOVE` falls back gracefully on Windows (which still does an atomic rename when source and target are on the same volume).

---

#### F2 — Orphaned `ConnectTask_*.properties` files

**Category 1 — Dead code.** Confidence: high (verified by grep).

Four locale property files exist whose keys are never referenced by any code:

- [`ConnectTask_en.properties`](src/main/resources/org/chris/portmapper/resources/ConnectTask_en.properties)
- [`ConnectTask_de.properties`](src/main/resources/org/chris/portmapper/resources/ConnectTask_de.properties)
- [`ConnectTask_it.properties`](src/main/resources/org/chris/portmapper/resources/ConnectTask_it.properties)
- [`ConnectTask_zh_CN.properties`](src/main/resources/org/chris/portmapper/resources/ConnectTask_zh_CN.properties)

These described the old BSAF `Task` subclass (title, description, startMessage, errorMessage, finishedMessage, updateAddresses, updatePortMappings). The `ConnectTask` BSAF replacement in step 5 became a plain `SwingWorker` with no bundle lookups. No code path calls `Messages.get` for any `ConnectTask.*` key, and there is no class that would load this bundle.

`-Werror` does not catch stale resource keys; this slips through the build wall.

**Fix:** Delete all four files. Run `./gradlew build` to confirm no breakage.

---

#### F7 — `PortMappingPreset.getCompleteDescription()` is structurally broken

**Category 1 — Dead code / correctness.** Confidence: high.

[`PortMappingPreset.java:87-99`](src/main/java/org/chris/portmapper/model/PortMappingPreset.java#L87-L99):

```java
public String getCompleteDescription() {
    final StringBuilder b = new StringBuilder();
    b.append(" ");
    b.append(remoteHost);     // null if any-host
    b.append(":");
    b.append(" -> ");
    b.append(internalClient); // null if localhost mode
    b.append(":");
    b.append(" ");
    b.append(" ");
    b.append(description);
    return b.toString();
}
```

The port numbers are completely absent from the output (unlike `PortMapping.getCompleteDescription()`, which formats them correctly). The only current caller is [`PortMapperApp.saveSettings()`](src/main/java/org/chris/portmapper/PortMapperApp.java#L122) at the TRACE log level, so the bug is invisible in normal operation but the method is misleadingly named.

**Fix:** Either mirror the `PortMapping.getCompleteDescription()` format and include port data from the `ports` field, or delete the method and remove its single TRACE-level caller.

---

#### F8 — `PresetListModel` listener never removed

**Category 13 — Swing resource & listener lifecycle.** Confidence: medium.

[`PresetListModel.java:40-43`](src/main/java/org/chris/portmapper/gui/PresetListModel.java#L40-L43) (constructor):

```java
public PresetListModel(final Settings settings) {
    this.settings = settings;
    settings.addPropertyChangeListener(Settings.PROPERTY_PORT_MAPPING_PRESETS, this);
}
```

There is no corresponding `removePropertyChangeListener` call. `Settings` is a singleton for the application lifetime, so this only becomes a real leak if `PortMapperView` (and thus a new `PresetListModel`) is ever recreated without also recreating `Settings`. In the current single-window app that doesn't happen, so the practical impact is low. However, the checklist explicitly flags this pattern.

**Fix:** Override `removeNotify()` on the list-model (it's called when the JList is removed from its parent), or expose a `dispose()` method called from `PortMapperView` cleanup paths. Simplest: keep the listener registration as-is but document the intentional lifetime tie in a javadoc comment so future-reviewers don't re-flag it.

---

### Informational (flagged, low priority)

#### F5 — `SBBIRouter.getUpTime()` is dead public API

**Category 1 — Dead code.** Confidence: high (verified — no callers anywhere in tree).

[`SBBIRouter.java:221-224`](src/main/java/org/chris/portmapper/router/sbbi/SBBIRouter.java#L221-L224):

```java
public long getUpTime() {
    // The SBBI library does not provide a method for getting the uptime.
    return 0;
}
```

Not declared in `IRouter`, no call site exists. Dead public methods on non-final classes cannot be eliminated by the compiler, so they add maintenance surface unnecessarily.

**Fix:** Delete the method.

---

#### F6 — Misleading class-level javadoc and SBBI cross-link in `JUPnPPortMappingExtractor`

**Category 9 — Documentation drift.** Confidence: high.

[`JUPnPPortMappingExtractor.java:30`](src/main/java/org/chris/portmapper/router/jupnp/JUPnPPortMappingExtractor.java#L30) imports `net.sbbi.upnp.impls.InternetGatewayDevice` solely so the class-level javadoc `@link` resolves:

```java
import net.sbbi.upnp.impls.InternetGatewayDevice;

/**
 * This class fetches all {@link PortMapping} from an {@link InternetGatewayDevice}.
 */
```

But `JUPnPPortMappingExtractor` does not operate on SBBI's `InternetGatewayDevice` at all — it works via jUPnP's `Service`/`ActionService` (see field `wanIPService` at the top of the class). The link is semantically wrong, and the SBBI import is only kept alive by the broken link.

**Fix:** Rewrite the class-level javadoc to reference jUPnP's `org.jupnp.model.meta.Service`. Delete the SBBI import once the link is rewritten — javac's `-Xlint:all` will then accept the file. (Caveat: if the SBBI jar were ever removed from `lib/`, the current broken javadoc would not actually fail the build because the import resolves fine while SBBI is present; this is a latent break.)

---

#### F9 — Duplicated router-factory instantiation logic between `PortMapperCli` and `PortMapperApp`

**Category 3 — Duplication.** Confidence: medium.

[`PortMapperCli.java:173-193`](src/main/java/org/chris/portmapper/PortMapperCli.java#L173-L193) duplicates the `createRouterFactory()` / `getClassForName()` pair also in [`PortMapperApp.java:271-307`](src/main/java/org/chris/portmapper/PortMapperApp.java#L271-L307). The CLI version additionally creates a throwaway `new PortMapperApp()` ([line 180](src/main/java/org/chris/portmapper/PortMapperCli.java#L180)) solely to satisfy the factory constructor's `(PortMapperApp)` parameter — an architectural seam left over from the original design.

The duplication is currently benign because both paths do the same thing. The risk is divergence: if the constructor contract changes, one site is updated and the other is not.

**Fix (deferred):** Extract factory instantiation to a static helper on `AbstractRouterFactory`, or relax the factory constructor contract to accept `null`/an injectable supplier instead of a full `PortMapperApp`. This is entangled with the "CLI creates throwaway `PortMapperApp`" design smell.

---

#### F10 — `SinglePortMapping.copy()` is an acknowledged no-op

**Category 1 — Dead code.** Confidence: high.

[`SinglePortMapping.java:47-49`](src/main/java/org/chris/portmapper/model/SinglePortMapping.java#L47-L49):

The method is documented as "no-op return-self; kept as a stable API affordance." No call sites for `copy()` exist anywhere in tree (verified by grep). The "stable API" rationale doesn't carry weight in a private fork with no external consumers; the method was retained during the records refactor to avoid changing call sites that were also being updated in the same commit. Those call sites are now updated, so the affordance can be retired.

**Fix:** Delete the method.

---

## Categories with no findings

These checklist categories were reviewed and found clean for this codebase state:

- **Cat 4 — Naming & consistency.** Package names (`jupnp`, `sbbi`, `weupnp`, `dummy`) correctly reflect their backends; no stale `Cling*` references remain in source; action-name string constants line up with the `.properties` keys.
- **Cat 6 — UPnP protocol response handling.** Error codes 713, 714, 402, 899 are treated as clean-stop sentinels in both `JUPnPPortMappingExtractor` and `SBBIPortMappingExtractor`. No loop treats 713 as an error.
- **Cat 7 — Java type safety.** The `@SuppressWarnings("unchecked")` uses in `AbstractJUPnPAction` and `JUPnPRegistryListener` are genuinely forced by jUPnP's raw-typed API; each is narrowly scoped. The records refactor (this period) addressed the model-types-should-be-records line item.
- **Cat 8 — Test gaps.** No new code paths in this period lack coverage at the level the existing suite provides. Coverage overall is thin and tracked separately in `doc/TODO.md`.
- **Cat 10 — Performance.** `getPortMappings()` enumeration is bounded by `MAX_NUM_PORTMAPPINGS = 500` and stops on the first terminal error code. No N+1 issues beyond the inherent UPnP-IGD protocol design.
- **Cat 11 — Robustness.** `IRouter` extends `AutoCloseable`; the CLI path uses try-with-resources around `connect()`. The GUI path calls `disconnectRouter()` via the shutdown hook. `JUPnPRouterFactory.shutdownServiceOnExit` correctly registers a shutdown hook for the `UpnpService`.
- **Cat 15 — Settings.xml backward compatibility.** The Cling→jUPnP FQCN migration shim in `Settings.getRouterFactoryClassName()` is still in place. Record-based `PortMappingPreset` / `SinglePortMapping` round-trip correctly through `XMLEncoder`/`XMLDecoder` via the canonical constructor.
- **Cat 16 — Reflection-by-FQCN contract.** All four factory classes expose `public (PortMapperApp)` constructors. `DummyRouterFactory` is in the settings dropdown by design (for testing).
- **Cat 17 — Transitional library coupling.** BSAF removal is complete. No new `org.jdesktop.application.*` imports exist. This checklist item can be repurposed for the next in-flight migration or deleted entirely; per the instruction at the bottom of the item, deletion is appropriate.
- **Cat 18 — GPL-3 license headers.** All source files carry the required `Copyright (C) 2015 Christoph Pirkl` header. The Hierynomus plugin enforces this at build time.
- **Cat 19 — `-Werror` / `-Xlint` cleanliness.** Build passes. `@SuppressWarnings` usages are either jUPnP raw-type forced, `this-escape` for Swing constructors, or Sonar-namespaced (`squid:`) which `javac` ignores.
- **Cat 20 — Logging & diagnosability.** All code uses SLF4J `LoggerFactory`. No `System.out` usage in main code (CLI help/error messages go through picocli and `CommandLineArguments.printHelp()`). No credentials or full SOAP envelopes logged at INFO.
- **Cat 21 — TODO/FIXME audit.** No `TODO`/`FIXME`/`HACK` markers in source (verified by grep). Outstanding work is tracked in `doc/TODO.md`.
- **Cat 2 — Dependencies.** `commons-jxpath:1.1` is correctly `runtimeOnly` (SBBI only). `picocli:4.7.7` is the active CLI library. No dead dependencies identified in `build.gradle` for the current backend set.

---

## Out-of-scope items

Items that came up during review but are deferred. They should be retained in `doc/TODO.md` if not already there.

1. **`Settings` migration shim removal** — already tracked in TODO.md backlog. Remove after the maintainer's `settings.xml` self-heals (one launch cycle).
2. **Wave 2 step 7 smoke test** — final whole-app smoke against jUPnP, weupnp, About dialog. Already tracked.
3. **README `-h` help block regeneration** with picocli's actual output. Already tracked.
4. **Re-enable GitHub Actions CI.** Already tracked.
5. **SBBI lifecycle decision.** Already tracked.
6. **`Actions.subscribePropertyListener` uses reflection** to call `addPropertyChangeListener`. A rename breaks it silently; direct typed dispatch would be safer. Requires changing the `Actions` API surface — a separate task.
7. **`WeUPnPRouter.getInternalHostName()` uses `url.trim().length() == 0`** instead of `url.isBlank()` (Java 11+). Trivial cosmetic fix; below the threshold for a standalone commit, can land with any other change in the file.
8. **`LogMessageOutputStream` threading** — `addMessage()` and `registerListener()` access `logListener` and the buffer without synchronization. In practice one writer (Logback appender) and one reader (the UI registration path), and the race window is essentially startup-only. Fix: make `logListener` `volatile`, synchronize `registerListener`. Deferred because the practical risk is small and no issues have been reported.
9. **`PortMappingPreset` implements `Serializable`** but is persisted via `XMLEncoder`, not Java object serialization. The `serialVersionUID` and `Serializable` interface are technically unnecessary (though harmless). Cosmetic.
10. **`SpinnerCellEditor` and `TextNumberCellEditor`** in [`gui/util/`](src/main/java/org/chris/portmapper/gui/util/) — verify whether they are still instantiated anywhere. The ports table now uses `DefaultCellEditor` with a `JComboBox` for protocol and raw integer editing. Confirm with a thorough grep before deleting.

---

## Suggested commit sequence

If the user wants to action these, the natural isolation would be (in increasing risk order):

1. **Dead-code sweep** — F2 + F5 + F6 + F10 in one commit (low risk, all deletes/javadoc).
2. **`getCompleteDescription` fix** — F7 alone (small but a correctness change).
3. **Atomic settings write** — F4 alone (file-I/O semantics).
4. **EDT compliance** — F1 + F3 in one commit (they touch the same `ConnectTask`/`PortMapperView` machinery; logically one change).
5. **Listener lifecycle hardening** — F8 alone (small, behavioral).
6. **Factory-instantiation deduplication** — F9 alone (touches both `PortMapperApp` and `PortMapperCli`).
