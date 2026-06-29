package io.ddd4j.data.crypto.domain.enums;

/**
 * 加密解密方式
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum CryptoType {

    /**
     * 默认的行为
     */
    NOOP,
    /**
     * 系统内部加解密
     */
    INTERNAL,
    /**
     * 弗兰科信息
     */
    FLKSEC

}
