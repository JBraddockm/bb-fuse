package com.jbraddockm.tunnel;

import java.util.Locale;

/**
 * The host-OS gate for the macOS-only tunnel. The utun interface and routes are macOS-specific (see
 * {@link TunDevice}); the handshake and data plane are portable. The CLI and the tunnel bring-up
 * seam both check this so a non-macOS host is refused cleanly rather than crashing partway.
 */
final class Platform {

  private Platform() {}

  /** Whether this host is macOS. */
  static boolean isMac() {
    return isMac(System.getProperty("os.name", ""));
  }

  /** Whether {@code osName} (an {@code os.name} value) is macOS. */
  static boolean isMac(String osName) {
    return osName != null && osName.toLowerCase(Locale.ROOT).contains("mac");
  }
}
