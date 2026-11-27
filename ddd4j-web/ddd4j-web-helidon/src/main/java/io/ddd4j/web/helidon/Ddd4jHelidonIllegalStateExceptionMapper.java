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
package io.ddd4j.web.helidon;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 覆盖 Jersey 的默认 IllegalStateException 映射，保持 ddd4j 的 409 语义。
 */
@Provider
@Priority(Priorities.USER)
public final class Ddd4jHelidonIllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    /** \u6267\u884c\u5b9e\u9645\u5f02\u5e38\u8f6c\u6362\u7684\u6846\u67b6\u6620\u5c04\u5668\u3002 */
    private final Ddd4jHelidonExceptionMapper delegate = new Ddd4jHelidonExceptionMapper();

    /**
     * 转换表示形式。
     * @param exception exception 参数。
     * @return 方法执行结果。
     */
    @Override
    public Response toResponse(IllegalStateException exception) {
        return delegate.toResponse(exception);
    }
}
