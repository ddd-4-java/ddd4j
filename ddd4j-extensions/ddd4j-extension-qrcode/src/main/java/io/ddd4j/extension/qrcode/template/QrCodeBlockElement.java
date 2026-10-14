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

/**
 * 二维码区域占位元素。
 */
public final class QrCodeBlockElement extends QrCodeFrameElement {

    private QrCodeBlockElement(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.zIndex);
    }

    /**
     * 创建构建器。
     * @return 方法执行结果。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link QrCodeBlockElement} 的构建器，用于设置占位区域的位置、尺寸与层级。
     */
    public static final class Builder {
        private int x;
        private int y;
        private int width;
        private int height;
        private int zIndex;

        /**
         * x() 方法。
         * @param x x 参数。
         * @return 方法执行结果。
         */
        public Builder x(int x) {
            this.x = x;
            return this;
        }

        /**
         * y() 方法。
         * @param y y 参数。
         * @return 方法执行结果。
         */
        public Builder y(int y) {
            this.y = y;
            return this;
        }

        /**
         * width() 方法。
         * @param width width 参数。
         * @return 方法执行结果。
         */
        public Builder width(int width) {
            this.width = width;
            return this;
        }

        /**
         * height() 方法。
         * @param height height 参数。
         * @return 方法执行结果。
         */
        public Builder height(int height) {
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
        public QrCodeBlockElement build() {
            return new QrCodeBlockElement(this);
        }
    }
}
