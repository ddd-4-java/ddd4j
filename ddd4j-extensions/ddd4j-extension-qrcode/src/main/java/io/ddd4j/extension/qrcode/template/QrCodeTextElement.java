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
 * 文本外框元素。
 */
@Getter
public final class QrCodeTextElement extends QrCodeFrameElement {

    /** \u6a21\u677f\u753b\u5e03\u4e0a\u663e\u793a\u7684\u6587\u672c\uff0c\u53ef\u5728\u6e32\u67d3\u524d\u7ed1\u5b9a\u53d8\u91cf\u3002 */
    private final String text;
    /** \u7528\u4e8e\u7ed8\u5236\u6587\u672c\u5143\u7d20\u7684\u5b57\u4f53\u540d\u79f0\u3002 */
    private final String fontName;
    /** \u7ed8\u5236\u6587\u672c\u5143\u7d20\u65f6\u4f7f\u7528\u7684\u5b57\u53f7\u3002 */
    private final int fontSize;
    /** \u662f\u5426\u4ee5\u7c97\u4f53\u7ed8\u5236\u6587\u672c\u5143\u7d20\u3002 */
    private final boolean bold;
    /** \u7ed8\u5236\u6587\u672c\u5143\u7d20\u6240\u7528\u7684\u989c\u8272\u3002 */
    private final String color;

    /**
     * \u4fdd\u5b58\u6a21\u677f\u6587\u672c\u5143\u7d20\u7684\u8fb9\u754c\u3001\u5c42\u7ea7\u3001\u6587\u672c\u4e0e\u5b57\u4f53\u6837\u5f0f\u3002
     * @param builder \u63d0\u4f9b\u6587\u672c\u548c\u7ed8\u5236\u53c2\u6570\u7684\u6784\u5efa\u5668
     */
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
        /** \u8981\u89e3\u7801\u6216\u7ed8\u5236\u7684\u4e8c\u7ef4\u7801\u6a21\u677f\u6587\u672c\u5185\u5bb9\u3002 */
        private final String text;
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
        /** \u7528\u4e8e\u7ed8\u5236\u6587\u672c\u5143\u7d20\u7684\u5b57\u4f53\u540d\u79f0\u3002 */
        private String fontName = "SansSerif";
        /** \u7ed8\u5236\u6587\u672c\u5143\u7d20\u65f6\u4f7f\u7528\u7684\u5b57\u53f7\u3002 */
        private int fontSize = 16;
        /** \u662f\u5426\u4ee5\u7c97\u4f53\u7ed8\u5236\u6587\u672c\u5143\u7d20\u3002 */
        private boolean bold;
        /** \u7ed8\u5236\u6587\u672c\u5143\u7d20\u6240\u7528\u7684\u989c\u8272\u3002 */
        private String color = "#000000";

        /**
         * \u7528\u6307\u5b9a\u6587\u672c\u521d\u59cb\u5316\u53ef\u8fdb\u4e00\u6b65\u914d\u7f6e\u4f4d\u7f6e\u548c\u5b57\u4f53\u7684\u6784\u5efa\u5668\u3002
         * @param text \u8981\u7ed8\u5236\u7684\u6587\u672c
         */
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
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * 设置绘制层级（值越大越靠上）。
         *
         * @param zIndex 层级值
         * @return 当前构建器
         */
        public Builder zIndex(int zIndex) {
            this.zIndex = zIndex;
            return this;
        }

        /**
         * 设置字体名称、字号与是否加粗。
         *
         * @param fontName 字体名称（如 {@code SansSerif}）
         * @param fontSize 字号
         * @param bold     是否加粗
         * @return 当前构建器
         */
        public Builder font(String fontName, int fontSize, boolean bold) {
            this.fontName = fontName;
            this.fontSize = fontSize;
            this.bold = bold;
            return this;
        }

        /**
         * 设置文本颜色（CSS 颜色值，默认 {@code #000000}）。
         *
         * @param color 文本颜色
         * @return 当前构建器
         */
        public Builder color(String color) {
            this.color = color;
            return this;
        }

        /**
         * 构建不可变的文本外框元素。
         *
         * @return 构建完成的元素实例
         */
        public QrCodeTextElement build() {
            return new QrCodeTextElement(this);
        }
    }
}
