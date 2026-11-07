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
      root = XmlParser.parse(stripByteOrderMark(xml));
    } catch (MalformedXmlException failure) {
      throw new InvalidDescriptorException("it is not well-formed XML: " + failure.getMessage());
    }
    if (!root.name().equals("qnx")) {
      throw new InvalidDescriptorException("its root element is '" + root.name() + "', not 'qnx'");
    }

    return new BarDescriptor(
        requiredText(root, "id"),
        requiredLocalizedText(root, "name"),
        localizedText(root, "description"),
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
        localizedText(root, "splashscreen"),
        permissions(root),
        assets(root),
        optionalText(root, "platformVersion"),
        configurations(root),
        optionalText(root, "entryPointType"),
        optionalText(root, "cascadesTheme"),
        optionalText(root, "packageLocale"),
        texts(root, "arg"),
        splashScreens(root),
        invokeTargets(root),
        entryPoints(root));
  }

  private static List<String> splashScreens(XmlElement root) {
    var images = new ArrayList<String>();
    var single = localizedText(root, "splashscreen");
    if (single != null) {
      for (var path : single.split(":")) {
        if (!path.isBlank()) {
          images.add(path.strip());
        }
      }
    }
    for (var group : root.children("splashScreens")) {
      for (var image : group.children("image")) {
        var value = textValue(image);
        if (value != null) {
          images.add(value);
        }
      }
    }
    return images;
  }

  private static List<EntryPoint> entryPoints(XmlElement root) {
    var entryPoints = new ArrayList<EntryPoint>();
    for (var entryPoint : root.children("entryPoint")) {
      entryPoint
          .attribute("id")
          .ifPresent(
              id ->
                  entryPoints.add(
                      new EntryPoint(
                          id,
                          localizedText(entryPoint, "name"),
                          optionalText(entryPoint, "entryPointType"),
                          permissions(entryPoint))));
    }
    return entryPoints;
  }

  private static List<InvokeTarget> invokeTargets(XmlElement root)
      throws InvalidDescriptorException {
    var targets = new ArrayList<InvokeTarget>();
    for (var target : root.children("invoke-target")) {
      targets.add(
          new InvokeTarget(
              requiredAttribute(target, "id"),
              firstText(target, "type", "invoke-target-type"),
              optionalText(target, "entry-point-id"),
              firstText(target, "name", "invoke-target-name"),
              target.child("icon").map(icon -> localizedText(icon, "image")).orElse(null),
              filters(target)));
    }
    return targets;
  }

  private static List<InvokeFilter> filters(XmlElement target) throws InvalidDescriptorException {
    var filters = new ArrayList<InvokeFilter>();
    for (var filter : target.children("filter")) {
      var properties = new ArrayList<EnvironmentVariable>();
      for (var property : filter.children("property")) {
        properties.add(
            new EnvironmentVariable(
                requiredAttribute(property, "var"), property.attribute("value").orElse("")));
      }
      filters.add(
          new InvokeFilter(texts(filter, "action"), texts(filter, "mime-type"), properties));
    }
    return filters;
  }

  /** The text of the first of two interchangeable child tags that is present. */
  private static String firstText(XmlElement parent, String first, String second) {
    var value = optionalText(parent, first);
    return value != null ? value : optionalText(parent, second);
  }

  /** Strips a leading byte-order mark, which some descriptors carry and the parser rejects. */
  private static String stripByteOrderMark(String xml) {
    return !xml.isEmpty() && xml.charAt(0) == '﻿' ? xml.substring(1) : xml;
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
      var image = localizedText(icon, "image");
      if (image != null) {
        icons.add(image);
      }
    }
    return icons;
  }

  private static List<EnvironmentVariable> environment(XmlElement root)
      throws InvalidDescriptorException {
    var variables = new ArrayList<EnvironmentVariable>();
    for (var entry : root.children("env")) {
      variables.add(
          new EnvironmentVariable(
              requiredAttribute(entry, "var"), entry.attribute("value").orElse("")));
    }
    return variables;
  }

  private static List<DescriptorAsset> assets(XmlElement parent) throws InvalidDescriptorException {
    var assets = new ArrayList<DescriptorAsset>();
    for (var asset : parent.children("asset")) {
      var path = asset.attribute("path").orElse(null);
      var destination =
          asset.attribute("dest").orElse(asset.text().isEmpty() ? null : asset.text());
      if (path == null && destination == null) {
        throw new InvalidDescriptorException("an asset has neither a path nor a destination");
      }
      assets.add(
          new DescriptorAsset(
              path,
              destination,
              asset.attribute("type").orElse(null),
              asset.attribute("entry").map(Boolean::parseBoolean).orElse(false),
              asset.attribute("entryPointId").orElse(null),
              patterns(asset, "include"),
              patterns(asset, "exclude"),
              asset.attribute("public").map(Boolean::parseBoolean).orElse(false)));
    }
    return assets;
  }

  private static List<String> patterns(XmlElement asset, String childName) {
    var patterns = new ArrayList<String>();
    for (var child : asset.children(childName)) {
      child.attribute("name").ifPresent(patterns::add);
    }
    return patterns;
  }

  private static List<Permission> permissions(XmlElement root) {
    var permissions = new ArrayList<Permission>();
    for (var permission : root.children("permission")) {
      if (!permission.text().isEmpty()) {
        permissions.add(
            new Permission(
                permission.text(),
                permission.attribute("system").map(Boolean::parseBoolean).orElse(false)));
      }
    }
    return permissions;
  }

  private static List<DescriptorConfiguration> configurations(XmlElement root)
      throws InvalidDescriptorException {
    var configurations = new ArrayList<DescriptorConfiguration>();
    for (var configuration : root.children("configuration")) {
      configurations.add(
          new DescriptorConfiguration(
              configuration.attribute("name").orElse(null),
              configuration.attribute("id").orElse(null),
              optionalText(configuration, "platformArchitecture"),
              optionalText(configuration, "entryPointType"),
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

  private static String requiredLocalizedText(XmlElement parent, String childName)
      throws InvalidDescriptorException {
    var text = localizedText(parent, childName);
    if (text == null) {
      throw new InvalidDescriptorException("it has no " + childName);
    }
    return text;
  }

  /**
   * The text of a child that may be localized: its own text, or the first non-empty {@code text}
   * variant it holds. The default-locale value comes first in a descriptor, so the first variant is
   * taken. {@code null} if the child is absent or carries no text.
   */
  private static String localizedText(XmlElement parent, String childName) {
    return parent.child(childName).map(BarDescriptorParser::textValue).orElse(null);
  }

  /** The element's own text, or the first non-empty {@code text} variant it holds; {@code null}. */
  private static String textValue(XmlElement element) {
    if (!element.text().isEmpty()) {
      return element.text();
    }
    return element.children("text").stream()
        .map(XmlElement::text)
        .filter(text -> !text.isEmpty())
        .findFirst()
        .orElse(null);
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
