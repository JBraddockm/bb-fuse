package com.jbraddockm.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.json.Json;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("The bb-dev launcher")
class LauncherTest {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  private int dispatch(String... arguments) {
    return Launcher.dispatch(
        arguments,
        new PrintStream(out, true, StandardCharsets.UTF_8),
        new PrintStream(err, true, StandardCharsets.UTF_8));
  }

  private String out() {
    return out.toString(StandardCharsets.UTF_8);
  }

  private String err() {
    return err.toString(StandardCharsets.UTF_8);
  }

  @Nested
  @DisplayName("without a usable namespace")
  class Usage {

    @Test
    @DisplayName("prints usage and fails when no namespace is given")
    void noNamespace() {
      assertThat(dispatch()).isEqualTo(Launcher.USAGE_ERROR);
      assertThat(err()).contains("Usage: bb-dev");
      assertThat(out()).isEmpty();
    }

    @Test
    @DisplayName("rejects a namespace that is not known")
    void unknownNamespace() {
      assertThat(dispatch("wibble")).isEqualTo(Launcher.USAGE_ERROR);
      assertThat(err()).contains("unknown namespace 'wibble'");
    }

    @Test
    @DisplayName("treats a lone --json as no namespace")
    void onlyJsonFlag() {
      assertThat(dispatch("--json")).isEqualTo(Launcher.USAGE_ERROR);
      assertThat(err()).contains("Usage: bb-dev");
    }
  }

  @Nested
  @DisplayName("routing to a namespace")
  class Routing {

    @ParameterizedTest
    @ValueSource(strings = {"deploy"})
    @DisplayName("routes a not-yet-built namespace to its stub")
    void routesStubNamespace(String namespace) {
      assertThat(dispatch(namespace)).isEqualTo(Output.NOT_IMPLEMENTED);
      assertThat(out()).contains("bb-dev " + namespace + ": not implemented yet");
    }

    @Test
    @DisplayName("emits a JSON object for a stub namespace when --json precedes it")
    void jsonBeforeNamespace() {
      assertThat(dispatch("--json", "deploy")).isEqualTo(Output.NOT_IMPLEMENTED);
      var parsed = Json.parse(out().strip());
      assertThat(parsed).isInstanceOf(Map.class);
      @SuppressWarnings("unchecked")
      var object = (Map<String, Object>) parsed;
      assertThat(object)
          .containsEntry("ok", false)
          .containsEntry("namespace", "deploy")
          .containsEntry("error", "not implemented");
    }

    @Test
    @DisplayName("accepts --json after the namespace too")
    void jsonAfterNamespace() {
      assertThat(dispatch("deploy", "--json")).isEqualTo(Output.NOT_IMPLEMENTED);
      assertThat(Json.parse(out().strip())).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("routes to the connect tool, which then validates its own options")
    void connectRoutesAndValidates() {
      // No -sshPublicKey, so the connect tool itself reports a usage error — which proves the
      // namespace word was consumed and the remaining options were handed to the tool.
      assertThat(dispatch("connect", "192.168.1.118", "-password", "12345")).isEqualTo(2);
      assertThat(out()).contains("Usage: bb-dev connect");
    }

    @Test
    @DisplayName("routes to the package tool, which then validates its own options")
    void packageRoutesAndValidates() {
      // No -package and no descriptor, so the package tool itself reports a usage error.
      assertThat(dispatch("package")).isEqualTo(2);
      assertThat(out()).contains("Usage: bb-dev package");
    }
  }
}
