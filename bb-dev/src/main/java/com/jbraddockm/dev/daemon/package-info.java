/**
 * The bb-dev daemon: one newline-JSON loop ({@code bb-dev -daemon}) that aggregates the deploy and
 * packaging namespaces over bb-core's daemon framework. It sits above the feature packages and
 * depends on both, which they never do on each other.
 */
package com.jbraddockm.dev.daemon;
