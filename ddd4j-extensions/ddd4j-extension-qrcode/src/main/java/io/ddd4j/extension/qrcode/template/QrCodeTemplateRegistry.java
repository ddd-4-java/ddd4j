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

/** Registry for reusable QR outer-frame templates. */
public interface QrCodeTemplateRegistry {

    /**
     * 注册一个二维码外框模板定义。
     *
     * @param definition 待注册的模板定义
     */
    void register(QrCodeTemplateDefinition definition);

    /**
     * 根据模板编号查找已注册的模板定义。
     *
     * @param id 模板编号
     * @return 模板定义，不存在时为空
     */
    Optional<QrCodeTemplateDefinition> find(String id);

    /**
     * 根据模板编号移除已注册的模板定义。
     *
     * @param id 模板编号
     * @return 移除成功返回 {@code true}，模板不存在返回 {@code false}
     */
    boolean remove(String id);
}
