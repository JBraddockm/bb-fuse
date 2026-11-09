/**
 * The bundled manifest-specification rule sets: the parsed rule model ({@link
 * com.jbraddockm.core.bar.rules.RuleSet}, {@link com.jbraddockm.core.bar.rules.SectionRule}, {@link
 * com.jbraddockm.core.bar.rules.AttributeRule}, …) and the shared value patterns, loaded from the
 * {@code rulesets/} resources and chosen by {@code Archive-Manifest-Version}.
 *
 * <p>A pure-JDK leaf: it depends only on {@code core.xml}, never on the rest of {@code core.bar},
 * so the bar facade's {@code ManifestValidator} reads one way into it.
 */
package com.jbraddockm.core.bar.rules;
