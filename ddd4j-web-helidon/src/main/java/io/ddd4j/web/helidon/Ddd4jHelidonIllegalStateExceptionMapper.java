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

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * 覆盖 Jersey 的默认 IllegalStateException 映射，保持 ddd4j 的 409 语义。
 */
@Provider
@Priority(Priorities.USER)
public final class Ddd4jHelidonIllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    private final Ddd4jHelidonExceptionMapper delegate = new Ddd4jHelidonExceptionMapper();

    @Override
    public Response toResponse(IllegalStateException exception) {
        return delegate.toResponse(exception);
    }
}
