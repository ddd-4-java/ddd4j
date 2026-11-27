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

    /**
     * 编码到二维码中的文本内容。
     */
    private final String content;
    /**
     * 在编码或绘制时使用的像素宽度。
     */
    @Builder.Default
    private final int width = 300;
    /**
     * 在编码或绘制时使用的像素高度。
     */
    @Builder.Default
    private final int height = 300;

    /**
     * 创建指定内容与尺寸的二维码请求。
     *
     * @param content 二维码内容
     * @param width   图像宽度
     * @param height  图像高度
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
