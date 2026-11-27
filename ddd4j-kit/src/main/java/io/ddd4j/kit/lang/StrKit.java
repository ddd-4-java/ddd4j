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

import cn.hutool.core.util.StrUtil;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * 字符串工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
public class StrKit extends StrUtil {

    /** \u65e0\u6cd5\u5339\u914d\u9996\u5b57\u6bcd\u65f6\u4f7f\u7528\u7684\u5360\u4f4d\u5b57\u7b26\u3002 */
    public static final char unknowChar = '*';
    /** \u8def\u5f84\u89c4\u8303\u5316\u91c7\u7528\u7684\u659c\u6760\u5206\u9694\u7b26\u3002 */
    private static final String FOLDER_SEPARATOR = "/";
    /** Windows \u8def\u5f84\u4e2d\u7684\u53cd\u659c\u6760\u5206\u9694\u7b26\u3002 */
    private static final String WINDOWS_FOLDER_SEPARATOR = "\\";
    /** \u8868\u793a\u8def\u5f84\u56de\u5230\u7236\u76ee\u5f55\u7684\u6807\u8bb0\u3002 */
    private static final String TOP_PATH = "..";
    /** \u8868\u793a\u8def\u5f84\u7559\u5728\u5f53\u524d\u76ee\u5f55\u7684\u6807\u8bb0\u3002 */
    private static final String CURRENT_PATH = ".";
    /** \u6587\u4ef6\u540d\u548c\u6269\u5c55\u540d\u4e4b\u95f4\u7684\u70b9\u53f7\u5206\u9694\u7b26\u3002 */
    private static final char EXTENSION_SEPARATOR = '.';
    /** GB2312 \u6c49\u5b57\u533a\u6bb5\u53ca\u5176\u9996\u5b57\u6bcd\u5bf9\u5e94\u5173\u7cfb\u7684\u8fb9\u754c\u3002 */
    private static final int[] allChineseScope = {1601, 1637, 1833, 2078,
            2274, 2302, 2433, 2594, 2787, 3106, 3212, 3472, 3635, 3722, 3730,
            3858, 4027, 4086, 4390, 4558, 4684, 4925, 5249, 5600,
            Integer.MAX_VALUE};
    /** \u6309\u6c49\u5b57\u533a\u6bb5\u6620\u5c04\u4f7f\u7528\u7684\u82f1\u6587\u5b57\u6bcd\u548c\u672a\u77e5\u5b57\u7b26\u6807\u8bb0\u3002 */
    private static final char[] allEnglishLetter = {'A', 'B', 'C', 'D', 'E',
            'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S',
            'T', 'W', 'X', 'Y', 'Z', unknowChar};
    /*
     * Any number of these characters are considered delimiters between
     * multiple context config paths in a single String value.
     */
    /** \u914d\u7f6e\u4f4d\u7f6e\u5217\u8868\u4f7f\u7528\u7684\u5206\u9694\u7b26\u96c6\u5408\u3002 */
    public static String CONFIG_LOCATION_DELIMITERS = ",; \t\n";

