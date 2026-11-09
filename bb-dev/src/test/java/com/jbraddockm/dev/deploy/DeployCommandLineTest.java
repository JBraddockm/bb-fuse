package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DeployCommandLine: parsing a bb-dev deploy command line")
class DeployCommandLineTest {

  private static DeployCommand command(String... arguments) throws UsageException {
    return DeployCommandLine.parse(List.of(arguments)).command();
  }

  @Nested
  @DisplayName("the device target")
  class Target {

    @Test
    @DisplayName("reads the device, password and port options")
    void readsTarget() throws UsageException {
      var invocation =
          DeployCommandLine.parse(
              List.of("test", "-d", "1.2.3.4", "-p", "secret", "--port", "8443"));

      assertThat(invocation.target()).isEqualTo(new DeployTarget("1.2.3.4", 8443, "secret"));
    }

    @Test
    @DisplayName("defaults the port to 443 and the password to empty")
    void defaults() throws UsageException {
      assertThat(DeployCommandLine.parse(List.of("test", "-d", "1.2.3.4")).target())
          .isEqualTo(new DeployTarget("1.2.3.4", 443, ""));
    }
  }

  @Nested
  @DisplayName("the command")
  class Command {

    @Test
    @DisplayName("maps the no-argument verbs")
    void noArgumentVerbs() throws UsageException {
      assertThat(command("test", "-d", "D")).isInstanceOf(DeployCommand.TestConnection.class);
      assertThat(command("info", "-d", "D")).isInstanceOf(DeployCommand.ListDeviceInfo.class);
      assertThat(command("apps", "-d", "D")).isInstanceOf(DeployCommand.ListInstalledApps.class);
    }

    @Test
    @DisplayName("builds an install with its flags")
    void install() throws UsageException {
      var command =
          command(
              "install",
              "app.bar",
              "-d",
              "D",
              "--launch",
              "--dev",
              "--debug-native",
              "--debug-host",
              "h");

      assertThat(command)
          .isEqualTo(
              new DeployCommand.InstallApp(Path.of("app.bar"), true, true, "h", true, false));
    }

    @Test
    @DisplayName("sets noSync for a --no-sync install")
    void installNoSync() throws UsageException {
      var command = command("install", "app.bar", "-d", "D", "--dev", "--no-sync");

      assertThat(command)
          .isEqualTo(
              new DeployCommand.InstallApp(Path.of("app.bar"), false, true, null, false, true));
    }

    @Test
    @DisplayName("names the application for an app verb by its full name")
    void appVerb() throws UsageException {
      assertThat(command("verify", "com.example.App.id", "-d", "D"))
          .isEqualTo(new DeployCommand.VerifyApp(new PackageRef.FullName("com.example.App.id")));
    }

    @Test
    @DisplayName("reads get with an output file and put with a local file")
    void fileVerbs() throws UsageException {
      assertThat(command("get", "com.x.App.id", "tmp/f.txt", "--out", "local.txt", "-d", "D"))
          .isEqualTo(
              new DeployCommand.GetFile(
                  new PackageRef.FullName("com.x.App.id"), "tmp/f.txt", Path.of("local.txt")));
      assertThat(command("put", "com.x.App.id", "local.txt", "tmp/f.txt", "-d", "D"))
          .isEqualTo(
              new DeployCommand.PutFile(
                  new PackageRef.FullName("com.x.App.id"), Path.of("local.txt"), "tmp/f.txt"));
    }

    @Test
    @DisplayName("reads a cores pull by full name, with an optional output location")
    void coresVerb() throws UsageException {
      assertThat(command("cores", "com.x.App.id", "--out", "dumps", "-d", "D"))
          .isEqualTo(
              new DeployCommand.PullCore(
                  new PackageRef.FullName("com.x.App.id"), Path.of("dumps")));
      assertThat(command("cores", "com.x.App.id", "-d", "D"))
          .isEqualTo(new DeployCommand.PullCore(new PackageRef.FullName("com.x.App.id"), null));
    }
  }

  @Nested
  @DisplayName("usage errors")
  class Errors {

    @Test
    @DisplayName("rejects a missing device")
    void missingDevice() {
      assertThatThrownBy(() -> DeployCommandLine.parse(List.of("apps")))
          .isInstanceOf(UsageException.class)
          .hasMessageContaining("device");
    }

    @Test
    @DisplayName("rejects an unknown command")
    void unknownCommand() {
      assertThatThrownBy(() -> DeployCommandLine.parse(List.of("frobnicate", "-d", "D")))
          .isInstanceOf(UsageException.class)
          .hasMessageContaining("unknown command");
    }

    @Test
    @DisplayName("rejects the wrong number of arguments")
    void wrongArguments() {
      assertThatThrownBy(() -> DeployCommandLine.parse(List.of("install", "-d", "D")))
          .isInstanceOf(UsageException.class)
          .hasMessageContaining("install takes 1");
    }

    @Test
    @DisplayName("rejects an unknown option")
    void unknownOption() {
      assertThatThrownBy(() -> DeployCommandLine.parse(List.of("apps", "-d", "D", "--nope")))
          .isInstanceOf(UsageException.class)
          .hasMessageContaining("unknown option");
    }
  }
}
