package com.jbraddockm.link.endpoint.pimsync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link PimStatusCode}: the plain-language meaning bb-link adds to a device code. */
@DisplayName("PimStatusCode: what a pimsync.cgi status code means")
class PimStatusCodeTest {

  @Nested
  @DisplayName("explaining a code")
  class Explaining {

    @ParameterizedTest(name = "{0}")
    @EnumSource(PimStatusCode.class)
    @DisplayName("every code this version knows has a meaning, found by its number")
    void everyKnownCodeHasAMeaning(PimStatusCode code) {
      assertThat(PimStatusCode.explain(code.number())).hasValue(code.meaning());
    }

    @Test
    @DisplayName("reads the meaning of a few codes, to pin the words")
    void readsSpecificMeanings() {
      assertThat(PimStatusCode.explain(0)).hasValue("The request succeeded.");
      assertThat(PimStatusCode.explain(1008))
          .hasValue("The device's PIM backend returned an error (HTTP 400, 404, 413 or 5xx).");
      assertThat(PimStatusCode.explain(1016))
          .hasValue("There was nothing to return or to commit; this is not an error.");
    }

    @Test
    @DisplayName("has nothing to say for a code it does not know")
    void unknownCodeHasNoMeaning() {
      assertThat(PimStatusCode.explain(2000)).isEmpty();
    }

    @Test
    @DisplayName("has nothing to say when there is no code at all")
    void noCodeHasNoMeaning() {
      assertThat(PimStatusCode.explain(null)).isEmpty();
    }
  }
}
