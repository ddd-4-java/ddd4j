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

/**
 * String constant pool adapted from MyBatis-Plus {@code StringPool}.
 *
 * <p>Constants are compile-time values and are used to avoid scattering
 * repeated protocol, file suffix, expression, and query literals.</p>
 */
public interface StrPool {

    String AMPERSAND = "&";
    String AND = "and";
    String AT = "@";
    String ASTERISK = "*";
    String STAR = ASTERISK;
    String BACK_SLASH = "\\";
    String COLON = ":";
    String COMMA = ",";
    String DASH = "-";
    String DDD4J_PREFIX = "ddd4j.";
    String DOLLAR = "$";
    String DOT = ".";
    String DOTDOT = "..";
    String DOT_CLASS = ".class";
    String DOT_JAVA = ".java";
    String DOT_XML = ".xml";
    String EMPTY = "";
    String EQUALS = "=";
    String NOT_EQUALS = "<>";
    String LIKE = "LIKE";
    String LIKE_LEFT = "LIKE_LEFT";
    String LIKE_RIGHT = "LIKE_RIGHT";
    String NOT_LIKE = "NOT_LIKE";
    String GT = ">";
    String GE = ">=";
    String LT = "<";
    String LE = "<=";
    String IN = "IN";
    String NOT_IN = "NOT_IN";
    String IS_NULL = "IS_NULL";
    String IS_NOT_NULL = "IS_NOT_NULL";
    String ASC = "ASC";
    String DESC = "DESC";
    String FALSE = "false";
    String SLASH = "/";
    String HASH = "#";
    String HAT = "^";
    String LEFT_BRACE = "{";
String LEFT_BRACKET = "(";
    String LEFT_CHEV = "<";
    String DOT_NEWLINE = ",\n";
    String MS = "ms";
    String NEWLINE = "\n";
    String N = "n";
    String NO = "no";
    String NULL = "null";
    String NUM = "NUM";
    String OFF = "off";
    String ON = "on";
    String PERCENT = "%";
    String PIPE = "|";
    String PLUS = "+";
    String QUESTION_MARK = "?";
    String EXCLAMATION_MARK = "!";
    String QUOTE = "\"";
    String RETURN = "\r";
    String TAB = "\t";
    String RIGHT_BRACE = "}";
    String RIGHT_BRACKET = ")";
    String RIGHT_CHEV = ">";
    String SEMICOLON = ";";
    String SINGLE_QUOTE = "'";
    String BACKTICK = "`";
    String SPACE = " ";
    String SQL = "sql";
    String TILDA = "~";
    String LEFT_SQ_BRACKET = "[";
    String RIGHT_SQ_BRACKET = "]";
    String TRUE = "true";
    String UNDERSCORE = "_";
    String UTF_8 = "UTF-8";
    String US_ASCII = "US-ASCII";
    String ISO_8859_1 = "ISO-8859-1";
    String Y = "y";
    String YES = "yes";
    String ONE = "1";
    String ZERO = "0";
    String DOLLAR_LEFT_BRACE = "${";
    String HASH_LEFT_BRACE = "#{";
    String CRLF = "\r\n";

    String HTML_NBSP = "&nbsp;";
    String HTML_AMP = "&amp";
    String HTML_QUOTE = "&quot;";
    String HTML_LT = "&lt;";
    String HTML_GT = "&gt;";

    // ---------------------------------------------------------------- array

    String[] EMPTY_ARRAY = new String[0];

    byte[] BYTES_NEW_LINE = StrPool.NEWLINE.getBytes(java.nio.charset.StandardCharsets.UTF_8);

}
