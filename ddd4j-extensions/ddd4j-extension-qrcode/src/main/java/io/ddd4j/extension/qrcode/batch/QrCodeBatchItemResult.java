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

    /**
     * 关联批量请求项和对应生成结果的调用方标识。
     */
    private final String itemId;
    /**
     * 当前批量项目是否成功生成二维码。
     */
    private final boolean success;
    /**
     * 成功生成的二维码制品及其模板信息。
     */
    private final QrCodeArtifact artifact;
    /**
     * 当前项目失败时返回的错误类别。
     */
    private final String errorCode;
    /**
     * 当前项目失败时返回的错误说明。
     */
    private final String errorMessage;
}
