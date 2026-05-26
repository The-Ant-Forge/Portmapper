### Review Checklist (UPnP PortMapper)

Tailored for this Java 21 LTS Swing fork. Use these categories when reviewing
any non-trivial change. Items are ordered roughly from "always check" to
"check if the diff touches that surface."

1. **Dead code** — unused classes, methods, imports, properties keys in
   `*.properties` bundles, unused `@Action` names. Note that `-Werror`
   already catches unused imports, but stale resource keys and orphan
   action names slip past it.

2. **Dead and under-utilized dependencies** — anything in `build.gradle`
   not referenced by the code (drop entirely) and anything referenced
   by only a handful of call sites where inlining or vendoring a small
   helper would shrink the dep footprint. Also CVE exposure on
   transitive deps. Currently flagged for utilization review: `args4j`
   is touched only by `CommandLineArguments` (candidate for inlining
   or swapping to picocli); `commons-jxpath:1.1` is pinned only for
   the SBBI backend and would be droppable if SBBI is dropped.

3. **Duplication** — repeated UPnP error decoding, duplicated dialog
   wiring across `AboutDialog` / `SettingsDialog` / `EditPresetDialog`,
   parallel router-implementation logic that should live in
   `AbstractRouter` / `AbstractRouterFactory`.

4. **Naming & consistency** — package names match the backend they wrap
   (`jupnp`, `sbbi`, `weupnp`, `dummy`); stale `Cling*` references after
   the rename; action-name string constants in `PortMapperView` line up
   with the `.properties` keys.

5. **Error handling** — `RouterException` is the user-facing failure
   contract for every `IRouter` method. Verify it is thrown (not
   swallowed, not wrapped in `RuntimeException`), that the message is
   actionable, and that GUI call sites surface it via the log panel or
   a `JOptionPane`. CLI mode must exit non-zero on failure.

6. **UPnP protocol response handling** — UPnP error code **713**
   ("SpecifiedArrayIndexInvalid") is the standard sentinel for
   "no more mappings"; loops that enumerate mappings must treat 713
   as a clean stop, not a failure. Check action timeouts, retry/backoff
   on transient SOAP faults, and multi-router selection semantics
   (current code is single-router; flag any new code that assumes a
   list).

7. **Java type safety** — raw types, missing generics, `@SuppressWarnings`
   without a justifying comment, `Object` parameters where a generic
   or sealed type would do, missing `@Override`. Records are available
   for `PortMapping` / `Protocol` / preset value-objects; flag PoJos
   that should be records.

8. **Test gaps** — current coverage is thin: `TestPortMappingExtractor`
   (SBBI extractor), `TestCommandLineArguments` (CLI parsing),
   `TestDummyRouter` (IRouter contract via DummyRouter). Any new code
   path on the IRouter contract, CLI arg surface, or extractor logic
   should grow a test. Use JUnit 5 + Mockito with `MockitoExtension`,
   mirror the package path under `src/test/java/`.

9. **Documentation drift** — `CLAUDE.md`, `doc/bsaf-plan.md`, javadoc
   on `IRouter` / `AbstractRouterFactory`, and bundled `.properties`
   strings must still describe what the code does. `javadoc.failOnError`
   is on; missing `@param` / `@return` is a build break, not just a
   review nit.

10. **Performance** — port-mapping enumeration runs against a live
    router and is the dominant user-visible latency. Avoid N+1 SOAP
    calls inside enumeration loops; cache `getExternalIPAddress`
    results for the duration of a session if used by multiple views.

11. **Robustness** — `IRouter` extends `AutoCloseable`; check that
    every router acquisition uses try-with-resources or wires through
    the `addExitListener` cleanup. Watch for leaked
    `org.jupnp.UpnpService` instances and unclosed HTTP connections in
    new code.

12. **Swing concurrency (EDT discipline)** — UPnP discovery and SOAP
    calls **must not** run on the Event Dispatch Thread. Use
    `SwingWorker` (or BSAF `Task` while it still exists) for any router
    operation; marshal results back with `SwingUtilities.invokeLater`.
    Reviewer should flag any direct `router.xxx()` call from an
    `ActionListener` body and any `JOptionPane` raised from a worker
    thread.

