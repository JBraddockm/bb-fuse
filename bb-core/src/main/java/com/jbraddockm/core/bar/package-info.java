/**
 * Reading BlackBerry archives ({@code .bar} files).
 *
 * <p>A bar is a ZIP archive whose {@code META-INF/MANIFEST.MF} describes the package: its kind, id,
 * name and version. {@link BarInspector} reads that manifest, and nothing else of the archive, in
 * pure JDK — {@link BarArchive} opens the ZIP and {@link Manifest} parses the manifest — and
 * returns the records of this package, so a bar can be inspected without any external library.
 */
package com.jbraddockm.core.bar;
