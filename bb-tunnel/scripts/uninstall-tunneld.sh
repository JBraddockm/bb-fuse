#!/bin/sh
# Remove the bb-tunneld LaunchDaemon installed by install-tunneld.sh.
#
#   usage: bb-tunnel/scripts/uninstall-tunneld.sh
set -eu

LABEL="com.jbraddockm.bb-tunneld"
PLIST="/Library/LaunchDaemons/${LABEL}.plist"
BIN="/usr/local/libexec/bb-tunneld"
SOCKET="/tmp/bb-tunneld.sock"

# Removing a LaunchDaemon is root-only; elevate once.
if [ "$(id -u)" -ne 0 ]; then
  echo "Removing the bb-tunneld LaunchDaemon needs administrator rights."
  exec sudo -- "$0" "$@"
fi

launchctl bootout system "${PLIST}" 2>/dev/null || true
rm -f "${PLIST}" "${BIN}" "${SOCKET}"

echo "bb-tunneld: uninstalled."
