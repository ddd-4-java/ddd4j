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
package io.ddd4j.extension.qrcode.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 二维码解码请求。
 */
@Getter
public final class QrCodeDecodeRequest {

    /** \u5f85\u89e3\u6790\u7684\u4e8c\u7ef4\u7801\u56fe\u50cf\u6570\u636e\u3002 */
    private final byte[] bytes;

    /**
     * \u4ece\u56fe\u50cf\u5b57\u8282\u521b\u5efa\u89e3\u7801\u8bf7\u6c42\u3002
     * @param bytes \u56fe\u50cf\u5b57\u8282
     */
    @Builder
    public QrCodeDecodeRequest(byte[] bytes) {
        this.bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes must not be null"), bytes.length);
    }

    /**
     * \u4ece\u56fe\u50cf\u5b57\u8282\u521b\u5efa\u89e3\u7801\u8bf7\u6c42\u3002
     * @param bytes \u56fe\u50cf\u5b57\u8282
     * @return \u89e3\u7801\u8bf7\u6c42
     */
    public static QrCodeDecodeRequest from(byte[] bytes) {
        return new QrCodeDecodeRequest(bytes);
    }
}
