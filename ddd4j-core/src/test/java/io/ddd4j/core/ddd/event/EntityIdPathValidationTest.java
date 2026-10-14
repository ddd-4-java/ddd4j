package io.ddd4j.core.ddd.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 三线共用的 EntityIdPath 文本校验契约。 */
class EntityIdPathValidationTest {

    @Test
    void acceptsSerializedPathsIncludingEscapedSeparators() {
        assertThat(EntityIdPath.isValid("String:order-1")).isTrue();
        assertThat(EntityIdPath.isValid("String:order-1/String:item-9")).isTrue();
        assertThat(EntityIdPath.isValid("String:a\\/b")).isTrue();
    }

    @Test
    void rejectsMalformedPathsWithoutThrowing() {
        String[] malformed = {null, "", "   ", "/", "String:", ":order-1", "String:a//b"};
        for (String value : malformed) {
            assertThat(EntityIdPath.isValid(value)).isFalse();
        }
    }

    @Test
    void rejectsNullEntityIdWithStableException() {
        StringEntityId rootId = new StringEntityId("order-1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EntityIdPath(Arrays.asList(rootId, null)))
                .withMessage("Entity identifier path must not contain null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EntityIdPath(rootId, null))
                .withMessage("Entity identifier path must not contain null");
    }

    @Test
    void validationAgreesWithParser() {
        String path = "String:order-1/String:item-9";
        assertThat(EntityIdPath.isValid(path)).isTrue();
        assertThat(EntityIdPath.valueOf(path).asString()).isEqualTo(path);
    }
}
