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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.ddd4j.extension.qrcode.model.QrCodeDecodeResult;
import lombok.Getter;

/** Decoded QR code results with application correlation metadata. */
@Getter
public final class QrCodeScanResult {

    private final String correlationId;
    private final List<QrCodeDecodeResult> results;

    public QrCodeScanResult(String correlationId, List<QrCodeDecodeResult> results) {
        this.correlationId = correlationId;
        this.results = Collections.unmodifiableList(new ArrayList<QrCodeDecodeResult>(results));
    }
}
