package com.jbraddockm.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.discovery.DiscoveryClient.Body;
import com.jbraddockm.core.testing.Resources;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DiscoveryClient}: reading a device's identity from {@code discovery.cgi} over
 * the plain {@code :80} listener.
 */
@DisplayName("DiscoveryClient: identifying a device over discovery.cgi")
class DiscoveryClientTest {

  private static DiscoveryClient answering(int status, String body) {
    return new DiscoveryClient(_ -> new Body(status, body));
  }

  private static DiscoveryClient serving(String fixture) {
    return answering(200, Resources.text(DiscoveryClientTest.class, fixture));
  }

  @Nested
  @DisplayName("when the device answers")
  class Answering {

    @Test
    @DisplayName("reads the characteristics, with the PIN as uppercase hex and the GUID")
    void readsTheCharacteristics() {
      var characteristics = serving("discovery--real.xml").read("169.254.0.1").orElseThrow();

      assertThat(characteristics.pin()).isEqualTo("2C1E7583");
      assertThat(characteristics.guid()).isEqualTo("EDunxOMuoNpdOlvSIBPJD1dfcMs=");
      assertThat(characteristics.modelName()).isEqualTo("STR100-1");
      assertThat(characteristics.platformVersion()).isEqualTo("10.3.3.3216");
    }
  }

  @Nested
  @DisplayName("when the host is not an identifiable device")
  class NotADevice {

    @Test
    @DisplayName("is nothing when the status is not a success")
    void notASuccessStatus() {
      assertThat(answering(404, "").read("10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("is nothing when the host cannot be reached")
    void cannotBeReached() {
      DiscoveryClient client =
          new DiscoveryClient(
              _ -> {
                throw new IOException("refused");
              });

      assertThat(client.read("10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("is nothing when the answer is not well-formed XML")
    void notXml() {
      assertThat(serving("discovery--synthetic-not-xml.xml").read("10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("is nothing when the answer carries no characteristics element")
    void noCharacteristicsElement() {
      assertThat(serving("discovery--synthetic-other-element.xml").read("10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("is nothing when the characteristics element is empty")
    void emptyCharacteristics() {
      assertThat(serving("discovery--synthetic-empty.xml").read("10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("is nothing when the device returned an error instead of its characteristics")
    void errorResponse() {
      assertThat(serving("discovery--synthetic-error.xml").read("10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("is nothing when there is no usable PIN")
    void noUsablePin() {
      assertThat(serving("discovery--synthetic-malformed.xml").read("10.0.0.1")).isEmpty();
    }
  }
}
