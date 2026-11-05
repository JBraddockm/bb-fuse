#!/bin/sh
# Install bb-tunneld as a macOS LaunchDaemon, so the tunnel broker runs under launchd and clients
# connect to it without `sudo`. Root is needed only to create the tunnel's utun interface and its
# routes; the socket is handed to the installing user so it can be used without root.
#
#   usage: bb-tunnel/scripts/install-tunneld.sh [path-to-bb-tunnel-binary]
#
# The binary defaults to ./build/bb-tunnel or ./bb-tunnel/target/bb-tunnel.
set -eu

LABEL="com.jbraddockm.bb-tunneld"
PLIST="/Library/LaunchDaemons/${LABEL}.plist"
BIN="/usr/local/libexec/bb-tunneld"
# The default socket that clients connect to.
SOCKET="/tmp/bb-tunneld.sock"
LOG="/var/log/bb-tunneld.log"

if [ "$(uname -s)" != "Darwin" ]; then
  echo "error: the tunnel daemon is macOS-only for now." >&2
  exit 1
fi

# Installing a LaunchDaemon requires root; re-run under sudo if needed.
if [ "$(id -u)" -ne 0 ]; then
  echo "Installing the bb-tunneld LaunchDaemon needs administrator rights."
  exec sudo -- "$0" "$@"
fi

# The user who should own the socket, so it can be used without root.
OWNER="${SUDO_USER:-root}"

# Resolve the source binary (argument, else a built one in the tree).
SRC="${1:-}"
if [ -z "$SRC" ]; then
  for candidate in "./build/bb-tunnel" "./bb-tunnel/target/bb-tunnel"; do
    if [ -x "$candidate" ]; then SRC="$candidate"; break; fi
  done
fi
if [ -z "$SRC" ] || [ ! -x "$SRC" ]; then
  echo "error: no bb-tunnel binary found; pass its path." >&2
  echo "       build one with: mvn -pl bb-tunnel -am package" >&2
  exit 2
fi

echo "bb-tunneld: installing ${SRC} -> ${BIN}  (socket ${SOCKET}, owner ${OWNER})"

# Stop any previous instance before replacing the binary and plist.
launchctl bootout system "${PLIST}" 2>/dev/null || true

install -d -m 755 "$(dirname "${BIN}")"
install -m 755 "${SRC}" "${BIN}"

cat > "${PLIST}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${LABEL}</string>
    <key>ProgramArguments</key>
    <array>
        <string>${BIN}</string>
        <string>serve</string>
        <string>${SOCKET}</string>
    </array>
    <key>EnvironmentVariables</key>
    <dict>
        <key>BB_TUNNELD_SOCKET_OWNER</key>
        <string>${OWNER}</string>
    </dict>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>ProcessType</key>
    <string>Background</string>
    <key>StandardOutPath</key>
    <string>${LOG}</string>
    <key>StandardErrorPath</key>
    <string>${LOG}</string>
</dict>
</plist>
EOF

chown root:wheel "${PLIST}"
chmod 644 "${PLIST}"

launchctl bootstrap system "${PLIST}"

echo "bb-tunneld: installed and started. Logs: ${LOG}"
echo "Clients connect to ${SOCKET} (owned by ${OWNER}); no per-run sudo."
echo "Uninstall with: bb-tunnel/scripts/uninstall-tunneld.sh"
