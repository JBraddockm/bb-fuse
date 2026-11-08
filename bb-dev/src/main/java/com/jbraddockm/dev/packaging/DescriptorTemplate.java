package com.jbraddockm.dev.packaging;

/**
 * A starter {@code bar-descriptor.xml} for {@code bb-dev package -generateXml}. It is a minimal but
 * complete native-application descriptor — identity, version, one entry-point asset, an icon and an
 * initial window — with the places a developer fills in left as obvious placeholders. It parses
 * with {@link BarDescriptor} as written.
 */
final class DescriptorTemplate {

  private DescriptorTemplate() {}

  /** The starter descriptor, with a trailing newline. */
  static String xml() {
    return String.join(
            "\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<qnx xmlns=\"http://www.qnx.com/schemas/application/1.0\">",
            "  <id>com.example.app</id>",
            "  <name>My App</name>",
            "  <versionNumber>1.0.0</versionNumber>",
            "  <author>Development</author>",
            "  <description>My application.</description>",
            "  <category>core.games</category>",
            "",
            "  <initialWindow>",
            "    <autoOrients>true</autoOrients>",
            "  </initialWindow>",
            "",
            "  <!-- The application binary; mark the entry point with entry=\"true\". -->",
            "  <asset path=\"arm/app\" entry=\"true\" type=\"Qnx/Elf\">app</asset>",
            "",
            "  <!-- The home-screen icon. -->",
            "  <icon>",
            "    <image>icon.png</image>",
            "  </icon>",
            "  <asset path=\"icon.png\">icon.png</asset>",
            "",
            "  <!-- Permissions the application needs, for example: -->",
            "  <!-- <permission system=\"false\">access_internet</permission> -->",
            "",
            "  <!-- A device build configuration. -->",
            "  <configuration name=\"Device-Release\">",
            "    <platformArchitecture>armv7-le</platformArchitecture>",
            "  </configuration>",
            "</qnx>")
        + "\n";
  }
}
