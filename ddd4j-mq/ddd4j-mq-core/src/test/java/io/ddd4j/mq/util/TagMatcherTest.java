package io.ddd4j.mq.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TagMatcher} tag 表达式单测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class TagMatcherTest {

    @Test
    void wildcardShouldMatchAnyInput() {
        assertTrue(TagMatcher.match("A", "*"));
        assertTrue(TagMatcher.match(null, "*"));
        assertTrue(TagMatcher.match("A", null));
    }

    @Test
    void includeShouldMatchOnlyDeclaredTags() {
        assertTrue(TagMatcher.match("A", "A || B"));
        assertTrue(TagMatcher.match("B", "A||B"));
        assertFalse(TagMatcher.match("C", "A || B"));
    }

    @Test
    void excludeShouldRejectDeclaredTag() {
        assertFalse(TagMatcher.match("C", "-C"));
        assertTrue(TagMatcher.match("A", "-C"));
    }

    @Test
    void compoundExpressionShouldApplyExcludeBeforeInclude() {
        assertTrue(TagMatcher.match("A", "* -C"));
        assertFalse(TagMatcher.match("C", "* -C"));
        assertFalse(TagMatcher.match("C", "A || B -C"));
    }

    @Test
    void emptyInputShouldNotMatchSpecificIncludes() {
        assertFalse(TagMatcher.match(null, "A"));
        assertFalse(TagMatcher.match("", "A"));
        assertTrue(TagMatcher.match("", "-C"));
    }

    @Test
    void findIncludesShouldKeepDeclarationOrder() {
        assertEquals(Arrays.asList("A", "B"), Collections.unmodifiableList(new ArrayList<>(TagMatcher.findIncludes("A || B -C"))));
        assertEquals(Arrays.asList(), Collections.unmodifiableList(new ArrayList<>(TagMatcher.findIncludes("* -C"))));
    }
}
