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

    /** 成功响应契约端点路径。 */
    public static final String SUCCESS = "/contract/success";
    /** 资源创建响应契约端点路径。 */
    public static final String CREATED = "/contract/created";
    /** 公开端点契约端点路径。 */
    public static final String PUBLIC = "/contract/public";
    /** 受保护端点契约端点路径。 */
    public static final String PROTECTED = "/contract/protected";
    /** 上下文传递契约端点路径。 */
    public static final String CONTEXT = "/contract/context";
    /** 幂等请求契约端点路径。 */
    public static final String IDEMPOTENT = "/contract/idempotent";
    /** 参数错误契约端点路径。 */
    public static final String BAD_REQUEST = "/contract/errors/bad-request";
    /** 禁止访问契约端点路径。 */
    public static final String FORBIDDEN = "/contract/errors/forbidden";
    /** 资源不存在契约端点路径。 */
    public static final String NOT_FOUND = "/contract/errors/not-found";
    /** 资源冲突契约端点路径。 */
    public static final String CONFLICT = "/contract/errors/conflict";
    /** 媒体类型不支持契约端点路径。 */
    public static final String UNSUPPORTED_MEDIA_TYPE = "/contract/errors/unsupported-media-type";
    /** 实体无法处理契约端点路径。 */
    public static final String UNPROCESSABLE_ENTITY = "/contract/errors/unprocessable-entity";
    /** 请求过多契约端点路径。 */
    public static final String TOO_MANY_REQUESTS = "/contract/errors/too-many-requests";
    /** 服务端错误契约端点路径。 */
    public static final String INTERNAL_SERVER_ERROR = "/contract/errors/internal-server-error";

    private WebContractPaths() {
    }
}