13. **Swing resource & listener lifecycle** — dialogs must `dispose()`
    when closed, `PropertyChangeListener`s added to `Settings` should
    be removed when the listening view goes away, timers / executors
    started for discovery must be shut down on disconnect. Flag
    listener leaks that pin the old `PortMapperView` after
    reconnection.

14. **File I/O correctness** — `settings.xml` lives under
    `%AppData%\UnknownApplicationVendor\PortMapper\` on Windows; use
    `java.nio.file` and UTF-8 explicitly. Atomic writes (write to
    `.tmp`, then `Files.move` with `ATOMIC_MOVE`) are worth it for
    settings persistence — a half-written `settings.xml` loses every
    preset.

15. **Settings.xml backward compatibility** — any rename of a field
    on `Settings` or any persisted model breaks loading of older
    files. Either keep the old field name, or add a migration shim
    (see `Settings.getRouterFactoryClassName()` for the established
    pattern). This becomes critical during the BSAF → XMLEncoder
    migration; see [doc/bsaf-plan.md](bsaf-plan.md) §4.

16. **Reflection-by-FQCN extension contract** — `RouterFactory`
    classes are resolved by fully-qualified class name from settings
    or the `-lib` CLI flag, **not** `ServiceLoader`. Every concrete
    factory must keep a `public RouterFactory(PortMapperApp app)`
    constructor and remain in the package recorded in existing
    `settings.xml` files (or carry a migration shim). Renaming or
    relocating a factory class is a settings-compat hazard.

17. **Transitional library coupling** — when actively replacing a
    library, flag new code that deepens coupling to the library
    being phased out (it makes the migration harder). **Currently
    in flight**: BSAF removal — prefer `Messages.get` over new
    `ResourceMap` lookups, plain `AbstractAction` / lambda
    `ActionListener` over new `@Action` annotations,
    `java.awt.Desktop` over `OSXAdapter`. Existing BSAF use is
    grandfathered; new BSAF coupling is the concern. **Delete this
    item once BSAF is fully removed** (or repurpose it for the next
    in-flight migration).

18. **GPL-3 attribution & license-header compliance** — every source
    file must carry the `Copyright (C) 2015 Christoph Pirkl` header
    from `gradle/license-header.txt`. New files: run
    `./gradlew licenseFormat` before committing or the build fails.
    Reviewer should still eyeball it — the plugin catches missing
    headers but not subtle edits to the header text.

19. **`-Werror` + `-Xlint:all,-classfile` cleanliness** — every javac
    warning is a build break. New code must not introduce missing
    `@param`/`@return`, unused imports, raw types, deprecation
    warnings, or unchecked cast warnings. If `@SuppressWarnings` is
    truly required, narrow the scope and comment why.

20. **Logging & diagnosability** — use SLF4J (`LoggerFactory.getLogger`),
    never `System.out` or `java.util.logging` directly (jul is bridged
    but new code shouldn't lean on the bridge). Don't log router
    credentials, full SOAP envelopes at INFO, or local IP/MAC at
    levels above DEBUG. The in-app log panel shows everything the
    user sees; treat it as user-visible output.

21. **TODO / FIXME / HACK audit** — resolve or remove stale markers.
    Anything left in tree should reference an open item in
    `doc/TODO.md` or `doc/bsaf-plan.md`.

### Deliverable

A review document under `doc/` named `Code-Review-YYMMDD.md` containing:

- **Summary table** with columns: Category, Description, Action,
  Impact, Effort, Risk
- **Detailed findings** grouped by category, ordered by impact then
  effort. Cite file paths with line ranges
  (e.g. `src/main/java/.../Foo.java:42-58`).
- **Out-of-scope items** captured in `doc/TODO.md` so they survive
  the review session.

### Process

1. Produce the review document only — **do not** implement fixes
   during review.
2. Walk findings with the user; agree on which items to action and in
   what order.
3. Implement approved items as **focused, isolatable commits** (one
   category per commit where possible — see modernization wave-2
   guidance in `CLAUDE.md`).
4. After each change: `./gradlew build` must stay green, including
   `licenseFormat`, tests, and the shadowJar step.
5. For changes that touch `Settings` or any persisted model:
   smoke-test loading an existing `settings.xml` from
   `%AppData%\UnknownApplicationVendor\PortMapper\` before declaring
   done.
