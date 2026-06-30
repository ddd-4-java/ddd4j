package io.ddd4j.mq.registry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MQTagMatcher} tag 表达式单测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MQTagMatcherTest {

    @Test
    void wildcardShouldMatchAnyInput() {
        assertTrue(MQTagMatcher.match("A", "*"));
        assertTrue(MQTagMatcher.match(null, "*"));
        assertTrue(MQTagMatcher.match("A", null));
    }

    @Test
    void includeShouldMatchOnlyDeclaredTags() {
        assertTrue(MQTagMatcher.match("A", "A || B"));
        assertTrue(MQTagMatcher.match("B", "A||B"));
        assertFalse(MQTagMatcher.match("C", "A || B"));
    }

    @Test
    void excludeShouldRejectDeclaredTag() {
        assertFalse(MQTagMatcher.match("C", "-C"));
        assertTrue(MQTagMatcher.match("A", "-C"));
    }

    @Test
    void compoundExpressionShouldApplyExcludeBeforeInclude() {
        assertTrue(MQTagMatcher.match("A", "* -C"));
        assertFalse(MQTagMatcher.match("C", "* -C"));
        assertFalse(MQTagMatcher.match("C", "A || B -C"));
    }

    @Test
    void emptyInputShouldNotMatchSpecificIncludes() {
        assertFalse(MQTagMatcher.match(null, "A"));
        assertFalse(MQTagMatcher.match("", "A"));
        assertTrue(MQTagMatcher.match("", "-C"));
    }

    @Test
    void findIncludesShouldKeepDeclarationOrder() {
        assertEquals(List.of("A", "B"), List.copyOf(MQTagMatcher.findIncludes("A || B -C")));
        assertEquals(List.of(), List.copyOf(MQTagMatcher.findIncludes("* -C")));
    }
}
