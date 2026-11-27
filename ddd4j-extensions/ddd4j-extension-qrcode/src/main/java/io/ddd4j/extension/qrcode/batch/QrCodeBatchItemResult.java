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

import io.ddd4j.extension.qrcode.result.QrCodeArtifact;
import lombok.Builder;
import lombok.Getter;

/**
 * Success or failure of one QR generation batch item.
 */
@Getter
@Builder
public final class QrCodeBatchItemResult {

    /** \u5173\u8054\u6279\u91cf\u8bf7\u6c42\u9879\u548c\u5bf9\u5e94\u751f\u6210\u7ed3\u679c\u7684\u8c03\u7528\u65b9\u6807\u8bc6\u3002 */
    private final String itemId;
    /** \u5f53\u524d\u6279\u91cf\u9879\u76ee\u662f\u5426\u6210\u529f\u751f\u6210\u4e8c\u7ef4\u7801\u3002 */
    private final boolean success;
    /** \u6210\u529f\u751f\u6210\u7684\u4e8c\u7ef4\u7801\u5236\u54c1\u53ca\u5176\u6a21\u677f\u4fe1\u606f\u3002 */
    private final QrCodeArtifact artifact;
    /** \u5f53\u524d\u9879\u76ee\u5931\u8d25\u65f6\u8fd4\u56de\u7684\u9519\u8bef\u7c7b\u522b\u3002 */
    private final String errorCode;
    /** \u5f53\u524d\u9879\u76ee\u5931\u8d25\u65f6\u8fd4\u56de\u7684\u9519\u8bef\u8bf4\u660e\u3002 */
    private final String errorMessage;
}
