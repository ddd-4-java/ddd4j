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
import org.apache.commons.lang3.StringUtils;

/**
 * 二维码生成请求。
 */
@Getter
@Builder
public final class QrCodeRequest {

    /** \u7f16\u7801\u5230\u4e8c\u7ef4\u7801\u4e2d\u7684\u6587\u672c\u5185\u5bb9\u3002 */
    private final String content;
    /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u5bbd\u5ea6\u3002 */
    @Builder.Default
    private final int width = 300;
    /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u9ad8\u5ea6\u3002 */
    @Builder.Default
    private final int height = 300;

    /**
     * \u521b\u5efa\u6307\u5b9a\u5185\u5bb9\u4e0e\u5c3a\u5bf8\u7684\u4e8c\u7ef4\u7801\u8bf7\u6c42\u3002
     * @param content \u4e8c\u7ef4\u7801\u5185\u5bb9
     * @param width \u56fe\u50cf\u5bbd\u5ea6
     * @param height \u56fe\u50cf\u9ad8\u5ea6
     */
    public QrCodeRequest(String content, int width, int height) {
        if (StringUtils.isBlank(content)) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        this.content = content;
        this.width = width;
        this.height = height;
    }
}
