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
     * 生成二维码。
     *
     * @param command 生成命令
     * @return 二维码产物
     */
    QrCodeArtifact generate(GenerateQrCodeCommand command);

    /**
     * 识别二维码。
     *
     * @param command 解码命令
     * @return 识别结果
     */
    QrCodeScanResult decode(DecodeQrCodeCommand command);

    /**
     * 按输入顺序批量生成二维码。
     *
     * @param items 批量项目
     * @return 批量结果
     */
    QrCodeBatchResult generateBatch(List<QrCodeBatchItem> items);
}
