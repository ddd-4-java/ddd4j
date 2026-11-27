package io.ddd4j.core.ddd.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 三线共用的 EntityIdPath 文本校验契约。 */
class EntityIdPathValidationTest {

    /** \u9a8c\u8bc1<code>EntityIdPath.isValid("String:order-1")</code>\u4e3a\u771f\uff0c<code>EntityIdPath.isValid("String:order-1/String:item-9")</code>\u4e3a\u771f\uff0c<code>EntityIdPath.isValid("String:a\\/b")</code>\u4e3a\u771f\u3002 */
    @Test
    void acceptsSerializedPathsIncludingEscapedSeparators() {
        assertThat(EntityIdPath.isValid("String:order-1")).isTrue();
        assertThat(EntityIdPath.isValid("String:order-1/String:item-9")).isTrue();
        assertThat(EntityIdPath.isValid("String:a\\/b")).isTrue();
    }

    /** \u9a8c\u8bc1<code>EntityIdPath.isValid(value)</code>\u4e3a\u5047\u3002 */
    @Test
    void rejectsMalformedPathsWithoutThrowing() {
        String[] malformed = {null, "", "   ", "/", "String:", ":order-1", "String:a//b"};
        for (String value : malformed) {
            assertThat(EntityIdPath.isValid(value)).isFalse();
        }
    }

    /** \u9a8c\u8bc1<code>new EntityIdPath(Arrays.asList(rootId, null))</code> \u629b\u51fa <code>IllegalArgumentException</code>\uff0c<code>new EntityIdPath(rootId, null)</code> \u629b\u51fa <code>IllegalArgumentException</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>EntityIdPath.isValid(path)</code>\u4e3a\u771f\uff0c<code>EntityIdPath.valueOf(path).asString()</code> \u7b49\u4e8e <code>path</code>\u3002 */
    @Test
    void validationAgreesWithParser() {
        String path = "String:order-1/String:item-9";
        assertThat(EntityIdPath.isValid(path)).isTrue();
        assertThat(EntityIdPath.valueOf(path).asString()).isEqualTo(path);
    }
}
