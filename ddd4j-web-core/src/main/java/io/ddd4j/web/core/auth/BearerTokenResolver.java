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
package io.ddd4j.web.core.auth;

import io.ddd4j.kit.lang.StrKit;

import java.util.Optional;

/**
 * 只接受 RFC 6750 标准 Authorization Bearer 头。
 */
public final class BearerTokenResolver {

    private static final String PREFIX = "Bearer ";

    public Optional<String> resolve(String authorization) {
        if (StrKit.isBlank(authorization) || !authorization.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Optional.empty();
        }
        String token = authorization.substring(PREFIX.length()).trim();
        return StrKit.isBlank(token) ? Optional.empty() : Optional.of(token);
    }
}
