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
package io.ddd4j.extension.qrcode.result;

import io.ddd4j.extension.qrcode.model.QrCodeDecodeResult;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decoded QR code results with application correlation metadata.
 */
@Getter
public final class QrCodeScanResult {

    /**
     * 用于关联同一次操作产生的事件或消息的标识。
     */
    private final String correlationId;
    /**
     * 从输入图像识别出的二维码内容。
     */
    private final List<QrCodeDecodeResult> results;

    /**
     * 创建带关联标识的二维码识别结果。
     *
     * @param correlationId 关联标识
     * @param results       解码结果
     */
    public QrCodeScanResult(String correlationId, List<QrCodeDecodeResult> results) {
        this.correlationId = correlationId;
        this.results = Collections.unmodifiableList(new ArrayList<QrCodeDecodeResult>(results));
    }
}
