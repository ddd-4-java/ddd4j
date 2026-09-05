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
package io.ddd4j.web.webmvc.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地资源配置属性。
 * <p>用于配置本地静态资源的存储路径和映射关系。</p>
 */
@Data
public class LocalResourceProperteis {

    /**
     * 本地存储路径
     */
    private String localStorage;

    /**
     * 本地静态资源映射是否是相对于 localStorage 的地址
     */
    private boolean localRelative;

    /**
     * 本地静态资源映射（key：访问路径，value：实际路径）
     */
    private Map<String, String> localLocations = new HashMap<>();

}
