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
package io.ddd4j.web.webflux;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebRequestContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

/**
 * 从 Reactor Context 读取 ddd4j 请求级状态。
 */
@UtilityClass
public class Ddd4jWebFluxContext {

    static final Class<WebRequestContext> REQUEST_CONTEXT_KEY = WebRequestContext.class;
    static final Class<Subject> SUBJECT_KEY = Subject.class;

    public Mono<WebRequestContext> currentRequest() {
        return Mono.deferContextual(context -> Mono.justOrEmpty(context.getOrEmpty(REQUEST_CONTEXT_KEY)));
    }

    public Mono<Subject> currentSubject() {
        return Mono.deferContextual(context -> Mono.justOrEmpty(context.getOrEmpty(SUBJECT_KEY)));
    }
}
