package com.jbraddockm.core.webdav;

import com.jbraddockm.core.xml.XmlElement;
import com.jbraddockm.core.xml.XmlParser;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code DAV:multistatus} document of a {@code PROPFIND} answer. The file-share server {@link
 * #build builds} one from a list of {@link DavResource}; the device-file client {@link #parse
 * parses} one back. Parsing is namespace-prefix-agnostic (the device may use {@code D:}, {@code d:}
 * or none), matching on local names.
 */
public final class MultiStatus {

  private static final DateTimeFormatter HTTP_DATE =
      DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

  private MultiStatus() {}

  /**
   * Builds the {@code DAV:multistatus} XML for a {@code PROPFIND} answer, one {@code response} per
   * resource with the properties the BB10 File Manager reads.
   *
   * @param resources the resources to report, the requested one first
   * @return the XML document, starting with the declaration
   */
  public static String build(List<DavResource> resources) {
    var xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
    xml.append("<D:multistatus xmlns:D=\"DAV:\">\n");
    for (var resource : resources) {
      xml.append("  <D:response>\n");
      xml.append("    <D:href>").append(escape(resource.href())).append("</D:href>\n");
      xml.append("    <D:propstat>\n      <D:prop>\n");
      if (resource.collection()) {
        xml.append("        <D:resourcetype><D:collection/></D:resourcetype>\n");
      } else {
        xml.append("        <D:resourcetype/>\n");
        xml.append("        <D:getcontentlength>")
            .append(resource.contentLength())
            .append("</D:getcontentlength>\n");
        if (resource.contentType() != null) {
          xml.append("        <D:getcontenttype>")
              .append(escape(resource.contentType()))
              .append("</D:getcontenttype>\n");
        }
      }
      xml.append("        <D:displayname>")
          .append(escape(resource.displayName()))
          .append("</D:displayname>\n");
      if (resource.lastModified() != null) {
        xml.append("        <D:getlastmodified>")
            .append(HTTP_DATE.format(resource.lastModified()))
            .append("</D:getlastmodified>\n");
      }
      xml.append("      </D:prop>\n");
      xml.append("      <D:status>").append(DavStatus.OK.line()).append("</D:status>\n");
      xml.append("    </D:propstat>\n  </D:response>\n");
    }
    xml.append("</D:multistatus>\n");
    return xml.toString();
  }

  /**
   * Parses a {@code DAV:multistatus} answer into its resources (the client side).
   *
   * @param document the XML text
   * @return the resources, in document order
   * @throws com.jbraddockm.core.xml.MalformedXmlException if the text is not well-formed XML
   */
  public static List<DavResource> parse(String document) {
    var root = XmlParser.parse(document);
    var resources = new ArrayList<DavResource>();
    for (var response : localChildren(root, "response")) {
      var href = localChild(response, "href").map(XmlElement::text).orElse("");
      var prop =
          localChild(response, "propstat")
              .flatMap(propstat -> localChild(propstat, "prop"))
              .orElse(null);
      if (prop == null) {
        continue;
      }
      var collection =
          localChild(prop, "resourcetype")
              .map(type -> localChild(type, "collection").isPresent())
              .orElse(false);
      var length =
          localChild(prop, "getcontentlength")
              .map(XmlElement::text)
              .map(String::strip)
              .flatMap(MultiStatus::parseLong)
              .orElse(0L);
      var lastModified =
          localChild(prop, "getlastmodified")
              .map(XmlElement::text)
              .flatMap(MultiStatus::parseHttpDate)
              .orElse(null);
      var displayName =
          localChild(prop, "displayname")
              .map(XmlElement::text)
              .filter(name -> !name.isEmpty())
              .orElseGet(() -> lastSegment(href));
      var contentType =
          localChild(prop, "getcontenttype")
              .map(XmlElement::text)
              .filter(type -> !type.isEmpty())
              .orElse(null);
      resources.add(
          new DavResource(href, collection, length, lastModified, displayName, contentType));
    }
    return List.copyOf(resources);
  }

  private static List<XmlElement> localChildren(XmlElement parent, String localName) {
    var matches = new ArrayList<XmlElement>();
    for (var child : parent.children()) {
      if (local(child.name()).equals(localName)) {
        matches.add(child);
      }
    }
    return matches;
  }

  private static Optional<XmlElement> localChild(XmlElement parent, String localName) {
    for (var child : parent.children()) {
      if (local(child.name()).equals(localName)) {
        return Optional.of(child);
      }
    }
    return Optional.empty();
  }

  private static String local(String name) {
    var colon = name.indexOf(':');
    return colon < 0 ? name : name.substring(colon + 1);
  }

  private static String lastSegment(String href) {
    var segments = DavPaths.segments(href);
    return segments.isEmpty() ? "" : segments.getLast();
  }

  private static Optional<Long> parseLong(String text) {
    try {
      return Optional.of(Long.parseLong(text));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  private static Optional<Instant> parseHttpDate(String text) {
    try {
      return Optional.of(Instant.from(HTTP_DATE.parse(text.strip())));
    } catch (DateTimeParseException _) {
      return Optional.empty();
    }
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
