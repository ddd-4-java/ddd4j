package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StrKit} (delegates to Hutool {@code StrUtil}).
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class StrKitTest {

    /** \u9a8c\u8bc1<code>StrKit.isBlank(null)</code>\u4e3a\u771f\u3002 */
    @Test
    void isBlank_shouldReturnTrueForNull() {
        assertThat(StrKit.isBlank(null)).isTrue();
    }

    /** \u9a8c\u8bc1<code>StrKit.isBlank("")</code>\u4e3a\u771f\u3002 */
    @Test
    void isBlank_shouldReturnTrueForEmptyString() {
        assertThat(StrKit.isBlank("")).isTrue();
    }

    /** \u9a8c\u8bc1<code>StrKit.isBlank(" ")</code>\u4e3a\u771f\uff0c<code>StrKit.isBlank("\t\n")</code>\u4e3a\u771f\u3002 */
    @Test
    void isBlank_shouldReturnTrueForWhitespaceOnly() {
        assertThat(StrKit.isBlank("   ")).isTrue();
        assertThat(StrKit.isBlank("\t\n")).isTrue();
    }

    /** \u9a8c\u8bc1<code>StrKit.isBlank("a")</code>\u4e3a\u5047\uff0c<code>StrKit.isBlank(" hello ")</code>\u4e3a\u5047\u3002 */
    @Test
    void isBlank_shouldReturnFalseForNonBlank() {
        assertThat(StrKit.isBlank("a")).isFalse();
        assertThat(StrKit.isBlank(" hello ")).isFalse();
    }

    /** \u9a8c\u8bc1<code>StrKit.isEmpty(null)</code>\u4e3a\u771f\u3002 */
    @Test
    void isEmpty_shouldReturnTrueForNull() {
        assertThat(StrKit.isEmpty(null)).isTrue();
    }

    /** \u9a8c\u8bc1<code>StrKit.isEmpty("")</code>\u4e3a\u771f\u3002 */
    @Test
    void isEmpty_shouldReturnTrueForEmptyString() {
        assertThat(StrKit.isEmpty("")).isTrue();
    }

    /** \u9a8c\u8bc1<code>StrKit.isEmpty(" ")</code>\u4e3a\u5047\u3002 */
    @Test
    void isEmpty_shouldReturnFalseForWhitespace() {
        // isEmpty does NOT treat whitespace as empty (unlike isBlank)
        assertThat(StrKit.isEmpty("  ")).isFalse();
    }

    /** \u9a8c\u8bc1<code>StrKit.isEmpty("a")</code>\u4e3a\u5047\u3002 */
    @Test
    void isEmpty_shouldReturnFalseForNonEmpty() {
        assertThat(StrKit.isEmpty("a")).isFalse();
    }

    /** \u9a8c\u8bc1<code>StrKit.isNotBlank("a")</code>\u4e3a\u771f\uff0c<code>StrKit.isNotBlank(null)</code>\u4e3a\u5047\uff0c<code>StrKit.isNotBlank(" ")</code>\u4e3a\u5047\u3002 */
    @Test
    void isNotBlank_shouldBeNegationOfIsBlank() {
        assertThat(StrKit.isNotBlank("a")).isTrue();
        assertThat(StrKit.isNotBlank(null)).isFalse();
        assertThat(StrKit.isNotBlank("  ")).isFalse();
    }

    /** \u9a8c\u8bc1<code>StrKit.isNotEmpty("a")</code>\u4e3a\u771f\uff0c<code>StrKit.isNotEmpty(null)</code>\u4e3a\u5047\uff0c<code>StrKit.isNotEmpty(" ")</code>\u4e3a\u771f\u3002 */
    @Test
    void isNotEmpty_shouldBeNegationOfIsEmpty() {
        assertThat(StrKit.isNotEmpty("a")).isTrue();
        assertThat(StrKit.isNotEmpty(null)).isFalse();
        assertThat(StrKit.isNotEmpty("  ")).isTrue();
    }

}
