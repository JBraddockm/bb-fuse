package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeployResults: the daemon result data for a command")
class DeployResultsTest {

  private static final PackageRef APP = new PackageRef.FullName("com.example.App.id");

  private static List<String> golden(String name) {
    return List.of(Resources.text(DeployResultsTest.class, name).split("\n", -1));
  }

  @Test
  @DisplayName("verify returns the confirmed package identity")
  void packageIdentity() {
    var data = DeployResults.data(new DeployCommand.VerifyApp(APP), golden("device--verify.txt"));

    assertThat(data)
        .containsEntry("name", "com.example.TLSDeBug.testDev_le_TLSDeBugc09806e9")
        .containsEntry("id", "testDev_le_TLSDeBugc09806e9")
        .containsEntry("version", "1.0.0.1");
  }

  @Test
  @DisplayName("apps nests the application list under 'apps'")
  void apps() {
    var data =
        DeployResults.data(
            new DeployCommand.ListInstalledApps(), golden("device--list-installed-apps.txt"));

    assertThat(data.get("apps"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .hasSize(233);
  }

  @Test
  @DisplayName("info nests the device properties under 'properties'")
  void info() {
    var data =
        DeployResults.data(
            new DeployCommand.ListDeviceInfo(), golden("device--list-device-info.txt"));

    assertThat(data.get("properties"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsEntry("device_os", "BlackBerry 10");
  }

  @Test
  @DisplayName("running reports the boolean run state")
  void running() {
    assertThat(
            DeployResults.data(
                new DeployCommand.IsAppRunning(APP), golden("device--is-app-running.txt")))
        .containsEntry("running", false);
    assertThat(
            DeployResults.data(
                new DeployCommand.IsAppRunning(APP), golden("device--is-app-running-true.txt")))
        .containsEntry("running", true);
  }

  @Test
  @DisplayName("launch reports the process id")
  void launch() {
    assertThat(DeployResults.data(new DeployCommand.LaunchApp(APP), golden("device--launch.txt")))
        .containsEntry("pid", 241_336_523L);
  }

  @Test
  @DisplayName("put reports the uploaded size and get reports the size and content")
  void fileTransfer() {
    assertThat(
            DeployResults.data(
                new DeployCommand.PutFile(APP, java.nio.file.Path.of("l"), "p"),
                golden("device--put-file.txt")))
        .containsEntry("size", 18L);
    assertThat(
            DeployResults.data(
                new DeployCommand.GetFile(APP, "p", null), golden("device--get-file.txt")))
        .containsEntry("size", 18L)
        .containsEntry("content", "bbdev mutate test");
  }

  @Test
  @DisplayName("test and terminate carry no data")
  void noData() {
    assertThat(DeployResults.data(new DeployCommand.TestConnection(), List.of("result::success")))
        .isEmpty();
    assertThat(
            DeployResults.data(new DeployCommand.TerminateApp(APP), List.of("result::Terminated")))
        .isEmpty();
  }
}
