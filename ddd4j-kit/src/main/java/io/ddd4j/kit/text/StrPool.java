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

    /** 固定文本 {@code &}。 */
    String AMPERSAND = "&";
    /** 查询表达式文本 {@code and}。 */
    String AND = "and";
    /** 固定文本 {@code @}。 */
    String AT = "@";
    /** 固定文本 {@code *}。 */
    String ASTERISK = "*";
    /** 星号常量的别名。 */
    String STAR = ASTERISK;
    /** 固定文本 {@code \\}。 */
    String BACK_SLASH = "\\";
    /** 固定文本 {@code :}。 */
    String COLON = ":";
    /** 固定文本 {@code ,}。 */
    String COMMA = ",";
    /** 固定文本 {@code -}。 */
    String DASH = "-";
    /** 固定文本 {@code ddd4j.}。 */
    String DDD4J_PREFIX = "ddd4j.";
    /** 固定文本 {@code $}。 */
    String DOLLAR = "$";
    /** 固定文本 {@code .}。 */
    String DOT = ".";
    /** 固定文本 {@code ..}。 */
    String DOTDOT = "..";
    /** 文件扩展名 {@code .class}。 */
    String DOT_CLASS = ".class";
    /** 文件扩展名 {@code .java}。 */
    String DOT_JAVA = ".java";
    /** 文件扩展名 {@code .xml}。 */
    String DOT_XML = ".xml";
    /** 固定文本 {@code }。 */
    String EMPTY = "";
    /** 固定文本 {@code =}。 */
    String EQUALS = "=";
    /** 查询表达式文本 {@code <>}。 */
    String NOT_EQUALS = "<>";
    /** 查询表达式文本 {@code LIKE}。 */
    String LIKE = "LIKE";
    /** 查询表达式文本 {@code LIKE_LEFT}。 */
    String LIKE_LEFT = "LIKE_LEFT";
    /** 查询表达式文本 {@code LIKE_RIGHT}。 */
    String LIKE_RIGHT = "LIKE_RIGHT";
    /** 查询表达式文本 {@code NOT_LIKE}。 */
    String NOT_LIKE = "NOT_LIKE";
    /** 查询表达式文本 {@code >}。 */
    String GT = ">";
    /** 查询表达式文本 {@code >=}。 */
    String GE = ">=";
    /** 查询表达式文本 {@code <}。 */
    String LT = "<";
    /** 查询表达式文本 {@code <=}。 */
    String LE = "<=";
    /** 查询表达式文本 {@code IN}。 */
    String IN = "IN";
    /** 查询表达式文本 {@code NOT_IN}。 */
    String NOT_IN = "NOT_IN";
    /** 查询表达式文本 {@code IS_NULL}。 */
    String IS_NULL = "IS_NULL";
    /** 查询表达式文本 {@code IS_NOT_NULL}。 */
    String IS_NOT_NULL = "IS_NOT_NULL";
    /** 查询表达式文本 {@code ASC}。 */
    String ASC = "ASC";
    /** 查询表达式文本 {@code DESC}。 */
    String DESC = "DESC";
    /** 固定文本 {@code false}。 */
    String FALSE = "false";
    /** 固定文本 {@code /}。 */
    String SLASH = "/";
    /** 固定文本 {@code #}。 */
    String HASH = "#";
    /** 固定文本 {@code ^}。 */
    String HAT = "^";
    /** 固定文本 <code>&#123;</code>。 */
    String LEFT_BRACE = "{";
    /** 固定文本 {@code (}。 */
    String LEFT_BRACKET = "(";
    /** 固定文本 {@code <}。 */
    String LEFT_CHEV = "<";
    /** 文件扩展名 {@code ,\n}。 */
    String DOT_NEWLINE = ",\n";
    /** 固定文本 {@code ms}。 */
    String MS = "ms";
    /** 固定文本 {@code \n}。 */
    String NEWLINE = "\n";
    /** 固定文本 {@code n}。 */
    String N = "n";
    /** 固定文本 {@code no}。 */
    String NO = "no";
    /** 固定文本 {@code null}。 */
    String NULL = "null";
    /** 固定文本 {@code NUM}。 */
    String NUM = "NUM";
    /** 固定文本 {@code off}。 */
    String OFF = "off";
    /** 固定文本 {@code on}。 */
    String ON = "on";
    /** 固定文本 {@code %}。 */
    String PERCENT = "%";
    /** 固定文本 {@code |}。 */
    String PIPE = "|";
    /** 固定文本 {@code +}。 */
    String PLUS = "+";
    /** 固定文本 {@code ?}。 */
    String QUESTION_MARK = "?";
    /** 固定文本 {@code !}。 */
    String EXCLAMATION_MARK = "!";
    /** 固定文本 {@code \"}。 */
    String QUOTE = "\"";
    /** 固定文本 {@code \r}。 */
    String RETURN = "\r";
    /** 固定文本 {@code \t}。 */
    String TAB = "\t";
    /** 固定文本 {@code }}。 */
    String RIGHT_BRACE = "}";
    /** 固定文本 {@code )}。 */
    String RIGHT_BRACKET = ")";
    /** 固定文本 {@code >}。 */
    String RIGHT_CHEV = ">";
    /** 固定文本 {@code ;}。 */
    String SEMICOLON = ";";
    /** 固定文本 {@code '}。 */
    String SINGLE_QUOTE = "'";
    /** 固定文本 {@code `}。 */
    String BACKTICK = "`";
    /** 固定文本 {@code  }。 */
    String SPACE = " ";
    /** 查询表达式文本 {@code sql}。 */
    String SQL = "sql";
    /** 固定文本 {@code ~}。 */
    String TILDA = "~";
    /** 固定文本 {@code [}。 */
    String LEFT_SQ_BRACKET = "[";
    /** 固定文本 {@code ]}。 */
    String RIGHT_SQ_BRACKET = "]";
    /** 固定文本 {@code true}。 */
    String TRUE = "true";
    /** 固定文本 {@code _}。 */
    String UNDERSCORE = "_";
    /** 字符集名称 {@code UTF-8}。 */
    String UTF_8 = "UTF-8";
    /** 字符集名称 {@code US-ASCII}。 */
    String US_ASCII = "US-ASCII";
    /** 字符集名称 {@code ISO-8859-1}。 */
    String ISO_8859_1 = "ISO-8859-1";
    /** 固定文本 {@code y}。 */
    String Y = "y";
    /** 固定文本 {@code yes}。 */
    String YES = "yes";
    /** 固定文本 {@code 1}。 */
    String ONE = "1";
    /** 固定文本 {@code 0}。 */
    String ZERO = "0";
    /** 固定文本 <code>$&#123;</code>。 */
    String DOLLAR_LEFT_BRACE = "${";
    /** 固定文本 <code>#&#123;</code>。 */
    String HASH_LEFT_BRACE = "#{";
    /** 固定文本 {@code \r\n}。 */
    String CRLF = "\r\n";

    /** HTML 实体文本 {@code &nbsp;}。 */
    String HTML_NBSP = "&nbsp;";
    /** HTML 实体文本 {@code &amp}。 */
    String HTML_AMP = "&amp";
    /** HTML 实体文本 {@code &quot;}。 */
    String HTML_QUOTE = "&quot;";
    /** HTML 实体文本 {@code &lt;}。 */
    String HTML_LT = "&lt;";
    /** HTML 实体文本 {@code &gt;}。 */
    String HTML_GT = "&gt;";

    // ---------------------------------------------------------------- array

    /** 共享的空字符串数组。 */
    String[] EMPTY_ARRAY = new String[0];

    /** 换行符的字节表示。 */
    byte[] BYTES_NEW_LINE = StrPool.NEWLINE.getBytes();

}
