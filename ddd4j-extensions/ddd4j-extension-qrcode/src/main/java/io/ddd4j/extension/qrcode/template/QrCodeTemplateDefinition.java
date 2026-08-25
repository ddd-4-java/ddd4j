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

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

import java.util.Objects;

/** Named reusable outer-frame template. */
@Getter
public final class QrCodeTemplateDefinition {

    private final String id;
    private final QrCodeFrame frame;

    public QrCodeTemplateDefinition(String id, QrCodeFrame frame) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("template id must not be blank");
        }
        this.id = id;
        this.frame = Objects.requireNonNull(frame, "frame must not be null");
    }
}
