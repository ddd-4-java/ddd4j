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

import io.micronaut.runtime.Micronaut;

/**
 * 共享 Order 业务内核的 Micronaut 启动入口。
 */
public final class MicronautOrderApplication {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>MicronautOrderApplication</code> \u5b9e\u4f8b\u3002 */
    private MicronautOrderApplication() {
    }

    /** @param args \u542f\u52a8\u53c2\u6570 */
    public static void main(String[] args) {
        Micronaut.run(MicronautOrderApplication.class, args);
    }
}
