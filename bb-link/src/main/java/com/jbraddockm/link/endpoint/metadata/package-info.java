/**
 * The {@code metadata.cgi} read client: enumerating the device's media inventory.
 *
 * <p>{@code metadata.cgi} is read-only — it reports what media is on the device (pictures, videos,
 * music, documents, playlists) and a library summary. The device answers a {@code
 * RimTabletResponse} envelope whose {@code Metadata} element holds the rows as JSON inside a CDATA
 * block. bb-link strips the envelope it already understands and hands the rows back unchanged, as
 * the pimsync readers do with their bodies: the row fields are the client's to interpret, not
 * bb-link's to type.
 */
package com.jbraddockm.link.endpoint.metadata;
