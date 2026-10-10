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

/** 文本外框元素。 */
@Getter
public final class QrCodeTextElement extends QrCodeFrameElement {

    private final String text;
    private final String fontName;
    private final int fontSize;
    private final boolean bold;
    private final String color;

    private QrCodeTextElement(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.zIndex);
        this.text = builder.text;
        this.fontName = builder.fontName;
        this.fontSize = builder.fontSize;
        this.bold = builder.bold;
        this.color = builder.color;
    }

    /**
     * 创建文本外框元素构建器。
     *
     * @param text 待绘制的文本
     * @return 新的构建器实例
     */
    public static Builder builder(String text) {
        return new Builder(text);
    }

    /**
     * {@link QrCodeTextElement} 构建器。
     *
     * <p>设置文本边界、字体与颜色后构建不可变元素。
     */
    public static final class Builder {
        private final String text;
        private int x;
        private int y;
        private int width;
        private int height;
        private int zIndex;
        private String fontName = "SansSerif";
        private int fontSize = 16;
        private boolean bold;
        private String color = "#000000";

        private Builder(String text) {
            this.text = text;
        }

        /**
         * 设置文本显示边界。
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
         * 设置字体名称、字号与是否加粗。
         *
         * @param fontName 字体名称（如 {@code SansSerif}）
         * @param fontSize 字号
         * @param bold     是否加粗
         * @return 当前构建器
         */
        public Builder font(String fontName, int fontSize, boolean bold) { this.fontName = fontName; this.fontSize = fontSize; this.bold = bold; return this; }

        /**
         * 设置文本颜色（CSS 颜色值，默认 {@code #000000}）。
         *
         * @param color 文本颜色
         * @return 当前构建器
         */
        public Builder color(String color) { this.color = color; return this; }

        /**
         * 构建不可变的文本外框元素。
         *
         * @return 构建完成的元素实例
         */
        public QrCodeTextElement build() { return new QrCodeTextElement(this); }
    }
}