    /*
     * 字串是否为空
     *
     * @param str
     * @return
     */
    /**
     * \u5224\u65ad\u8f93\u5165\u662f\u5426\u4e3a\u7a7a\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean isEmpty(String str) {
        if (Objects.isNull(str)) {
            return true;
        } else if (str.length() == 0) {
            return true;
        } else if ("NULL".equalsIgnoreCase(str)) {
            return true;
        }
        return false;
    }

    /*
     * 该方法不能被删除；可能引起调用者代码报错
     */
    /**
     * \u5224\u65ad\u8f93\u5165\u662f\u5426\u975e\u7a7a\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }


    /*
     * 判断 Null 或 空字符串
     *
     * @param str
     * @return
     */
    /**
     * \u5224\u65ad\u5b57\u7b26\u4e32\u662f\u5426\u4e3a\u7a7a\u6216\u4ec5\u542b\u7a7a\u767d\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean isNull(String str) {
        return Objects.isNull(str) || str.trim().length() == 0;
    }

    //---------------------------------------------------------------------
    // General convenience methods for working with Strings
    //---------------------------------------------------------------------

    /*
     * Check whether the given String is empty.
     * <p>This method accepts any Object as an argument, comparing it to
     * {@code null} and the empty String. As a consequence, this method
     * will never return {@code true} for a non-null non-String object.
     * <p>The Object signature is useful for general attribute handling code
     * that commonly deals with Strings but generally has to iterate over
     * Objects since attributes may e.g. be primitive value objects as well.
     * @param str the candidate String
     * @since 3.2.1
     */
    /**
     * \u5224\u65ad\u8f93\u5165\u662f\u5426\u4e3a\u7a7a\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean isEmpty(Object str) {
        return (Objects.isNull(str) || "".equals(str));
    }

    /*
     * Check that the given CharSequence is neither {@code null} nor of length 0.
     * Note: Will return {@code true} for a CharSequence that purely consists of whitespace.
     * <p><pre class="code">
     * StringUtils.hasLength(null) = false
     * StringUtils.hasLength("") = false
     * StringUtils.hasLength(" ") = true
     * StringUtils.hasLength("Hello") = true
     * </pre>
     * @param str the CharSequence to check (may be {@code null})
     * @return {@code true} if the CharSequence is not null and has length
     * @see #hasText(String)
     */
    /**
     * \u5224\u65ad\u8f93\u5165\u662f\u5426\u6709\u5b57\u7b26\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean hasLength(CharSequence str) {
        return (Objects.nonNull(str) && str.length() > 0);
    }

    /*
     * Check that the given String is neither {@code null} nor of length 0.
     * Note: Will return {@code true} for a String that purely consists of whitespace.
     * @param str the String to check (may be {@code null})
     * @return {@code true} if the String is not null and has length
     * @see #hasLength(CharSequence)
     */
    /**
     * \u5224\u65ad\u8f93\u5165\u662f\u5426\u6709\u5b57\u7b26\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean hasLength(String str) {
        return hasLength((CharSequence) str);
    }

    /*
     * Check whether the given CharSequence has actual text.
     * More specifically, returns {@code true} if the string not {@code null},
     * its length is greater than 0, and it contains at least one non-whitespace character.
     * <p><pre class="code">
     * StringUtils.hasText(null) = false
     * StringUtils.hasText("") = false
     * StringUtils.hasText(" ") = false
     * StringUtils.hasText("12345") = true
     * StringUtils.hasText(" 12345 ") = true
     * </pre>
     * @param str the CharSequence to check (may be {@code null})
     * @return {@code true} if the CharSequence is not {@code null},
     * its length is greater than 0, and it does not contain whitespace only
     * @see Character#isWhitespace
     */
    /**
     * \u5224\u65ad\u8f93\u5165\u662f\u5426\u6709\u975e\u7a7a\u767d\u5b57\u7b26\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean hasText(CharSequence str) {
        if (!hasLength(str)) {
            return false;
        }
        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /*
     * Check whether the given String has actual text.
     * More specifically, returns {@code true} if the string not {@code null},
     * its length is greater than 0, and it contains at least one non-whitespace character.
     * @param str the String to check (may be {@code null})
     * @return {@code true} if the String is not {@code null}, its length is
     * greater than 0, and it does not contain whitespace only
     * @see #hasText(CharSequence)
     */
    /**
     * \u5224\u65ad\u8f93\u5165\u662f\u5426\u6709\u975e\u7a7a\u767d\u5b57\u7b26\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean hasText(String str) {
        return hasText((CharSequence) str);
    }

    /*
     * Check whether the given CharSequence contains any whitespace characters.
     * @param str the CharSequence to check (may be {@code null})
     * @return {@code true} if the CharSequence is not empty and
     * contains at least 1 whitespace character
     * @see Character#isWhitespace
     */
    /**
     * \u5224\u65ad\u8f93\u5165\u662f\u5426\u5305\u542b\u7a7a\u767d\u5b57\u7b26\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean containsWhitespace(CharSequence str) {
        if (!hasLength(str)) {
            return false;
        }
        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /*
     * Check whether the given String contains any whitespace characters.
     * @param str the String to check (may be {@code null})
     * @return {@code true} if the String is not empty and
     * contains at least 1 whitespace character
     * @see #containsWhitespace(CharSequence)
     */
    /**
     * \u5224\u65ad\u8f93\u5165\u662f\u5426\u5305\u542b\u7a7a\u767d\u5b57\u7b26\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean containsWhitespace(String str) {
        return containsWhitespace((CharSequence) str);
    }

    /*
     * Trim leading and trailing whitespace from the given String.
     * @param str the String to check
     * @return the trimmed String
     * @see java.lang.Character#isWhitespace
     */
    /**
     * \u6267\u884c trim whitespace \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String trimWhitespace(String str) {
        if (!hasLength(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(0))) {
            sb.deleteCharAt(0);
        }
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /*
     * Trim <i>all</i> whitespace from the given String:
     * leading, trailing, and in between characters.
     * @param str the String to check
     * @return the trimmed String
     * @see java.lang.Character#isWhitespace
     */
    /**
     * \u6267\u884c trim all whitespace \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String trimAllWhitespace(String str) {
        if (!hasLength(str)) {
            return str;
        }
        int len = str.length();
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /*
     * Trim leading whitespace from the given String.
     * @param str the String to check
     * @return the trimmed String
     * @see java.lang.Character#isWhitespace
     */
    /**
     * \u6267\u884c trim leading whitespace \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String trimLeadingWhitespace(String str) {
        if (!hasLength(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(0))) {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }

    /*
     * Trim trailing whitespace from the given String.
     * @param str the String to check
     * @return the trimmed String
     * @see java.lang.Character#isWhitespace
     */
    /**
     * \u6267\u884c trim trailing whitespace \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String trimTrailingWhitespace(String str) {
        if (!hasLength(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /*
     * Trim all occurrences of the supplied leading character from the given String.
     * @param str the String to check
     * @param leadingCharacter the leading character to be trimmed
     * @return the trimmed String
     */
    /**
     * \u6267\u884c trim leading character \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @param leadingCharacter \u524d\u5bfc\u5b57\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String trimLeadingCharacter(String str, char leadingCharacter) {
        if (!hasLength(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() > 0 && sb.charAt(0) == leadingCharacter) {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }

    /*
     * Trim all occurrences of the supplied trailing character from the given String.
     * @param str the String to check
     * @param trailingCharacter the trailing character to be trimmed
     * @return the trimmed String
     */
    /**
     * \u6267\u884c trim trailing character \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @param trailingCharacter \u5c3e\u968f\u5b57\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String trimTrailingCharacter(String str, char trailingCharacter) {
        if (!hasLength(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == trailingCharacter) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }


    /*
     * Test if the given String starts with the specified prefix,
     * ignoring upper/lower case.
     * @param str the String to check
     * @param prefix the prefix to look for
     * @see java.lang.String#startsWith
     */
    /**
     * \u6267\u884c starts with ignore case \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @param prefix \u524d\u7f00
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean startsWithIgnoreCase(String str, String prefix) {
        if (Objects.isNull(str) || Objects.isNull(prefix)) {
            return false;
        }
        if (str.startsWith(prefix)) {
            return true;
        }
        if (str.length() < prefix.length()) {
            return false;
        }
        String lcStr = str.substring(0, prefix.length()).toLowerCase();
        String lcPrefix = prefix.toLowerCase();
        return lcStr.equals(lcPrefix);
    }

    /*
     * Test if the given String ends with the specified suffix,
     * ignoring upper/lower case.
     * @param str the String to check
     * @param suffix the suffix to look for
     * @see java.lang.String#endsWith
     */
    /**
     * \u6267\u884c ends with ignore case \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @param suffix \u540e\u7f00
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean endsWithIgnoreCase(String str, String suffix) {
        if (Objects.isNull(str) || Objects.isNull(suffix)) {
            return false;
        }
        if (str.endsWith(suffix)) {
            return true;
        }
        if (str.length() < suffix.length()) {
            return false;
        }

        String lcStr = str.substring(str.length() - suffix.length()).toLowerCase();
        String lcSuffix = suffix.toLowerCase();
        return lcStr.equals(lcSuffix);
    }

    /*
     * Test whether the given string matches the given substring
     * at the given index.
     * @param str the original string (or StringBuilder)
     * @param index the index in the original string to start matching against
     * @param substring the substring to match at the given index
     */
    /**
     * \u6267\u884c substring match \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @param index \u8d77\u59cb\u4f4d\u7f6e
     * @param substring \u5f85\u5339\u914d\u5b50\u4e32
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean substringMatch(CharSequence str, int index, CharSequence substring) {
        for (int j = 0; j < substring.length(); j++) {
            int i = index + j;
            if (i >= str.length() || str.charAt(i) != substring.charAt(j)) {
                return false;
            }
        }
        return true;
    }

    /*
     * Count the occurrences of the substring in string s.
     * @param str string to search in. Return 0 if this is null.
     * @param sub string to search for. Return 0 if this is null.
     */
    /**
     * \u6267\u884c count occurrences of \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @param sub \u5f85\u5339\u914d\u5b50\u4e32
     * @return \u8ba1\u7b97\u7ed3\u679c
     */
    public static int countOccurrencesOf(String str, String sub) {
        if (Objects.isNull(str) || Objects.isNull(sub) || str.length() == 0 || sub.length() == 0) {
            return 0;
        }
        int count = 0;
        int pos = 0;
        int idx;
        while ((idx = str.indexOf(sub, pos)) != -1) {
            ++count;
            pos = idx + sub.length();
        }
        return count;
    }

