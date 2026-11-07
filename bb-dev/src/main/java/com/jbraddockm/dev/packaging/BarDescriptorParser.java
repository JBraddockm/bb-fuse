package com.jbraddockm.dev.packaging;

import com.jbraddockm.core.xml.MalformedXmlException;
import com.jbraddockm.core.xml.XmlElement;
import com.jbraddockm.core.xml.XmlParser;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a {@code bar-descriptor.xml} into a {@link BarDescriptor}. The grammar is the native
 * packager's (the {@code qnx}-rooted descriptor of {@code analysis/BAR-FORMAT.md} §6); the errors
 * mirror BlackBerry's {@code BbtExtensionXml}/{@code BaseXml} — a missing root, a missing mandatory
 * element or attribute, and a missing entry point.
 */
final class BarDescriptorParser {

  private BarDescriptorParser() {}

  static BarDescriptor parse(String xml) throws InvalidDescriptorException {
    XmlElement root;
    try {
      root = XmlParser.parse(xml);
    } catch (MalformedXmlException failure) {
      throw new InvalidDescriptorException("it is not well-formed XML: " + failure.getMessage());
    }
    if (!root.name().equals("qnx")) {
      throw new InvalidDescriptorException("its root element is '" + root.name() + "', not 'qnx'");
    }

    var assets = assets(root);
    var configurations = configurations(root);
    var descriptor =
        new BarDescriptor(
            requiredText(root, "id"),
            requiredText(root, "name"),
            optionalText(root, "description"),
            requiredText(root, "versionNumber"),
            initialWindow(root),
            icons(root),
            optionalText(root, "author"),
            optionalText(root, "authorId"),
            optionalText(root, "packageId"),
            optionalText(root, "buildId"),
            optionalText(root, "buildIdFile"),
            optionalText(root, "category"),
            environment(root),
            optionalText(root, "splashscreen"),
            texts(root, "permission"),
            assets,
            optionalText(root, "platformVersion"),
            configurations);

    if (descriptor.entryPoint().isEmpty()) {
      throw new InvalidDescriptorException(
          "it has no entry point: no asset is marked entry=\"true\"");
    }
    return descriptor;
  }

  private static InitialWindow initialWindow(XmlElement root) {
    return root.child("initialWindow")
        .map(
            window ->
                new InitialWindow(
                    window.childText("autoOrients").map(Boolean::parseBoolean).orElse(false),
                    window.childText("aspectRatio").orElse(null)))
        .orElse(null);
  }

  private static List<String> icons(XmlElement root) {
    var icons = new ArrayList<String>();
    for (var icon : root.children("icon")) {
      icon.childText("image").ifPresent(icons::add);
    }
    return icons;
  }

  private static List<EnvironmentVariable> environment(XmlElement root)
      throws InvalidDescriptorException {
    var variables = new ArrayList<EnvironmentVariable>();
    for (var entry : root.children("env")) {
      variables.add(
          new EnvironmentVariable(
              requiredAttribute(entry, "var"), requiredAttribute(entry, "value")));
    }
    return variables;
  }

  private static List<DescriptorAsset> assets(XmlElement parent) throws InvalidDescriptorException {
    var assets = new ArrayList<DescriptorAsset>();
    for (var asset : parent.children("asset")) {
      var path = asset.attribute("path").orElse(null);
      var destination = asset.attribute("dest").orElse(null);
      if (path == null && destination == null) {
        throw new InvalidDescriptorException("an asset has neither a path nor a dest");
      }
      assets.add(
          new DescriptorAsset(
              path,
              destination,
              asset.attribute("type").orElse(null),
              asset.attribute("entry").map(Boolean::parseBoolean).orElse(false)));
    }
    return assets;
  }

  private static List<DescriptorConfiguration> configurations(XmlElement root)
      throws InvalidDescriptorException {
    var configurations = new ArrayList<DescriptorConfiguration>();
    for (var configuration : root.children("configuration")) {
      configurations.add(
          new DescriptorConfiguration(
              requiredAttribute(configuration, "id"),
              configuration.attribute("name").orElse(null),
              optionalText(configuration, "platformArchitecture"),
              texts(configuration, "arg"),
              assets(configuration)));
    }
    return configurations;
  }

  private static List<String> texts(XmlElement parent, String childName) {
    var texts = new ArrayList<String>();
    for (var child : parent.children(childName)) {
      if (!child.text().isEmpty()) {
        texts.add(child.text());
      }
    }
    return texts;
  }

  private static String requiredText(XmlElement parent, String childName)
      throws InvalidDescriptorException {
    return parent
        .childText(childName)
        .orElseThrow(() -> new InvalidDescriptorException("it has no " + childName));
  }

  private static String optionalText(XmlElement parent, String childName) {
    return parent.childText(childName).orElse(null);
  }

  private static String requiredAttribute(XmlElement element, String attributeName)
      throws InvalidDescriptorException {
    return element
        .attribute(attributeName)
        .filter(value -> !value.isBlank())
        .orElseThrow(
            () ->
                new InvalidDescriptorException(
                    "its " + element.name() + " has no " + attributeName));
  }
}
