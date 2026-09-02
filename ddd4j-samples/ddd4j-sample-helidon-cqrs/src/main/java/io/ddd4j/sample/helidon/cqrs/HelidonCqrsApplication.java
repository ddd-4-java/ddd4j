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
package io.ddd4j.sample.helidon.cqrs;

import io.helidon.microprofile.server.Server;

/**
 * Helidon MP CQRS 集成示例启动入口。
 *
 * <p>CQRS 组件通过 {@link HelidonCqrsBeans} 以 CDI 方式装配，
 * {@code OrderResource} 通过 {@code @Inject} 获取依赖。
 */
public class HelidonCqrsApplication {

    public static void main(String[] args) {
        Server.builder()
                .addApplication(new HelidonCqrsJaxRsApplication())
                .build()
                .start();
    }
}
