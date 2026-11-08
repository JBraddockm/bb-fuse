package com.jbraddockm.core.device;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509KeyManager;

/** A key manager that offers nothing — enough to build a client where an identity is optional. */
final class NoIdentity implements X509KeyManager {

  @Override
  public String[] getClientAliases(String keyType, Principal[] issuers) {
    return null;
  }

  @Override
  public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
    return null;
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
    return null;
  }

  @Override
  public PrivateKey getPrivateKey(String alias) {
    return null;
  }
}
