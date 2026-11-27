package io.ddd4j.extension.qrcode.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.ddd4j.extension.qrcode.model.QrCodeDecodeResult;
import lombok.Getter;

/**
 * Decoded QR code results with application correlation metadata.
 */
@Getter
public final class QrCodeScanResult {

    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final String correlationId;
    /** \u4ece\u8f93\u5165\u56fe\u50cf\u8bc6\u522b\u51fa\u7684\u4e8c\u7ef4\u7801\u5185\u5bb9\u3002 */
    private final List<QrCodeDecodeResult> results;

    /**
     * \u521b\u5efa\u5e26\u5173\u8054\u6807\u8bc6\u7684\u4e8c\u7ef4\u7801\u8bc6\u522b\u7ed3\u679c\u3002
     * @param correlationId \u5173\u8054\u6807\u8bc6
     * @param results \u89e3\u7801\u7ed3\u679c
     */
    public QrCodeScanResult(String correlationId, List<QrCodeDecodeResult> results) {
        this.correlationId = correlationId;
        this.results = Collections.unmodifiableList(new ArrayList<QrCodeDecodeResult>(results));
    }
}
