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

import java.util.Optional;

/**
 * Registry for reusable QR outer-frame templates.
 */
public interface QrCodeTemplateRegistry {

    /**
     * 注册二维码外框模板。
     *
     * @param definition 模板定义
     */
    void register(QrCodeTemplateDefinition definition);

    /**
     * 按 ID 查找外框模板。
     *
     * @param id 模板 ID
     * @return 可选模板定义
     */
    Optional<QrCodeTemplateDefinition> find(String id);

    /**
     * 移除外框模板。
     *
     * @param id 模板 ID
     * @return 移除成功时为 true
     */
    boolean remove(String id);
}
