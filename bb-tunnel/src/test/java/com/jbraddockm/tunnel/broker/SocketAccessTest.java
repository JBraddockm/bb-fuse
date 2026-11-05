package com.jbraddockm.tunnel.broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link SocketAccess}: resolving who the broker socket is handed to. */
@DisplayName("SocketAccess: who the broker socket is handed to")
class SocketAccessTest {

  @Test
  @DisplayName("no owner in the environment leaves the socket as it is")
  void none() {
    assertThat(SocketAccess.from(Map.of())).isEmpty();
  }

  @Test
  @DisplayName("SUDO_USER owns the socket on a sudo run, with owner-only access")
  void sudoUser() {
    assertThat(SocketAccess.from(Map.of("SUDO_USER", "alice")))
        .hasValueSatisfying(
            access -> {
              assertThat(access.owner()).isEqualTo("alice");
              assertThat(access.group()).isNull();
              assertThat(access.permissions()).isEqualTo("rw-------");
            });
  }

  @Test
  @DisplayName("BB_TUNNELD_SOCKET_OWNER (set by launchd) takes precedence over SUDO_USER")
  void ownerOverride() {
    assertThat(SocketAccess.from(Map.of("BB_TUNNELD_SOCKET_OWNER", "bob", "SUDO_USER", "alice")))
        .get()
        .extracting(SocketAccess::owner)
        .isEqualTo("bob");
  }

  @Test
  @DisplayName("a group opens owner+group access and sets the group")
  void withGroup() {
    assertThat(SocketAccess.from(Map.of("SUDO_USER", "alice", "BB_TUNNELD_SOCKET_GROUP", "staff")))
        .hasValueSatisfying(
            access -> {
              assertThat(access.group()).isEqualTo("staff");
              assertThat(access.permissions()).isEqualTo("rw-rw----");
            });
  }

  @Test
  @DisplayName("blank values are ignored")
  void blanks() {
    assertThat(SocketAccess.from(Map.of("SUDO_USER", "  ", "BB_TUNNELD_SOCKET_OWNER", "")))
        .isEmpty();
  }
}
