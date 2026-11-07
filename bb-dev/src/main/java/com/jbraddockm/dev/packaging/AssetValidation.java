package com.jbraddockm.dev.packaging;

import com.jbraddockm.core.bar.BarValidation.Message;
import com.jbraddockm.core.bar.BarValidation.Severity;
import java.util.List;

/**
 * What the asset rules say about a descriptor's assets: the warnings of BlackBerry's {@code
 * AttributeValidator}, which are advisory and do not by themselves stop a bar being built.
 *
 * @param messages what the rules found, in the order the assets appear
 */
record AssetValidation(List<Message> messages) {

  AssetValidation {
    messages = List.copyOf(messages);
  }

  /**
   * Tells whether the rules found nothing to warn about.
   *
   * @return {@code true} if there is no message of severity {@link Severity#WARNING}
   */
  boolean clean() {
    return messages.stream().noneMatch(message -> message.severity() == Severity.WARNING);
  }
}
