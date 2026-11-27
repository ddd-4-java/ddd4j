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
package io.ddd4j.core.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Page}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class PageTest {

    /**
     * 验证<code>page.getTotal()</code>为零，<code>page.getCurrent()</code>为零，<code>page.getRecords()</code>为空。
     */
    @Test
    void empty_shouldHaveZeroTotalAndEmptyRecords() {
        Page<String> page = Page.empty();

        assertThat(page.getTotal()).isZero();
        assertThat(page.getCurrent()).isZero();
        assertThat(page.getSize()).isZero();
        assertThat(page.isEmpty()).isTrue();
        assertThat(page.getRecords()).isEmpty();
    }

    /**
     * 验证<code>page.getRecords()</code> 按顺序仅包含 <code>"a"</code>、<code>"b"</code>，<code>page.getTotal()</code> 等于 <code>2L</code>，<code>page.isEmpty()</code>为假。
     */
    @Test
    void succeed_shouldCarryRecordsAndTotal() {
        List<String> records = Arrays.asList("a", "b");

        Page<String> page = Page.succeed(records, 2L, 1L, 10L);

        assertThat(page.getRecords()).containsExactly("a", "b");
        assertThat(page.getTotal()).isEqualTo(2L);
        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(10L);
        assertThat(page.isEmpty()).isFalse();
    }

    /**
     * 验证<code>page.getCurrent()</code> 等于 <code>2L</code>，<code>page.getSize()</code> 等于 <code>20L</code>，<code>page.isEmpty()</code>为真。
     */
    @Test
    void constructor_withCurrentAndSize_shouldInitEmptyRecords() {
        Page<String> page = new Page<>(2, 20);

        assertThat(page.getCurrent()).isEqualTo(2L);
        assertThat(page.getSize()).isEqualTo(20L);
        assertThat(page.getRecords()).isEmpty();
        assertThat(page.isEmpty()).isTrue();
    }

    /**
     * 验证<code>page.getRecords()</code> 按顺序仅包含 <code>"x"</code>。
     */
    @Test
    void add_shouldAppendElement() {
        Page<String> page = Page.<String>empty().setCurrent(1).setSize(10);

        page.add("x");

        assertThat(page.getRecords()).containsExactly("x");
    }

    /**
     * 验证<code>removed</code>为真，<code>page.getRecords()</code> 按顺序仅包含 <code>"b"</code>。
     */
    @Test
    void remove_shouldDeleteElement() {
        Page<String> page = Page.succeed(new ArrayList<>(Arrays.asList("a", "b")), 2L, 1L, 10L);

        boolean removed = page.remove("a");

        assertThat(removed).isTrue();
        assertThat(page.getRecords()).containsExactly("b");
    }

    /**
     * 验证<code>page.contains("a")</code>为真，<code>page.contains("z")</code>为假，<code>page.contains(null)</code>为假。
     */
    @Test
    void contains_shouldDetectElement() {
        Page<String> page = Page.succeed(Collections.singletonList("a"), 1L, 1L, 10L);

        assertThat(page.contains("a")).isTrue();
        assertThat(page.contains("z")).isFalse();
        assertThat(page.contains(null)).isFalse();
    }

    /**
     * 验证<code>page.isEmpty()</code>为真。
     */
    @Test
    void isEmpty_shouldBeTrueWhenRecordsNull() {
        Page<String> page = new Page<>();
        page.setRecords(null);

        assertThat(page.isEmpty()).isTrue();
    }

    /**
     * 验证<code>result</code> 指向同一实例 <code>page</code>，<code>collected</code> 按顺序仅包含 <code>"a"</code>、<code>"b"</code>。
     */
    @Test
    void peek_shouldApplyConsumerAndReturnSelf() {
        List<String> collected = new ArrayList<>();
        Page<String> page = Page.succeed(Arrays.asList("a", "b"), 2L, 1L, 10L);

        Page<String> result = page.peek(collected::add);

        assertThat(result).isSameAs(page);
        assertThat(collected).containsExactly("a", "b");
    }

    /**
     * 验证<code>page.getExtras()</code> 含对应条目 <code>"key"</code>、<code>"value"</code>。
     */
    @Test
    void extras_shouldLazilyInitMap() {
        Page<String> page = new Page<>();

        page.extras().put("key", "value");

        assertThat(page.getExtras()).containsEntry("key", "value");
    }

    /**
     * 验证<code>collected</code> 按顺序仅包含 <code>"a"</code>、<code>"b"</code>。
     */
    @Test
    void stream_shouldExposeRecords() {
        Page<String> page = Page.succeed(Arrays.asList("a", "b"), 2L, 1L, 10L);

        List<String> collected = page.stream().collect(java.util.stream.Collectors.toList());

        assertThat(collected).containsExactly("a", "b");
    }

    /**
     * 验证<code>page.getRecords()</code> 按顺序仅包含 <code>"x"</code>，<code>page.getTotal()</code> 等于 <code>1L</code>，<code>page.getSize()</code> 等于 <code>5L</code>。
     */
    @Test
    void builderMethods_shouldChainCorrectly() {
        Page<String> page = Page.<String>empty()
                .setRecords(Collections.singletonList("x"))
                .setTotal(1L)
                .setCurrent(1L)
                .setSize(5L);

        assertThat(page.getRecords()).containsExactly("x");
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(5L);
    }

    /**
     * 验证<code>page.iterator()</code>非空，<code>page.iterator().hasNext()</code>为假，<code>visited</code>为空。
     */
    @Test
    void emptyPages_shouldSupportIterationAndStreams() {
        for (Page<String> page : Arrays.asList(new Page<String>(), Page.<String>empty())) {
            assertThat(page.iterator()).isNotNull();
            assertThat(page.iterator().hasNext()).isFalse();
            assertThat(page.spliterator()).isNotNull();
            assertThat(page.spliterator().estimateSize()).isZero();
            assertThat(java.util.stream.StreamSupport.stream(page.spliterator(), false).count()).isZero();
            assertThat(page.stream().count()).isZero();
            List<String> visited = new ArrayList<>();
            for (String value : page) {
                visited.add(value);
            }
            assertThat(visited).isEmpty();
        }
    }
}
