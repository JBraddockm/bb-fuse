package com.jbraddockm.core.bar;

/**
 * One asset of a {@code .bar}: a ZIP entry other than the manifest, at its in-package path.
 *
 * @param path the entry path within the archive, with {@code /} separators
 * @param size the uncompressed size in bytes, or {@code -1} if the archive does not record it
 */
public record BarAsset(String path, long size) {}
