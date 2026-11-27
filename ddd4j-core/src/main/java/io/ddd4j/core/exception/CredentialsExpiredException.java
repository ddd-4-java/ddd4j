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

import java.io.Serial;

/**
 * 凭证（密码）过期异常。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：通过 {@code SaTempKit.updatePassword(...)} 检测</li>
 *   <li>Shiro：{@code ExpiredCredentialsException}</li>
 *   <li>Spring Security：{@code CredentialsExpiredException}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class CredentialsExpiredException extends AuthenticationException {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u51ed\u636e\u8fc7\u671f\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     */
    public CredentialsExpiredException(String message) {
        super(message);
    }

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u51ed\u636e\u8fc7\u671f\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public CredentialsExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
