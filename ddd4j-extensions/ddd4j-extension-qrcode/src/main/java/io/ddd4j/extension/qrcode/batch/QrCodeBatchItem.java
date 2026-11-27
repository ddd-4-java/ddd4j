package io.ddd4j.extension.qrcode.batch;

import io.ddd4j.extension.qrcode.command.GenerateQrCodeCommand;
import lombok.Builder;
import lombok.Getter;

/**
 * One correlated item in a non-atomic QR generation batch.
 */
@Getter
@Builder
public final class QrCodeBatchItem {

    /** \u5173\u8054\u6279\u91cf\u8bf7\u6c42\u9879\u548c\u5bf9\u5e94\u751f\u6210\u7ed3\u679c\u7684\u8c03\u7528\u65b9\u6807\u8bc6\u3002 */
    private final String itemId;
    /** \u5f53\u524d\u6279\u91cf\u9879\u76ee\u8981\u6267\u884c\u7684\u4e8c\u7ef4\u7801\u751f\u6210\u547d\u4ee4\u3002 */
    private final GenerateQrCodeCommand command;
}
