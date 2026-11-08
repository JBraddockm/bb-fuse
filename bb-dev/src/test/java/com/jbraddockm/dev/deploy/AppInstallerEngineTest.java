package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.device.DeviceHttp;
import com.jbraddockm.dev.testing.Resources;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("AppInstallerEngine: running a command over appInstaller.cgi")
class AppInstallerEngineTest {

  private FakeDeviceHttp http;
  private FakePoster poster;
  private AppInstallerEngine engine;

  @BeforeEach
  void setUp() {
    http = new FakeDeviceHttp(Resources.text(AppInstallerEngineTest.class, "login--success.xml"));
    poster = new FakePoster();
    engine =
        new AppInstallerEngine(
            http,
            poster,
            _ -> new AppInstallerEngine.Identity("gYABC", "com.example.App"),
            null,
            () -> {});
  }

  private List<String> run(DeployCommand command, List<String> forwarded) throws Exception {
    return engine.run(command, forwarded::add);
  }

  @Test
  @DisplayName("a connection test logs in and reports success without posting a command")
  void testConnectionIsLoginOnly() throws Exception {
    var lines = run(new DeployCommand.TestConnection(), new ArrayList<>());

    assertThat(lines).containsExactly("Info: Sending request: TEST", "result::success");
    assertThat(poster.body).isNull();
    assertThat(http.logins).isEqualTo(1);
  }

  @Nested
  @DisplayName("the answer")
  class Answer {

    @Test
    @DisplayName(
        "announces the request, then returns and forwards every line of the device's reply")
    void returnsAndForwardsLines() throws Exception {
      poster.reply("actual_id::gYABC\nresult::success");
      var forwarded = new ArrayList<String>();

      var lines = run(new DeployCommand.ListInstalledApps(), forwarded);

      assertThat(lines)
          .containsExactly("Info: Sending request: LIST", "actual_id::gYABC", "result::success");
      assertThat(forwarded).isEqualTo(lines);
    }

    @Test
    @DisplayName("keeps a final line that the device did not end with a newline")
    void keepsAFinalUnterminatedLine() throws Exception {
      poster.reply("result::success");

      assertThat(run(new DeployCommand.ListInstalledApps(), new ArrayList<>()))
          .contains("result::success");
    }
  }

  @Nested
  @DisplayName("the request it posts")
  class Request {

    @Test
    @DisplayName("carries the verb and the announced tool name for each command")
    void carriesTheVerb() throws Exception {
      poster.reply("result::success");

      run(new DeployCommand.ListDeviceInfo(), new ArrayList<>());

      assertThat(ascii(poster.body.head()))
          .contains("name=\"command\"")
          .contains("List Device Info");
    }

    @Test
    @DisplayName("reads the bar identity for an install-and-launch and sends it as id and name")
    void installAndLaunchCarriesTheBarIdentity(@TempDir Path folder) throws Exception {
      var bar = java.nio.file.Files.write(folder.resolve("app.bar"), new byte[] {1});
      poster.reply("result::success");

      run(new DeployCommand.InstallApp(bar, true, true, null, false, false), new ArrayList<>());

      var wire = ascii(poster.body.head()) + ascii(poster.body.tail());
      assertThat(wire).contains("Install and Launch").contains("gYABC").contains("com.example.App");
    }

    @Test
    @DisplayName(
        "sends the package id and name of a reference command, and the asset path of a get")
    void getFileCarriesTheAssetPath() throws Exception {
      poster.reply("Info: Sending File: 3\nabc");

      run(
          new DeployCommand.GetFile(
              new PackageRef.NameAndId("com.example.App", "gYABC"), "data/x.txt", null),
          new ArrayList<>());

      var wire = ascii(poster.body.head()) + ascii(poster.body.tail());
      assertThat(wire).contains("Get File").contains("name=\"asset_path\"").contains("data/x.txt");
    }
  }

  @Nested
  @DisplayName("a failure the device reports")
  class ReportedFailure {

    @Test
    @DisplayName("is raised as a device report carrying the failure line")
    void raisesTheFailureLine() {
      poster.reply("result::failure 810 cannot access member");

      assertThatThrownBy(() -> run(new DeployCommand.ListInstalledApps(), new ArrayList<>()))
          .isInstanceOf(DeviceReportException.class)
          .hasMessage("result::failure 810 cannot access member")
          .matches(failure -> !((DeviceReportException) failure).isAuthenticationFailure());
    }

    @Test
    @DisplayName("is marked an authentication failure for the device's stale-login line")
    void marksTheStaleLogin() {
      poster.reply("Error: User authentication failed");

      assertThatThrownBy(() -> run(new DeployCommand.ListInstalledApps(), new ArrayList<>()))
          .isInstanceOf(DeviceReportException.class)
          .matches(failure -> ((DeviceReportException) failure).isAuthenticationFailure());
    }
  }

  @Nested
  @DisplayName("the login lifetime")
  class Login {

    @Test
    @DisplayName("logs in once and reuses the session for the next command")
    void logsInOnce() throws Exception {
      poster.reply("result::success");
      poster.reply("result::success");

      run(new DeployCommand.ListInstalledApps(), new ArrayList<>());
      run(new DeployCommand.ListInstalledApps(), new ArrayList<>());

      assertThat(http.logins).isEqualTo(1);
    }

    @Test
    @DisplayName("logs in again after it is told to forget the login")
    void reLoginsAfterForget() throws Exception {
      poster.reply("result::success");
      poster.reply("result::success");

      run(new DeployCommand.ListInstalledApps(), new ArrayList<>());
      engine.forgetLogin();
      run(new DeployCommand.ListInstalledApps(), new ArrayList<>());

      assertThat(http.logins).isEqualTo(2);
    }
  }

  private static String ascii(byte[] bytes) {
    return new String(bytes, StandardCharsets.US_ASCII);
  }

  /** A device connection that accepts a certificate login and counts how often it logs in. */
  private static final class FakeDeviceHttp implements DeviceHttp {

    private final String successBody;
    private int logins;

    FakeDeviceHttp(String successBody) {
      this.successBody = successBody;
    }

    @Override
    public String get(String pathAndQuery) {
      logins++;
      return successBody;
    }

    @Override
    public boolean hasLoginCookie() {
      return false;
    }

    @Override
    public boolean offersClientCertificate() {
      return true;
    }
  }

  /** A poster that returns canned answers and remembers the last body it was given. */
  private static final class FakePoster implements Poster {

    private final Deque<String> replies = new ArrayDeque<>();
    private MultipartBody body;

    void reply(String text) {
      replies.add(text);
    }

    @Override
    public Answer post(MultipartBody body) {
      this.body = body;
      return new Answer(
          200,
          "text/plain",
          new ByteArrayInputStream(replies.remove().getBytes(StandardCharsets.UTF_8)));
    }
  }
}
