package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("MultipartBody: the multipart/form-data body of an appInstaller command")
class MultipartBodyTest {

  private static String ascii(byte[] bytes) {
    return new String(bytes, StandardCharsets.US_ASCII);
  }

  @Test
  @DisplayName("names the multipart content type with its own boundary")
  void contentType() {
    assertThat(MultipartBody.command("test").build().contentType())
        .isEqualTo("multipart/form-data; boundary=----bbdevDeployBoundary");
  }

  @Test
  @DisplayName(
      "lays a command with no file out exactly, part headers and closing boundary included")
  void textOnlyCommandIsByteExact() {
    var body = MultipartBody.command("test").build();

    assertThat(ascii(body.head()))
        .isEqualTo(Resources.text(MultipartBodyTest.class, "multipart--test-command.txt"));
  }

  @Nested
  @DisplayName("the order and presence of parts")
  class Parts {

    @Test
    @DisplayName(
        "keeps the device's part order: command, flags, file, then id, name and asset path")
    void keepsTheDeviceOrder(@TempDir Path folder) throws IOException {
      var bar = Files.write(folder.resolve("app.bar"), new byte[] {1, 2, 3});
      var body =
          MultipartBody.command("Install and Launch")
              .devMode(true)
              .file(bar)
              .packageId("gYABC")
              .packageName("com.example.App")
              .build();

      var wire = ascii(body.head()) + ascii(body.tail());

      assertThat(wire)
          .containsSubsequence(
              "name=\"command\"",
              "Install and Launch",
              "name=\"dev_mode\"",
              "on",
              "name=\"file\"; filename=\"app.bar\"",
              "application/zip",
              "name=\"package_id\"",
              "gYABC",
              "name=\"package_name\"",
              "com.example.App");
    }

    @Test
    @DisplayName("omits a flag that is off and an option that is absent")
    void omitsAbsentParts() {
      var wire = ascii(MultipartBody.command("List").devMode(false).debugHost(null).build().head());

      assertThat(wire).doesNotContain("dev_mode").doesNotContain("debug_host");
    }

    @Test
    @DisplayName("streams the file content between the part header and the trailing parts")
    void streamsTheFileContent(@TempDir Path folder) throws IOException {
      var bar = Files.writeString(folder.resolve("app.bar"), "ZIPDATA");
      var body = MultipartBody.command("Install").file(bar).build();

      assertThat(ascii(body.inMemory()))
          .contains("name=\"file\"; filename=\"app.bar\"")
          .contains("\r\n\r\nZIPDATA\r\n")
          .endsWith("------bbdevDeployBoundary--\r\n");
      assertThat(body.contentLength()).isEqualTo(body.inMemory().length);
    }
  }
}
