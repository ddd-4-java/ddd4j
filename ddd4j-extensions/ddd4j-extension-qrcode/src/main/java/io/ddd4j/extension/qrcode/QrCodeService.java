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
package io.ddd4j.extension.qrcode;

import io.ddd4j.extension.qrcode.batch.QrCodeBatchItem;
import io.ddd4j.extension.qrcode.batch.QrCodeBatchResult;
import io.ddd4j.extension.qrcode.command.DecodeQrCodeCommand;
import io.ddd4j.extension.qrcode.command.GenerateQrCodeCommand;
import io.ddd4j.extension.qrcode.result.QrCodeArtifact;
import io.ddd4j.extension.qrcode.result.QrCodeScanResult;

import java.util.List;

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
