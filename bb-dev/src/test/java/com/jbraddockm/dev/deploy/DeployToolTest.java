package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.Output;
import com.jbraddockm.dev.testing.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link DeployTool}'s orchestration — the dev-mode fallback and saving a download. */
@DisplayName("DeployTool: install fallback and saving a download")
class DeployToolTest {

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private FakePoster poster;

  /**
   * Runs the deploy tool against an engine whose device answers a posted verb with {@code reply}.
   */
  private int run(List<String> arguments, String reply) {
    poster = new FakePoster(reply);
    DeployTool.Engines engines =
        (_, _, password, _) ->
            new AppInstallerEngine(
                new FakeDeviceHttp(Resources.text(DeployToolTest.class, "login--success.xml")),
                poster,
                _ -> new AppInstallerEngine.Identity("gYABC", "com.example.App"),
                password,
                () -> {});
    return new DeployTool(engines)
        .run(arguments, new Output(new PrintStream(bytes, true, StandardCharsets.UTF_8), false));
  }

  private String printed() {
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("falls through to a full install when the bar cannot be synced")
  void fallsBackToAFullInstall(@TempDir Path folder) throws IOException {
    var bar = Files.writeString(folder.resolve("app.bar"), "not a bar archive");

    var code = run(List.of("install", bar.toString(), "--device", "d", "--dev"), "result::success");

    assertThat(code).isZero();
    assertThat(poster.body).as("the install was uploaded over the engine").isNotNull();
    assertThat(printed()).doesNotContain("Synced");
  }

  @Test
  @DisplayName("saves a downloaded file to the path given by --out")
  void savesADownloadToTheOutPath(@TempDir Path folder) {
    var out = folder.resolve("x.txt");

    var code =
        run(
            List.of(
                "get",
                "com.example.App.gYABC",
                "data/x.txt",
                "--device",
                "d",
                "--out",
                out.toString()),
            "Info: Sending File: 3\nabc");

    assertThat(code).isZero();
    assertThat(out).hasContent("abc");
    assertThat(printed()).contains("Saved");
  }
}
