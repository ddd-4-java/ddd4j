package io.ddd4j.mq.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TagMatcher} tag 表达式单测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class TagMatcherTest {

    /** \u9a8c\u8bc1<code>TagMatcher.match("A", "*")</code>\u4e3a\u771f\uff0c<code>TagMatcher.match(null, "*")</code>\u4e3a\u771f\uff0c<code>TagMatcher.match("A", null)</code>\u4e3a\u771f\u3002 */
    @Test
    void wildcardShouldMatchAnyInput() {
        assertTrue(TagMatcher.match("A", "*"));
        assertTrue(TagMatcher.match(null, "*"));
        assertTrue(TagMatcher.match("A", null));
    }

    /** \u9a8c\u8bc1<code>TagMatcher.match("A", "A || B")</code>\u4e3a\u771f\uff0c<code>TagMatcher.match("B", "A||B")</code>\u4e3a\u771f\uff0c<code>TagMatcher.match("C", "A || B")</code>\u4e3a\u5047\u3002 */
    @Test
    void includeShouldMatchOnlyDeclaredTags() {
        assertTrue(TagMatcher.match("A", "A || B"));
        assertTrue(TagMatcher.match("B", "A||B"));
        assertFalse(TagMatcher.match("C", "A || B"));
    }

    /** \u9a8c\u8bc1<code>TagMatcher.match("C", "-C")</code>\u4e3a\u5047\uff0c<code>TagMatcher.match("A", "-C")</code>\u4e3a\u771f\u3002 */
    @Test
    void excludeShouldRejectDeclaredTag() {
        assertFalse(TagMatcher.match("C", "-C"));
        assertTrue(TagMatcher.match("A", "-C"));
    }

    /** \u9a8c\u8bc1<code>TagMatcher.match("A", "* -C")</code>\u4e3a\u771f\uff0c<code>TagMatcher.match("C", "* -C")</code>\u4e3a\u5047\uff0c<code>TagMatcher.match("C", "A || B -C")</code>\u4e3a\u5047\u3002 */
    @Test
    void compoundExpressionShouldApplyExcludeBeforeInclude() {
        assertTrue(TagMatcher.match("A", "* -C"));
        assertFalse(TagMatcher.match("C", "* -C"));
        assertFalse(TagMatcher.match("C", "A || B -C"));
    }

    /** \u9a8c\u8bc1<code>TagMatcher.match(null, "A")</code>\u4e3a\u5047\uff0c<code>TagMatcher.match("", "A")</code>\u4e3a\u5047\uff0c<code>TagMatcher.match("", "-C")</code>\u4e3a\u771f\u3002 */
    @Test
    void emptyInputShouldNotMatchSpecificIncludes() {
        assertFalse(TagMatcher.match(null, "A"));
        assertFalse(TagMatcher.match("", "A"));
        assertTrue(TagMatcher.match("", "-C"));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Arrays.asList()</code>\u3001<code>TagMatcher.findIncludes()</code>\u3001<code>Collections.emptyList()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void findIncludesShouldKeepDeclarationOrder() {
        assertEquals(List.of("A", "B"), List.copyOf(TagMatcher.findIncludes("A || B -C")));
        assertEquals(List.of(), List.copyOf(TagMatcher.findIncludes("* -C")));
    }
}
