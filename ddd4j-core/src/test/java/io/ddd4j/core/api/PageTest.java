package io.ddd4j.core.api;

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

    /** \u9a8c\u8bc1<code>page.getTotal()</code>\u4e3a\u96f6\uff0c<code>page.getCurrent()</code>\u4e3a\u96f6\uff0c<code>page.getRecords()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void empty_shouldHaveZeroTotalAndEmptyRecords() {
        Page<String> page = Page.empty();

        assertThat(page.getTotal()).isZero();
        assertThat(page.getCurrent()).isZero();
        assertThat(page.getSize()).isZero();
        assertThat(page.isEmpty()).isTrue();
        assertThat(page.getRecords()).isEmpty();
    }

    /** \u9a8c\u8bc1<code>page.getRecords()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"a"</code>\u3001<code>"b"</code>\uff0c<code>page.getTotal()</code> \u7b49\u4e8e <code>2L</code>\uff0c<code>page.isEmpty()</code>\u4e3a\u5047\u3002 */
    @Test
    void succeed_shouldCarryRecordsAndTotal() {
        List<String> records = List.of("a", "b");

        Page<String> page = Page.succeed(records, 2L, 1L, 10L);

        assertThat(page.getRecords()).containsExactly("a", "b");
        assertThat(page.getTotal()).isEqualTo(2L);
        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(10L);
        assertThat(page.isEmpty()).isFalse();
    }

    /** \u9a8c\u8bc1<code>page.getCurrent()</code> \u7b49\u4e8e <code>2L</code>\uff0c<code>page.getSize()</code> \u7b49\u4e8e <code>20L</code>\uff0c<code>page.isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void constructor_withCurrentAndSize_shouldInitEmptyRecords() {
        Page<String> page = new Page<>(2, 20);

        assertThat(page.getCurrent()).isEqualTo(2L);
        assertThat(page.getSize()).isEqualTo(20L);
        assertThat(page.getRecords()).isEmpty();
        assertThat(page.isEmpty()).isTrue();
    }

    /** \u9a8c\u8bc1<code>page.getRecords()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"x"</code>\u3002 */
    @Test
    void add_shouldAppendElement() {
        Page<String> page = Page.<String>empty().setCurrent(1).setSize(10);

        page.add("x");

        assertThat(page.getRecords()).containsExactly("x");
    }

    /** \u9a8c\u8bc1<code>removed</code>\u4e3a\u771f\uff0c<code>page.getRecords()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"b"</code>\u3002 */
    @Test
    void remove_shouldDeleteElement() {
        Page<String> page = Page.succeed(new ArrayList<>(List.of("a", "b")), 2L, 1L, 10L);

        boolean removed = page.remove("a");

        assertThat(removed).isTrue();
        assertThat(page.getRecords()).containsExactly("b");
    }

    /** \u9a8c\u8bc1<code>page.contains("a")</code>\u4e3a\u771f\uff0c<code>page.contains("z")</code>\u4e3a\u5047\uff0c<code>page.contains(null)</code>\u4e3a\u5047\u3002 */
    @Test
    void contains_shouldDetectElement() {
        Page<String> page = Page.succeed(List.of("a"), 1L, 1L, 10L);

        assertThat(page.contains("a")).isTrue();
        assertThat(page.contains("z")).isFalse();
        assertThat(page.contains(null)).isFalse();
    }

    /** \u9a8c\u8bc1<code>page.isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void isEmpty_shouldBeTrueWhenRecordsNull() {
        Page<String> page = new Page<>();
        page.setRecords(null);

        assertThat(page.isEmpty()).isTrue();
    }

    /** \u9a8c\u8bc1<code>result</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>page</code>\uff0c<code>collected</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"a"</code>\u3001<code>"b"</code>\u3002 */
    @Test
    void peek_shouldApplyConsumerAndReturnSelf() {
        List<String> collected = new ArrayList<>();
        Page<String> page = Page.succeed(List.of("a", "b"), 2L, 1L, 10L);

        Page<String> result = page.peek(collected::add);

        assertThat(result).isSameAs(page);
        assertThat(collected).containsExactly("a", "b");
    }

    /** \u9a8c\u8bc1<code>page.getExtras()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"key"</code>\u3001<code>"value"</code>\u3002 */
    @Test
    void extras_shouldLazilyInitMap() {
        Page<String> page = new Page<>();

        page.extras().put("key", "value");

        assertThat(page.getExtras()).containsEntry("key", "value");
    }

    /** \u9a8c\u8bc1<code>collected</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"a"</code>\u3001<code>"b"</code>\u3002 */
    @Test
    void stream_shouldExposeRecords() {
        Page<String> page = Page.succeed(List.of("a", "b"), 2L, 1L, 10L);

        List<String> collected = page.stream().toList();

        assertThat(collected).containsExactly("a", "b");
    }

    /** \u9a8c\u8bc1<code>page.getRecords()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"x"</code>\uff0c<code>page.getTotal()</code> \u7b49\u4e8e <code>1L</code>\uff0c<code>page.getSize()</code> \u7b49\u4e8e <code>5L</code>\u3002 */
    @Test
    void builderMethods_shouldChainCorrectly() {
        Page<String> page = Page.<String>empty()
                .setRecords(List.of("x"))
                .setTotal(1L)
                .setCurrent(1L)
                .setSize(5L);

        assertThat(page.getRecords()).containsExactly("x");
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(5L);
    }
}
