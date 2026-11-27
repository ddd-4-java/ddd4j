package io.ddd4j.extension.qrcode.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 二维码 PNG 输出。
 */
@Getter
public final class QrCodeOutput {

    /** \u751f\u6210\u7684\u4e8c\u7ef4\u7801\u56fe\u50cf\u5b57\u8282\u3002 */
    private final byte[] bytes;

    /**
     * \u4fdd\u5b58 PNG \u8f93\u51fa\u5b57\u8282\u7684\u526f\u672c\u3002
     * @param bytes PNG \u5b57\u8282
     */
    public QrCodeOutput(byte[] bytes) {
        this.bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes must not be null"), bytes.length);
    }
}
