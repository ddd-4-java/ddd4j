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

import java.awt.image.BufferedImage;

/** 图片外框元素。 */
public final class QrCodeImageElement extends QrCodeFrameElement {

    private final BufferedImage image;

    private QrCodeImageElement(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.zIndex);
        this.image = builder.image;
    }

    /**
     * 获取嵌入的图片。
     *
     * @return 图片实例
     */
    public BufferedImage getImage() { return image; }

    /**
     * 创建图片外框元素构建器。
     *
     * @param image 嵌入的图片
     * @return 新的构建器实例
     */
    public static Builder builder(BufferedImage image) { return new Builder(image); }

    /**
     * {@link QrCodeImageElement} 构建器。
     *
     * <p>设置图片边界与层级后构建不可变元素。
     */
    public static final class Builder {
        private final BufferedImage image;
        private int x;
        private int y;
        private int width;
        private int height;
        private int zIndex;
        private Builder(BufferedImage image) { this.image = image; }

        /**
         * 设置图片显示边界。
         *
         * @param x      左上角 X 坐标
         * @param y      左上角 Y 坐标
         * @param width  显示宽度（像素）
         * @param height 显示高度（像素）
         * @return 当前构建器
         */
        public Builder bounds(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; return this; }

        /**
         * 设置绘制层级（值越大越靠上）。
         *
         * @param zIndex 层级值
         * @return 当前构建器
         */
        public Builder zIndex(int zIndex) { this.zIndex = zIndex; return this; }

        /**
         * 构建不可变的图片外框元素。
         *
         * @return 构建完成的元素实例
         */
        public QrCodeImageElement build() { return new QrCodeImageElement(this); }
    }
}
