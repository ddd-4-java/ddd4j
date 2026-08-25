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
package io.ddd4j.sample.micronaut;

import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.SubjectProvider;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Objects;

/**
 * 本地 Bearer 获取入口，演示 HTTP Bearer 到 Subject SPI 的桥接。
 */
@Controller("/api/auth")
public class AuthenticationController {

    private final SubjectProvider subjectProvider;

    public AuthenticationController(SubjectProvider subjectProvider) {
        this.subjectProvider = Objects.requireNonNull(subjectProvider, "subjectProvider must not be null");
    }

    @Post("/tokens/{userId}")
    public R<TokenResponse> issueToken(String userId) {
        String token = subjectProvider.getSubject().login(AuthRequest.of(userId));
        return R.ok(new TokenResponse(token));
    }

    public record TokenResponse(String token) {
    }
}
