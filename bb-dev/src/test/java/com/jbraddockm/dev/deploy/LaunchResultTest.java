package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LaunchResult: parsing the device's Launch response")
class LaunchResultTest {

  @Test
  @DisplayName("reads the launched process id from the result line")
  void readsPid() {
    var text = Resources.text(LaunchResultTest.class, "device--launch.txt");

    var launch = LaunchResult.parse(List.of(text.split("\n", -1)));

    assertThat(launch.pid()).isEqualTo(241_336_523L);
  }
}
