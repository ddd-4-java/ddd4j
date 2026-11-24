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
package io.ddd4j.mq.listener;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证空策略支持列表和显式配置的监听契约。 */
class MQListenerSupportsContractTest {

    @Test
    void absentSupportsReturnsAnImmutableEmptyListThatMatchesNoStrategy() {
        List<String> supports = MQListener.builder().build().supports();

        assertTrue(supports.isEmpty());
        assertFalse(supports.contains("kafka"));
        assertFalse(supports.contains(null));
        assertThrows(UnsupportedOperationException.class, () -> supports.add("kafka"));
    }

    @Test
    void configuredSupportsKeepsTheDeclaredStrategies() {
        List<String> declared = Collections.singletonList("kafka");

        assertEquals(declared, MQListener.builder().supports(declared).build().supports());
    }
}
