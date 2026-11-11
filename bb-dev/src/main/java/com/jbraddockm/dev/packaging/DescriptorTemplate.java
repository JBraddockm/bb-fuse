package com.jbraddockm.dev.packaging;

/**
 * A starter {@code bar-descriptor.xml} for {@code bb-dev package -generateXml}. It mirrors the
 * descriptor Momentics writes for a standard (empty) Cascades project: identity, the four build
 * configurations (Device-Debug/Release/Profile and Simulator-Debug, with the Cascades entry point
 * on Release), version, author and icon, the initial window, the asset and translation entries, and
 * the {@code run_native} permission with the Qt library path. Values that are not substituted by
 * the IDE are fixed placeholders a developer edits — the app name ({@code MyApp}), {@code
 * com.example.} id prefix and {@code Example Inc.} author; the {@code authorId} stays commented,
 * since it is filled from the signing certificate at signing time, not at project creation. Parses
 * with {@link com.jbraddockm.dev.packaging.descriptor.BarDescriptor} as written.
 */
final class DescriptorTemplate {

  private DescriptorTemplate() {}

  /** The starter descriptor, with a trailing newline. */
  static String xml() {
    return String.join(
            "\n",
            "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>",
            "<qnx xmlns=\"http://www.qnx.com/schemas/application/1.0\">",
            "",
            "    <!-- A universally unique application identifier, reverse-DNS style. Change it."
                + " Required. -->",
            "    <id>com.example.MyApp</id>",
            "",
            "    <configuration name=\"Device-Debug\">",
            "        <platformArchitecture>armle-v7</platformArchitecture>",
            "        <asset path=\"arm/o.le-v7-g/MyApp\" entry=\"true\""
                + " type=\"Qnx/Elf\">MyApp</asset>",
            "    </configuration>",
            "    <configuration name=\"Device-Release\">",
            "        <entryPointType>Qnx/Cascades</entryPointType>",
            "        <platformArchitecture>armle-v7</platformArchitecture>",
            "        <asset path=\"arm/o.le-v7/MyApp.so\" entry=\"true\""
                + " type=\"Qnx/Elf\">MyApp.so</asset>",
            "    </configuration>",
            "    <configuration name=\"Device-Profile\">",
            "        <platformArchitecture>armle-v7</platformArchitecture>",
            "        <asset path=\"arm-p/o.le-v7-g/MyApp\" entry=\"true\""
                + " type=\"Qnx/Elf\">MyApp</asset>",
            "    </configuration>",
            "    <configuration name=\"Simulator-Debug\">",
            "        <platformArchitecture>x86</platformArchitecture>",
            "        <asset path=\"x86/o-g/MyApp\" entry=\"true\" type=\"Qnx/Elf\">MyApp</asset>",
            "    </configuration>",
            "",
            "    <!-- The name shown in the installer. Optional. -->",
            "    <name>MyApp</name>",
            "",
            "    <!-- Application version, <0-999>.<0-999>.<0-999>. Required. -->",
            "    <versionNumber>1.0.0</versionNumber>",
            "",
            "    <!-- Fourth version segment (0..65535). -->",
            "    <buildId>1</buildId>",
            "",
            "    <!-- <versionLabel></versionLabel> -->",
            "",
            "    <!-- Description shown in the installer. Optional. -->",
            "    <description>The MyApp application</description>",
            "",
            "    <!-- <copyright></copyright> -->",
            "",
            "    <!-- Author name used for signing; must match your development certificate. -->",
            "    <author>Example Inc.</author>",
            "",
            "    <!-- Author id from the signing authority; filled in at signing time. -->",
            "    <!-- <authorId>ABC1234YjsnUk235h</authorId> -->",
            "",
            "    <initialWindow>",
            "        <autoOrients>true</autoOrients>",
            "        <systemChrome>none</systemChrome>",
            "    </initialWindow>",
            "",
            "    <!-- The home-screen icon, 114x114. -->",
            "    <icon>",
            "        <image>icon.png</image>",
            "    </icon>",
            "",
            "    <asset path=\"icon.png\">icon.png</asset>",
            "    <asset path=\"assets\">assets</asset>",
            "",
            "    <!-- Locale support. -->",
            "    <asset path=\"translations\" dest=\"qm\">",
            "        <include name=\"*.qm\"/>",
            "    </asset>",
            "",
            "    <!-- Permission to execute native code. Required for native applications. -->",
            "    <permission system=\"true\">run_native</permission>",
            "    <env var=\"LD_LIBRARY_PATH\" value=\"app/native/lib:/usr/lib/qt4/lib\"/>",
            "",
            "</qnx>")
        + "\n";
  }
}
