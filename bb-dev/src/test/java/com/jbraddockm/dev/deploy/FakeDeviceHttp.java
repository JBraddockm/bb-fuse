package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.device.DeviceHttp;

/** A device connection that accepts a certificate login and counts how often it logs in. */
final class FakeDeviceHttp implements DeviceHttp {

  private final String successBody;
  int logins;

  FakeDeviceHttp(String successBody) {
    this.successBody = successBody;
  }

  @Override
  public String get(String pathAndQuery) {
    logins++;
    return successBody;
  }

  @Override
  public boolean hasLoginCookie() {
    return false;
  }

  @Override
  public boolean offersClientCertificate() {
    return true;
  }
}
