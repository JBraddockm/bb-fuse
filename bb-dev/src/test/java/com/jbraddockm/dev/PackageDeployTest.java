package com.jbraddockm.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.packaging.BarDeployer;
import com.jbraddockm.dev.packaging.PackageTool;
import com.jbraddockm.dev.testing.Resources;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("bb-dev package -installApp / -launchApp")
class PackageDeployTest {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  @TempDir private Path project;

  private Path descriptor() throws Exception {
    var descriptor = project.resolve("bar-descriptor.xml");
    Files.writeString(
        descriptor, Resources.text(PackageDeployTest.class, "package-cli--descriptor.xml"));
    var binary = project.resolve("arm/app");
    Files.createDirectories(binary.getParent());
    Files.write(binary, new byte[] {0x7F, 'E', 'L', 'F'});
    return descriptor;
  }

  private int run(RecordingDeployer deployer, String... extra) throws Exception {
    var bar = project.resolve("out.bar");
    var arguments = new ArrayList<>(List.of("-package", bar.toString()));
    arguments.addAll(List.of(extra));
    arguments.add(descriptor().toString());
    return new PackageTool(deployer)
        .run(arguments, new Output(new PrintStream(out, true, StandardCharsets.UTF_8), false));
  }

  private String out() {
    return out.toString(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("builds then installs the bar on the device")
  void installApp() throws Exception {
    var deployer = new RecordingDeployer();

    var status = run(deployer, "-installApp", "-device", "1.2.3.4", "-password", "pw");

    assertThat(status).isZero();
    assertThat(deployer.installedBar).isEqualTo(project.resolve("out.bar"));
    assertThat(deployer.host).isEqualTo("1.2.3.4");
    assertThat(deployer.port).isEqualTo(443);
    assertThat(deployer.password).isEqualTo("pw");
    assertThat(deployer.devMode).isFalse();
    assertThat(deployer.launchedAfterInstall).isFalse();
    assertThat(out()).contains("wrote").contains("✓ Installed com.example.App");
  }

  @Test
  @DisplayName("installs in development mode and launches when both flags are given")
  void installAndLaunchDevMode() throws Exception {
    var deployer = new RecordingDeployer();

    var status = run(deployer, "-devMode", "-installApp", "-launchApp", "-device", "d");

    assertThat(status).isZero();
    assertThat(deployer.devMode).isTrue();
    assertThat(deployer.launchedAfterInstall).isTrue();
    assertThat(deployer.launchedBar).isNull();
  }

  @Test
  @DisplayName("launches without installing when only -launchApp is given")
  void launchOnly() throws Exception {
    var deployer = new RecordingDeployer();

    var status = run(deployer, "-launchApp", "-device", "d");

    assertThat(status).isZero();
    assertThat(deployer.installedBar).isNull();
    assertThat(deployer.launchedBar).isEqualTo(project.resolve("out.bar"));
    assertThat(out()).contains("✓ Launched com.example.App (pid 42)");
  }

  @Test
  @DisplayName("rejects -installApp without a device, before building")
  void installNeedsDevice() throws Exception {
    var deployer = new RecordingDeployer();

    var status = run(deployer, "-installApp");

    assertThat(status).isEqualTo(2);
    assertThat(deployer.installedBar).isNull();
    assertThat(Files.exists(project.resolve("out.bar"))).isFalse();
  }

  /** Records what the package tool asked the deploy engine to do. */
  private static final class RecordingDeployer implements BarDeployer {
    Path installedBar;
    Path launchedBar;
    String host;
    int port;
    String password;
    boolean devMode;
    boolean launchedAfterInstall;

    @Override
    public String install(
        Path bar, String host, int port, String password, boolean devMode, boolean launch) {
      this.installedBar = bar;
      this.host = host;
      this.port = port;
      this.password = password;
      this.devMode = devMode;
      this.launchedAfterInstall = launch;
      return "Installed com.example.App" + (launch ? " and launched it" : "");
    }

    @Override
    public String launch(Path bar, String host, int port, String password) {
      this.launchedBar = bar;
      this.host = host;
      this.port = port;
      this.password = password;
      return "Launched com.example.App (pid 42)";
    }
  }
}
