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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;

/** Thread-safe in-memory QR template registry. */
public class InMemoryQrCodeTemplateRegistry implements QrCodeTemplateRegistry {

    private final ConcurrentMap<String, QrCodeTemplateDefinition> templates =
            new ConcurrentHashMap<String, QrCodeTemplateDefinition>();

    @Override
    public void register(QrCodeTemplateDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        templates.put(definition.getId(), definition);
    }

    @Override
    public Optional<QrCodeTemplateDefinition> find(String id) {
        if (StringUtils.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(templates.get(id));
    }

    @Override
    public boolean remove(String id) {
        return StringUtils.isNotBlank(id) && Objects.nonNull(templates.remove(id));
    }
}
