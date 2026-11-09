package com.jbraddockm.core.bar.rules;

/**
 * The rule for one manifest attribute within a section, as a {@code <attribute>} of a rule set.
 * Lengths and cardinalities of {@code -1} / {@link Integer#MAX_VALUE} mean unbounded.
 *
 * @param name the attribute name ({@code Archive-Asset-x-Digest} uses {@code x} for the hash)
 * @param required when the attribute is required
 * @param regexId the id of the value pattern in {@code regex.properties}, or empty for none
 * @param minLength the least value length, or {@code -1}
 * @param maxLength the greatest value length, or {@code -1}
 * @param minCardinality the least number of times it may appear in a section
 * @param maxCardinality the greatest number of times it may appear
 * @param continuationAllowed whether its value may wrap onto continuation lines
 * @param canLocalize whether locale-suffixed variants are allowed
 * @param mandatoryFirstInSection whether it must be the first attribute of its section
 */
public record AttributeRule(
    String name,
    Requirement required,
    String regexId,
    int minLength,
    int maxLength,
    int minCardinality,
    int maxCardinality,
    boolean continuationAllowed,
    boolean canLocalize,
    boolean mandatoryFirstInSection) {}
