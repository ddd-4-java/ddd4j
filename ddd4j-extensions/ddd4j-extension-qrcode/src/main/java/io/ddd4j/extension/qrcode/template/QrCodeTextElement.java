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
     * 创建构建器。
     * @param text 文本内容。
     * @return 方法执行结果。
     */
    public static Builder builder(String text) {
        return new Builder(text);
    }

    /**
     * 文本外框元素的构建器，用于链式设置文本位置、字体、颜色与层级并构建元素。
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
         * font() 方法。
         * @param fontName 字符串参数。
         * @param fontSize fontSize 参数。
         * @param bold bold 参数。
         * @return 方法执行结果。
         */
        public Builder font(String fontName, int fontSize, boolean bold) { this.fontName = fontName; this.fontSize = fontSize; this.bold = bold; return this; }
        /**
         * color() 方法。
         * @param color 字符串参数。
         * @return 方法执行结果。
         */
        public Builder color(String color) { this.color = color; return this; }
        /**
         * 构建对象。
         * @return 当前对象自身，支持链式调用。
         */
        public QrCodeTextElement build() { return new QrCodeTextElement(this); }
    }
}
