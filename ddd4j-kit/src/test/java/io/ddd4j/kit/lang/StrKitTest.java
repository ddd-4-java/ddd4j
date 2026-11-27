/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    /** \u9a8c\u8bc1\u6309\u5b57\u9762\u66ff\u6362\u5168\u90e8\u5339\u914d\uff0c\u5305\u542b\u4e2d\u6587\u548c\u8fde\u7eed\u91cd\u590d\u6587\u672c\u3002 */
    @Test
    void replaceAll_shouldReplaceEachLiteralOccurrenceWithoutDamagingUnicode() {
        assertThat(StrKit.replaceAll("中", "X", "甲中乙中")).isEqualTo("甲X乙X");
        assertThat(StrKit.replaceAll("aa", "X", "aaaa")).isEqualTo("XX");
        assertThat(StrKit.replaceAll(".", "X", "a.b")).isEqualTo("aXb");
    }

    /** \u9a8c\u8bc1<code>StrKit.split("\u7532|\u4e59|\u4e19", '|')</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"\u7532"</code>\u3001<code>"\u4e59"</code>\u3001<code>"\u4e19"</code>\uff0c<code>StrKit.split("\u7532\u25c7\u4e59", '\u25c7')</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"\u7532"</code>\u3001<code>"\u4e59"</code>\uff0c<code>StrKit.split(null, '|')</code>\u4e3a\u7a7a\u3002 */
    @Test
    void splitByCharacter_shouldPreserveUnicodeAndFinalSegment() {
        assertThat(StrKit.split("甲|乙|丙", '|')).containsExactly("甲", "乙", "丙");
        assertThat(StrKit.split("甲◇乙", '◇')).containsExactly("甲", "乙");
        assertThat(StrKit.split("|甲||", '|')).containsExactly("", "甲", "", "");
        assertThat(StrKit.split("原文", '|')).containsExactly("原文");
        assertThat(StrKit.split(null, '|')).isEmpty();
    }

    /** \u9a8c\u8bc1<code>StrKit.trimWhitespace(" ")</code>\u4e3a\u7a7a\uff0c<code>StrKit.trimWhitespace(" a b ")</code> \u7b49\u4e8e <code>"a b"</code>\uff0c<code>StrKit.trimTrailingCharacter("///", '/')</code>\u4e3a\u7a7a\u3002 */
    @Test
    void trimMethods_shouldHandleEmptyAndRepeatedBoundaryCharacters() {
        assertThat(StrKit.trimWhitespace("   ")).isEmpty();
        assertThat(StrKit.trimWhitespace("  a b  ")).isEqualTo("a b");
        assertThat(StrKit.trimAllWhitespace(" a b ")).isEqualTo("ab");
        assertThat(StrKit.trimLeadingWhitespace("  a  ")).isEqualTo("a  ");
        assertThat(StrKit.trimTrailingWhitespace("  a  ")).isEqualTo("  a");
        assertThat(StrKit.trimLeadingCharacter("///a/", '/')).isEqualTo("a/");
        assertThat(StrKit.trimTrailingCharacter("/a///", '/')).isEqualTo("/a");
        assertThat(StrKit.trimLeadingCharacter("///", '/')).isEmpty();
        assertThat(StrKit.trimTrailingCharacter("///", '/')).isEmpty();
    }

}
