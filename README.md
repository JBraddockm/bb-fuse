# BlackBerry 10 Native Tools

A **clean-room** reimplementation of the **BlackBerry Link** and **BlackBerry Blend** device
protocols, the **bp2p Wi-Fi tunnel** they run over, and the BlackBerry 10 developer tools (device
connect, application deploy and packaging). Every module is written from scratch in **Java 25** and
builds to a standalone native executable with **GraalVM Native Image**.

> **Clean-room — no proprietary BlackBerry code.** This project does **not** use, bundle, or depend
> on any BlackBerry/QNX library, the BlackBerry 10 NDK, or the `blackberry-deploy` /
> `blackberry-packager` tools. The device protocols, the `login.cgi` password/TLS handshake, the
> `.bar` archive format, device discovery, pairing, the file share and the deploy/package paths are
> all independently reimplemented in pure Java on the JDK. **No NDK and no proprietary libraries are
> required to build or run.**

**Feature parity** with the original BlackBerry Link / Blend and the NDK developer tools is kept, **except for code
signing and debug-token issuance**, which are intentionally out of scope — they
depend on BlackBerry signing identities and RIM infrastructure. Everything else is supported:
unsigned and development-mode application install/uninstall, firmware install, device discovery and
pairing, PIM sync, backup and restore, the device file share, media metadata, and the device
connect / deploy / package developer workflow.

The project uses the **Community Edition of GraalVM 25**.

## Modules

- [bb-core](bb-core) — The shared, pure-JDK foundation the other modules build on: JSON, the daemon
  server loop, error types, `.bar` inspection and manifest validation, the device login + TLS client
  stack (`core.device`), and device discovery (`core.discovery`). It depends on nothing else in the
  repository.
- [bb-link](bb-link) — The BlackBerry Link protocol: session, pairing, PIM sync, update/install,
  backup and restore, the device file share, media metadata and discovery.
- [bb-tunnel](bb-tunnel) — The bp2p Wi-Fi tunnel the device's Wi-Fi service surface runs over.
- [bb-dev](bb-dev) — The native developer tools, replacing the NDK's `blackberry-connect`,
  `blackberry-deploy` and `blackberry-packager`: it starts the device's SSH daemon (connect) and
  deploys and packages applications — in pure JDK, with no BlackBerry tooling. (Signing and
  debug-token issuance are not included.)
- **bb-blend** — Implementation of BlackBerry Blend; coming soon.

## Requirements

- Java / GraalVM Community Edition 25
- Maven

No BlackBerry 10 NDK and no proprietary BlackBerry libraries are required.

## Setup

### 1. Install Maven

Apache Maven is required to build the project.

Maven can be installed using your operating system's package manager or manually. See the official
Maven installation documentation: [Apache Maven Installation Guide](https://maven.apache.org/install.html)

### 2. Install GraalVM 25

The project requires **GraalVM Community Edition 25**.

GraalVM must be available on your `PATH`, and `JAVA_HOME` must point to the GraalVM installation.

Verify the installation with:

```bash
java -version
```

You should see GraalVM 25 in the output.

Also verify that `JAVA_HOME` points to the same installation:

```bash
echo "$JAVA_HOME"
"$JAVA_HOME/bin/java" -version
```

For example:

```text
openjdk 25.0.1 2025-10-21
OpenJDK Runtime Environment GraalVM CE 25.0.1+8.1 (build 25.0.1+8-jvmci-b01)
OpenJDK 64-Bit Server VM GraalVM CE 25.0.1+8.1 (build 25.0.1+8-jvmci-b01, mixed mode, sharing)
```

You can also verify that the native-image tool is available:

```bash
native-image --version
```

If `native-image` is not found, make sure the GraalVM `bin` directory is included in your `PATH`.

## Project Structure

The repository contains multiple BlackBerry 10 tools, each built as a separate Maven module:

```text
.
├── bb-core/     (shared pure-JDK foundation)
├── bb-link/     (BlackBerry Link protocol)
├── bb-tunnel/   (bp2p Wi-Fi tunnel)
├── bb-dev/      (connect / deploy / package developer tools)
└── pom.xml
```

## Building

The native executables are built as part of the Maven `package` phase.

The project uses the GraalVM Native Image Maven plugin. The native compilation is configured with:

```xml

<executions>
    <execution>
        <id>build-native</id>

        <goals>
            <goal>compile-no-fork</goal>
        </goals>

        <phase>package</phase>
    </execution>
</executions>
```

Therefore, running Maven through the `package` phase automatically invokes the native-image build.

### Build all modules

To build all modules in the repository:

```bash
mvn package
```

For a clean build:

```bash
mvn clean package
```

This compiles the Java sources and then invokes GraalVM Native Image during the Maven `package`
phase to produce the native executable for each module.

### Build an individual module

To build the `bb-dev` developer tools:

```bash
mvn -pl bb-dev -am package
```

To build the `bb-link` module:

```bash
mvn -pl bb-link -am package
```

To build the `bb-tunnel` module:

```bash
mvn -pl bb-tunnel -am package
```

The tunnel runs as a background **macOS LaunchDaemon**, so clients reach it without `sudo` on every
call. After building, install it from the project root with:

```bash
scripts/install-tunneld.sh
```

This copies the collected `build/bb-tunnel` binary into place and starts it under `launchd`. Root is
needed once — to create the `utun` interface and its routes; the broker socket is then handed to your
user. Remove it with:

```bash
scripts/uninstall-tunneld.sh
```

> **The tunnel is macOS-only for now.**

See [bb-tunnel/README.md](bb-tunnel/README.md) for the identity-certificate and pairing requirements.

### Build Output

The resulting native executable is placed in the `target` directory of the corresponding Maven
module. The exact executable filename depends on the module configuration.

### Collected binaries: `build/`

For convenience, each executable module's native binary is also **copied to a single root `build/`
folder** during `package`:

```text
build/
├── bb-dev, bb-link, bb-tunnel   (native executables)
└── devices/
    └── <PIN>/     ← per-device certs / identities (created at run time)
```

`build/` is a copy, not a module target, so **`mvn clean` leaves it intact** — the collected binaries
survive a clean (only each module's `target/` is wiped). `build/` is git-ignored (the binaries can
embed certificate material, and `devices/` holds key material), so nothing there is committed.
