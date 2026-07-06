package io.ddd4j.annotation.api;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ApiIdempotentType} enum.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ApiIdempotentTypeTest {

    @Test
    void values_shouldContainTokenAndArgs() {
        assertThat(ApiIdempotentType.values())
                .containsExactlyInAnyOrder(ApiIdempotentType.TOKEN, ApiIdempotentType.ARGS);
    }

    @Test
    void valueOfIgnoreCase_shouldResolveExactName() {
        assertThat(ApiIdempotentType.valueOfIgnoreCase("TOKEN")).isEqualTo(ApiIdempotentType.TOKEN);
        assertThat(ApiIdempotentType.valueOfIgnoreCase("ARGS")).isEqualTo(ApiIdempotentType.ARGS);
    }

    @Test
    void valueOfIgnoreCase_shouldThrowForUnknown() {
        assertThatThrownBy(() -> ApiIdempotentType.valueOfIgnoreCase("UNKNOWN"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void valueOfIgnoreCase_shouldThrowForNull() {
        assertThatThrownBy(() -> ApiIdempotentType.valueOfIgnoreCase(null))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void equals_enum_shouldCompareByName() {
        assertThat(ApiIdempotentType.TOKEN.equals(ApiIdempotentType.TOKEN)).isTrue();
        assertThat(ApiIdempotentType.TOKEN.equals(ApiIdempotentType.ARGS)).isFalse();
    }

    @Test
    void equals_string_shouldCompareIgnoreCaseViaValueOf() {
        assertThat(ApiIdempotentType.TOKEN.equals("TOKEN")).isTrue();
        assertThat(ApiIdempotentType.ARGS.equals("ARGS")).isTrue();
        assertThat(ApiIdempotentType.TOKEN.equals("ARGS")).isFalse();
    }
}
