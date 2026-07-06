package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StrKit} (delegates to Hutool {@code StrUtil}).
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class StrKitTest {

    @Test
    void isBlank_shouldReturnTrueForNull() {
        assertThat(StrKit.isBlank(null)).isTrue();
    }

    @Test
    void isBlank_shouldReturnTrueForEmptyString() {
        assertThat(StrKit.isBlank("")).isTrue();
    }

    @Test
    void isBlank_shouldReturnTrueForWhitespaceOnly() {
        assertThat(StrKit.isBlank("   ")).isTrue();
        assertThat(StrKit.isBlank("\t\n")).isTrue();
    }

    @Test
    void isBlank_shouldReturnFalseForNonBlank() {
        assertThat(StrKit.isBlank("a")).isFalse();
        assertThat(StrKit.isBlank(" hello ")).isFalse();
    }

    @Test
    void isEmpty_shouldReturnTrueForNull() {
        assertThat(StrKit.isEmpty(null)).isTrue();
    }

    @Test
    void isEmpty_shouldReturnTrueForEmptyString() {
        assertThat(StrKit.isEmpty("")).isTrue();
    }

    @Test
    void isEmpty_shouldReturnFalseForWhitespace() {
        // isEmpty does NOT treat whitespace as empty (unlike isBlank)
        assertThat(StrKit.isEmpty("  ")).isFalse();
    }

    @Test
    void isEmpty_shouldReturnFalseForNonEmpty() {
        assertThat(StrKit.isEmpty("a")).isFalse();
    }

    @Test
    void isNotBlank_shouldBeNegationOfIsBlank() {
        assertThat(StrKit.isNotBlank("a")).isTrue();
        assertThat(StrKit.isNotBlank(null)).isFalse();
        assertThat(StrKit.isNotBlank("  ")).isFalse();
    }

    @Test
    void isNotEmpty_shouldBeNegationOfIsEmpty() {
        assertThat(StrKit.isNotEmpty("a")).isTrue();
        assertThat(StrKit.isNotEmpty(null)).isFalse();
        assertThat(StrKit.isNotEmpty("  ")).isTrue();
    }

    @Test
    void setIsNotBlank_shouldMirrorIsNotBlank() {
        assertThat(StrKit.setIsNotBlank("x")).isTrue();
        assertThat(StrKit.setIsNotBlank("")).isFalse();
    }

    @Test
    void setIsBlank_shouldMirrorIsBlank() {
        assertThat(StrKit.setIsBlank("  ")).isTrue();
        assertThat(StrKit.setIsBlank("x")).isFalse();
    }
}
