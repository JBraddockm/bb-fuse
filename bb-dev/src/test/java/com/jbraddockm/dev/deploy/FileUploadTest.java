package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FileUpload: parsing the device's Put File response")
class FileUploadTest {

  @Test
  @DisplayName("reads the number of bytes the device accepted")
  void readsSize() {
    var text = Resources.text(FileUploadTest.class, "device--put-file.txt");

    var upload = FileUpload.parse(List.of(text.split("\n", -1)));

    assertThat(upload.size()).isEqualTo(18L);
  }
}
