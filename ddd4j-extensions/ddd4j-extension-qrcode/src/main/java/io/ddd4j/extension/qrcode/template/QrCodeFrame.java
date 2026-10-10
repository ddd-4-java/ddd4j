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

/** 可复用的二维码外框模板。 */
@Getter
public final class QrCodeFrame {

    private final int width;
    private final int height;
    private final String backgroundColor;
    private final List<QrCodeFrameElement> elements;

    private QrCodeFrame(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
        this.backgroundColor = builder.backgroundColor;
        this.elements = Collections.unmodifiableList(new ArrayList<>(builder.elements));
    }

    /**
     * 创建构建器。
     * @param width width 参数。
     * @param height height 参数。
     * @return 方法执行结果。
     */
    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    /**
     * {@link QrCodeFrame} 的构建器，用于设置外框尺寸、背景色并追加区域元素。
     */
    public static final class Builder {
        private final int width;
        private final int height;
        private String backgroundColor = "#FFFFFF";
        private final List<QrCodeFrameElement> elements = new ArrayList<>();

        private Builder(int width, int height) {
            this.width = width;
            this.height = height;
        }

        /**
         * backgroundColor() 方法。
         * @param backgroundColor 字符串参数。
         * @return 方法执行结果。
         */
        public Builder backgroundColor(String backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        /**
         * 追加元素。
         * @param element element 参数。
         * @return 方法执行结果。
         */
        public Builder addElement(QrCodeFrameElement element) {
            this.elements.add(element);
            return this;
        }

        /**
         * 构建对象。
         * @return 当前对象自身，支持链式调用。
         */
        public QrCodeFrame build() {
            return new QrCodeFrame(this);
        }
    }
}
