package io.ddd4j.extension.qrcode;

import java.util.List;

import io.ddd4j.extension.qrcode.batch.QrCodeBatchItem;
import io.ddd4j.extension.qrcode.batch.QrCodeBatchResult;
import io.ddd4j.extension.qrcode.command.DecodeQrCodeCommand;
import io.ddd4j.extension.qrcode.command.GenerateQrCodeCommand;
import io.ddd4j.extension.qrcode.result.QrCodeArtifact;
import io.ddd4j.extension.qrcode.result.QrCodeScanResult;

/**
 * Framework-neutral QR code application service.
 */
public interface QrCodeService {

    /**
     * \u751f\u6210\u4e8c\u7ef4\u7801\u3002
     * @param command \u751f\u6210\u547d\u4ee4
     * @return \u4e8c\u7ef4\u7801\u4ea7\u7269
     */
    QrCodeArtifact generate(GenerateQrCodeCommand command);

    /**
     * \u8bc6\u522b\u4e8c\u7ef4\u7801\u3002
     * @param command \u89e3\u7801\u547d\u4ee4
     * @return \u8bc6\u522b\u7ed3\u679c
     */
    QrCodeScanResult decode(DecodeQrCodeCommand command);

    /**
     * \u6309\u8f93\u5165\u987a\u5e8f\u6279\u91cf\u751f\u6210\u4e8c\u7ef4\u7801\u3002
     * @param items \u6279\u91cf\u9879\u76ee
     * @return \u6279\u91cf\u7ed3\u679c
     */
    QrCodeBatchResult generateBatch(List<QrCodeBatchItem> items);
}
