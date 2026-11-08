package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeployCommand: the variants validate their parameters when created")
class DeployCommandTest {

  private static final PackageRef APP = new PackageRef.NameAndId("com.example.App", "gYABId");

  @Test
  @DisplayName("install requires a bar")
  void installNeedsABar() {
    assertThatThrownBy(() -> new DeployCommand.InstallApp(null, false, false, null, false, false))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("a command that names an application rejects a name-and-id reference with no name")
  void runningNeedsAName() {
    assertThatThrownBy(
            () -> new DeployCommand.IsAppRunning(new PackageRef.NameAndId(null, "gYABId")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("package name");
  }

  @Test
  @DisplayName("get rejects a blank sandbox path")
  void getNeedsAPath() {
    assertThatThrownBy(() -> new DeployCommand.GetFile(APP, " ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sandbox path");
  }

  @Test
  @DisplayName("put rejects a bar-file reference for the target")
  void putRejectsABarFile() {
    assertThatThrownBy(
            () ->
                new DeployCommand.PutFile(
                    new PackageRef.BarFile(Path.of("app.bar")), Path.of("local"), "sandbox/x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bar file");
  }

  @Test
  @DisplayName("put requires a local file")
  void putNeedsALocalFile() {
    assertThatThrownBy(() -> new DeployCommand.PutFile(APP, null, "sandbox/x"))
        .isInstanceOf(NullPointerException.class);
  }
}
