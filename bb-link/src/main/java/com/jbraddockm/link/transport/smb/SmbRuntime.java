package com.jbraddockm.link.transport.smb;

/**
 * Applies the jcifs settings the device's Samba requires, before any jcifs class reads them.
 *
 * <p>The device accepts only plain NTLM (not extended security) and answers nothing to the NTLMv2
 * exchange the library defaults to, so the authentication level is pinned to NTLMv1. The settings
 * are process-global, which jcifs reads once from the system properties; a value already set by the
 * operator is left untouched. {@link JcifsFileStore} applies them before its first use of the
 * library.
 */
final class SmbRuntime {

  private static final String EXTENDED_SECURITY = "jcifs.client.useExtendedSecurity";
  private static final String LM_COMPATIBILITY = "jcifs.lmCompatibility";

  private SmbRuntime() {}

  /** Sets the required properties unless the operator has already set them. */
  static void apply() {
    setIfAbsent(EXTENDED_SECURITY, "false");
    setIfAbsent(LM_COMPATIBILITY, "0");
  }

  private static void setIfAbsent(String key, String value) {
    if (System.getProperty(key) == null) {
      System.setProperty(key, value);
    }
  }
}
