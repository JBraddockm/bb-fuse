package com.jbraddockm.core.device;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

/**
 * Offers one client identity to the device, whatever the device asks for.
 *
 * <p>The standard key manager only offers a certificate whose issuer is on the list of acceptable
 * authorities that the server sends. The device's list may name the root while the certificate was
 * issued by an intermediate that only the device holds, in which case the standard manager would
 * send nothing at all. A real client, such as BlackBerry Link, sends its certificate anyway, and
 * the device decides.
 *
 * <p>It extends {@link X509ExtendedKeyManager} so that a client built on an {@code SSLEngine} —
 * which {@code java.net.http} is — asks it for the client alias through {@link
 * #chooseEngineClientAlias}; a plain {@code X509KeyManager} is consulted only by the socket path
 * and would never present the certificate over the engine.
 */
public final class ClientKeyManager extends X509ExtendedKeyManager {

  private static final String ALIAS = "device-client";

  private final X509Certificate[] chain;
  private final PrivateKey key;

  /**
   * Holds the identity to offer.
   *
   * @param identity the certificate chain and private key to present
   */
  public ClientKeyManager(PemIdentity.Loaded identity) {
    this.chain = identity.chain().clone();
    this.key = identity.key();
  }

  @Override
  public String[] getClientAliases(String keyType, Principal[] issuers) {
    return new String[] {ALIAS};
  }

  @Override
  public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
    return ALIAS;
  }

  @Override
  public String chooseEngineClientAlias(String[] keyTypes, Principal[] issuers, SSLEngine engine) {
    return ALIAS;
  }

  @Override
  public String[] getServerAliases(String keyType, Principal[] issuers) {
    return null;
  }

  @Override
  public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
    return null;
  }

  @Override
  public X509Certificate[] getCertificateChain(String alias) {
    return ALIAS.equals(alias) ? chain.clone() : null;
  }

  @Override
  public PrivateKey getPrivateKey(String alias) {
    return ALIAS.equals(alias) ? key : null;
  }
}
