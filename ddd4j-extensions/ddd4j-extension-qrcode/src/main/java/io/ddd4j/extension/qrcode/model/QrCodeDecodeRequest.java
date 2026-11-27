package io.ddd4j.extension.qrcode.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 二维码解码请求。
 */
@Getter
public final class QrCodeDecodeRequest {

    /** \u5f85\u89e3\u6790\u7684\u4e8c\u7ef4\u7801\u56fe\u50cf\u6570\u636e\u3002 */
    private final byte[] bytes;

    /**
     * \u4ece\u56fe\u50cf\u5b57\u8282\u521b\u5efa\u89e3\u7801\u8bf7\u6c42\u3002
     * @param bytes \u56fe\u50cf\u5b57\u8282
     */
    @Builder
    public QrCodeDecodeRequest(byte[] bytes) {
        this.bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes must not be null"), bytes.length);
    }

    /**
     * \u4ece\u56fe\u50cf\u5b57\u8282\u521b\u5efa\u89e3\u7801\u8bf7\u6c42\u3002
     * @param bytes \u56fe\u50cf\u5b57\u8282
     * @return \u89e3\u7801\u8bf7\u6c42
     */
    public static QrCodeDecodeRequest from(byte[] bytes) {
        return new QrCodeDecodeRequest(bytes);
    }
}
