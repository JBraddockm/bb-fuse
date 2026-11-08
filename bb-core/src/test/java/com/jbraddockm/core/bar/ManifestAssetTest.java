package com.jbraddockm.core.bar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ManifestAsset: one asset of a bar")
class ManifestAssetTest {

  @Test
  @DisplayName("is a native binary only when its type is Qnx/Elf, without regard to case")
  void isNativeBinary() {
    assertThat(new ManifestAsset("a", "d", "Qnx/Elf").isNativeBinary()).isTrue();
    assertThat(new ManifestAsset("a", "d", "qnx/elf").isNativeBinary()).isTrue();
    assertThat(new ManifestAsset("a", "d", "Qnx/Qml").isNativeBinary()).isFalse();
    assertThat(new ManifestAsset("a", "d", null).isNativeBinary()).isFalse();
  }

  @Test
  @DisplayName("rejects a null name or digest")
  void rejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> new ManifestAsset(null, "d", null));
    assertThatNullPointerException().isThrownBy(() -> new ManifestAsset("a", null, null));
  }
}
