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
package io.ddd4j.web.quarkus;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 公开路径配置的三线解析契约。 */
class Ddd4jQuarkusPublicPathsContractTest {

    @Test
    void trimsAndDiscardsBlankPaths() {
        Config config = (Config) Proxy.newProxyInstance(Config.class.getClassLoader(),
                new Class<?>[]{Config.class}, (proxy, method, args) -> {
                    if ("getOptionalValue".equals(method.getName())) {
                        return "ddd4j.web.public-paths".equals(args[0])
                                ? Optional.of(" /health , , /api/** ") : Optional.empty();
                    }
                    if ("getOptionalValues".equals(method.getName())) {
                        return Optional.of(Arrays.asList(" /health ", " ", " /api/** "));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        Ddd4jQuarkusWebConfiguration actual = Ddd4jQuarkusWebConfiguration.from(config);
        assertEquals(Arrays.asList("/health", "/api/**"), actual.getPublicPaths());
    }
}
