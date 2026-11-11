# bb-dev

An all-in-one native developer tool for BlackBerry 10 devices. A single executable with five
namespaces: `connect`, `deploy`, `package`, `debug`, and `pull`.

```
bb-dev <connect|deploy|package|debug|pull> [--json] [options...]
```

| Global flag | Effect |
|---|---|
| `--json` | Emit one JSON object instead of plain text. |
| `-daemon` | Serve `deploy` and `package` as a newline-delimited JSON daemon on stdin/stdout. |
| `help` | Print help. Use `bb-dev <command> -help` for a command's own options. |

---

## connect

Start the device's SSH daemon and hold the session (required for `debug` and `pull`).

```
bb-dev connect <host> [-sshPublicKey <path>] [-password <pw>]
```

The SSH public key defaults to Momentics' own `~/.rim/bbt_id_rsa.pub`, so an existing Momentics
install connects with no key argument — the device already authorises that key. When the default key
is absent, an RSA key pair is generated there once (same name as Momentics), the private half so you
can then SSH in. Pass `-sshPublicKey <path>` to use a different key; an explicit key is used as-is
and never generated.

```
$ bb-dev connect 192.168.1.82 -password •••••
Connecting to 192.168.1.82:4455
Authenticating
SSH daemon started on 192.168.1.82; holding the session — stop this command to end it

$ bb-dev --json connect 192.168.1.82 -password •••••
{"ok":true,"host":"192.168.1.82","sshd":true,"sshKey":"/Users/you/.rim/bbt_id_rsa.pub","generatedKey":false}
```

---

## deploy

Install and manage applications over the device's HTTPS service.

```
bb-dev deploy <command> --device <host> [-p <pw>] [--port <n>] [--json]
```

| Command | Description |
|---|---|
| `test` | Check the connection and login. |
| `info` | Show the device's properties. |
| `apps` | List the installed applications. |
| `install <bar>` | Install an application. |
| `launch <app>` | Launch an installed application. |
| `terminate <app>` | Stop a running application. |
| `running <app>` | Report whether an application is running. |
| `verify <app>` | Verify an installed application. |
| `uninstall <app>` | Uninstall an application. |
| `get <app> <path>` | Download a file from the application's sandbox. |
| `put <app> <local> <path>` | Upload a file into the application's sandbox. |

`install` options: `--launch`, `--dev` (development-mode install), `--no-sync` (full upload),
`--debug-host <host>`, `--debug-native` (hold at start-up for a debugger). `get` option: `--out <file>`.
`<app>` is the full name shown by `apps`.

```
$ bb-dev deploy test --device 192.168.1.82 -p •••••
✓ Connected.

$ bb-dev deploy apps --device 192.168.1.82 -p •••••
com.example.TLSDeBug.testDev_le_TLSDeBugc09806e9   1.0.0.1   4.3 MB

$ bb-dev --json deploy running com.example.TLSDeBug… --device 192.168.1.82 -p •••••
{"ok":true,"running":true}
```

---

## package

Build an unsigned BlackBerry application archive (`.bar`) from native binaries and a descriptor.

```
bb-dev package -package <out.bar> <bar-descriptor.xml> <files...> [options]
```

Commands: `-package` (default), `-list`, `-listManifest`. Key options: `-appVersion <X.Y.Z>`,
`-buildId <id>`, `-devMode`, `-configuration <name>`, `-C <dir>`, `-e <file> <path>`,
`-generateXml <file>`. On-device: `-installApp`, `-launchApp`, `-device <host>`, `-password <pw>`.
`@<file>` reads arguments from a file.

```
$ bb-dev package -package MyApp.bar bar-descriptor.xml arm/o.le-v7/MyApp assets
```

---

## debug

Inspect a running or crashed application over the device's qconn launcher (requires a held `connect`).

```
bb-dev debug <command> [arguments]
```

| Command | Description |
|---|---|
| `run <host> <command> [args...]` | Spawn an executable and stream its console. |
| `core <host> <app>` | Pull the newest crash core from the application's logs. |
| `logs <host>` | Stream the device log (slog2info). |

`run` options: `--cwd <dir>`, `--env NAME=VALUE` (repeatable), `--pty`, `--hold`, `--port <n>`.
`core` options: `-p <pw>`, `--out <dir>`, `--port <n>`.
`logs` options: `--once` (dump, don't follow), `--grep <text>`, `--port <n>`, `-- <slog2info option>...`.

```
$ bb-dev debug run 192.168.1.82 cat /etc/os.version
[pid 315359390]
10.3.3.3216
[exit 0]
```

---

## pull

Read a device file or directory listing to the host (requires a held `connect`).

```
bb-dev pull <host> <device-path> [-o <local>]
bb-dev pull <host> --list <dir>
```

```
$ bb-dev pull 192.168.1.82 --list /tmp
bbfd
bbfdfix
bbdev-p2.txt
```
