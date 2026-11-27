package io.ddd4j.core.ddd.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 三线共用的 EntityIdPath 文本校验契约。
 */
class EntityIdPathValidationTest {

    /**
     * 验证<code>EntityIdPath.isValid("String:order-1")</code>为真，<code>EntityIdPath.isValid("String:order-1/String:item-9")</code>为真，<code>EntityIdPath.isValid("String:a\\/b")</code>为真。
     */
    @Test
    void acceptsSerializedPathsIncludingEscapedSeparators() {
        assertThat(EntityIdPath.isValid("String:order-1")).isTrue();
        assertThat(EntityIdPath.isValid("String:order-1/String:item-9")).isTrue();
        assertThat(EntityIdPath.isValid("String:a\\/b")).isTrue();
    }

    /**
     * 验证<code>EntityIdPath.isValid(value)</code>为假。
     */
    @Test
    void rejectsMalformedPathsWithoutThrowing() {
        String[] malformed = {null, "", "   ", "/", "String:", ":order-1", "String:a//b"};
        for (String value : malformed) {
            assertThat(EntityIdPath.isValid(value)).isFalse();
        }
    }

    /**
     * 验证<code>new EntityIdPath(Arrays.asList(rootId, null))</code> 抛出 <code>IllegalArgumentException</code>，<code>new EntityIdPath(rootId, null)</code> 抛出 <code>IllegalArgumentException</code>。
     */
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

    /**
     * 验证<code>EntityIdPath.isValid(path)</code>为真，<code>EntityIdPath.valueOf(path).asString()</code> 等于 <code>path</code>。
     */
    @Test
    void validationAgreesWithParser() {
        String path = "String:order-1/String:item-9";
        assertThat(EntityIdPath.isValid(path)).isTrue();
        assertThat(EntityIdPath.valueOf(path).asString()).isEqualTo(path);
    }
}
