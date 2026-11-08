package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AppRunState: parsing the device's Is Running response")
class AppRunStateTest {

  private static AppRunState parse(String resource) {
    return AppRunState.parse(
        List.of(Resources.text(AppRunStateTest.class, resource).split("\n", -1)));
  }

  @Test
  @DisplayName("reads result::false as not running")
  void notRunning() {
    assertThat(parse("device--is-app-running.txt").running()).isFalse();
  }

  @Test
  @DisplayName("reads result::true as running")
  void running() {
    assertThat(parse("device--is-app-running-true.txt").running()).isTrue();
  }
}
