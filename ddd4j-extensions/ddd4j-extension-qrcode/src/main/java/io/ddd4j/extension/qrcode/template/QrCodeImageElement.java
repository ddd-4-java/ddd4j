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

/**
 * 图片外框元素。
 */
public final class QrCodeImageElement extends QrCodeFrameElement {

    /** \u7ed8\u5236\u5230\u6a21\u677f\u753b\u5e03\u4e0a\u7684\u6e90\u56fe\u50cf\u3002 */
    private final BufferedImage image;

    /**
     * \u6309\u6784\u5efa\u914d\u7f6e\u4fdd\u5b58\u56fe\u50cf\u53ca\u5176\u5728\u753b\u5e03\u4e2d\u7684\u4f4d\u7f6e\u3001\u5c3a\u5bf8\u4e0e\u5c42\u7ea7\u3002
     * @param builder \u63d0\u4f9b\u56fe\u50cf\u53ca\u7ed8\u5236\u8fb9\u754c\u7684\u6784\u5efa\u5668
     */
    private QrCodeImageElement(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.zIndex);
        this.image = builder.image;
    }

    /**
     * 创建构建器。
     * @param image image 参数。
     * @return 方法执行结果。
     */
    public static Builder builder(BufferedImage image) {
        return new Builder(image);
    }

    /**
     * 获取值。
     * @return 方法执行结果。
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * 类型说明。
     * @author ddd4j
     * @since 3.0.x
     */
    public static final class Builder {
        /** \u7ed8\u5236\u5230\u6a21\u677f\u753b\u5e03\u4e0a\u7684\u6e90\u56fe\u50cf\u3002 */
        private final BufferedImage image;
        /** \u5143\u7d20\u5728\u6a21\u677f\u753b\u5e03\u4e2d\u7684\u6a2a\u5411\u50cf\u7d20\u5750\u6807\u3002 */
        private int x;
        /** \u5143\u7d20\u5728\u6a21\u677f\u753b\u5e03\u4e2d\u7684\u7eb5\u5411\u50cf\u7d20\u5750\u6807\u3002 */
        private int y;
        /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u5bbd\u5ea6\u3002 */
        private int width;
        /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u9ad8\u5ea6\u3002 */
        private int height;
        /** \u91cd\u53e0\u5143\u7d20\u5728\u753b\u5e03\u4e2d\u7684\u53e0\u653e\u987a\u5e8f\u3002 */
        private int zIndex;

        /**
         * \u4ece\u6e90\u56fe\u50cf\u5f00\u59cb\u6784\u9020\u6a21\u677f\u56fe\u50cf\u5143\u7d20\u3002
         * @param image \u9700\u8981\u7ed8\u5236\u5230\u6a21\u677f\u753b\u5e03\u4e0a\u7684\u56fe\u50cf
         */
        private Builder(BufferedImage image) {
            this.image = image;
        }

        /**
         * bounds() 方法。
         * @param x x 参数。
         * @param y y 参数。
         * @param width width 参数。
         * @param height height 参数。
         * @return 方法执行结果。
         */
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * zIndex() 方法。
         * @param zIndex zIndex 参数。
         * @return 方法执行结果。
         */
        public Builder zIndex(int zIndex) {
            this.zIndex = zIndex;
            return this;
        }

        /**
         * 构建对象。
         * @return 当前对象自身，支持链式调用。
         */
        public QrCodeImageElement build() {
            return new QrCodeImageElement(this);
        }
    }
}
