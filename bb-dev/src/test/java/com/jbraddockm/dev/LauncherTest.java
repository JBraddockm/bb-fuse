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
    @ValueSource(strings = {"connect", "deploy", "package"})
    @DisplayName("routes each namespace to its tool")
    void routesEachNamespace(String namespace) {
      assertThat(dispatch(namespace)).isEqualTo(Output.NOT_IMPLEMENTED);
      assertThat(out()).contains("bb-dev " + namespace + ": not implemented yet");
    }

    @Test
    @DisplayName("emits a JSON object when --json precedes the namespace")
    void jsonBeforeNamespace() {
      assertThat(dispatch("--json", "connect")).isEqualTo(Output.NOT_IMPLEMENTED);
      var parsed = Json.parse(out().strip());
      assertThat(parsed).isInstanceOf(Map.class);
      @SuppressWarnings("unchecked")
      var object = (Map<String, Object>) parsed;
      assertThat(object)
          .containsEntry("ok", false)
          .containsEntry("namespace", "connect")
          .containsEntry("error", "not implemented");
    }

    @Test
    @DisplayName("accepts --json after the namespace too")
    void jsonAfterNamespace() {
      assertThat(dispatch("deploy", "--json")).isEqualTo(Output.NOT_IMPLEMENTED);
      assertThat(Json.parse(out().strip())).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("routes when tool options follow the namespace")
    void toolOptionsFollow() {
      assertThat(dispatch("connect", "192.168.1.118", "-password", "12345"))
          .isEqualTo(Output.NOT_IMPLEMENTED);
    }
  }
}
