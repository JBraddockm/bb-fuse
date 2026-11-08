package com.jbraddockm.dev.packaging.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ImageDimensions: reading a PNG or JPEG header")
class ImageDimensionsTest {

  @TempDir private Path folder;

  private Path image(byte[] bytes) throws IOException {
    return Files.write(folder.resolve("image"), bytes);
  }

  @Test
  @DisplayName("reads the width and height from a PNG IHDR chunk")
  void png() throws Exception {
    var bytes =
        ByteBuffer.allocate(24)
            .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
            .putInt(13)
            .put("IHDR".getBytes(StandardCharsets.US_ASCII))
            .putInt(300)
            .putInt(200)
            .array();

    assertThat(ImageDimensions.of(image(bytes))).isEqualTo("{300x200}");
  }

  @Test
  @DisplayName("reads the width and height from a JPEG start-of-frame marker")
  void jpeg() throws Exception {
    var bytes =
        ByteBuffer.allocate(12)
            .put(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0, 0x00, 0x11, 0x08})
            .putShort((short) 200) // height
            .putShort((short) 300) // width
            .put((byte) 0)
            .array();

    assertThat(ImageDimensions.of(image(bytes))).isEqualTo("{300x200}");
  }

  @Test
  @DisplayName("returns null for a file that is neither a PNG nor a JPEG")
  void notAnImage() throws Exception {
    assertThat(ImageDimensions.of(image(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})))
        .isNull();
  }
}
