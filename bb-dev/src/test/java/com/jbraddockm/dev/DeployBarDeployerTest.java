package com.jbraddockm.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the device-free logic of {@link DeployBarDeployer}, the package namespace's deploy bridge.
 */
@DisplayName("DeployBarDeployer: reading the launch result")
class DeployBarDeployerTest {

  @Test
  @DisplayName("takes the launched process id from the device's result line")
  void readsThePid() {
    assertThat(DeployBarDeployer.pid(List.of("Info: launching", "result::246263936")))
        .isEqualTo(" (pid 246263936)");
  }

  @Test
  @DisplayName("reports no process id when no result line carries one")
  void reportsNoPidWhenAbsent() {
    assertThat(DeployBarDeployer.pid(List.of("result::success"))).isEmpty();
  }
}
