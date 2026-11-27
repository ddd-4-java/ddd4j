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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory QR template registry.
 */
public class InMemoryQrCodeTemplateRegistry implements QrCodeTemplateRegistry {

    /**
     * 按模板标识索引已注册的二维码模板。
     */
    private final ConcurrentMap<String, QrCodeTemplateDefinition> templates =
            new ConcurrentHashMap<String, QrCodeTemplateDefinition>();

    /**
     * 注册二维码外框模板。
     *
     * <p>此实现调用 <code>templates.put(definition.getId(), definition)</code>。</p>
     *
     * @param definition 模板定义
     */
    @Override
    public void register(QrCodeTemplateDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        templates.put(definition.getId(), definition);
    }

    /**
     * 查找与给定条件对应的条目。
     *
     * <p>此实现调用 <code>StringUtils.isBlank(id)</code>。</p>
     *
     * @param id 模板 ID
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.ofNullable(templates.get(id))</code>
     */
    @Override
    public Optional<QrCodeTemplateDefinition> find(String id) {
        if (StringUtils.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(templates.get(id));
    }

    /**
     * 移除外框模板。
     *
     * <p>此实现调用 <code>StringUtils.isNotBlank(id)</code>。</p>
     *
     * @param id 模板 ID
     * @return 实际返回<code>StringUtils.isNotBlank(id) &amp;&amp; Objects.nonNull(templates.remove(id))</code>
     */
    @Override
    public boolean remove(String id) {
        return StringUtils.isNotBlank(id) && Objects.nonNull(templates.remove(id));
    }
}
