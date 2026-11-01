# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Jetty Manager publishes one Java 8 library, `com.devives:devive-jetty-container` (the `container` module). It launches and stops a real Jetty distribution (Jetty 9 **or** 12) from inside the host JVM via `jetty.start.Main` — so all standard Jetty `start.jar` XML/module configuration applies — while exposing a typed lifecycle API (`Container`/`Server`) with **no compile-time dependency on Jetty**. Jetty objects are reached only through reflection and dynamic proxies.

Shared utilities (`ExceptionUtils`, `Try`, `ProxyBuilder`, `AbstractLifeCycle`, `ClassLoaderUtils`) come from the external `com.devives:devive-commons-lang` library (resolved from `mavenLocal()` first). Javadoc and comments are written in Russian; match that style when editing existing files.

## Restrictions

**Do not add narrative comments** — no fix-rationale comments, no comments narrating what a line or block does, no comments explaining why a change was made. Let code and names speak. Javadoc on public API documents the contract only (parameters, outcomes, invariants), not the history or motivation of an edit. The user deletes such comments after the fact; never add them, including in fixes produced from code review.

## Commands

Gradle 8.10 wrapper (`./gradlew` / `gradlew.bat`). Toolchains are pinned to BellSoft Liberica JDKs: 8 for all published modules, 21 for the Jetty 12 test module (Gradle auto-provisions if not installed).

- `./gradlew build` — compile and jar all modules. **Tests are disabled by default** (root `build.gradle` sets `tasks.withType(Test) { enabled = false }`); individual modules opt back in, so plain `./gradlew test` runs nothing.
- `./gradlew :container:jar` — build the published artifact. This also triggers `:container-context:jar` and `:container-injection:jar`, whose version-less jars are copied (by `copyJarToContainerResources`) into `container/src/main/resources/com/devives/jetty/container/lib/`. Those jars are **generated and git-ignored — never edit or commit them by hand**.
- `./gradlew :container-test:jetty9:test` — the integration test suite (the only normally-run tests).
- Single test: `./gradlew :container-test:jetty9:test --tests "com.devives.jetty.container.Jetty9ContainerTest.startStop"`
- License header tasks (hierynomus plugin, `strictCheck true`): every source file must carry the ASF Apache header (`container/codequality/HEADER`) or license-check tasks fail the build.
- Publishing: `./gradlew publish` per module writes to a file repo at `<module>/build/repo`; `:container` and `:container-injection` additionally sign publications (requires signing properties, only for publish, not `build`). `./gradlew :container:zip-for-central-sonatype` assembles the Maven Central zip (collected in the untracked `distribution/` folder, which is not a Gradle module).

Running the integration tests requires a Jetty distribution unzipped **into the test module's directory** (git-ignored, downloaded manually; tests resolve paths relative to the module dir):
- `container-test/jetty9/jetty-distribution-9.4.58.v20250814/` (uses its bundled `demo-base` as `jetty.base`; binds ports 8080, and 9080 in the parallel test).
- `container-test:jetty12` is commented out in `settings.gradle`; to enable it, uncomment it and provide `container-test/jetty12/jetty-home-12.1.1/` plus a `jetty-base/` next to it, with a JDK 21 available.

## Architecture

### Classloader model (the core design)

Three classloader worlds exist at runtime, and code in `container` never references Jetty classes directly:

1. **Application classloader** — `Container`, `ContainerBuilder`, `ServerAdapter`, public `Server`, and the `contract.*` interfaces.
2. **Start classloader** — a `URLClassLoader` over `start.jar` plus extra start jars, parent = app classloader (built by `ContainerBuilder.buildJettyClassLoader`). `Container.onStart()` reflectively invokes `org.eclipse.jetty.start.Main.main(startArgs)` in it; `Main` then builds Jetty's own layered classloaders and starts the server in the current thread.
3. **Jetty's classloaders** — the server itself plus the two injected Devive jars.

The bridge is the `@Contract(className = "...")` annotation plus `ProxyBuilder` from devive-commons. Each contract interface (`Main`, `Server`, `LifeCycle`, `Environment`, `ShutdownMonitor`, `ShutdownService`, `ContainerContext`) mirrors a foreign class; its `Statics` accessor loads the named class from a given classloader and invokes its statics reflectively, while `ProxyBuilder.forClasses(...).setTarget(foreignObject)` wraps foreign instances (the Jetty server object, its beans via `ServerAdapter.getBeans`) behind the local interface. That is how the API stays typed without Jetty on any classpath.

