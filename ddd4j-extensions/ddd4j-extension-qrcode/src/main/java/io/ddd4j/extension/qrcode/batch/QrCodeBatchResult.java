/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.extension.qrcode.batch;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
