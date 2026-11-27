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
package io.ddd4j.mq.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TagMatcher} tag 表达式单测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class TagMatcherTest {

    /**
     * 验证<code>TagMatcher.match("A", "*")</code>为真，<code>TagMatcher.match(null, "*")</code>为真，<code>TagMatcher.match("A", null)</code>为真。
     */
    @Test
    void wildcardShouldMatchAnyInput() {
        assertTrue(TagMatcher.match("A", "*"));
        assertTrue(TagMatcher.match(null, "*"));
        assertTrue(TagMatcher.match("A", null));
    }

    /**
     * 验证<code>TagMatcher.match("A", "A || B")</code>为真，<code>TagMatcher.match("B", "A||B")</code>为真，<code>TagMatcher.match("C", "A || B")</code>为假。
     */
    @Test
    void includeShouldMatchOnlyDeclaredTags() {
        assertTrue(TagMatcher.match("A", "A || B"));
        assertTrue(TagMatcher.match("B", "A||B"));
        assertFalse(TagMatcher.match("C", "A || B"));
    }

    /**
     * 验证<code>TagMatcher.match("C", "-C")</code>为假，<code>TagMatcher.match("A", "-C")</code>为真。
     */
    @Test
    void excludeShouldRejectDeclaredTag() {
        assertFalse(TagMatcher.match("C", "-C"));
        assertTrue(TagMatcher.match("A", "-C"));
    }

    /**
     * 验证<code>TagMatcher.match("A", "* -C")</code>为真，<code>TagMatcher.match("C", "* -C")</code>为假，<code>TagMatcher.match("C", "A || B -C")</code>为假。
     */
    @Test
    void compoundExpressionShouldApplyExcludeBeforeInclude() {
        assertTrue(TagMatcher.match("A", "* -C"));
        assertFalse(TagMatcher.match("C", "* -C"));
        assertFalse(TagMatcher.match("C", "A || B -C"));
    }

    /**
     * 验证<code>TagMatcher.match(null, "A")</code>为假，<code>TagMatcher.match("", "A")</code>为假，<code>TagMatcher.match("", "-C")</code>为真。
     */
    @Test
    void emptyInputShouldNotMatchSpecificIncludes() {
        assertFalse(TagMatcher.match(null, "A"));
        assertFalse(TagMatcher.match("", "A"));
        assertTrue(TagMatcher.match("", "-C"));
    }

    /**
     * 在测试框架中执行 <code>Arrays.asList()</code>、<code>TagMatcher.findIncludes()</code>、<code>Collections.emptyList()</code>，验证该场景。
     */
    @Test
    void findIncludesShouldKeepDeclarationOrder() {
        assertEquals(Arrays.asList("A", "B"), new ArrayList<>(TagMatcher.findIncludes("A || B -C")));
        assertEquals(Collections.emptyList(), new ArrayList<>(TagMatcher.findIncludes("* -C")));
    }
}
