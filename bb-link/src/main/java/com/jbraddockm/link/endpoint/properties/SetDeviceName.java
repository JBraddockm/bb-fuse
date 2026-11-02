package com.jbraddockm.link.endpoint.properties;

import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import com.jbraddockm.link.transport.cgi.ResponseElement;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.XmlConversation;

/**
 * Sets the device's name, the one shown in Device Connections and the operating system.
 *
 * <p>The name is checked against the device's own rules before it is sent (see {@link DeviceName}),
 * so an unacceptable name is refused here rather than at the device. Changes the device, so it is
 * never repeated after a dropped connection.
 *
 * <p>When the device rejects the request it answers an error envelope whose {@code ErrorIdentifier}
 * {@link #interpret(ResponseEnvelope, DeviceName) is surfaced} as a {@code COMMAND_FAILED} error
 * carrying the device's number and message. The set path of {@code dynamicProperties.cgi} uses
 * three: {@code 257} a bad or missing attribute name, {@code 258} a rejected value (missing, too
 * long, or failing validation), and {@code 259} a value that validated but could not be written.
 * For the device name, {@code 259} is the usual outcome on a normal device: writing the host name
 * ({@code _CS_HOSTNAME}) needs the {@code egid=0} privilege the device grants only to a
 * BlackBerry-ID-owner session, so a paired client — bb-link or BlackBerry Link alike — cannot apply
 * it on a device with no BlackBerry ID signed in. An authorization failure is reported without an
 * identifier. See {@code bb-link/PROTOCOL.md} for the full account.
 *
 * @param name the name to set
 */
public record SetDeviceName(DeviceName name) implements DeviceCommand<SetDeviceNameResult> {

  /** The settable attribute of {@code dynamicProperties.cgi} that carries the device name. */
  static final String DEVICE_NAME_ATTRIBUTE = "DeviceInformation\\DeviceName";

  /**
   * The value of the {@code action} field that makes {@code dynamicProperties.cgi} write rather
   * than read. The program chooses between setting and reading on this field, not on the HTTP
   * method, so a request without it is answered as a read: the property tree with a {@code Success}
   * status, and nothing is written.
   */
  static final String SET_ACTION = "set";

  /**
   * Validates the name and builds the command.
   *
   * @param name the name to set
   * @throws com.jbraddockm.core.error.InvalidArgumentException if the name is one the device would
   *     reject
   */
  public SetDeviceName(String name) {
    this(DeviceName.of(name));
  }

  /**
   * Returns the request that sets the name.
   *
   * @param name the validated name
   * @return the request
   */
  public static CgiRequest.Form request(DeviceName name) {
    return CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES)
        .withText(FormField.ACTION, SET_ACTION)
        .withText(FormField.NAME, DEVICE_NAME_ATTRIBUTE)
        .withText(FormField.VALUE, name.value())
        .describedAs("Setting the device name");
  }

  /**
   * Reads the device's answer to {@link #request(DeviceName)}.
   *
   * @param response the answer
   * @param name the name that was set
   * @return the outcome as a {@link Step.Finish}, or why it failed as a {@link Step.Fail}
   */
  public static Step<SetDeviceNameResult> interpret(ResponseEnvelope response, DeviceName name) {
    var failure = ResponseFailures.failureFor(response, ResponseElement.DYNAMIC_PROPERTIES);
    if (failure.isPresent()) {
      return new Step.Fail<>(failure.get());
    }
    return new Step.Finish<>(new SetDeviceNameResult(name.value()));
  }

  @Override
  public Conversation<SetDeviceNameResult> conversation() {
    return new XmlConversation<>() {
      @Override
      public Step<SetDeviceNameResult> start() {
        return new Step.Send<>(request(name));
      }

      @Override
      protected Step<SetDeviceNameResult> onResponse(ResponseEnvelope response) {
        return interpret(response, name);
      }
    };
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
