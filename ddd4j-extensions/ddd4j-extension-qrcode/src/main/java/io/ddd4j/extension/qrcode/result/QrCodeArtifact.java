package io.ddd4j.extension.qrcode.result;

import io.ddd4j.extension.qrcode.model.QrCodeOutput;
import lombok.Builder;
import lombok.Getter;

/**
 * Generated QR code together with application correlation metadata.
 */
@Getter
@Builder
public final class QrCodeArtifact {

    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final String correlationId;
    /** \u5f53\u524d\u5236\u54c1\u751f\u6210\u65f6\u4f7f\u7528\u7684\u6a21\u677f\u6807\u8bc6\u3002 */
    private final String templateId;
    /** \u5f53\u524d\u5236\u54c1\u751f\u6210\u7684\u4e8c\u7ef4\u7801\u56fe\u50cf\u3002 */
    private final QrCodeOutput output;
}
