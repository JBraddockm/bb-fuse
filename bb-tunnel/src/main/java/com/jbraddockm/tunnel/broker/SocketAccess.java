package com.jbraddockm.tunnel.broker;

import java.util.Map;
import java.util.Optional;

/**
 * Who the freshly-bound broker socket is handed to, so an unprivileged consumer can connect. The
 * daemon runs as root (under {@code sudo} or launchd), so the default root-owned socket is not
 * connectable — this resolves the intended owner, and an optional group, from the environment the
 * launcher set.
 *
 * <ul>
 *   <li>{@code BB_TUNNELD_SOCKET_OWNER} names the user (launchd sets it in the plist, since there
 *       is no {@code SUDO_USER} under launchd);
 *   <li>{@code SUDO_USER} is the fallback for a {@code sudo bb-tunnel serve} run;
 *   <li>{@code BB_TUNNELD_SOCKET_GROUP} optionally shares the socket with a group.
 * </ul>
 *
 * <p>With no owner resolvable the daemon is already running as the connecting user, so the socket's
 * default owner access is left as is ({@link Optional#empty()}).
 *
 * @param owner the user to own the socket
 * @param group the group to share it with, or {@code null}
 * @param permissions the POSIX permission string to set (owner-only, or owner+group)
 */
record SocketAccess(String owner, String group, String permissions) {

  /** Resolves the intended socket access from the process environment, if any applies. */
  static Optional<SocketAccess> from(Map<String, String> environment) {
    var owner =
        firstNonBlank(environment.get("BB_TUNNELD_SOCKET_OWNER"), environment.get("SUDO_USER"));
    if (owner == null) {
      return Optional.empty();
    }
    var group = blankToNull(environment.get("BB_TUNNELD_SOCKET_GROUP"));
    var permissions = group != null ? "rw-rw----" : "rw-------";
    return Optional.of(new SocketAccess(owner, group, permissions));
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return blankToNull(second);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
