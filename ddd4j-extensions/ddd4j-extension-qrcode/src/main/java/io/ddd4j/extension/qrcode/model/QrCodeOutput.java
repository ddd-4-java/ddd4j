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

import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 二维码 PNG 输出。
 */
@Getter
public final class QrCodeOutput {

    /** \u751f\u6210\u7684\u4e8c\u7ef4\u7801\u56fe\u50cf\u5b57\u8282\u3002 */
    private final byte[] bytes;

    /**
     * \u4fdd\u5b58 PNG \u8f93\u51fa\u5b57\u8282\u7684\u526f\u672c\u3002
     * @param bytes PNG \u5b57\u8282
     */
    public QrCodeOutput(byte[] bytes) {
        this.bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes must not be null"), bytes.length);
    }
}
