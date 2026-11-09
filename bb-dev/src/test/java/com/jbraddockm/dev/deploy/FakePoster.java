package com.jbraddockm.dev.deploy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** A poster that returns canned answers in order and remembers the last body it was given. */
final class FakePoster implements Poster {

  private final Deque<String> replies = new ArrayDeque<>();
  private final Deque<byte[]> rawReplies = new ArrayDeque<>();
  MultipartBody body;

  FakePoster(String... replies) {
    this.replies.addAll(List.of(replies));
  }

  void reply(String text) {
    replies.add(text);
  }

  /** Queues a raw-bytes answer (for binary downloads), taking precedence over text replies. */
  void replyRaw(byte[] bytes) {
    rawReplies.add(bytes);
  }

  @Override
  public Answer post(MultipartBody body) {
    this.body = body;
    if (!rawReplies.isEmpty()) {
      return new Answer(
          200, "application/octet-stream", new ByteArrayInputStream(rawReplies.remove()));
    }
    var text = replies.isEmpty() ? "" : replies.remove();
    return new Answer(
        200, "text/plain", new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }
}
