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
package io.ddd4j.extension.qrcode.template;

import lombok.Getter;

/**
 * 二维码外框元素的公共坐标属性。
 */
@Getter
public abstract class QrCodeFrameElement {

    /**
     * 元素在模板画布中的横向像素坐标。
     */
    private final int x;
    /**
     * 元素在模板画布中的纵向像素坐标。
     */
    private final int y;
    /**
     * 在编码或绘制时使用的像素宽度。
     */
    private final int width;
    /**
     * 在编码或绘制时使用的像素高度。
     */
    private final int height;
    /**
     * 重叠元素在画布中的叠放顺序。
     */
    private final int zIndex;

    /**
     * 创建外框元素的坐标及层级属性。
     *
     * @param x      水平坐标
     * @param y      垂直坐标
     * @param width  宽度
     * @param height 高度
     * @param zIndex 层级
     */
    protected QrCodeFrameElement(int x, int y, int width, int height, int zIndex) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.zIndex = zIndex;
    }
}
