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

    /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u5bbd\u5ea6\u3002 */
    private final int width;
    /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u9ad8\u5ea6\u3002 */
    private final int height;
    /** \u586b\u5145\u6a21\u677f\u753b\u5e03\u65f6\u4f7f\u7528\u7684\u80cc\u666f\u8272\u3002 */
    private final String backgroundColor;
    /** \u5c42\u53e0\u7ed8\u5236\u5230\u4e8c\u7ef4\u7801\u6a21\u677f\u753b\u5e03\u4e2d\u7684\u5143\u7d20\u3002 */
    private final List<QrCodeFrameElement> elements;

    /**
     * \u901a\u8fc7\u6784\u5efa\u914d\u7f6e\u751f\u6210\u753b\u5e03\uff0c\u5e76\u590d\u5236\u5143\u7d20\u5217\u8868\u4ee5\u9694\u79bb\u540e\u7eed\u6784\u5efa\u64cd\u4f5c\u3002
     * @param builder \u63d0\u4f9b\u753b\u5e03\u5c3a\u5bf8\u3001\u80cc\u666f\u548c\u5143\u7d20\u7684\u6784\u5efa\u5668
     */
    private QrCodeFrame(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
        this.backgroundColor = builder.backgroundColor;
        this.elements = Collections.unmodifiableList(new ArrayList<>(builder.elements));
    }

    /**
     * \u521b\u5efa\u5916\u6846\u6a21\u677f\u6784\u5efa\u5668\u3002
     * @param width \u753b\u5e03\u5bbd\u5ea6\uff08\u50cf\u7d20\uff09
     * @param height \u753b\u5e03\u9ad8\u5ea6\uff08\u50cf\u7d20\uff09
     * @return \u65b0\u7684\u6784\u5efa\u5668\u5b9e\u4f8b
     */
    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    /**
     * {@link QrCodeFrame} \u6784\u5efa\u5668\u3002
     *
     *  <p>\u8bbe\u7f6e\u80cc\u666f\u8272\u5e76\u6dfb\u52a0\u5143\u7d20\u540e\u6784\u5efa\u4e0d\u53ef\u53d8\u5916\u6846\u6a21\u677f\u3002
     */
    public static final class Builder {
        /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u5bbd\u5ea6\u3002 */
        private final int width;
        /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u9ad8\u5ea6\u3002 */
        private final int height;
        /** \u5c42\u53e0\u7ed8\u5236\u5230\u4e8c\u7ef4\u7801\u6a21\u677f\u753b\u5e03\u4e2d\u7684\u5143\u7d20\u3002 */
        private final List<QrCodeFrameElement> elements = new ArrayList<>();
        /** \u586b\u5145\u6a21\u677f\u753b\u5e03\u65f6\u4f7f\u7528\u7684\u80cc\u666f\u8272\u3002 */
        private String backgroundColor = "#FFFFFF";

        /**
         * \u4f7f\u7528\u6307\u5b9a\u50cf\u7d20\u5c3a\u5bf8\u5f00\u59cb\u6784\u5efa\u6a21\u677f\u753b\u5e03\u3002
         * @param width \u753b\u5e03\u5bbd\u5ea6\uff0c\u5355\u4f4d\u4e3a\u50cf\u7d20
         * @param height \u753b\u5e03\u9ad8\u5ea6\uff0c\u5355\u4f4d\u4e3a\u50cf\u7d20
         */
        private Builder(int width, int height) {
            this.width = width;
            this.height = height;
        }

        /**
         * \u8bbe\u7f6e\u753b\u5e03\u80cc\u666f\u8272\uff08CSS \u989c\u8272\u503c\uff0c\u9ed8\u8ba4 {@code #FFFFFF}\uff09\u3002
         * @param backgroundColor \u80cc\u666f\u8272
         * @return \u5f53\u524d\u6784\u5efa\u5668
         */
        public Builder backgroundColor(String backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        /**
         * \u8ffd\u52a0\u4e00\u4e2a\u753b\u5e03\u5143\u7d20\u3002
         * @param element \u5f85\u6dfb\u52a0\u7684\u5143\u7d20
         * @return \u5f53\u524d\u6784\u5efa\u5668
         */
        public Builder addElement(QrCodeFrameElement element) {
            this.elements.add(element);
            return this;
        }

        /**
         * \u6784\u5efa\u4e0d\u53ef\u53d8\u7684\u5916\u6846\u6a21\u677f\u3002
         * @return \u6784\u5efa\u5b8c\u6210\u7684\u6a21\u677f\u5b9e\u4f8b
         */
        public QrCodeFrame build() {
            return new QrCodeFrame(this);
        }
    }
}
