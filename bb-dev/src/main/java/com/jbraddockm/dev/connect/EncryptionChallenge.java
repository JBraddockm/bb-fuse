package com.jbraddockm.dev.connect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The device's RSA encryption challenge, parsed from an {@code EncryptedChallengeResponse} payload.
 * The header is little-endian; only the RSA container is needed to recover the session key.
 *
 * @param container the RSA-encrypted blob to decrypt with our private key
 * @param expectedSignatureLength the signature length the device expects (128)
 * @param expectedSignatureType the signature type the device expects (1, RSA-1.5)
 */
record EncryptionChallenge(
    byte[] container, int expectedSignatureLength, int expectedSignatureType) {

  static EncryptionChallenge parse(byte[] payload) {
    var buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
    buffer.getShort(); // entireLength
    buffer.getShort(); // version
    int sourceLength = buffer.getShort() & 0xFFFF;
    buffer.getShort(); // sessionKeyLength
    buffer.getShort(); // sessionKeyType
    int containerLength = buffer.getShort() & 0xFFFF;
    buffer.getShort(); // containerPrimitive
    buffer.getShort(); // containerType
    int expectedSignatureLength = buffer.getShort() & 0xFFFF;
    int expectedSignatureType = buffer.getShort() & 0xFFFF;
    buffer.getInt(); // containerKeyVersion
    buffer.position(buffer.position() + sourceLength); // sourceName
    byte[] container = new byte[containerLength];
    buffer.get(container);
    return new EncryptionChallenge(container, expectedSignatureLength, expectedSignatureType);
  }
}
