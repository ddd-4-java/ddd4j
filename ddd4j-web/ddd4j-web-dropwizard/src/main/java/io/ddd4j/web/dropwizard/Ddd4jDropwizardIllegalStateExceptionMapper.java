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
package io.ddd4j.web.dropwizard;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import java.util.Objects;

/**
 * 覆盖 Dropwizard 默认的 500 映射，将非法领域状态交给 ddd4j 统一翻译为 409。
 */
public final class Ddd4jDropwizardIllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    /** \u6267\u884c\u5b9e\u9645\u5f02\u5e38\u8f6c\u6362\u7684\u6846\u67b6\u6620\u5c04\u5668\u3002 */
    private final Ddd4jDropwizardExceptionMapper delegate;

    /** \u4f7f\u7528\u9ed8\u8ba4 ddd4j \u5f02\u5e38\u6620\u5c04\u5668\u3002 */
    public Ddd4jDropwizardIllegalStateExceptionMapper() {
        this(new Ddd4jDropwizardExceptionMapper());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u59d4\u6258\u5904\u7406\u975e\u6cd5\u72b6\u6001\u5f02\u5e38\u3002
     * @param delegate ddd4j \u5f02\u5e38\u6620\u5c04\u5668
     */
    public Ddd4jDropwizardIllegalStateExceptionMapper(Ddd4jDropwizardExceptionMapper delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

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
