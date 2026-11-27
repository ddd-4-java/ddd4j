package io.ddd4j.extension.qrcode.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

/**
 * Ordered, non-atomic batch result.
 */
@Getter
public final class QrCodeBatchResult {

    /** \u6309\u6279\u91cf\u6267\u884c\u7ed3\u679c\u4fdd\u7559\u7684\u9010\u9879\u751f\u6210\u7ed3\u679c\u3002 */
    private final List<QrCodeBatchItemResult> items;

    /**
     * \u6309\u8f93\u5165\u987a\u5e8f\u4fdd\u5b58\u6279\u91cf\u9879\u76ee\u7ed3\u679c\u3002
     * @param items \u6279\u91cf\u9879\u76ee\u7ed3\u679c
     */
    public QrCodeBatchResult(List<QrCodeBatchItemResult> items) {
        this.items = Collections.unmodifiableList(new ArrayList<QrCodeBatchItemResult>(items));
    }

    /**
     * \u7edf\u8ba1\u6210\u529f\u9879\u76ee\u3002
     * @return \u6210\u529f\u6570\u91cf
     */
    public long successCount() {
        return items.stream().filter(QrCodeBatchItemResult::isSuccess).count();
    }

    /**
     * \u7edf\u8ba1\u5931\u8d25\u9879\u76ee\u3002
     * @return \u5931\u8d25\u6570\u91cf
     */
    public long failureCount() {
        return items.size() - successCount();
    }
}
