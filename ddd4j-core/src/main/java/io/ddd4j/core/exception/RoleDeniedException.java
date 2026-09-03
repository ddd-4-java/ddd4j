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
package io.ddd4j.core.exception;


/**
 * 角色被拒绝异常（访问受保护资源但缺少必要角色）。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：{@code NotRoleException}</li>
 *   <li>Shiro：{@code UnauthorizedException}（role 检查失败）</li>
 *   <li>Spring Security：{@code AccessDeniedException}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class RoleDeniedException extends AuthorizationException {

    private static final long serialVersionUID = 1L;

    public RoleDeniedException(String message) {
        super(message);
    }

    public RoleDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}