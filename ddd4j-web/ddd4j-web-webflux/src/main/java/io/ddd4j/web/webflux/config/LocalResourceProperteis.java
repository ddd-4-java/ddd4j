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
package io.ddd4j.web.webflux.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地静态资源映射配置属性。
 */
@Data
public class LocalResourceProperteis {

    // 本地存储路径
    /** \u672c\u5730\u9759\u6001\u8d44\u6e90\u76ee\u5f55\u3002 */
    private String localStorage;

    // 本地静态资源映射是否是相对于localStorage的地址
    /** \u9759\u6001\u8d44\u6e90\u76ee\u5f55\u662f\u5426\u76f8\u5bf9\u4e8e\u672c\u5730\u5b58\u50a8\u8def\u5f84\u89e3\u6790\u3002 */
    private boolean localRelative;

    // 本地静态资源映射
    /** URL \u8def\u5f84\u6a21\u5f0f\u4e0e\u672c\u5730\u9759\u6001\u8d44\u6e90\u76ee\u5f55\u7684\u6620\u5c04\u3002 */
    private Map<String, String> localLocations = new HashMap<>();

}
