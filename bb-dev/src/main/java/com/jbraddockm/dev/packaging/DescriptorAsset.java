package com.jbraddockm.dev.packaging;

/**
 * An {@code asset} of a descriptor: a file to pack, where it goes in the archive, and whether it is
 * the entry point. At least one of {@link #path} and {@link #destination} is present; when only one
 * is given the other is derived during packaging, which is {@code P7}'s work.
 *
 * @param path the source path on the host, relative to the assets root, from the {@code path}
 *     attribute; may be {@code null}
 * @param destination the path inside the archive, from the {@code dest} attribute; may be {@code
 *     null}, meaning the root of the assets
 * @param type the asset type, for example {@code Qnx/Elf}, from the {@code type} attribute; may be
 *     {@code null}
 * @param entry whether this asset is the application entry point ({@code entry: true})
 */
public record DescriptorAsset(String path, String destination, String type, boolean entry) {}