    /*
     * Replace all occurrences of a substring within a string with
     * another string.
     * @param inString String to examine
     * @param oldPattern String to replace
     * @param newPattern String to insert
     * @return a String with the replacements
     */
    /**
     * \u6267\u884c replace \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param inString \u6e90\u5b57\u7b26\u4e32
     * @param oldPattern \u5f85\u66ff\u6362\u6a21\u5f0f
     * @param newPattern \u66ff\u6362\u6587\u672c
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String replace(String inString, String oldPattern, String newPattern) {
        if (!hasLength(inString) || !hasLength(oldPattern) || Objects.isNull(newPattern)) {
            return inString;
        }
        StringBuilder sb = new StringBuilder();
        int pos = 0; // our position in the old string
        int index = inString.indexOf(oldPattern);
        // the index of an occurrence we've found, or -1
        int patLen = oldPattern.length();
        while (index >= 0) {
            sb.append(inString.substring(pos, index));
            sb.append(newPattern);
            pos = index + patLen;
            index = inString.indexOf(oldPattern, pos);
        }
        sb.append(inString.substring(pos));
        // remember to append any characters to the right of a match
        return sb.toString();
    }

    /*
     * Delete all occurrences of the given substring.
     * @param inString the original String
     * @param pattern the pattern to delete all occurrences of
     * @return the resulting String
     */
    /**
     * \u6267\u884c delete \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param inString \u6e90\u5b57\u7b26\u4e32
     * @param pattern \u5339\u914d\u6a21\u5f0f
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String delete(String inString, String pattern) {
        return replace(inString, pattern, "");
    }

    /*
     * Delete any character in a given String.
     * @param inString the original String
     * @param charsToDelete a set of characters to delete.
     * E.g. "az\n" will delete 'a's, 'z's and new lines.
     * @return the resulting String
     */
    /**
     * \u6267\u884c delete any \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param inString \u6e90\u5b57\u7b26\u4e32
     * @param charsToDelete \u5f85\u5220\u9664\u5b57\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String deleteAny(String inString, String charsToDelete) {
        if (!hasLength(inString) || !hasLength(charsToDelete)) {
            return inString;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inString.length(); i++) {
            char c = inString.charAt(i);
            if (charsToDelete.indexOf(c) == -1) {
                sb.append(c);
            }
        }
        return sb.toString();
    }


    //---------------------------------------------------------------------
    // Convenience methods for working with formatted Strings
    //---------------------------------------------------------------------


    /*
     * Unqualify a string qualified by a '.' dot character. For example,
     * "this.name.is.qualified", returns "qualified".
     * @param qualifiedName the qualified name
     */
    /**
     * \u6267\u884c unqualify \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param qualifiedName \u9650\u5b9a\u540d\u79f0
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String unqualify(String qualifiedName) {
        return unqualify(qualifiedName, '.');
    }

    /*
     * Unqualify a string qualified by a separator character. For example,
     * "this:name:is:qualified" returns "qualified" if using a ':' separator.
     * @param qualifiedName the qualified name
     * @param separator the separator
     */
    /**
     * \u6267\u884c unqualify \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param qualifiedName \u9650\u5b9a\u540d\u79f0
     * @param separator \u5206\u9694\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String unqualify(String qualifiedName, char separator) {
        return qualifiedName.substring(qualifiedName.lastIndexOf(separator) + 1);
    }

    /*
     * Capitalize a {@code String}, changing the first letter to
     * upper case as per {@link Character#toUpperCase(char)}.
     * No other letters are changed.
     * @param str the String to capitalize, may be {@code null}
     * @return the capitalized String, {@code null} if null
     */
    /**
     * \u6267\u884c capitalize \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String capitalize(String str) {
        return changeFirstCharacterCase(str, true);
    }

    /*
     * Uncapitalize a {@code String}, changing the first letter to
     * lower case as per {@link Character#toLowerCase(char)}.
     * No other letters are changed.
     * @param str the String to uncapitalize, may be {@code null}
     * @return the uncapitalized String, {@code null} if null
     */
    /**
     * \u6267\u884c uncapitalize \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String uncapitalize(String str) {
        return changeFirstCharacterCase(str, false);
    }

    /**
     * \u8f6c\u6362\u5b57\u7b26\u4e32\u9996\u5b57\u7b26\u7684\u5927\u5c0f\u5199\uff0c\u5176\u4f59\u5b57\u7b26\u4fdd\u6301\u539f\u6837\u3002
     * @param str \u8981\u8f6c\u6362\u7684\u5b57\u7b26\u4e32
     * @param capitalize \u662f\u5426\u628a\u9996\u5b57\u7b26\u8f6c\u6210\u5927\u5199
     * @return \u8f6c\u6362\u7ed3\u679c\uff0c\u7a7a\u5b57\u7b26\u4e32\u6216\u7a7a\u5f15\u7528\u539f\u6837\u8fd4\u56de
     */
    private static String changeFirstCharacterCase(String str, boolean capitalize) {
        if (Objects.isNull(str) || str.length() == 0) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length());
        if (capitalize) {
            sb.append(Character.toUpperCase(str.charAt(0)));
        } else {
            sb.append(Character.toLowerCase(str.charAt(0)));
        }
        sb.append(str.substring(1));
        return sb.toString();
    }

    /*
     * Extract the filename from the given path,
     * e.g. "mypath/myfile.txt" -> "myfile.txt".
     * @param path the file path (may be {@code null})
     * @return the extracted filename, or {@code null} if none
     */
    /**
     * \u6267\u884c get filename \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param path \u8def\u5f84
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String getFilename(String path) {
        if (Objects.isNull(path)) {
            return null;
        }
        int separatorIndex = path.lastIndexOf(FOLDER_SEPARATOR);
        return (separatorIndex != -1 ? path.substring(separatorIndex + 1) : path);
    }

    /*
     * Extract the filename extension from the given path,
     * e.g. "mypath/myfile.txt" -> "txt".
     * @param path the file path (may be {@code null})
     * @return the extracted filename extension, or {@code null} if none
     */
    /**
     * \u6267\u884c get filename extension \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param path \u8def\u5f84
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String getFilenameExtension(String path) {
        if (Objects.isNull(path)) {
            return null;
        }
        int extIndex = path.lastIndexOf(EXTENSION_SEPARATOR);
        if (extIndex == -1) {
            return null;
        }
        int folderIndex = path.lastIndexOf(FOLDER_SEPARATOR);
        if (folderIndex > extIndex) {
            return null;
        }
        return path.substring(extIndex + 1);
    }

    /*
     * Strip the filename extension from the given path,
     * e.g. "mypath/myfile.txt" -> "mypath/myfile".
     * @param path the file path (may be {@code null})
     * @return the path with stripped filename extension,
     * or {@code null} if none
     */
    /**
     * \u6267\u884c strip filename extension \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param path \u8def\u5f84
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String stripFilenameExtension(String path) {
        if (Objects.isNull(path)) {
            return null;
        }
        int extIndex = path.lastIndexOf(EXTENSION_SEPARATOR);
        if (extIndex == -1) {
            return path;
        }
        int folderIndex = path.lastIndexOf(FOLDER_SEPARATOR);
        if (folderIndex > extIndex) {
            return path;
        }
        return path.substring(0, extIndex);
    }

    /*
     * Apply the given relative path to the given path,
     * assuming standard Java folder separation (i.e. "/" separators).
     * @param path the path to start from (usually a full file path)
     * @param relativePath the relative path to apply
     * (relative to the full file path above)
     * @return the full file path that results from applying the relative path
     */
    /**
     * \u6267\u884c apply relative path \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param path \u8def\u5f84
     * @param relativePath \u76f8\u5bf9\u8def\u5f84
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String applyRelativePath(String path, String relativePath) {
        int separatorIndex = path.lastIndexOf(FOLDER_SEPARATOR);
        if (separatorIndex != -1) {
            String newPath = path.substring(0, separatorIndex);
            if (!relativePath.startsWith(FOLDER_SEPARATOR)) {
                newPath += FOLDER_SEPARATOR;
            }
            return newPath + relativePath;
        } else {
            return relativePath;
        }
    }

    /*
     * Normalize the path by suppressing sequences like "path/.." and
     * inner simple dots.
     * <p>The result is convenient for path comparison. For other uses,
     * notice that Windows separators ("\") are replaced by simple slashes.
     * @param path the original path
     * @return the normalized path
     */
    /**
     * \u6267\u884c clean path \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param path \u8def\u5f84
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String cleanPath(String path) {
        if (Objects.isNull(path)) {
            return null;
        }
        String pathToUse = replace(path, WINDOWS_FOLDER_SEPARATOR, FOLDER_SEPARATOR);

        // Strip prefix from path to analyze, to not treat it as part of the
        // first path element. This is necessary to correctly parse paths like
        // "file:core/../core/io/Resource.class", where the ".." should just
        // strip the first "core" directory while keeping the "file:" prefix.
        int prefixIndex = pathToUse.indexOf(":");
        String prefix = "";
        if (prefixIndex != -1) {
            prefix = pathToUse.substring(0, prefixIndex + 1);
            if (prefix.contains("/")) {
                prefix = "";
            } else {
                pathToUse = pathToUse.substring(prefixIndex + 1);
            }
        }
        if (pathToUse.startsWith(FOLDER_SEPARATOR)) {
            prefix = prefix + FOLDER_SEPARATOR;
            pathToUse = pathToUse.substring(1);
        }

        String[] pathArray = delimitedListToStringArray(pathToUse, FOLDER_SEPARATOR);
        List<String> pathElements = new LinkedList<>();
        int tops = 0;

        for (int i = pathArray.length - 1; i >= 0; i--) {
            String element = pathArray[i];
            if (CURRENT_PATH.equals(element)) {
                // Points to current directory - drop it.
            } else if (TOP_PATH.equals(element)) {
                // Registering top path found.
                tops++;
            } else {
                if (tops > 0) {
                    // Merging path element with element corresponding to top path.
                    tops--;
                } else {
                    // Normal path element found.
                    pathElements.add(0, element);
                }
            }
        }

        // Remaining top paths need to be retained.
        for (int i = 0; i < tops; i++) {
            pathElements.add(0, TOP_PATH);
        }

        return prefix + collectionToDelimitedString(pathElements, FOLDER_SEPARATOR);
    }

    /*
     * Compare two paths after normalization of them.
     * @param path1 first path for comparison
     * @param path2 second path for comparison
     * @return whether the two paths are equivalent after normalization
     */
    /**
     * \u6267\u884c path equals \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param path1 \u7b2c\u4e00\u4e2a\u8def\u5f84
     * @param path2 \u7b2c\u4e8c\u4e2a\u8def\u5f84
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    public static boolean pathEquals(String path1, String path2) {
        return cleanPath(path1).equals(cleanPath(path2));
    }

    /*
     * Parse the given {@code localeString} value into a {@link Locale}.
     * <p>This is the inverse operation of {@link Locale#toString Locale's toString}.
     * @param localeString the locale String, following {@code Locale's}
     * {@code toString()} format ("en", "en_UK", etc);
     * also accepts spaces as separators, as an alternative to underscores
     * @return a corresponding {@code Locale} instance
     * @throws IllegalArgumentException in case of an invalid locale specification
     */
    /**
     * \u6267\u884c parse locale string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param localeString \u8bed\u8a00\u533a\u57df\u6587\u672c
     * @return \u89e3\u6790\u540e\u7684\u8bed\u8a00\u533a\u57df
     */
    public static Locale parseLocaleString(String localeString) {
        String[] parts = tokenizeToStringArray(localeString, "_ ", false, false);
        String language = (parts.length > 0 ? parts[0] : "");
        String country = (parts.length > 1 ? parts[1] : "");
        validateLocalePart(language);
        validateLocalePart(country);
        String variant = "";
        if (parts.length > 2) {
            // There is definitely a variant, and it is everything after the country
            // code sans the separator between the country code and the variant.
            int endIndexOfCountryCode = localeString.indexOf(country, language.length()) + country.length();
            // Strip off any leading '_' and whitespace, what's left is the variant.
            variant = trimLeadingWhitespace(localeString.substring(endIndexOfCountryCode));
            if (variant.startsWith("_")) {
                variant = trimLeadingCharacter(variant, '_');
            }
        }
        return (language.length() > 0 ? new Locale(language, country, variant) : null);
    }

    /**
     * \u8981\u6c42\u533a\u57df\u4ee3\u7801\u7247\u6bb5\u4ec5\u542b\u5b57\u6bcd\u3001\u6570\u5b57\u3001\u4e0b\u5212\u7ebf\u6216\u7a7a\u683c\u3002
     * @param localePart \u5f85\u68c0\u67e5\u7684\u533a\u57df\u4ee3\u7801\u7247\u6bb5
     * @throws IllegalArgumentException \u7247\u6bb5\u542b\u6709\u4e0d\u652f\u6301\u7684\u5b57\u7b26
     */
    private static void validateLocalePart(String localePart) {
        for (int i = 0; i < localePart.length(); i++) {
            char ch = localePart.charAt(i);
            if (ch != '_' && ch != ' ' && !Character.isLetterOrDigit(ch)) {
                throw new IllegalArgumentException(
                        "Locale part \"" + localePart + "\" contains invalid characters");
            }
        }
    }

    /*
     * Determine the RFC 3066 compliant language tag,
     * as used for the HTTP "Accept-Language" header.
     * @param locale the Locale to transform to a language tag
     * @return the RFC 3066 compliant language tag as String
     */
    /**
     * \u6267\u884c to language tag \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param locale \u8bed\u8a00\u533a\u57df
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String toLanguageTag(Locale locale) {
        return locale.getLanguage() + (hasText(locale.getCountry()) ? "-" + locale.getCountry() : "");
    }

    /*
     * Parse the given {@code timeZoneString} value into a {@link TimeZone}.
     * @param timeZoneString the time zone String, following {@link TimeZone#getTimeZone(String)}
     * but throwing {@link IllegalArgumentException} in case of an invalid time zone specification
     * @return a corresponding {@link TimeZone} instance
     * @throws IllegalArgumentException in case of an invalid time zone specification
     */
    /**
     * \u6267\u884c parse time zone string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param timeZoneString \u65f6\u533a\u6587\u672c
     * @return \u89e3\u6790\u540e\u7684\u65f6\u533a
     */
    public static TimeZone parseTimeZoneString(String timeZoneString) {
        TimeZone timeZone = TimeZone.getTimeZone(timeZoneString);
        if ("GMT".equals(timeZone.getID()) && !timeZoneString.startsWith("GMT")) {
            // We don't want that GMT fallback...
            throw new IllegalArgumentException("Invalid time zone specification '" + timeZoneString + "'");
        }
        return timeZone;
    }


    //---------------------------------------------------------------------
    // Convenience methods for working with String arrays
    //---------------------------------------------------------------------

    /*
     * Append the given String to the given String array, returning a new array
     * consisting of the input array contents plus the given String.
     * @param array the array to append to (can be {@code null})
     * @param str the String to append
     * @return the new array (never {@code null})
     */
    /**
     * \u6267\u884c add string to array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param array \u6e90\u6570\u7ec4
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] addStringToArray(String[] array, String str) {
        if (ObjectUtils.isEmpty(array)) {
            return new String[]{str};
        }
        String[] newArr = new String[array.length + 1];
        System.arraycopy(array, 0, newArr, 0, array.length);
        newArr[array.length] = str;
        return newArr;
    }

    /*
     * Concatenate the given String arrays into one,
     * with overlapping array elements included twice.
     * <p>The order of elements in the original arrays is preserved.
     * @param array1 the first array (can be {@code null})
     * @param array2 the second array (can be {@code null})
     * @return the new array ({@code null} if both given arrays were {@code null})
     */
    /**
     * \u6267\u884c concatenate string arrays \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param array1 \u7b2c\u4e00\u4e2a\u6570\u7ec4
     * @param array2 \u7b2c\u4e8c\u4e2a\u6570\u7ec4
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] concatenateStringArrays(String[] array1, String[] array2) {
        if (ObjectUtils.isEmpty(array1)) {
            return array2;
        }
        if (ObjectUtils.isEmpty(array2)) {
            return array1;
        }
        String[] newArr = new String[array1.length + array2.length];
        System.arraycopy(array1, 0, newArr, 0, array1.length);
        System.arraycopy(array2, 0, newArr, array1.length, array2.length);
        return newArr;
    }

    /*
     * Merge the given String arrays into one, with overlapping
     * array elements only included once.
     * <p>The order of elements in the original arrays is preserved
     * (with the exception of overlapping elements, which are only
     * included on their first occurrence).
     * @param array1 the first array (can be {@code null})
     * @param array2 the second array (can be {@code null})
     * @return the new array ({@code null} if both given arrays were {@code null})
     */
    /**
     * \u6267\u884c merge string arrays \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param array1 \u7b2c\u4e00\u4e2a\u6570\u7ec4
     * @param array2 \u7b2c\u4e8c\u4e2a\u6570\u7ec4
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] mergeStringArrays(String[] array1, String[] array2) {
        if (ObjectUtils.isEmpty(array1)) {
            return array2;
        }
        if (ObjectUtils.isEmpty(array2)) {
            return array1;
        }
        List<String> result = new ArrayList<String>();
        result.addAll(Arrays.asList(array1));
        for (String str : array2) {
            if (!result.contains(str)) {
                result.add(str);
            }
        }
        return toStringArray(result);
    }

    /*
     * Turn given source String array into sorted array.
     * @param array the source array
     * @return the sorted array (never {@code null})
     */
    /**
     * \u6267\u884c sort string array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param array \u6e90\u6570\u7ec4
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] sortStringArray(String[] array) {
        if (ObjectUtils.isEmpty(array)) {
            return new String[0];
        }
        Arrays.sort(array);
        return array;
    }

    /*
     * Copy the given Collection into a String array.
     * The Collection must contain String elements only.
     * @param collection the Collection to copy
     * @return the String array ({@code null} if the passed-in
     * Collection was {@code null})
     */
    /**
     * \u6267\u884c to string array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param collection \u6e90\u96c6\u5408
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] toStringArray(Collection<String> collection) {
        if (Objects.isNull(collection)) {
            return null;
        }
        return collection.toArray(new String[collection.size()]);
    }

    /*
     * Copy the given Enumeration into a String array.
     * The Enumeration must contain String elements only.
     * @param enumeration the Enumeration to copy
     * @return the String array ({@code null} if the passed-in
     * Enumeration was {@code null})
     */
    /**
     * \u6267\u884c to string array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param enumeration \u6e90\u679a\u4e3e
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] toStringArray(Enumeration<String> enumeration) {
        if (Objects.isNull(enumeration)) {
            return null;
        }
        List<String> list = Collections.list(enumeration);
        return list.toArray(new String[list.size()]);
    }

    /*
     * Trim the elements of the given String array,
     * calling {@code String.trim()} on each of them.
     * @param array the original String array
     * @return the resulting array (of the same size) with trimmed elements
     */
    /**
     * \u6267\u884c trim array elements \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param array \u6e90\u6570\u7ec4
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] trimArrayElements(String[] array) {
        if (ObjectUtils.isEmpty(array)) {
            return new String[0];
        }
        String[] result = new String[array.length];
        for (int i = 0; i < array.length; i++) {
            String element = array[i];
            result[i] = (Objects.nonNull(element) ? element.trim() : null);
        }
        return result;
    }

    /*
     * Remove duplicate Strings from the given array.
     * Also sorts the array, as it uses a TreeSet.
     * @param array the String array
     * @return an array without duplicates, in natural sort order
     */
    /**
     * \u6267\u884c remove duplicate strings \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param array \u6e90\u6570\u7ec4
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] removeDuplicateStrings(String[] array) {
        if (ObjectUtils.isEmpty(array)) {
            return array;
        }
        Set<String> set = new TreeSet<String>();
        for (String element : array) {
            set.add(element);
        }
        return toStringArray(set);
    }

    /*
     * Take an array Strings and split each element based on the given delimiter.
     * A {@code Properties} instance is then generated, with the left of the
     * delimiter providing the key, and the right of the delimiter providing the value.
     * <p>Will trim both the key and value before adding them to the
     * {@code Properties} instance.
     * @param array the array to process
     * @param delimiter to split each element using (typically the equals symbol)
     * @return a {@code Properties} instance representing the array contents,
     * or {@code null} if the array to process was null or empty
     */
    /**
     * \u6267\u884c split array elements into properties \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param array \u6e90\u6570\u7ec4
     * @param delimiter \u5206\u9694\u7b26
     * @return \u62c6\u5206\u540e\u7684\u5c5e\u6027\u96c6\u5408
     */
    public static Properties splitArrayElementsIntoProperties(String[] array, String delimiter) {
        return splitArrayElementsIntoProperties(array, delimiter, null);
    }

    /*
     * Take an array Strings and split each element based on the given delimiter.
     * A {@code Properties} instance is then generated, with the left of the
     * delimiter providing the key, and the right of the delimiter providing the value.
     * <p>Will trim both the key and value before adding them to the
     * {@code Properties} instance.
     * @param array the array to process
     * @param delimiter to split each element using (typically the equals symbol)
     * @param charsToDelete one or more characters to remove from each element
     * prior to attempting the split operation (typically the quotation mark
     * symbol), or {@code null} if no removal should occur
     * @return a {@code Properties} instance representing the array contents,
     * or {@code null} if the array to process was {@code null} or empty
     */
    /**
     * \u6267\u884c split array elements into properties \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param array \u6e90\u6570\u7ec4
     * @param delimiter \u5206\u9694\u7b26
     * @param charsToDelete \u5f85\u5220\u9664\u5b57\u7b26
     * @return \u62c6\u5206\u540e\u7684\u5c5e\u6027\u96c6\u5408
     */
    public static Properties splitArrayElementsIntoProperties(
            String[] array, String delimiter, String charsToDelete) {

        if (ObjectUtils.isEmpty(array)) {
            return null;
        }
        Properties result = new Properties();
        for (String element : array) {
            if (Objects.nonNull(charsToDelete)) {
                element = deleteAny(element, charsToDelete);
            }
            String[] splittedElement = split(element, delimiter);
            if (Objects.isNull(splittedElement)) {
                continue;
            }
            result.setProperty(splittedElement[0].trim(), splittedElement[1].trim());
        }
        return result;
    }

    /*
     *
     * @description	：获得以 ",; \t\n"分割的字符数组
     * @author 		： redacted-legacy-family
     * @date 		：Dec 17, 2015 9:07:47 PM
     * @param str
     * @return
     */
    /**
     * \u6267\u884c tokenize to string array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] tokenizeToStringArray(String str) {
        return tokenizeToStringArray(str, CONFIG_LOCATION_DELIMITERS, true, true);
    }

    /*
     * Tokenize the given String into a String array via a StringTokenizer.
     * Trims tokens and omits empty tokens.
     * <p>The given delimiters string is supposed to consist of any number of
     * delimiter characters. Each of those characters can be used to separate
     * tokens. A delimiter is always a single character; for multi-character
     * delimiters, consider using {@code delimitedListToStringArray}
     * @param str the String to tokenize
     * @param delimiters the delimiter characters, assembled as String
     * (each of those characters is individually considered as delimiter).
     * @return an array of the tokens
     * @see java.util.StringTokenizer
     * @see String#trim()
     * @see #delimitedListToStringArray
     */
    /**
     * \u6267\u884c tokenize to string array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @param delimiters \u5206\u9694\u7b26\u96c6\u5408
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] tokenizeToStringArray(String str, String delimiters) {
        return tokenizeToStringArray(str, delimiters, true, true);
    }

    /*
     * Tokenize the given String into a String array via a StringTokenizer.
     * <p>The given delimiters string is supposed to consist of any number of
     * delimiter characters. Each of those characters can be used to separate
     * tokens. A delimiter is always a single character; for multi-character
     * delimiters, consider using {@code delimitedListToStringArray}
     * @param str the String to tokenize
     * @param delimiters the delimiter characters, assembled as String
     * (each of those characters is individually considered as delimiter)
     * @param trimTokens trim the tokens via String's {@code trim}
     * @param ignoreEmptyTokens omit empty tokens from the result array
     * (only applies to tokens that are empty after trimming; StringTokenizer
     * will not consider subsequent delimiters as token in the first place).
     * @return an array of the tokens ({@code null} if the input String
     * was {@code null})
     * @see java.util.StringTokenizer
     * @see String#trim()
     * @see #delimitedListToStringArray
     */
    /**
     * \u6267\u884c tokenize to string array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @param delimiters \u5206\u9694\u7b26\u96c6\u5408
     * @param trimTokens \u65b9\u6cd5\u53c2\u6570
     * @param ignoreEmptyTokens \u65b9\u6cd5\u53c2\u6570
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] tokenizeToStringArray(
            String str, String delimiters, boolean trimTokens, boolean ignoreEmptyTokens) {

        if (Objects.isNull(str)) {
            return null;
        }
        StringTokenizer st = new StringTokenizer(str, delimiters);
        List<String> tokens = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (trimTokens) {
                token = token.trim();
            }
            if (!ignoreEmptyTokens || !token.isEmpty()) {
                tokens.add(token);
            }
        }
        return toStringArray(tokens);
    }

    /*
     * Take a String which is a delimited list and convert it to a String array.
     * <p>A single delimiter can consists of more than one character: It will still
     * be considered as single delimiter string, rather than as bunch of potential
     * delimiter characters - in contrast to {@code tokenizeToStringArray}.
     * @param str the input String
     * @param delimiter the delimiter between elements (this is a single delimiter,
     * rather than a bunch individual delimiter characters)
     * @return an array of the tokens in the list
     * @see #tokenizeToStringArray
     */
    /**
     * \u6267\u884c delimited list to string array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @param delimiter \u5206\u9694\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] delimitedListToStringArray(String str, String delimiter) {
        return delimitedListToStringArray(str, delimiter, null);
    }

    /*
     * Take a String which is a delimited list and convert it to a String array.
     * <p>A single delimiter can consists of more than one character: It will still
     * be considered as single delimiter string, rather than as bunch of potential
     * delimiter characters - in contrast to {@code tokenizeToStringArray}.
     * @param str the input String
     * @param delimiter the delimiter between elements (this is a single delimiter,
     * rather than a bunch individual delimiter characters)
     * @param charsToDelete a set of characters to delete. Useful for deleting unwanted
     * line breaks: e.g. "\r\n\f" will delete all new lines and line feeds in a String.
     * @return an array of the tokens in the list
     * @see #tokenizeToStringArray
     */
    /**
     * \u6267\u884c delimited list to string array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @param delimiter \u5206\u9694\u7b26
     * @param charsToDelete \u5f85\u5220\u9664\u5b57\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] delimitedListToStringArray(String str, String delimiter, String charsToDelete) {
        if (Objects.isNull(str)) {
            return new String[0];
        }
        if (Objects.isNull(delimiter)) {
            return new String[]{str};
        }
        List<String> result = new ArrayList<String>();
        if ("".equals(delimiter)) {
            for (int i = 0; i < str.length(); i++) {
                result.add(deleteAny(str.substring(i, i + 1), charsToDelete));
            }
        } else {
            int pos = 0;
            int delPos;
            while ((delPos = str.indexOf(delimiter, pos)) != -1) {
                result.add(deleteAny(str.substring(pos, delPos), charsToDelete));
                pos = delPos + delimiter.length();
            }
            if (str.length() > 0 && pos <= str.length()) {
                // Add rest of String, but not in case of empty input.
                result.add(deleteAny(str.substring(pos), charsToDelete));
            }
        }
        return toStringArray(result);
    }

    /*
     * Convert a CSV list into an array of Strings.
     * @param str the input String
     * @return an array of Strings, or the empty array in case of empty input
     */
    /**
     * \u6267\u884c comma delimited list to string array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] commaDelimitedListToStringArray(String str) {
        return delimitedListToStringArray(str, ",");
    }

    /*
     * Convenience method to convert a CSV string list to a set.
     * Note that this will suppress duplicates.
     * @param str the input String
     * @return a Set of String entries in the list
     */
    /**
     * \u6267\u884c comma delimited list to set \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u8f6c\u6362\u540e\u7684\u5b57\u7b26\u4e32\u96c6\u5408
     */
    public static Set<String> commaDelimitedListToSet(String str) {
        Set<String> set = new TreeSet<String>();
        String[] tokens = commaDelimitedListToStringArray(str);
        for (String token : tokens) {
            set.add(token);
        }
        return set;
    }

    /*
     * Convenience method to return a Collection as a delimited (e.g. CSV)
     * String. E.g. useful for {@code toString()} implementations.
     * @param coll the Collection to display
     * @param delim the delimiter to use (probably a ",")
     * @param prefix the String to start each element with
     * @param suffix the String to end each element with
     * @return the delimited String
     */
    /**
     * \u6267\u884c collection to delimited string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param coll \u6e90\u96c6\u5408
     * @param delim \u5206\u9694\u7b26
     * @param prefix \u524d\u7f00
     * @param suffix \u540e\u7f00
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String collectionToDelimitedString(Collection<?> coll, String delim, String prefix, String suffix) {
        if (CollKit.isEmpty(coll)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = coll.iterator();
        while (it.hasNext()) {
            sb.append(prefix).append(it.next()).append(suffix);
            if (it.hasNext()) {
                sb.append(delim);
            }
        }
        return sb.toString();
    }

    /*
     * Convenience method to return a Collection as a delimited (e.g. CSV)
     * String. E.g. useful for {@code toString()} implementations.
     * @param coll the Collection to display
     * @param delim the delimiter to use (probably a ",")
     * @return the delimited String
     */
    /**
     * \u6267\u884c collection to delimited string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param coll \u6e90\u96c6\u5408
     * @param delim \u5206\u9694\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String collectionToDelimitedString(Collection<?> coll, String delim) {
        return collectionToDelimitedString(coll, delim, "", "");
    }

    /*
     * Convenience method to return a Collection as a CSV String.
     * E.g. useful for {@code toString()} implementations.
     * @param coll the Collection to display
     * @return the delimited String
     */
    /**
     * \u6267\u884c collection to comma delimited string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param coll \u6e90\u96c6\u5408
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String collectionToCommaDelimitedString(Collection<?> coll) {
        return collectionToDelimitedString(coll, ",");
    }

    /*
     * Convenience method to return a String array as a delimited (e.g. CSV)
     * String. E.g. useful for {@code toString()} implementations.
     * @param arr the array to display
     * @param delim the delimiter to use (probably a ",")
     * @return the delimited String
     */
    /**
     * \u6267\u884c array to delimited string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param arr \u6e90\u6570\u7ec4
     * @param delim \u5206\u9694\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String arrayToDelimitedString(Object[] arr, String delim) {
        if (ObjectUtils.isEmpty(arr)) {
            return "";
        }
        if (arr.length == 1) {
            return Objects.toString(arr[0]);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(delim);
            }
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /*
     * Convenience method to return a String array as a CSV String.
     * E.g. useful for {@code toString()} implementations.
     * @param arr the array to display
     * @return the delimited String
     */
    /**
     * \u6267\u884c array to comma delimited string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param arr \u6e90\u6570\u7ec4
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String arrayToCommaDelimitedString(Object[] arr) {
        return arrayToDelimitedString(arr, ",");
    }

    /*
     * 生成查询字串Map
     *
     * @param str
     * @return
     */
    /**
     * \u6267\u884c get map from query param string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u89e3\u6790\u540e\u7684\u952e\u503c\u6620\u5c04
     */
    public static Map<String, String> getMapFromQueryParamString(String str) {
        Map<String, String> param = new HashMap<String, String>();
        String keyValues[] = str.split("`");
        for (int i = 0; i < keyValues.length; i++) {

        }
        return param;
    }

    /*
     * 全替换
     *
     * @param src
     *            替换字串
     * @param tar
     *            替换目标
     * @param str
     *            主字串
     * @return
     */
    /**
     * \u6267\u884c replace all \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param src \u6e90\u5b57\u7b26\u4e32
     * @param tar \u76ee\u6807\u6587\u672c
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String replaceAll(String src, String tar, String str) {
        return str.replace(src, tar);
    }

    /*
     * 取中文拼音首字符
     *
     * @param str
     * @return
     */
    /**
     * \u53d6\u5f97\u4e2d\u6587\u8bcd\u8bed\u7684\u62fc\u97f3\u9996\u5b57\u6bcd\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u63d0\u53d6\u7684\u5b57\u7b26
     */
    public static char getFirstLetterFromChinessWord(String str) {
        char result = '*';
        String temp = str.toUpperCase();
        try {
            byte[] bytes = temp.getBytes("gbk");
            if (bytes[0] < 128 && bytes[0] > 0) {
                return (char) bytes[0];
            }

            int gbkIndex = 0;

            for (int i = 0; i < bytes.length; i++) {
                bytes[i] -= 160;
            }
            gbkIndex = bytes[0] * 100 + bytes[1];
            for (int i = 0; i < allEnglishLetter.length; i++) {
                if (gbkIndex >= allChineseScope[i]
                        && gbkIndex < allChineseScope[i + 1]) {
                    result = allEnglishLetter[i];
                    break;
                }
            }

        } catch (Exception e) {

        }
        return result;
    }

    /*
     * 字串分割
     *
     * @param src
     * @param letter
     * @return
     */
    /**
     * \u6267\u884c split \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param src \u6e90\u5b57\u7b26\u4e32
     * @param letter \u5206\u9694\u5b57\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] split(String src, char letter) {
        if (Objects.isNull(src)) {
            return new String[0];
        }
        List<String> segments = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < src.length(); i++) {
            if (src.charAt(i) == letter) {
                segments.add(src.substring(start, i));
                start = i + 1;
            }
        }
        segments.add(src.substring(start));
        return segments.toArray(new String[0]);
    }

    /*
     * Split a String at the first occurrence of the delimiter.
     * Does not include the delimiter in the result.
     * @param toSplit the string to split
     * @param delimiter to split the string up with
     * @return a two element array with index 0 being before the delimiter, and
     * index 1 being after the delimiter (neither element includes the delimiter);
     * or {@code null} if the delimiter wasn't found in the given input String
     */
    /**
     * \u6267\u884c split \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param toSplit \u5f85\u62c6\u5206\u5b57\u7b26\u4e32
     * @param delimiter \u5206\u9694\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] split(String toSplit, String delimiter) {
        if (!hasLength(toSplit) || !hasLength(delimiter)) {
            return null;
        }
        int offset = toSplit.indexOf(delimiter);
        if (offset < 0) {
            return null;
        }
        String beforeDelimiter = toSplit.substring(0, offset);
        String afterDelimiter = toSplit.substring(offset + delimiter.length());
        return new String[]{beforeDelimiter, afterDelimiter};
    }

    /**
     * \u6267\u884c splits \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param toSplit \u5f85\u62c6\u5206\u5b57\u7b26\u4e32
     * @param regex \u6b63\u5219\u8868\u8fbe\u5f0f
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] splits(String toSplit, String regex) {
        if (!hasLength(toSplit) || !hasLength(regex)) {
            return new String[]{};
        }
        return toSplit.split(regex);
    }

    /*
     * 去除最后一个字符
     *
     * @param str
     * @return
     */
    /**
     * \u6267\u884c remove last \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String removeLast(String str) {
        if (isNull(str)) {
            return str;
        }
        return str.substring(0, str.length() - 1);

    }

    /*
     * 为字符串的每个元素增加单引号，供sql语句调用 如字符串"123,567"变成"'123','567'"
     *
     * @param str
     * @return
     */
    /**
     * \u6267\u884c add quotation \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String addQuotation(String str) {
        if (Objects.isNull(str)) {
            return null;
        }
        String newStr = "";
        String[] strs = split(str, ",");
        for (int i = 0; i < strs.length; i++) {
            if (i > 0) {
                newStr += ",";
            }
            newStr += "'" + strs[i] + "'";
        }
        return newStr;

    }

    /*
     * list转string数组
     *
     * @param list
     * @return String[]
     */
    /**
     * \u6267\u884c list to array \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param list \u6e90\u5217\u8868
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] listToArray(List<String> list) {
        String[] strs = new String[list.size()];
        return list.toArray(strs);
    }

    /*
     * list转string字符串,以符号分隔
     *
     * @param list
     * @param separator
     * @return String
     */
    /**
     * \u6267\u884c list to string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param list \u6e90\u5217\u8868
     * @param separator \u5206\u9694\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String listToString(List<String> list, String separator) {
        return StringUtils.join(listToArray(list), separator);
    }

    /*
     * 生成随即密码
     *
     * @author 来自网上
     * @param pwd_len
     *            生成的密码的总长度
     * @return 密码的字符串
     */
    /**
     * \u751f\u6210\u6307\u5b9a\u957f\u5ea6\u7684\u968f\u673a\u5b57\u7b26\u4e32\u3002
     * @param pwd_len \u76ee\u6807\u957f\u5ea6
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String genRandomNum(int pwd_len) {
        // 36是因为数组是从0开始的，26个字母+10个数字 + 下划线
        final int maxNum = 37;
        int i; // 生成的随机数
        int count = 0; // 生成的密码的长度
        char[] str = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k',
                'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w',
                'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8',
                '9', '_'};
        StringBuilder pwd = new StringBuilder("");
        Random r = new Random();
        while (count < pwd_len) {
            // 生成随机数，取绝对值，防止生成负数，
            i = Math.abs(r.nextInt(maxNum)); // 生成的数最大为36-1
            if (i >= 0 && i < str.length) {
                pwd.append(str[i]);
                count++;
            }
        }
        return pwd.toString();
    }

    /*
     * 将传入字符串改为非NULL值.
     *
     * @param str
     * @return 传入NULL,返回空"".
     */
    /**
     * \u6267\u884c kill null \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String killNull(String str) {
        if (Objects.isNull(str)) {
            return "";
        }
        return str;
    }

    /*
     *
     * @description: 圆括号()包裹
     * @author : redacted-legacy-family
     * @date : 2014-4-29
     * @time : 下午03:11:57
     * @param source
     * @return
     */
    /**
     * \u6267\u884c parentheses \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param source \u6e90\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String parentheses(String source) {
        return (Objects.nonNull(source) ? "(" + source + ")" : null);
    }

    /*
     *
     * @description: 方括号[]包裹
     * @author : redacted-legacy-family
     * @date : 2014-4-29
     * @time : 下午03:11:57
     * @param source
     * @return
     */
    /**
     * \u6267\u884c brackets \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param source \u6e90\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String brackets(String source) {
        return (Objects.nonNull(source) ? "[" + source + "]" : null);
    }

    /**
     * \u6267\u884c ditto \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param source \u6e90\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String ditto(String source) {
        return (Objects.nonNull(source) ? "\"" + source + "\"" : null);
    }

    /*
     * Quote the given String with single quotes.
     *
     * @param str
     *            the input String (e.g. "myString")
     * @return the quoted String (e.g. "'myString'"), or
     *         <code>null<code> if the input was <code>null</code>
     */
    /**
     * \u6267\u884c quote \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String quote(String str) {
        return (Objects.nonNull(str) ? "'" + str + "'" : null);
    }

    /*
     *
     * @description: 将String集合元素用'包围，并拼接
     * @author : redacted-legacy-family
     * @date : 2014-4-29
     * @time : 下午02:13:09
     * @param list
     * @param separator
     * @return
     */
    /**
     * \u6267\u884c quote \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param array \u6e90\u6570\u7ec4
     * @param separator \u5206\u9694\u7b26
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String quote(String[] array, String separator) {
        if (Objects.nonNull(array) && array.length != 0) {
            String[] last = new String[array.length];
            for (int i = 0; i < array.length; i++) {
                last[i] = quote(array[i]);
            }
            return StringUtils.join(last, separator);
        } else {
            return "";
        }

    }

    /*
     * Turn the given Object into a String with single quotes if it is a String;
     * keeping the Object as-is else.
     *
     * @param obj
     *            the input Object (e.g. "myString")
     * @return the quoted String (e.g. "'myString'"), or the input object as-is
     *         if not a String
     */
    /**
     * \u6267\u884c quote if string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param obj \u5f85\u5904\u7406\u5bf9\u8c61
     * @return \u5904\u7406\u540e\u7684\u5bf9\u8c61
     */
    public static Object quoteIfString(Object obj) {
        return (obj instanceof String ? quote((String) obj) : obj);
    }

    /*
     *
     * @param string
     * @return
     * @description： 把一个字符的非Alpha字符都去掉,String string = "1\r\n1\r\n";-->结果："11";
     * @return: String
     * @method: trimToAlphaString
     * @author: redacted-legacy-family
     * @version: 2010-12-15 下午09:06:02
     */
    /**
     * \u6267\u884c trim to alpha string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param string \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String trimToAlphaString(String string) {
        if (Objects.isNull(string) || string.length() == 0) {
            return "";
        }
        return string.replaceAll("[^\\w]", "");
    }

    /*
     *
     * @param string
     * @return
     * @description： 把一个字符的非Alpha字符都去掉，并返回每个字符的数组,String string =
     *               "1\r\n1\r\n";-->结果：new String[]{"1","1"};
     *
     * @return: String[]
     * @method: trimToAlphaStrings
     * @author: redacted-legacy-family
     * @version: 2010-12-15 下午09:06:31
     */
    /**
     * \u6267\u884c trim to alpha strings \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param string \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32\u6570\u7ec4
     */
    public static String[] trimToAlphaStrings(String string) {
        if (Objects.isNull(string) || string.length() == 0) {
            return new String[0];
        }
        char[] chars = string.replaceAll("[^\\w]", "").toCharArray();
        String[] strs = new String[chars.length];
        for (int i = 0; i < chars.length; i++) {
            strs[i] = String.valueOf(chars[i]);
        }
        return strs;
    }

    /**
     * \u6267\u884c trim to string \u5b57\u7b26\u4e32\u5904\u7406\u3002
     * @param str \u5f85\u5904\u7406\u5b57\u7b26\u4e32
     * @return \u5904\u7406\u540e\u7684\u5b57\u7b26\u4e32
     */
    public static String trimToString(String str) {
        if (Objects.isNull(str) || str.trim().length() == 0) {
            return null;
        } else {
            return str.trim();
        }
    }
}
