package com.jbraddockm.core.bar;

import java.util.Objects;

/**
 * One asset as the manifest describes it: an archive-relative name, its SHA-512 digest, and its
 * type when the manifest records one. Each asset sits in its own manifest section (an {@code
 * Archive-Asset-Name} with its {@code Archive-Asset-*-Digest} attributes). The dev-mode sync keys
 * its diff on {@link #name} and compares {@link #sha512Digest} — both the bar and the device report
 * the digest in the same BlackBerry base64url form, so they compare as strings with no decoding.
 *
 * @param name the {@code Archive-Asset-Name}, the archive-relative path
 * @param sha512Digest the {@code Archive-Asset-SHA-512-Digest}, or empty if the section has none
 * @param type the {@code Archive-Asset-Type} (for example {@code Qnx/Elf}), or {@code null} if
 *     absent
 */
public record ManifestAsset(String name, String sha512Digest, String type) {

  public ManifestAsset {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(sha512Digest, "sha512Digest");
  }

  /**
   * Whether this asset is a native ELF binary, as its {@code Archive-Asset-Type} marks it.
   *
   * @return {@code true} if the type is {@code Qnx/Elf}
   */
  public boolean isNativeBinary() {
    return "Qnx/Elf".equalsIgnoreCase(type);
  }
}