### Injection mechanism (how Jetty's lifecycle events get back out)

`ContainerBuilder.injectContainerResources()` extracts three resources from the container jar into a temp dir (tracked for deletion on container close), wiring them differently per Jetty major version:

- `lib/devive-jetty-container-context.jar` (from `:container-context`) → added as a **start jar**. Contains only `ContainerContext` (a `ThreadLocal<ServerEventListener>`) and the `ServerEventListener` interface — deliberately Jetty-free so it can sit anywhere in Jetty's loader hierarchy.
- `lib/devive-jetty-container-injection.jar` (from `:container-injection`) → added via `--lib`. Contains `ServerEventListenerAdapter implements org.eclipse.jetty.util.component.LifeCycle.Listener`; its constructor captures the listener from `ContainerContext.getServerEventListener()` at creation time.
- `etc/jetty{9,12}/container-injection.xml` → passed as a start argument; the XML instantiates the adapter and registers it on the server (`addLifeCycleListener` on Jetty 9, `addEventListener` on Jetty 12).

Startup handshake: `Container.onStart()` reflectively calls `ContainerContext.setServerEventListener(...)` on the starting thread, runs `Main.main(startArgs)` **on that same thread** (the XML config instantiates the adapter, which reads the ThreadLocal), then nulls it in a `finally`. Thereafter events flow Jetty → adapter → proxy hop → `ServerAdapter.ServerProxyListener`, which maintains a state machine, start/stop `CompletableFuture`s, and fires `Container.Listener.onServerConstructed`. `ContainerBuilder.disableInjections()` turns this off so callers can wire the three resources themselves manually (see the `startStop_manualInjections` test for the pattern).

Because the injected jars are read from the container module's **resources**, changing sources in `container-context`/`container-injection` only takes effect once their `jar` tasks re-run and re-copy — always run `:container:jar` (not just `compileJava`) before testing behavior through the container.

### Jetty 9 vs 12 differences

- Version is auto-detected from the `start.jar` manifest `Implementation-Version`, or forced with `ContainerBuilder.setVersion(Version.Jetty9|Jetty12)`. It selects the injection XML and the stop plumbing.
- Actual stop port/key are read back after start: Jetty 12 exposes them via `ShutdownService` beans on the server; Jetty 9 via the static `ShutdownMonitor` instance.
- `ServerAdapter.closeJettyClassLoaders()` closes `Environment` classloaders on stop for Jetty ≥ 12 — a fix for Jetty not closing its `URLClassLoader`s (file-handle leaks).

### Concurrency / lifecycle gotchas

- `Container.onStart()` runs entirely under a JVM-global lock (`ENV_VARIABLES_LOCK`) and sets `STOP.PORT`/`STOP.KEY`/`STOP.EXIT` system properties only around `Main.main`, clearing them afterwards. This is because Jetty's static `ShutdownMonitor` reads those properties, and concurrent container starts would otherwise share one stop-port value and fail on the socket. Keep any start-path changes inside this lock.
- `container.stop()` / `close()` stops the server synchronously through the proxy; `stopAsync()` re-invokes `Main.main` with `--stop` and the actual port/key over the stop socket, completing its `Future` when the `handleStopped` event arrives. `close()` also deletes the temp injection files and closes the start classloader.
- In `Jetty9ContainerTest`, `startStop_OnJvmExit` is `@Order`ed last on purpose — it relies on JVM shutdown to stop the server and holds port 8080 until exit.

### Modules

- `container` — the published artifact; public API is `ContainerBuilder` → `Container` (a `LifeCycle`, `AutoCloseable`), `Server`, `Version`. Embeds the other two modules' jars and injection XMLs as resources.
- `container-context` — the ThreadLocal bridge injected into Jetty's start loader (declared against Jetty 12 only to prove it uses no Jetty API; it imports none).
- `container-injection` — the `LifeCycle.Listener` adapter injected into Jetty's lib loader; compiled `compileOnly` against Jetty 9.4 (the listener API it uses is compatible with 12).
- `container-test` — shared HTTP assertion helper (`HttpURLConnectionBuilder`, main sources).
- `container-test/jetty9`, `container-test/jetty12` — start/stop integration tests against real distributions.

Each publishable module has its own `LICENSE`/`NOTICE` (jars embed them) and publishing config; Maven POM metadata lives in the module `build.gradle` files.
