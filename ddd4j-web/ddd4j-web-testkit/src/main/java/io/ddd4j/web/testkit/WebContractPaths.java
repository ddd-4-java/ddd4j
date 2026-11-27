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

    /** \u6210\u529f\u54cd\u5e94\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String SUCCESS = "/contract/success";
    /** \u8d44\u6e90\u521b\u5efa\u54cd\u5e94\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String CREATED = "/contract/created";
    /** \u516c\u5f00\u7aef\u70b9\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String PUBLIC = "/contract/public";
    /** \u53d7\u4fdd\u62a4\u7aef\u70b9\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String PROTECTED = "/contract/protected";
    /** \u4e0a\u4e0b\u6587\u4f20\u9012\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String CONTEXT = "/contract/context";
    /** \u5e42\u7b49\u8bf7\u6c42\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String IDEMPOTENT = "/contract/idempotent";
    /** \u53c2\u6570\u9519\u8bef\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String BAD_REQUEST = "/contract/errors/bad-request";
    /** \u7981\u6b62\u8bbf\u95ee\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String FORBIDDEN = "/contract/errors/forbidden";
    /** \u8d44\u6e90\u4e0d\u5b58\u5728\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String NOT_FOUND = "/contract/errors/not-found";
    /** \u8d44\u6e90\u51b2\u7a81\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String CONFLICT = "/contract/errors/conflict";
    /** \u5a92\u4f53\u7c7b\u578b\u4e0d\u652f\u6301\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String UNSUPPORTED_MEDIA_TYPE = "/contract/errors/unsupported-media-type";
    /** \u5b9e\u4f53\u65e0\u6cd5\u5904\u7406\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String UNPROCESSABLE_ENTITY = "/contract/errors/unprocessable-entity";
    /** \u8bf7\u6c42\u8fc7\u591a\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String TOO_MANY_REQUESTS = "/contract/errors/too-many-requests";
    /** \u670d\u52a1\u7aef\u9519\u8bef\u5951\u7ea6\u7aef\u70b9\u8def\u5f84\u3002 */
    public static final String INTERNAL_SERVER_ERROR = "/contract/errors/internal-server-error";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>WebContractPaths</code> \u5b9e\u4f8b\u3002 */
    private WebContractPaths() {
    }
}
