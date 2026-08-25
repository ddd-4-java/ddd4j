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

    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    public static final class Builder {
        private final int width;
        private final int height;
        private String backgroundColor = "#FFFFFF";
        private final List<QrCodeFrameElement> elements = new ArrayList<>();

        private Builder(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public Builder backgroundColor(String backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public Builder addElement(QrCodeFrameElement element) {
            this.elements.add(element);
            return this;
        }

        public QrCodeFrame build() {
            return new QrCodeFrame(this);
        }
    }
}
