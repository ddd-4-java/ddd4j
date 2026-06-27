package io.ddd4j.extension.pf4j.point.crypto;

import org.pf4j.ExtensionPoint;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface CryptoExtensionPoint extends ExtensionPoint {

    String encrypt(String source, Object secretKey);

    String decrypt(String source, Object secretKey);

}
