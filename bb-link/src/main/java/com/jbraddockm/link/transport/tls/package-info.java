/**
 * The device TLS scheme for the HTTP client: {@link com.jbraddockm.link.transport.tls.TrustAllTls},
 * which accepts the device's self-signed certificate and offers the client identity. The identity
 * itself — the certificate, the key, and the key manager that presents it — now lives in {@code
 * bb-core} as {@link com.jbraddockm.core.device.ClientCertificate}, {@link
 * com.jbraddockm.core.device.PemIdentity} and {@link com.jbraddockm.core.device.ClientKeyManager},
 * shared with the other device clients. This scheme stays here until the CGI transport moves off
 * the Apache HTTP client.
 */
package com.jbraddockm.link.transport.tls;
