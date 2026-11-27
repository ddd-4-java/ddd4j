/*
 * Copyright (c) 2011-2025, baomidou (jobob@qq.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.kit.text;

import java.nio.charset.StandardCharsets;

/**
 * String constant pool adapted from MyBatis-Plus {@code StringPool}.
 *
 * <p>Constants are compile-time values and are used to avoid scattering
 * repeated protocol, file suffix, expression, and query literals.</p>
 */
public interface StrPool {

    /** \u56fa\u5b9a\u6587\u672c {@code &}\u3002 */
    String AMPERSAND = "&";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code and}\u3002 */
    String AND = "and";
    /** \u56fa\u5b9a\u6587\u672c {@code @}\u3002 */
    String AT = "@";
    /** \u56fa\u5b9a\u6587\u672c {@code *}\u3002 */
    String ASTERISK = "*";
    /** \u661f\u53f7\u5e38\u91cf\u7684\u522b\u540d\u3002 */
    String STAR = ASTERISK;
    /** \u56fa\u5b9a\u6587\u672c {@code \\}\u3002 */
    String BACK_SLASH = "\\";
    /** \u56fa\u5b9a\u6587\u672c {@code :}\u3002 */
    String COLON = ":";
    /** \u56fa\u5b9a\u6587\u672c {@code ,}\u3002 */
    String COMMA = ",";
    /** \u56fa\u5b9a\u6587\u672c {@code -}\u3002 */
    String DASH = "-";
    /** \u56fa\u5b9a\u6587\u672c {@code ddd4j.}\u3002 */
    String DDD4J_PREFIX = "ddd4j.";
    /** \u56fa\u5b9a\u6587\u672c {@code $}\u3002 */
    String DOLLAR = "$";
    /** \u56fa\u5b9a\u6587\u672c {@code .}\u3002 */
    String DOT = ".";
    /** \u56fa\u5b9a\u6587\u672c {@code ..}\u3002 */
    String DOTDOT = "..";
    /** \u6587\u4ef6\u6269\u5c55\u540d {@code .class}\u3002 */
    String DOT_CLASS = ".class";
    /** \u6587\u4ef6\u6269\u5c55\u540d {@code .java}\u3002 */
    String DOT_JAVA = ".java";
    /** \u6587\u4ef6\u6269\u5c55\u540d {@code .xml}\u3002 */
    String DOT_XML = ".xml";
    /** \u56fa\u5b9a\u6587\u672c {@code}\u3002 */
    String EMPTY = "";
    /** \u56fa\u5b9a\u6587\u672c {@code =}\u3002 */
    String EQUALS = "=";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code <>}\u3002 */
    String NOT_EQUALS = "<>";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code LIKE}\u3002 */
    String LIKE = "LIKE";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code LIKE_LEFT}\u3002 */
    String LIKE_LEFT = "LIKE_LEFT";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code LIKE_RIGHT}\u3002 */
    String LIKE_RIGHT = "LIKE_RIGHT";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code NOT_LIKE}\u3002 */
    String NOT_LIKE = "NOT_LIKE";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code >}\u3002 */
    String GT = ">";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code >=}\u3002 */
    String GE = ">=";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code <}\u3002 */
    String LT = "<";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code <=}\u3002 */
    String LE = "<=";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code IN}\u3002 */
    String IN = "IN";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code NOT_IN}\u3002 */
    String NOT_IN = "NOT_IN";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code IS_NULL}\u3002 */
    String IS_NULL = "IS_NULL";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code IS_NOT_NULL}\u3002 */
    String IS_NOT_NULL = "IS_NOT_NULL";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code ASC}\u3002 */
    String ASC = "ASC";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code DESC}\u3002 */
    String DESC = "DESC";
    /** \u56fa\u5b9a\u6587\u672c {@code false}\u3002 */
    String FALSE = "false";
    /** \u56fa\u5b9a\u6587\u672c {@code /}\u3002 */
    String SLASH = "/";
    /** \u56fa\u5b9a\u6587\u672c {@code #}\u3002 */
    String HASH = "#";
    /** \u56fa\u5b9a\u6587\u672c {@code ^}\u3002 */
    String HAT = "^";
    /** \u56fa\u5b9a\u6587\u672c <code>&#123;</code>\u3002 */
    String LEFT_BRACE = "{";
    /** \u56fa\u5b9a\u6587\u672c {@code (}\u3002 */
    String LEFT_BRACKET = "(";
    /** \u56fa\u5b9a\u6587\u672c {@code <}\u3002 */
    String LEFT_CHEV = "<";
    /** \u6587\u4ef6\u6269\u5c55\u540d {@code ,\n}\u3002 */
    String DOT_NEWLINE = ",\n";
    /** \u56fa\u5b9a\u6587\u672c {@code ms}\u3002 */
    String MS = "ms";
    /** \u56fa\u5b9a\u6587\u672c {@code \n}\u3002 */
    String NEWLINE = "\n";
    /** \u56fa\u5b9a\u6587\u672c {@code n}\u3002 */
    String N = "n";
    /** \u56fa\u5b9a\u6587\u672c {@code no}\u3002 */
    String NO = "no";
    /** \u56fa\u5b9a\u6587\u672c {@code null}\u3002 */
    String NULL = "null";
    /** \u56fa\u5b9a\u6587\u672c {@code NUM}\u3002 */
    String NUM = "NUM";
    /** \u56fa\u5b9a\u6587\u672c {@code off}\u3002 */
    String OFF = "off";
    /** \u56fa\u5b9a\u6587\u672c {@code on}\u3002 */
    String ON = "on";
    /** \u56fa\u5b9a\u6587\u672c {@code %}\u3002 */
    String PERCENT = "%";
    /** \u56fa\u5b9a\u6587\u672c {@code |}\u3002 */
    String PIPE = "|";
    /** \u56fa\u5b9a\u6587\u672c {@code +}\u3002 */
    String PLUS = "+";
    /** \u56fa\u5b9a\u6587\u672c {@code ?}\u3002 */
    String QUESTION_MARK = "?";
    /** \u56fa\u5b9a\u6587\u672c {@code !}\u3002 */
    String EXCLAMATION_MARK = "!";
    /** \u56fa\u5b9a\u6587\u672c {@code \"}\u3002 */
    String QUOTE = "\"";
    /** \u56fa\u5b9a\u6587\u672c {@code \r}\u3002 */
    String RETURN = "\r";
    /** \u56fa\u5b9a\u6587\u672c {@code \t}\u3002 */
    String TAB = "\t";
    /** \u56fa\u5b9a\u6587\u672c {@code}}\u3002 */
    String RIGHT_BRACE = "}";
    /** \u56fa\u5b9a\u6587\u672c {@code )}\u3002 */
    String RIGHT_BRACKET = ")";
    /** \u56fa\u5b9a\u6587\u672c {@code >}\u3002 */
    String RIGHT_CHEV = ">";
    /** \u56fa\u5b9a\u6587\u672c {@code ;}\u3002 */
    String SEMICOLON = ";";
    /** \u56fa\u5b9a\u6587\u672c {@code '}\u3002 */
    String SINGLE_QUOTE = "'";
    /** \u56fa\u5b9a\u6587\u672c {@code `}\u3002 */
    String BACKTICK = "`";
    /** \u56fa\u5b9a\u6587\u672c {@code }\u3002 */
    String SPACE = " ";
    /** \u67e5\u8be2\u8868\u8fbe\u5f0f\u6587\u672c {@code sql}\u3002 */
    String SQL = "sql";
    /** \u56fa\u5b9a\u6587\u672c {@code ~}\u3002 */
    String TILDA = "~";
    /** \u56fa\u5b9a\u6587\u672c {@code [}\u3002 */
    String LEFT_SQ_BRACKET = "[";
    /** \u56fa\u5b9a\u6587\u672c {@code ]}\u3002 */
    String RIGHT_SQ_BRACKET = "]";
    /** \u56fa\u5b9a\u6587\u672c {@code true}\u3002 */
    String TRUE = "true";
    /** \u56fa\u5b9a\u6587\u672c {@code _}\u3002 */
    String UNDERSCORE = "_";
    /** \u5b57\u7b26\u96c6\u540d\u79f0 {@code UTF-8}\u3002 */
    String UTF_8 = "UTF-8";
    /** \u5b57\u7b26\u96c6\u540d\u79f0 {@code US-ASCII}\u3002 */
    String US_ASCII = "US-ASCII";
    /** \u5b57\u7b26\u96c6\u540d\u79f0 {@code ISO-8859-1}\u3002 */
    String ISO_8859_1 = "ISO-8859-1";
    /** \u56fa\u5b9a\u6587\u672c {@code y}\u3002 */
    String Y = "y";
    /** \u56fa\u5b9a\u6587\u672c {@code yes}\u3002 */
    String YES = "yes";
    /** \u56fa\u5b9a\u6587\u672c {@code 1}\u3002 */
    String ONE = "1";
    /** \u56fa\u5b9a\u6587\u672c {@code 0}\u3002 */
    String ZERO = "0";
    /** \u56fa\u5b9a\u6587\u672c <code>$&#123;</code>\u3002 */
    String DOLLAR_LEFT_BRACE = "${";
    /** \u56fa\u5b9a\u6587\u672c <code>#&#123;</code>\u3002 */
    String HASH_LEFT_BRACE = "#{";
    /** \u56fa\u5b9a\u6587\u672c {@code \r\n}\u3002 */
    String CRLF = "\r\n";

    /** HTML \u5b9e\u4f53\u6587\u672c {@code &nbsp;}\u3002 */
    String HTML_NBSP = "&nbsp;";
    /** HTML \u5b9e\u4f53\u6587\u672c {@code &amp}\u3002 */
    String HTML_AMP = "&amp";
    /** HTML \u5b9e\u4f53\u6587\u672c {@code &quot;}\u3002 */
    String HTML_QUOTE = "&quot;";
    /** HTML \u5b9e\u4f53\u6587\u672c {@code &lt;}\u3002 */
    String HTML_LT = "&lt;";
    /** HTML \u5b9e\u4f53\u6587\u672c {@code &gt;}\u3002 */
    String HTML_GT = "&gt;";

    // ---------------------------------------------------------------- array

    /** \u5171\u4eab\u7684\u7a7a\u5b57\u7b26\u4e32\u6570\u7ec4\u3002 */
    String[] EMPTY_ARRAY = new String[0];

    /** \u6362\u884c\u7b26\u7684\u5b57\u8282\u8868\u793a\u3002 */
    byte[] BYTES_NEW_LINE = StrPool.NEWLINE.getBytes(StandardCharsets.UTF_8);

}
