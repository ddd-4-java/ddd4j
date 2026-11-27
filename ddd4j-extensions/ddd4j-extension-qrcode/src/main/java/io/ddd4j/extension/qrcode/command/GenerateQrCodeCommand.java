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
package io.ddd4j.extension.qrcode.command;

import io.ddd4j.extension.qrcode.model.QrCodeRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Application command for QR code generation.
 */
@Getter
@Builder
public final class GenerateQrCodeCommand {

    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final String correlationId;
    /** \u9009\u62e9\u4e8c\u7ef4\u7801\u751f\u6210\u6a21\u677f\u4f7f\u7528\u7684\u6807\u8bc6\u3002 */
    private final String templateId;
    /** \u5f53\u524d\u547d\u4ee4\u643a\u5e26\u7684\u4e8c\u7ef4\u7801\u8f93\u5165\u6570\u636e\u3002 */
    private final QrCodeRequest request;
}
