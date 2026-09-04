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

import java.util.ArrayList;
import java.util.List;

/**
 * Web 基础配置属性。
 * <p>包含日志拦截、MVC 响应、认证令牌和 WebSocket 等相关配置。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
// @ConfigurationProperties(prefix = "base-web")
public class BaseWebProperties {
    /**
     * 日志拦截配置
     */
    private Log log = new Log();
    /**
     * MVC 响应配置
     */
    private Mvc mvc = new Mvc();
    /**
     * Bearer 认证配置
     */
    private Auth auth = new Auth();
    /**
     * WebSocket 配置
     */
    private Ws ws = new Ws();

    /**
     * 日志拦截配置。
     */
    @Data
    public static class Log {
        /**
         * 日志拦截包含路径
         */
        private String includes = "/**";
        /**
         * 日志拦截不包含路径
         */
        private String excludes = "/error";
    }

    /**
     * MVC 响应配置。
     */
    @Data
    public static class Mvc {
        /**
         * 是否启用 R 响应包装
         */
        private Boolean enableRResponse = true;
    }

    /**
     * Bearer 认证配置。
     */
    @Data
    public static class Auth {
        /**
         * Bearer 访问令牌列表
         */
        private List<String> bearerTokens = new ArrayList<>();
    }

    /**
     * WebSocket 配置。
     */
    @Data
    public static class Ws {
        /**
         * 断线重连时间（单位：分钟）
         */
        private Integer reconnectTime = 5;
    }

}