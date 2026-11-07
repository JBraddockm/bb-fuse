package com.jbraddockm.dev.packaging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the pixel size of a PNG or JPEG from its header, as the manifest records it for splash
 * screens ({@code {width x height}}). Only these two formats are read, because they are the only
 * ones a splash screen uses.
 */
final class ImageDimensions {

  private ImageDimensions() {}

  /**
   * The size of an image as {@code {width x height}}, or {@code null} if it cannot be read.
   *
   * @param file the image file
   * @return the size tag, for example {@code {768x1280}}, or {@code null}
   * @throws IOException if the file cannot be read
   */
  static String of(Path file) throws IOException {
    var bytes = Files.readAllBytes(file);
    int[] size = isPng(bytes) ? png(bytes) : isJpeg(bytes) ? jpeg(bytes) : null;
    return size == null ? null : "{" + size[0] + "x" + size[1] + "}";
  }

  private static boolean isPng(byte[] b) {
    return b.length >= 24 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
  }

  private static int[] png(byte[] b) {
    // The IHDR chunk follows the 8-byte signature and the 8-byte chunk header; width then height.
    return new int[] {intAt(b, 16), intAt(b, 20)};
  }

  private static boolean isJpeg(byte[] b) {
    return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
  }

  private static int[] jpeg(byte[] b) {
    var index = 2;
    while (index + 9 < b.length) {
      if ((b[index] & 0xFF) != 0xFF) {
        index++;
        continue;
      }
      var marker = b[index + 1] & 0xFF;
      if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
        // A start-of-frame marker: height then width, each two bytes, after the 3-byte prefix.
        return new int[] {shortAt(b, index + 7), shortAt(b, index + 5)};
      }
      index += 2 + shortAt(b, index + 2);
    }
    return null;
  }

  private static int intAt(byte[] b, int offset) {
    return (b[offset] & 0xFF) << 24
        | (b[offset + 1] & 0xFF) << 16
        | (b[offset + 2] & 0xFF) << 8
        | (b[offset + 3] & 0xFF);
  }

  private static int shortAt(byte[] b, int offset) {
    return (b[offset] & 0xFF) << 8 | (b[offset + 1] & 0xFF);
  }
}
