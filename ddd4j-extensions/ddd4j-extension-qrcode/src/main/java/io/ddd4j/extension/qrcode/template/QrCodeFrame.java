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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可复用的二维码外框模板。
 */
@Getter
public final class QrCodeFrame {

    /**
     * 在编码或绘制时使用的像素宽度。
     */
    private final int width;
    /**
     * 在编码或绘制时使用的像素高度。
     */
    private final int height;
    /**
     * 填充模板画布时使用的背景色。
     */
    private final String backgroundColor;
    /**
     * 层叠绘制到二维码模板画布中的元素。
     */
    private final List<QrCodeFrameElement> elements;

    /**
     * 通过构建配置生成画布，并复制元素列表以隔离后续构建操作。
     *
     * @param builder 提供画布尺寸、背景和元素的构建器
     */
    private QrCodeFrame(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
        this.backgroundColor = builder.backgroundColor;
        this.elements = Collections.unmodifiableList(new ArrayList<>(builder.elements));
    }

    /**
     * 创建外框模板构建器。
     *
     * @param width  画布宽度（像素）
     * @param height 画布高度（像素）
     * @return 新的构建器实例
     */
    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    /**
     * {@link QrCodeFrame} 构建器。
     *
     * <p>设置背景色并添加元素后构建不可变外框模板。
     */
    public static final class Builder {
        /**
         * 在编码或绘制时使用的像素宽度。
         */
        private final int width;
        /**
         * 在编码或绘制时使用的像素高度。
         */
        private final int height;
        /**
         * 层叠绘制到二维码模板画布中的元素。
         */
        private final List<QrCodeFrameElement> elements = new ArrayList<>();
        /**
         * 填充模板画布时使用的背景色。
         */
        private String backgroundColor = "#FFFFFF";

        /**
         * 使用指定像素尺寸开始构建模板画布。
         *
         * @param width  画布宽度，单位为像素
         * @param height 画布高度，单位为像素
         */
        private Builder(int width, int height) {
            this.width = width;
            this.height = height;
        }

        /**
         * 设置画布背景色（CSS 颜色值，默认 {@code #FFFFFF}）。
         *
         * @param backgroundColor 背景色
         * @return 当前构建器
         */
        public Builder backgroundColor(String backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        /**
         * 追加一个画布元素。
         *
         * @param element 待添加的元素
         * @return 当前构建器
         */
        public Builder addElement(QrCodeFrameElement element) {
            this.elements.add(element);
            return this;
        }

        /**
         * 构建不可变的外框模板。
         *
         * @return 构建完成的模板实例
         */
        public QrCodeFrame build() {
            return new QrCodeFrame(this);
        }
    }
}
