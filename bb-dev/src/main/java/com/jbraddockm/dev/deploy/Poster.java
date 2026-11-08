package com.jbraddockm.dev.deploy;

import java.io.IOException;
import java.io.InputStream;

/**
 * The seam that posts a built {@link MultipartBody} to the device's {@code appInstaller.cgi} and
 * hands back the raw answer. A real poster runs the HTTPS request; a test poster returns a canned
 * reply, so the engine can be exercised with no network.
 */
interface Poster {

  /**
   * Posts the body and returns the device's answer.
   *
   * @param body the request body
   * @return the answer
   * @throws IOException if the request fails
   */
  Answer post(MultipartBody body) throws IOException;

  /**
   * One device answer: the HTTP status, the content type if any, and the response body as a stream
   * the caller closes.
   *
   * @param status the HTTP status code
   * @param contentType the {@code Content-Type} header, or {@code null}
   * @param body the response body
   */
  record Answer(int status, String contentType, InputStream body) {}
}
