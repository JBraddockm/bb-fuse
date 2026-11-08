package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.device.DeviceHttp;
import com.jbraddockm.dev.testing.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DeployService}: the deploy daemon service and how it maps a failure to a code.
 */
@DisplayName("DeployService: the deploy daemon service")
class DeployServiceTest {

  @Test
  @DisplayName("names the service bb-dev deploy")
  void names() {
    assertThat(new DeployService().info().tool()).isEqualTo("bb-dev deploy");
  }

  @Test
  @DisplayName("offers the deploy verbs over the daemon loop")
  void offersTheDeployVerbs() {
    var names = new DeployService().commands().stream().map(CommandSpec::wireName).toList();

    assertThat(names).contains("test", "info", "apps", "verify", "install", "get", "put");
  }

  @Test
  @DisplayName("maps a failure the device reports to COMMAND_FAILED")
  void mapsACommandFailure() {
    assertThat(verifyAnsweredWith("result::failure 810 cannot access member"))
        .contains("\"code\":\"COMMAND_FAILED\"");
  }

  @Test
  @DisplayName("maps the device's stale-login line to AUTH_FAILED")
  void mapsAStaleLoginToAuthFailed() {
    assertThat(verifyAnsweredWith("Error: User authentication failed"))
        .contains("\"code\":\"AUTH_FAILED\"");
  }

  /** Runs {@code verify} against a session whose device answers with {@code deviceReply}. */
  private static String verifyAnsweredWith(String deviceReply) {
    var engine =
        new AppInstallerEngine(
            new FakeHttp(Resources.text(DeployServiceTest.class, "login--success.xml")),
            new FakePoster(deviceReply),
            _ -> new AppInstallerEngine.Identity("gYABC", "com.example.App"),
            "",
            () -> {});
    var verify =
        new DeployService()
            .commands().stream()
                .filter(command -> command.wireName().equals("verify"))
                .map(command -> (CommandSpec.SessionScoped<DeploySession>) command)
                .findFirst()
                .orElseThrow()
                .factory()
                .apply(new RequestParams(Map.of("app", "com.example.App.gYABC")));
    var bytes = new ByteArrayOutputStream();
    verify.run(
        new DeploySession(engine),
        Reply.standalone(new PrintStream(bytes, true, StandardCharsets.UTF_8), 1));
    return bytes.toString(StandardCharsets.UTF_8);
  }

  private record FakeHttp(String loginBody) implements DeviceHttp {
    @Override
    public String get(String pathAndQuery) {
      return loginBody;
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

  private record FakePoster(String reply) implements Poster {
    @Override
    public Answer post(MultipartBody body) {
      return new Answer(
          200, "text/plain", new ByteArrayInputStream(reply.getBytes(StandardCharsets.UTF_8)));
    }
  }
}
