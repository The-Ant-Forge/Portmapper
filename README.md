# UPnP PortMapper (The Ant Forge fork)

UPnP PortMapper is a desktop application for managing the port mappings (port forwarding) of a UPnP-enabled router on the local network. View, add, and remove port mappings without touching the router's web interface; preset support makes recurring mappings (game servers, SSH, web servers) a one-click affair.

This repository is **The Ant Forge's private modernization fork** of the original [UPnP PortMapper by Christoph Pirkl](https://github.com/kaklakariada/portmapper), licensed under GPL-3.0. Upstream development was effectively dormant at v2.2.4; this fork modernizes the toolchain and dependencies for current Java. **No work in this repository is submitted upstream**, and **no artifacts are published to Maven Central.**

## Requirements

- **Java 21 LTS** or later. The Adoptium Temurin builds are recommended: <https://adoptium.net/temurin/releases/?package=jre&version=21>

Verify with:

```
java -version
```

## Running

```sh
java -jar portmapper-<version>-all.jar
```

or build and run in one step from a clone:

```sh
./gradlew run
```

## Command-line interface

PortMapper supports a headless CLI mode in addition to the Swing GUI. See available options:

```
java -jar portmapper-<version>-all.jar -h
 -add                  : Add a new port mapping
 -delete               : Delete a port mapping
 -description VAL      : Description of the port mapping
 -externalPort N       : External port of the port mapping
 -gui                  : Start graphical user interface
 -h (-help)            : Print usage help
 -info                 : Print router info
 -internalPort N       : Internal port of the port mapping
 -ip VAL               : Internal IP of the port mapping (default: localhost)
 -lib VAL              : UPnP library to use
 -list                 : Print existing port mappings
 -protocol [TCP | UDP] : Protocol of the port mapping
 -routerIndex N        : Router index if more than one is found (zero-based)
```

### Examples

- Add a port mapping for a specific IP:

  ```sh
  java -jar portmapper-<version>-all.jar -add -externalPort <port> -internalPort <port> -ip <ip-addr> -protocol tcp
  ```

- Add a port mapping for the local machine (omit IP):

  ```sh
  java -jar portmapper-<version>-all.jar -add -externalPort <port> -internalPort <port> -protocol tcp
  ```

- Delete a port mapping:

  ```sh
  java -jar portmapper-<version>-all.jar -delete -externalPort <port> -protocol tcp
  ```

- List existing port mappings:

  ```sh
  java -jar portmapper-<version>-all.jar -list
  ```

- Use a specific UPnP backend library:

  ```sh
  java -jar portmapper-<version>-all.jar -lib org.chris.portmapper.router.weupnp.WeUPnPRouterFactory -list
  ```

### UPnP libraries

PortMapper ships three third-party UPnP libraries. If the default doesn't work for your device, try a different one:

- [Cling](https://github.com/4thline/cling): `org.chris.portmapper.router.cling.ClingRouterFactory` (default)
- [weupnp](https://github.com/bitletorg/weupnp): `org.chris.portmapper.router.weupnp.WeUPnPRouterFactory`
- [SBBI UPnP lib](https://sourceforge.net/projects/upnplibmobile/): `org.chris.portmapper.router.sbbi.SBBIRouterFactory`
- `org.chris.portmapper.router.dummy.DummyRouterFactory` (for testing only)

> **Note:** Replacing the abandoned Cling/BSAF libraries with maintained alternatives (jUPnP, etc.) is on the modernization roadmap for this fork.

### Language

PortMapper detects your OS language and uses English (`en`), German (`de`), Italian (`it`), or simplified Chinese (`zh`). To override:

```sh
java "-Duser.language=de" -jar portmapper-<version>-all.jar
```

Note the quotes — required when passing `-D` options through PowerShell.

### Custom configuration directory

PortMapper stores GUI settings as XML in a per-user directory. On Windows that's `%AppData%\UnknownApplicationVendor\PortMapper\`. Override with:

```sh
java "-Dportmapper.config.dir=C:/path/to/config" -jar portmapper-<version>-all.jar
```

The directory must exist before launching. CLI mode does not read this directory — all options must be passed as args.

### Manually specifying a router URL

When using the `weupnp` backend, you can bypass UPnP discovery and connect directly to a known router URL:

```sh
java "-Dportmapper.locationUrl=http://192.168.178.1:49000/igddesc.xml" \
     -jar portmapper-<version>-all.jar \
     -lib org.chris.portmapper.router.weupnp.WeUPnPRouterFactory <args>
```

This is useful when a network bridge or unusual topology prevents auto-discovery from working.

## Troubleshooting

### Router not found

- Confirm the router has UPnP enabled.
- Try a different UPnP backend with `-lib`. `DummyRouterFactory` is for testing only.
- Disable any active network bridge on your machine — bridges sometimes break UPnP discovery.
- Set the log level to `TRACE` in the Settings dialog, reconnect, and inspect the log output.

### Adding port mappings fails

- Check that your router allows write access via UPnP (some only permit read).
- Verify port mappings can be added manually via the router's web UI as a sanity check.
- Try a different UPnP backend with `-lib`.

### Multiple routers

If your network has more than one UPnP gateway, use the `weupnp` or `sbbi` backends — both prompt you to select one. The `cling` backend only supports a single router.

### Expiring port mappings

Some routers garbage-collect port mappings after a period. To work around this, schedule the CLI to re-add the mapping periodically. Windows `.cmd` example:

```cmd
:loop
java -jar portmapper-<version>-all.jar -add -externalPort <port> -internalPort <port> -protocol tcp
timeout 21600
goto loop
```

### "Got error response when fetching port mapping" in the log

This is expected. UPnP doesn't expose the number of available port mappings, so PortMapper enumerates them until the router returns an error — which is the signal that there are no more. The error is informational, not a failure.

## Development

See the [developer guide](doc/developer_guide.md) for build instructions.

## License

GPL-3.0, inherited from the upstream project. See [LICENSE](LICENSE). The original work is copyright (C) 2015 Christoph Pirkl; this fork's modifications are additions under the same license.
