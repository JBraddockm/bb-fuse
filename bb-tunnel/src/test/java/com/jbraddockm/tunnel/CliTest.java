package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link Cli}'s supported-OS guard. */
@DisplayName("Cli: the macOS-only guard")
class CliTest {

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"Mac OS X", "macOS", "Mac OS"})
  @DisplayName("macOS is supported")
  void macOs(String osName) {
    assertThat(Cli.supportedOs(osName)).isTrue();
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"Linux", "Windows 11", ""})
  @DisplayName("every other platform is refused")
  void otherPlatforms(String osName) {
    assertThat(Cli.supportedOs(osName)).isFalse();
  }
}
