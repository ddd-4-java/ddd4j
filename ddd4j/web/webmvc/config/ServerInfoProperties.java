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

/**
 * 服务信息
 */
@Data
public class ServerInfoProperties {

    /**
     * 服务节点 UID
     */
    private String uid;
    /**
     * 服务节点名称
     */
    private String name;

    /**
     * 服务节点描述
     */
    private String description;

    /**
     * 服务节点版本
     */
    private String version;

    @Override
    public String toString() {
        return "ServiceInfo{" + "name='" + name + '\'' + ", version='" + version + '\'' + '}';
    }
}
