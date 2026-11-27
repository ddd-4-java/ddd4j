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
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * Named reusable outer-frame template.
 */
@Getter
public final class QrCodeTemplateDefinition {

    /** \u7528\u4e8e\u767b\u8bb0\u548c\u68c0\u7d22\u6a21\u677f\u5b9a\u4e49\u7684\u6807\u8bc6\u3002 */
    private final String id;
    /** \u63cf\u8ff0\u6a21\u677f\u753b\u5e03\u548c\u53e0\u52a0\u5143\u7d20\u7684\u914d\u7f6e\u3002 */
    private final QrCodeFrame frame;

    /**
     * \u521b\u5efa\u547d\u540d\u7684\u5916\u6846\u6a21\u677f\u3002
     * @param id \u6a21\u677f ID
     * @param frame \u5916\u6846\u5b9a\u4e49
     */
    public QrCodeTemplateDefinition(String id, QrCodeFrame frame) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("template id must not be blank");
        }
        this.id = id;
        this.frame = Objects.requireNonNull(frame, "frame must not be null");
    }
}
