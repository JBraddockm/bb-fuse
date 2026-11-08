package com.jbraddockm.core.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.testing.Resources;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Hashing a device login answer")
class PwCryptoTest {

  private static final HexFormat HEX = HexFormat.of();

  /** The golden vectors, each produced by the original BlackBerry {@code PwCrypto}. */
  static Stream<String> vectors() {
    return Resources.text(PwCryptoTest.class, "pwcrypto-vectors.txt")
        .lines()
        .filter(line -> !line.isBlank());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("vectors")
  @DisplayName("matches the original byte-for-byte, from strings and from raw bytes")
  void matchesOriginal(String vector) {
    // type | algorithm | password | salt | iterationCount | challenge | expected
    var field = vector.split("\\|", -1);
    var algorithm = Integer.parseInt(field[1]);
    var iterationCount = Integer.parseInt(field[4]);
    var expected = field[6];

    var actual =
        field[0].equals("b")
            ? PwCrypto.hashAndEncode(
                HEX.parseHex(field[2]),
                algorithm,
                HEX.parseHex(field[3]),
                iterationCount,
                HEX.parseHex(field[5]))
            : PwCrypto.hashAndEncode(field[2], algorithm, field[3], iterationCount, field[5]);

    assertThat(actual).isEqualTo(expected);
  }
}
