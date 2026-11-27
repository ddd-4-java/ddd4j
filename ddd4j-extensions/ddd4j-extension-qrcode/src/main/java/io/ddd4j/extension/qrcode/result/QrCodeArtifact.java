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

import io.ddd4j.extension.qrcode.model.QrCodeOutput;
import lombok.Builder;
import lombok.Getter;

/**
 * Generated QR code together with application correlation metadata.
 */
@Getter
@Builder
public final class QrCodeArtifact {

    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final String correlationId;
    /** \u5f53\u524d\u5236\u54c1\u751f\u6210\u65f6\u4f7f\u7528\u7684\u6a21\u677f\u6807\u8bc6\u3002 */
    private final String templateId;
    /** \u5f53\u524d\u5236\u54c1\u751f\u6210\u7684\u4e8c\u7ef4\u7801\u56fe\u50cf\u3002 */
    private final QrCodeOutput output;
}
