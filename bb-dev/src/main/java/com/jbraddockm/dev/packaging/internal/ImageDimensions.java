package com.jbraddockm.dev.packaging.internal;

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

  private static boolean isPng(byte[] bytes) {
    return bytes.length >= 24
        && (bytes[0] & 0xFF) == 0x89
        && bytes[1] == 'P'
        && bytes[2] == 'N'
        && bytes[3] == 'G';
  }

  private static int[] png(byte[] bytes) {
    // The IHDR chunk follows the 8-byte signature and the 8-byte chunk header; width then height.
    return new int[] {intAt(bytes, 16), intAt(bytes, 20)};
  }

  private static boolean isJpeg(byte[] bytes) {
    return bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
  }

  private static int[] jpeg(byte[] bytes) {
    var index = 2;
    while (index + 9 < bytes.length) {
      if ((bytes[index] & 0xFF) != 0xFF) {
        index++;
        continue;
      }
      var marker = bytes[index + 1] & 0xFF;
      if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
        // A start-of-frame marker: height then width, each two bytes, after the 3-byte prefix.
        return new int[] {shortAt(bytes, index + 7), shortAt(bytes, index + 5)};
      }
      index += 2 + shortAt(bytes, index + 2);
    }
    return null;
  }

  private static int intAt(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF) << 24
        | (bytes[offset + 1] & 0xFF) << 16
        | (bytes[offset + 2] & 0xFF) << 8
        | (bytes[offset + 3] & 0xFF);
  }

  private static int shortAt(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF) << 8 | (bytes[offset + 1] & 0xFF);
  }
}
