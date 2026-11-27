package io.ddd4j.extension.qrcode.command;

import io.ddd4j.extension.qrcode.model.QrCodeDecodeRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Application command for QR code decoding.
 */
@Getter
@Builder
public final class DecodeQrCodeCommand {

    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final String correlationId;
    /** \u5f53\u524d\u547d\u4ee4\u643a\u5e26\u7684\u4e8c\u7ef4\u7801\u8f93\u5165\u6570\u636e\u3002 */
    private final QrCodeDecodeRequest request;
}
