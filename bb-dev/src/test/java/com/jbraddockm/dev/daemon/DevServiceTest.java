package com.jbraddockm.dev.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.daemon.CommandSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DevService: the unified bb-dev daemon service")
class DevServiceTest {

  @Test
  @DisplayName("names the tool bb-dev")
  void info() {
    assertThat(new DevService().info().tool()).isEqualTo("bb-dev");
  }

  @Test
  @DisplayName("offers both the deploy verbs and the package verbs over one loop")
  void commands() {
    var names = new DevService().commands().stream().map(CommandSpec::wireName).toList();

    assertThat(names)
        .contains("apps", "info", "verify", "install", "get", "put")
        .contains("package", "listBar", "manifest");
  }
}
