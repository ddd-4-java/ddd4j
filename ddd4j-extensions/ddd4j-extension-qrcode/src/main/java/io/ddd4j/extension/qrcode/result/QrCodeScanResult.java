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
