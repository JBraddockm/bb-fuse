package com.jbraddockm.core.bar;

/** When a manifest rule applies — the {@code required} property of a section or attribute. */
enum Requirement {

  /** Always required. */
  ALWAYS,

  /** Required only when the package is signed. */
  IF_SIGNED,

  /** Required only when its section is present. */
  IF_SECTION,

  /** Not required. */
  NEVER;

  static Requirement parse(String value) {
    if (value == null) {
      return NEVER;
    }
    return switch (value) {
      case "true" -> ALWAYS;
      case "if-signed" -> IF_SIGNED;
      case "if-section" -> IF_SECTION;
      default -> NEVER;
    };
  }
}
