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
package io.ddd4j.data.external;

import lombok.Data;

/**
 * 外部服务配置属性类
 * <p>用于配置外部服务（如百度地图等）的访问密钥</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class ExternalProperties {

    /**
     * 配置前缀
     */
    public static final String PREFIX = "ddd4j.external";

    /**
     * 百度地图AK密钥
     */
    private String baiduAk;

}
