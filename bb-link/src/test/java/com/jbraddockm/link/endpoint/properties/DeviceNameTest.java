package com.jbraddockm.link.endpoint.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link DeviceName}: the device's own rules for an acceptable name, replicated so a bad
 * name is refused before it is sent.
 */
@DisplayName("DeviceName: the rules the device applies to a device name")
class DeviceNameTest {

  @Nested
  @DisplayName("a name the device accepts")
  class Accepted {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"MyPhone", "BLACKBERRY-4C5D", "a.b-c", "a", "host.example-1"})
    @DisplayName("passes and keeps its value")
    void passes(String value) {
      assertThat(DeviceName.of(value).value()).isEqualTo(value);
    }

    @Test
    @DisplayName("may be as long as the device's limit")
    void mayReachTheLimit() {
      var longest = "a".repeat(DeviceName.MAX_LENGTH);

      assertThat(DeviceName.of(longest).value()).isEqualTo(longest);
    }
  }

  @Nested
  @DisplayName("a name the device would reject")
  class Rejected {

    @Test
    @DisplayName("a missing name is required, naming the parameter")
    void missingIsRequired() {
      assertThatThrownBy(() -> DeviceName.of(null))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("name"))
          .hasMessageContaining("is required");
    }

    @Test
    @DisplayName("an empty name is required")
    void emptyIsRequired() {
      assertThatThrownBy(() -> DeviceName.of(""))
          .isInstanceOf(InvalidArgumentException.class)
          .hasMessageContaining("is required");
    }

    @Test
    @DisplayName("a name over the limit is too long")
    void overTheLimitIsTooLong() {
      var tooLong = "a".repeat(DeviceName.MAX_LENGTH + 1);

      assertThatThrownBy(() -> DeviceName.of(tooLong))
          .isInstanceOf(InvalidArgumentException.class)
          .hasMessageContaining("too long");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"My Phone", "under_score", "slash/here", "bang!", "café", "a:b"})
    @DisplayName("a name with a character outside letters, digits, '.' and '-' is rejected")
    void aDisallowedCharacterIsRejected(String value) {
      assertThatThrownBy(() -> DeviceName.of(value))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("name"))
          .hasMessageContaining("may use only");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {".foo", "-foo", "foo.", "foo-", "-", "."})
    @DisplayName("a name that does not start and end with a letter or digit is rejected")
    void mustStartAndEndAlphanumeric(String value) {
      assertThatThrownBy(() -> DeviceName.of(value))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("name"))
          .hasMessageContaining("must start and end");
    }
  }
}
