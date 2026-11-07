package com.jbraddockm.link.endpoint.metadata;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.WireValue;
import java.util.Arrays;

/**
 * The sort direction of a list result. {@link #DEFAULT} is the device's own ordering and is not
 * sent.
 */
public enum SortOrder implements WireValue {

  /** The device's default ordering — sent as no {@code order} parameter. */
  DEFAULT("default"),

  /** Ascending. */
  ASC("asc"),

  /** Descending. */
  DESC("desc");

  private final String wireName;

  SortOrder(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Recognises an order by its wire name.
   *
   * @param value the order as written in a request
   * @return the matching order
   * @throws InvalidArgumentException if the value is not a known order
   */
  public static SortOrder of(String value) {
    return Arrays.stream(values())
        .filter(order -> order.wireName.equals(value))
        .findFirst()
        .orElseThrow(() -> new InvalidArgumentException("order", "is not asc or desc: " + value));
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
