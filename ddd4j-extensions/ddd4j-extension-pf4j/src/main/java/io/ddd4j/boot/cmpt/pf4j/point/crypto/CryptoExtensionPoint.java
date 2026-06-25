package io.ddd4j.boot.cmpt.pf4j.point.crypto;

import org.pf4j.ExtensionPoint;

public interface CryptoExtensionPoint extends ExtensionPoint {

    String encrypt(String source, Object secretKey);

    String decrypt(String source, Object secretKey);

}
