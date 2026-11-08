package com.jbraddockm.dev.packaging.descriptor;

/**
 * The {@code initialWindow} of a descriptor: how the application's first window is oriented.
 *
 * @param autoOrients whether the application orients itself ({@code autoOrients: true}); when it
 *     does, the fixed {@link #aspectRatio} does not apply
 * @param aspectRatio the fixed orientation, {@code portrait} or {@code landscape}; may be {@code
 *     null} to use the device default. The value is carried through as written and not checked
 *     here.
 */
public record InitialWindow(boolean autoOrients, String aspectRatio) {}
