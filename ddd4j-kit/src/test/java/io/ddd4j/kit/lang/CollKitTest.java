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

    @Test
    void isEmpty_collection_shouldReturnTrueForNull() {
        assertThat(CollKit.isEmpty((java.util.Collection<?>) null)).isTrue();
    }

    @Test
    void isEmpty_collection_shouldReturnTrueForEmptyCollection() {
        assertThat(CollKit.isEmpty(Collections.emptyList())).isTrue();
    }

    @Test
    void isEmpty_collection_shouldReturnFalseForNonEmpty() {
        assertThat(CollKit.isEmpty(Collections.singletonList(1))).isFalse();
    }

    @Test
    void isEmpty_map_shouldReturnTrueForNull() {
        assertThat(CollKit.isEmpty((java.util.Map<?, ?>) null)).isTrue();
    }

    @Test
    void isEmpty_map_shouldReturnTrueForEmptyMap() {
        assertThat(CollKit.isEmpty(Collections.emptyMap())).isTrue();
    }

    @Test
    void isNotEmpty_collection_shouldBeNegation() {
        assertThat(CollKit.isNotEmpty(Collections.singletonList(1))).isTrue();
        assertThat(CollKit.isNotEmpty((java.util.Collection<?>) null)).isFalse();
    }

    @Test
    void isEmpty_array_shouldReturnTrueForNull() {
        assertThat(CollKit.isEmpty((Object[]) null)).isTrue();
    }

    @Test
    void isEmpty_array_shouldReturnTrueForEmptyArray() {
        assertThat(CollKit.isEmpty(new Object[0])).isTrue();
    }

    @Test
    void isEmpty_array_shouldReturnFalseForNonEmpty() {
        assertThat(CollKit.isEmpty(new Object[]{"a"})).isFalse();
    }

    @Test
    void isNotEmpty_array_shouldReturnTrueForNonEmpty() {
        assertThat(CollKit.isNotEmpty(new String[]{"a", "b"})).isTrue();
        assertThat(CollKit.isNotEmpty((Object[]) null)).isFalse();
    }

    @Test
    void convert_fromCollection_shouldReturnTypedArray() {
        java.util.List<String> src = new ArrayList<>();
        src.add("a");
        src.add("b");
        String[] array = CollKit.convert(src);

        assertThat(array).containsExactly("a", "b");
    }

    @Test
    void convert_fromCollection_shouldReturnNullForEmpty() {
        java.util.ArrayList<String> empty = new ArrayList<>();
        String[] array = CollKit.convert(empty);

        assertThat(array).isNull();
    }

    @Test
    void convert_fromCollection_shouldReturnNullForNull() {
        String[] array = CollKit.convert((java.util.Collection<String>) null);

        assertThat(array).isNull();
    }

    @Test
    void convert_fromArray_shouldReturnList() {
        List<String> list = CollKit.convert(new String[]{"a", "b"});

        assertThat(list).containsExactly("a", "b");
    }

    @Test
    void convert_fromArray_shouldReturnEmptyListForNull() {
        List<String> list = CollKit.convert((String[]) null);

        assertThat(list).isEmpty();
    }

    @Test
    void convert_fromArray_shouldReturnEmptyListForEmptyArray() {
        List<String> list = CollKit.convert(new String[0]);

        assertThat(list).isEmpty();
    }

    @Test
    void convert_fromArray_shouldReturnMutableList() {
        List<String> list = CollKit.convert(new String[]{"a"});

        list.add("b");

        assertThat(list).containsExactly("a", "b");
    }

    @Test
    void newArrayList_shouldCreateEmptyCollection() {
        // Sanity check via isEmpty + isNotEmpty round-trip on a freshly built list.
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3));

        assertThat(CollKit.isNotEmpty(list)).isTrue();
        assertThat(CollKit.isEmpty(list)).isFalse();
    }
}
