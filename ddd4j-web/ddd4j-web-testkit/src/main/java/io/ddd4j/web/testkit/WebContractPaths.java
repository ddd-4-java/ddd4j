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
package io.ddd4j.web.testkit;

/**
 * 所有 Web 适配器契约测试共享的端点路径。
 */
public final class WebContractPaths {

    /** 无需鉴权即返回成功结果的端点路径。 */
    public static final String SUCCESS = "/contract/success";
    /** 返回 201 创建状态的端点路径。 */
    public static final String CREATED = "/contract/created";
    /** 配置为公开访问的端点路径。 */
    public static final String PUBLIC = "/contract/public";
    /** 需要鉴权才能访问的端点路径。 */
    public static final String PROTECTED = "/contract/protected";
    /** 回显请求上下文标识的端点路径。 */
    public static final String CONTEXT = "/contract/context";
    /** 验证幂等键语义的端点路径。 */
    public static final String IDEMPOTENT = "/contract/idempotent";
    /** 触发参数校验失败（400）的端点路径。 */
    public static final String BAD_REQUEST = "/contract/errors/bad-request";
    /** 触发访问被拒（403）的端点路径。 */
    public static final String FORBIDDEN = "/contract/errors/forbidden";
    /** 触发资源不存在（404）的端点路径。 */
    public static final String NOT_FOUND = "/contract/errors/not-found";
    /** 触发请求冲突（409）的端点路径。 */
    public static final String CONFLICT = "/contract/errors/conflict";
    /** 触发不支持的媒体类型（415）的端点路径。 */
    public static final String UNSUPPORTED_MEDIA_TYPE = "/contract/errors/unsupported-media-type";
    /** 触发无法处理的实体（422）的端点路径。 */
    public static final String UNPROCESSABLE_ENTITY = "/contract/errors/unprocessable-entity";
    /** 触发请求过于频繁（429）的端点路径。 */
    public static final String TOO_MANY_REQUESTS = "/contract/errors/too-many-requests";
    /** 触发服务端内部错误（500）的端点路径。 */
    public static final String INTERNAL_SERVER_ERROR = "/contract/errors/internal-server-error";

    private WebContractPaths() {
    }
}
