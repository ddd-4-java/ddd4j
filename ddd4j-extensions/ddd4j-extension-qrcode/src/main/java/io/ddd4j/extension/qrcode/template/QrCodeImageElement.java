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
     * 获取值。
     * @return 方法执行结果。
     */
    public BufferedImage getImage() { return image; }
    /**
     * 创建构建器。
     * @param image image 参数。
     * @return 方法执行结果。
     */
    public static Builder builder(BufferedImage image) { return new Builder(image); }

    /**
     * 类型说明。
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     * @since 3.0.x
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
         * bounds() 方法。
         * @param x x 参数。
         * @param y y 参数。
         * @param width width 参数。
         * @param height height 参数。
         * @return 方法执行结果。
         */
        public Builder bounds(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; return this; }
        /**
         * zIndex() 方法。
         * @param zIndex zIndex 参数。
         * @return 方法执行结果。
         */
        public Builder zIndex(int zIndex) { this.zIndex = zIndex; return this; }
        /**
         * 构建对象。
         * @return 当前对象自身，支持链式调用。
         */
        public QrCodeImageElement build() { return new QrCodeImageElement(this); }
    }
}
