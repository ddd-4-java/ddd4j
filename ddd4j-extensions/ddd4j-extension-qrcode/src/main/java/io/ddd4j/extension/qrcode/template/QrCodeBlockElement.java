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

/** 二维码区域占位元素。 */
public final class QrCodeBlockElement extends QrCodeFrameElement {

    private QrCodeBlockElement(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.zIndex);
    }

    /**
     * 创建区域占位元素构建器。
     *
     * @return 新的构建器实例
     */
    public static Builder builder() { return new Builder(); }

    /**
     * {@link QrCodeBlockElement} 构建器。
     *
     * <p>逐步设置区域坐标、尺寸与层级后构建不可变元素。
     */
    public static final class Builder {
        private int x;
        private int y;
        private int width;
        private int height;
        private int zIndex;

        /**
         * 设置区域左上角 X 坐标。
         *
         * @param x X 坐标
         * @return 当前构建器
         */
        public Builder x(int x) { this.x = x; return this; }

        /**
         * 设置区域左上角 Y 坐标。
         *
         * @param y Y 坐标
         * @return 当前构建器
         */
        public Builder y(int y) { this.y = y; return this; }

        /**
         * 设置区域宽度（像素）。
         *
         * @param width 宽度
         * @return 当前构建器
         */
        public Builder width(int width) { this.width = width; return this; }

        /**
         * 设置区域高度（像素）。
         *
         * @param height 高度
         * @return 当前构建器
         */
        public Builder height(int height) { this.height = height; return this; }

        /**
         * 设置绘制层级（值越大越靠上）。
         *
         * @param zIndex 层级值
         * @return 当前构建器
         */
        public Builder zIndex(int zIndex) { this.zIndex = zIndex; return this; }

        /**
         * 构建不可变的区域占位元素。
         *
         * @return 构建完成的元素实例
         */
        public QrCodeBlockElement build() { return new QrCodeBlockElement(this); }
    }
}
