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

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 三线共享的 Helidon Web 配置读取契约。
 */
class Ddd4jHelidonWebConfigurationContractTest {

    @Test
    void publicPathsTrimWhitespaceAroundEntries() {
        String key = "ddd4j.web.public-paths";
        String previous = System.getProperty(key);
        System.setProperty(key, " /public , /health ");
        try {
            Ddd4jHelidonWebConfiguration configuration =
                    Ddd4jHelidonWebConfiguration.from(ConfigProvider.getConfig());
            assertEquals(Arrays.asList("/public", "/health"), configuration.getPublicPaths());
        } finally {
            if (Objects.isNull(previous)) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }
}
