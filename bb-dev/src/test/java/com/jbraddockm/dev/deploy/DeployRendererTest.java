package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeployRenderer: rendering a command's device reply")
class DeployRendererTest {

  private static final PackageRef APP = new PackageRef.FullName("com.example.App.id");

  private static List<String> golden(String name) {
    return List.of(Resources.text(DeployRendererTest.class, name).split("\n", -1));
  }

  @Test
  @DisplayName("summarises an install with the confirmed name and version")
  void install() {
    var rendered =
        DeployRenderer.render(
            new DeployCommand.InstallApp(Path.of("x.bar"), false, true, null, false),
            golden("device--install.txt"));

    assertThat(rendered.text())
        .isEqualTo("✓ Installed com.example.TLSDeBug.testDev_le_TLSDeBugc09806e9 1.0.0.1");
  }

  @Test
  @DisplayName("summarises a verify")
  void verify() {
    var rendered =
        DeployRenderer.render(new DeployCommand.VerifyApp(APP), golden("device--verify.txt"));

    assertThat(rendered.text()).startsWith("✓ Verified com.example.TLSDeBug");
  }

  @Test
  @DisplayName("reports the launched process id")
  void launch() {
    var rendered =
        DeployRenderer.render(new DeployCommand.LaunchApp(APP), golden("device--launch.txt"));

    assertThat(rendered.text()).isEqualTo("✓ Launched (pid 241336523)");
  }

  @Test
  @DisplayName("reports the run state both ways")
  void running() {
    assertThat(
            DeployRenderer.render(
                    new DeployCommand.IsAppRunning(APP), golden("device--is-app-running.txt"))
                .text())
        .isEqualTo("Not running.");
    assertThat(
            DeployRenderer.render(
                    new DeployCommand.IsAppRunning(APP), golden("device--is-app-running-true.txt"))
                .text())
        .isEqualTo("Running.");
  }

  @Test
  @DisplayName("reports the uploaded byte count")
  void put() {
    var rendered =
        DeployRenderer.render(
            new DeployCommand.PutFile(APP, Path.of("local"), "path"),
            golden("device--put-file.txt"));

    assertThat(rendered.text()).isEqualTo("✓ Uploaded 18 bytes");
  }

  @Test
  @DisplayName("prints the downloaded content")
  void get() {
    var rendered =
        DeployRenderer.render(
            new DeployCommand.GetFile(APP, "path", null), golden("device--get-file.txt"));

    assertThat(rendered.text()).isEqualTo("bbdev mutate test");
  }

  @Test
  @DisplayName("lists the installed applications with a count footer")
  void apps() {
    var rendered =
        DeployRenderer.render(
            new DeployCommand.ListInstalledApps(), golden("device--list-installed-apps.txt"));

    assertThat(rendered.text()).contains("com.example.Term49").endsWith("233 applications");
  }

  @Test
  @DisplayName("aligns the device properties as key and value")
  void info() {
    var rendered =
        DeployRenderer.render(
            new DeployCommand.ListDeviceInfo(), golden("device--list-device-info.txt"));

    assertThat(rendered.text())
        .contains("device_os")
        .contains("BlackBerry 10")
        .contains("modelfullname");
  }
}
