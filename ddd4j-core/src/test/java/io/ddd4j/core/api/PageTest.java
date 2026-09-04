package io.ddd4j.core.api;

import java.util.Collections;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Page}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class PageTest {

    @Test
    void empty_shouldHaveZeroTotalAndEmptyRecords() {
        Page<String> page = Page.empty();

        assertThat(page.getTotal()).isZero();
        assertThat(page.getCurrent()).isZero();
        assertThat(page.getSize()).isZero();
        assertThat(page.isEmpty()).isTrue();
        assertThat(page.getRecords()).isEmpty();
    }

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

    @Test
    void constructor_withCurrentAndSize_shouldInitEmptyRecords() {
        Page<String> page = new Page<>(2, 20);

        assertThat(page.getCurrent()).isEqualTo(2L);
        assertThat(page.getSize()).isEqualTo(20L);
        assertThat(page.getRecords()).isEmpty();
        assertThat(page.isEmpty()).isTrue();
    }

    @Test
    void add_shouldAppendElement() {
        Page<String> page = Page.<String>empty().setCurrent(1).setSize(10);

        page.add("x");

        assertThat(page.getRecords()).containsExactly("x");
    }

    @Test
    void remove_shouldDeleteElement() {
        Page<String> page = Page.succeed(new ArrayList<>(Arrays.asList("a", "b")), 2L, 1L, 10L);

        boolean removed = page.remove("a");

        assertThat(removed).isTrue();
        assertThat(page.getRecords()).containsExactly("b");
    }

    @Test
    void contains_shouldDetectElement() {
        Page<String> page = Page.succeed(Collections.singletonList("a"), 1L, 1L, 10L);

        assertThat(page.contains("a")).isTrue();
        assertThat(page.contains("z")).isFalse();
        assertThat(page.contains(null)).isFalse();
    }

    @Test
    void isEmpty_shouldBeTrueWhenRecordsNull() {
        Page<String> page = new Page<>();
        page.setRecords(null);

        assertThat(page.isEmpty()).isTrue();
    }

    @Test
    void peek_shouldApplyConsumerAndReturnSelf() {
        List<String> collected = new ArrayList<>();
        Page<String> page = Page.succeed(Arrays.asList("a", "b"), 2L, 1L, 10L);

        Page<String> result = page.peek(collected::add);

        assertThat(result).isSameAs(page);
        assertThat(collected).containsExactly("a", "b");
    }

    @Test
    void extras_shouldLazilyInitMap() {
        Page<String> page = new Page<>();

        page.extras().put("key", "value");

        assertThat(page.getExtras()).containsEntry("key", "value");
    }

    @Test
    void stream_shouldExposeRecords() {
        Page<String> page = Page.succeed(Arrays.asList("a", "b"), 2L, 1L, 10L);

        List<String> collected = page.stream().toList();

        assertThat(collected).containsExactly("a", "b");
    }

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
}
