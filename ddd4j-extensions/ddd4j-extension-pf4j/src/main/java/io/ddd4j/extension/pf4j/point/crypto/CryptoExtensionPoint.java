package io.ddd4j.extension.pf4j.point.crypto;

import org.pf4j.ExtensionPoint;

public interface CryptoExtensionPoint extends ExtensionPoint {

    String encrypt(String source, Object secretKey);

    String decrypt(String source, Object secretKey);

}
