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

import java.util.Collections;
import io.ddd4j.sample.helidon.cqrs.web.OrderResource;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;

/**
 * Helidon JAX-RS 应用声明。
 */
@ApplicationPath("/")
public class HelidonCqrsJaxRsApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Collections.singleton(OrderResource.class);
    }
}
