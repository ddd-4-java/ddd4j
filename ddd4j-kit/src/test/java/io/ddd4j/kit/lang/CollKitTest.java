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
package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CollKit} (delegates to Hutool {@code CollUtil} + custom array helpers).
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class CollKitTest {

    /**
     * 验证<code>CollKit.isEmpty((java.util.Collection&lt;?&gt;) null)</code>为真。
     */
    @Test
    void isEmpty_collection_shouldReturnTrueForNull() {
        assertThat(CollKit.isEmpty((java.util.Collection<?>) null)).isTrue();
    }

    /**
     * 验证<code>CollKit.isEmpty(Collections.emptyList())</code>为真。
     */
    @Test
    void isEmpty_collection_shouldReturnTrueForEmptyCollection() {
        assertThat(CollKit.isEmpty(Collections.emptyList())).isTrue();
    }

    /**
     * 验证<code>CollKit.isEmpty(Collections.singletonList(1))</code>为假。
     */
    @Test
    void isEmpty_collection_shouldReturnFalseForNonEmpty() {
        assertThat(CollKit.isEmpty(Collections.singletonList(1))).isFalse();
    }

    /**
     * 验证<code>CollKit.isEmpty((java.util.Map&lt;?, ?&gt;) null)</code>为真。
     */
    @Test
    void isEmpty_map_shouldReturnTrueForNull() {
        assertThat(CollKit.isEmpty((java.util.Map<?, ?>) null)).isTrue();
    }

    /**
     * 验证<code>CollKit.isEmpty(Collections.emptyMap())</code>为真。
     */
    @Test
    void isEmpty_map_shouldReturnTrueForEmptyMap() {
        assertThat(CollKit.isEmpty(Collections.emptyMap())).isTrue();
    }

    /**
     * 验证<code>CollKit.isNotEmpty(Collections.singletonList(1))</code>为真，<code>CollKit.isNotEmpty((java.util.Collection&lt;?&gt;) null)</code>为假。
     */
    @Test
    void isNotEmpty_collection_shouldBeNegation() {
        assertThat(CollKit.isNotEmpty(Collections.singletonList(1))).isTrue();
        assertThat(CollKit.isNotEmpty((java.util.Collection<?>) null)).isFalse();
    }

    /**
     * 验证<code>CollKit.isEmpty((Object[]) null)</code>为真。
     */
    @Test
    void isEmpty_array_shouldReturnTrueForNull() {
        assertThat(CollKit.isEmpty((Object[]) null)).isTrue();
    }

    /**
     * 验证<code>CollKit.isEmpty(new Object[0])</code>为真。
     */
    @Test
    void isEmpty_array_shouldReturnTrueForEmptyArray() {
        assertThat(CollKit.isEmpty(new Object[0])).isTrue();
    }

    /**
     * 验证<code>CollKit.isEmpty(new Object[]{"a"})</code>为假。
     */
    @Test
    void isEmpty_array_shouldReturnFalseForNonEmpty() {
        assertThat(CollKit.isEmpty(new Object[]{"a"})).isFalse();
    }

    /**
     * 验证<code>CollKit.isNotEmpty(new String[]{"a", "b"})</code>为真，<code>CollKit.isNotEmpty((Object[]) null)</code>为假。
     */
    @Test
    void isNotEmpty_array_shouldReturnTrueForNonEmpty() {
        assertThat(CollKit.isNotEmpty(new String[]{"a", "b"})).isTrue();
        assertThat(CollKit.isNotEmpty((Object[]) null)).isFalse();
    }

    /**
     * 验证<code>array</code> 按顺序仅包含 <code>"a"</code>、<code>"b"</code>。
     */
    @Test
    void convert_fromCollection_shouldReturnTypedArray() {
        java.util.List<String> src = new ArrayList<>();
        src.add("a");
        src.add("b");
        String[] array = CollKit.convert(src);

        assertThat(array).containsExactly("a", "b");
    }

    /**
     * 验证<code>array</code>为空。
     */
    @Test
    void convert_fromCollection_shouldReturnNullForEmpty() {
        java.util.ArrayList<String> empty = new ArrayList<>();
        String[] array = CollKit.convert(empty);

        assertThat(array).isNull();
    }

    /**
     * 验证<code>array</code>为空。
     */
    @Test
    void convert_fromCollection_shouldReturnNullForNull() {
        String[] array = CollKit.convert((java.util.Collection<String>) null);

        assertThat(array).isNull();
    }

    /**
     * 验证<code>list</code> 按顺序仅包含 <code>"a"</code>、<code>"b"</code>。
     */
    @Test
    void convert_fromArray_shouldReturnList() {
        List<String> list = CollKit.convert(new String[]{"a", "b"});

        assertThat(list).containsExactly("a", "b");
    }

    /**
     * 验证<code>list</code>为空。
     */
    @Test
    void convert_fromArray_shouldReturnEmptyListForNull() {
        List<String> list = CollKit.convert((String[]) null);

        assertThat(list).isEmpty();
    }

    /**
     * 验证<code>list</code>为空。
     */
    @Test
    void convert_fromArray_shouldReturnEmptyListForEmptyArray() {
        List<String> list = CollKit.convert(new String[0]);

        assertThat(list).isEmpty();
    }

    /**
     * 验证<code>list</code> 按顺序仅包含 <code>"a"</code>、<code>"b"</code>。
     */
    @Test
    void convert_fromArray_shouldReturnMutableList() {
        List<String> list = CollKit.convert(new String[]{"a"});

        list.add("b");

        assertThat(list).containsExactly("a", "b");
    }

    /**
     * 验证<code>CollKit.isNotEmpty(list)</code>为真，<code>CollKit.isEmpty(list)</code>为假。
     */
    @Test
    void newArrayList_shouldCreateEmptyCollection() {
        // Sanity check via isEmpty + isNotEmpty round-trip on a freshly built list.
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3));

        assertThat(CollKit.isNotEmpty(list)).isTrue();
        assertThat(CollKit.isEmpty(list)).isFalse();
    }
}
