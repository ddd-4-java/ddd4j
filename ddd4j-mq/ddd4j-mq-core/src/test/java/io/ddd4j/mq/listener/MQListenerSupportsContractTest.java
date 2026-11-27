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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证空策略支持列表和显式配置的监听契约。
 */
class MQListenerSupportsContractTest {

    /**
     * 验证<code>supports.isEmpty()</code>为真，<code>supports.contains("kafka")</code>为假，<code>supports.add("kafka")</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void absentSupportsReturnsAnImmutableEmptyListThatMatchesNoStrategy() {
        List<String> supports = MQListener.builder().build().supports();

        assertTrue(supports.isEmpty());
        assertFalse(supports.contains("kafka"));
        assertFalse(supports.contains(null));
        assertThrows(UnsupportedOperationException.class, () -> supports.add("kafka"));
    }

    /**
     * 验证<code>MQListener.builder().supports(declared).build().supports()</code> 等于 <code>declared</code>。
     */
    @Test
    void configuredSupportsKeepsTheDeclaredStrategies() {
        List<String> declared = Collections.singletonList("kafka");

        assertEquals(declared, MQListener.builder().supports(declared).build().supports());
    }
}
