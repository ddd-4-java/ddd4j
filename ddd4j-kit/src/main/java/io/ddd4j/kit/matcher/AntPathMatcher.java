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
package io.ddd4j.kit.matcher;


import io.ddd4j.kit.lang.StrKit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link PathMatcher} implementation for Ant-style path patterns.
 *
 * <p>Part of this mapping code has been kindly borrowed from <a href="https://ant.apache.org">Apache Ant</a>.
 *
 * <p>The mapping matches URLs using the following rules:<br>
 * <ul>
 * <li>{@code ?} matches one character</li>
 * <li>{@code *} matches zero or more characters</li>
 * <li>{@code **} matches zero or more <em>directories</em> in a path</li>
 * <li>{@code {spring:[a-z]+}} matches the regexp {@code [a-z]+} as a path variable named "spring"</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <ul>
 * <li>{@code com/t?st.jsp} &mdash; matches {@code com/test.jsp} but also
 * {@code com/tast.jsp} or {@code com/txst.jsp}</li>
 * <li>{@code com/*.jsp} &mdash; matches all {@code .jsp} files in the
 * {@code com} directory</li>
 * <li><code>com/&#42;&#42;/test.jsp</code> &mdash; matches all {@code test.jsp}
 * files underneath the {@code com} path</li>
 * <li><code>org/springframework/&#42;&#42;/*.jsp</code> &mdash; matches all
 * {@code .jsp} files underneath the {@code org/springframework} path</li>
 * <li><code>org/&#42;&#42;/servlet/bla.jsp</code> &mdash; matches
 * {@code org/springframework/servlet/bla.jsp} but also
 * {@code org/springframework/testing/servlet/bla.jsp} and {@code org/servlet/bla.jsp}</li>
 * <li>{@code com/{filename:\\w+}.jsp} will match {@code com/test.jsp} and assign the value {@code test}
 * to the {@code filename} variable</li>
 * </ul>
 *
 * <p><strong>Note:</strong> a pattern and a path must both be absolute or must
 * both be relative in order for the two to match. Therefore, it is recommended
 * that users of this implementation to sanitize patterns in order to prefix
 * them with "/" as it makes sense in the context in which they're used.
 *
 * @author Alef Arendsen
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 * @author Vladislav Kisel
 * @since 16.07.2003
 */
public class AntPathMatcher implements PathMatcher {

    /**
     * Default path separator: "/".
     */
    public static final String DEFAULT_PATH_SEPARATOR = "/";

    /**
     * 自动停用模式缓存的条目数阈值。
     */
    private static final int CACHE_TURNOFF_THRESHOLD = 65536;

    /**
     * 匹配不跨路径分隔符的 URI 模板变量。
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{[^/]+?\\}");

    /**
     * 表示通配或 URI 模板开始的字符。
     */
    private static final char[] WILDCARD_CHARS = {'*', '?', '{'};
    /**
     * 缓存路径段模式编译后的匹配器。
     */
    final Map<String, AntPathStringMatcher> stringMatcherCache = new ConcurrentHashMap<>(256);
    /**
     * 缓存按路径分隔符切分的模式片段。
     */
    private final Map<String, String[]> tokenizedPatternCache = new ConcurrentHashMap<>(256);
    /**
     * 路径或模式各段之间使用的分隔符。
     */
    private String pathSeparator;
    /**
     * 保存当前分隔符对应的通配后缀。
     */
    private PathSeparatorPatternCache pathSeparatorPatternCache;
    /**
     * 匹配字符时是否区分大小写。
     */
    private boolean caseSensitive = true;
    /**
     * 分割路径段后是否去掉段首尾空白。
     */
    private boolean trimTokens = false;
    /**
     * 控制模式缓存启用、停用或自动选择的配置。
     */
    private volatile Boolean cachePatterns;


    /**
     * Create a new instance with the {@link #DEFAULT_PATH_SEPARATOR}.
     */
    public AntPathMatcher() {
        this.pathSeparator = DEFAULT_PATH_SEPARATOR;
        this.pathSeparatorPatternCache = new PathSeparatorPatternCache(DEFAULT_PATH_SEPARATOR);
    }

    /**
     * A convenient, alternative constructor to use with a custom path separator.
     *
     * @param pathSeparator the path separator to use, must not be {@code null}.
     * @since 4.1
     */
    public AntPathMatcher(String pathSeparator) {
        Objects.requireNonNull(pathSeparator, "'pathSeparator' must not be null");
        this.pathSeparator = pathSeparator;
        this.pathSeparatorPatternCache = new PathSeparatorPatternCache(pathSeparator);
    }


    /**
     * Set the path separator to use for pattern parsing.
     * <p>Default is "/", as in Ant.
     *
     * @param pathSeparator 路径分隔符；为 null 时回退到默认分隔符
     */
    public void setPathSeparator(String pathSeparator) {
        this.pathSeparator = (Objects.nonNull(pathSeparator) ? pathSeparator : DEFAULT_PATH_SEPARATOR);
        this.pathSeparatorPatternCache = new PathSeparatorPatternCache(this.pathSeparator);
    }

    /**
     * Specify whether to perform pattern matching in a case-sensitive fashion.
     * <p>Default is {@code true}. Switch this to {@code false} for case-insensitive matching.
     *
     * @param caseSensitive 是否区分大小写
     * @since 4.2
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Specify whether to trim tokenized paths and patterns.
     * <p>Default is {@code false}.
     *
     * @param trimTokens 是否修剪路径令牌两端空白
     */
    public void setTrimTokens(boolean trimTokens) {
        this.trimTokens = trimTokens;
    }

    /**
     * Specify whether to cache parsed pattern metadata for patterns passed
     * into this matcher's {@link #match} method. A value of {@code true}
     * activates an unlimited pattern cache; a value of {@code false} turns
     * the pattern cache off completely.
     * <p>Default is for the cache to be on, but with the variant to automatically
     * turn it off when encountering too many patterns to cache at runtime
     * (the threshold is 65536), assuming that arbitrary permutations of patterns
     * are coming in, with little chance for encountering a recurring pattern.
     *
     * @param cachePatterns 是否缓存字符串匹配器
     * @see #getStringMatcher(String)
     * @since 4.0.1
     */
    public void setCachePatterns(boolean cachePatterns) {
        this.cachePatterns = cachePatterns;
    }

    /**
     * 停用模式缓存并清空已经缓存的路径分段和匹配器。
     */
    private void deactivatePatternCache() {
        this.cachePatterns = false;
        this.tokenizedPatternCache.clear();
        this.stringMatcherCache.clear();
    }


    /**
     * Does the given {@code path} represent a pattern that can be matched。
     *
     * <p>此实现调用 <code>path.length()</code>。</p>
     *
     * @param path the path to check
     * @return 根据执行路径返回<code>false</code>、<code>true</code>
     */
    @Override
    public boolean isPattern(String path) {
        if (Objects.isNull(path)) {
            return false;
        }
        boolean uriVar = false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '*' || c == '?') {
                return true;
            }
            if (c == '{') {
                uriVar = true;
                continue;
            }
            if (c == '}' && uriVar) {
                return true;
            }
        }
        return false;
    }

    /**
     * Match the given {@code path} against the given {@code pattern},。
     *
     * <p>此实现调用 <code>doMatch(pattern, path, true, null)</code>。</p>
     *
     * @param pattern the pattern to match against
     * @param path    the path to test
     * @return 实际返回<code>doMatch(pattern, path, true, null)</code>
     */
    @Override
    public boolean match(String pattern, String path) {
        return doMatch(pattern, path, true, null);
    }

    /**
     * Match the given {@code path} against the corresponding part of the given。
     *
     * <p>此实现调用 <code>doMatch(pattern, path, false, null)</code>。</p>
     *
     * @param pattern the pattern to match against
     * @param path    the path to test
     * @return 实际返回<code>doMatch(pattern, path, false, null)</code>
     */
    @Override
    public boolean matchStart(String pattern, String path) {
        return doMatch(pattern, path, false, null);
    }

    /**
     * Actually match the given {@code path} against the given {@code pattern}.
     *
     * @param pattern              the pattern to match against
     * @param path                 the path to test
     * @param fullMatch            whether a full pattern match is required (else a pattern match
     *                             as far as the given base path goes is sufficient)
     * @param uriTemplateVariables URI 模板变量映射，用于承接模式中捕获的变量值
     * @return {@code true} if the supplied {@code path} matched, {@code false} if it didn't
     */
    protected boolean doMatch(String pattern, String path, boolean fullMatch,
                              Map<String, String> uriTemplateVariables) {

        if (Objects.isNull(path) || path.startsWith(this.pathSeparator) != pattern.startsWith(this.pathSeparator)) {
            return false;
        }

        String[] pattDirs = tokenizePattern(pattern);
        if (fullMatch && this.caseSensitive && !isPotentialMatch(path, pattDirs)) {
            return false;
        }

        String[] pathDirs = tokenizePath(path);
        int pattIdxStart = 0;
        int pattIdxEnd = pattDirs.length - 1;
        int pathIdxStart = 0;
        int pathIdxEnd = pathDirs.length - 1;

        // Match all elements up to the first **
        while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            String pattDir = pattDirs[pattIdxStart];
            if ("**".equals(pattDir)) {
                break;
            }
            if (!matchStrings(pattDir, pathDirs[pathIdxStart], uriTemplateVariables)) {
                return false;
            }
            pattIdxStart++;
            pathIdxStart++;
        }

        if (pathIdxStart > pathIdxEnd) {
            // Path is exhausted, only match if rest of pattern is * or **'s
            if (pattIdxStart > pattIdxEnd) {
                return (pattern.endsWith(this.pathSeparator) == path.endsWith(this.pathSeparator));
            }
            if (!fullMatch) {
                return true;
            }
            if (pattIdxStart == pattIdxEnd && pattDirs[pattIdxStart].equals("*") && path.endsWith(this.pathSeparator)) {
                return true;
            }
            for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
                if (!pattDirs[i].equals("**")) {
                    return false;
                }
            }
            return true;
        } else if (pattIdxStart > pattIdxEnd) {
            // String not exhausted, but pattern is. Failure.
            return false;
        } else if (!fullMatch && "**".equals(pattDirs[pattIdxStart])) {
            // Path start definitely matches due to "**" part in pattern.
            return true;
        }

        // up to last '**'
        while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            String pattDir = pattDirs[pattIdxEnd];
            if (pattDir.equals("**")) {
                break;
            }
            if (!matchStrings(pattDir, pathDirs[pathIdxEnd], uriTemplateVariables)) {
                return false;
            }
            if (pattIdxEnd == (pattDirs.length - 1) &&
                    pattern.endsWith(this.pathSeparator) != path.endsWith(this.pathSeparator)) {
                return false;
            }
            pattIdxEnd--;
            pathIdxEnd--;
        }
        if (pathIdxStart > pathIdxEnd) {
            // String is exhausted
            for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
                if (!pattDirs[i].equals("**")) {
                    return false;
                }
            }
            return true;
        }

        while (pattIdxStart != pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            int patIdxTmp = -1;
            for (int i = pattIdxStart + 1; i <= pattIdxEnd; i++) {
                if (pattDirs[i].equals("**")) {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == pattIdxStart + 1) {
                // '**/**' situation, so skip one
                pattIdxStart++;
                continue;
            }
            // Find the pattern between padIdxStart & padIdxTmp in str between
            // strIdxStart & strIdxEnd
            int patLength = (patIdxTmp - pattIdxStart - 1);
            int strLength = (pathIdxEnd - pathIdxStart + 1);
            int foundIdx = -1;

            strLoop:
            for (int i = 0; i <= strLength - patLength; i++) {
                for (int j = 0; j < patLength; j++) {
                    String subPat = pattDirs[pattIdxStart + j + 1];
                    String subStr = pathDirs[pathIdxStart + i + j];
                    if (!matchStrings(subPat, subStr, uriTemplateVariables)) {
                        continue strLoop;
                    }
                }
                foundIdx = pathIdxStart + i;
                break;
            }

            if (foundIdx == -1) {
                return false;
            }

            pattIdxStart = patIdxTmp;
            pathIdxStart = foundIdx + patLength;
        }

        for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
            if (!pattDirs[i].equals("**")) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查路径及模式的实义前缀，以提前排除不匹配的候选。
     *
     * @param path     待匹配的路径
     * @param pattDirs 按路径分隔符切分的模式片段
     * @return 还可能与模式匹配时返回 true
     */
    private boolean isPotentialMatch(String path, String[] pattDirs) {
        if (!this.trimTokens) {
            int pos = 0;
            for (String pattDir : pattDirs) {
                int skipped = skipSeparator(path, pos, this.pathSeparator);
                pos += skipped;
                skipped = skipSegment(path, pos, pattDir);
                if (skipped < pattDir.length()) {
                    return (skipped > 0 || (pattDir.length() > 0 && isWildcardChar(pattDir.charAt(0))));
                }
                pos += skipped;
            }
        }
        return true;
    }

    /**
     * 统计模式实义前缀中可与当前位置匹配的字符，遇到通配符时停止。
     *
     * @param path   当前被匹配的路径
     * @param pos    要开始匹配的路径偏移
     * @param prefix 待匹配的模式片段
     * @return 可以跳过的字符数
     */
    private int skipSegment(String path, int pos, String prefix) {
        int skipped = 0;
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (isWildcardChar(c)) {
                return skipped;
            }
            int currPos = pos + skipped;
            if (currPos >= path.length()) {
                return 0;
            }
            if (c == path.charAt(currPos)) {
                skipped++;
            }
        }
        return skipped;
    }

    /**
     * 从给定偏移跳过连续出现的路径分隔符。
     *
     * @param path      含路径分隔符的字符串
     * @param pos       开始检查的偏移
     * @param separator 要跳过的分隔符
     * @return 跳过的字符数
     */
    private int skipSeparator(String path, int pos, String separator) {
        int skipped = 0;
        while (path.startsWith(separator, pos + skipped)) {
            skipped += separator.length();
        }
        return skipped;
    }

    /**
     * 判断字符是否为 Ant 通配符或 URI 模板的开始标记。
     *
     * @param c 待检查字符
     * @return 属于通配或模板字符时返回 true
     */
    private boolean isWildcardChar(char c) {
        for (char candidate : WILDCARD_CHARS) {
            if (c == candidate) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tokenize the given path pattern into parts, based on this matcher's settings.
     * <p>Performs caching based on {@link #setCachePatterns}, delegating to
     * {@link #tokenizePath(String)} for the actual tokenization algorithm.
     *
     * @param pattern the pattern to tokenize
     * @return the tokenized pattern parts
     */
    protected String[] tokenizePattern(String pattern) {
        String[] tokenized = null;
        Boolean cachePatterns = this.cachePatterns;
        if (Objects.isNull(cachePatterns) || cachePatterns) {
            tokenized = this.tokenizedPatternCache.get(pattern);
        }
        if (Objects.isNull(tokenized)) {
            tokenized = tokenizePath(pattern);
            if (Objects.isNull(cachePatterns) && this.tokenizedPatternCache.size() >= CACHE_TURNOFF_THRESHOLD) {
                // Try to adapt to the runtime situation that we're encountering:
                // There are obviously too many different patterns coming in here...
                // So let's turn off the cache since the patterns are unlikely to be reoccurring.
                deactivatePatternCache();
                return tokenized;
            }
            if (Objects.isNull(cachePatterns) || cachePatterns) {
                this.tokenizedPatternCache.put(pattern, tokenized);
            }
        }
        return tokenized;
    }

    /**
     * Tokenize the given path into parts, based on this matcher's settings.
     *
     * @param path the path to tokenize
     * @return the tokenized path parts
     */
    protected String[] tokenizePath(String path) {
        return StrKit.tokenizeToStringArray(path, this.pathSeparator, this.trimTokens, true);
    }

    /**
     * Test whether a string matches against a pattern.
     *
     * @param pattern              the pattern to match against (never {@code null})
     * @param str                  the String which must be matched against the pattern (never {@code null})
     * @param uriTemplateVariables 接收已匹配模板变量及其值的映射
     * @return {@code true} if the string matches against the pattern, or {@code false} otherwise
     */
    private boolean matchStrings(String pattern, String str,
                                 Map<String, String> uriTemplateVariables) {

        return getStringMatcher(pattern).matchStrings(str, uriTemplateVariables);
    }

    /**
     * Build or retrieve an {@link AntPathStringMatcher} for the given pattern.
     * <p>The default implementation checks this AntPathMatcher's internal cache
     * (see {@link #setCachePatterns}), creating a new AntPathStringMatcher instance
     * if no cached copy is found.
     * <p>When encountering too many patterns to cache at runtime (the threshold is 65536),
     * it turns the default cache off, assuming that arbitrary permutations of patterns
     * are coming in, with little chance for encountering a recurring pattern.
     * <p>This method may be overridden to implement a custom cache strategy.
     *
     * @param pattern the pattern to match against (never {@code null})
     * @return a corresponding AntPathStringMatcher (never {@code null})
     * @see #setCachePatterns
     */
    protected AntPathStringMatcher getStringMatcher(String pattern) {
        AntPathStringMatcher matcher = null;
        Boolean cachePatterns = this.cachePatterns;
        if (Objects.isNull(cachePatterns) || cachePatterns) {
            matcher = this.stringMatcherCache.get(pattern);
        }
        if (Objects.isNull(matcher)) {
            matcher = new AntPathStringMatcher(pattern, this.pathSeparator, this.caseSensitive);
            if (Objects.isNull(cachePatterns) && this.stringMatcherCache.size() >= CACHE_TURNOFF_THRESHOLD) {
                // Try to adapt to the runtime situation that we're encountering:
                // There are obviously too many different patterns coming in here...
                // So let's turn off the cache since the patterns are unlikely to be reoccurring.
                deactivatePatternCache();
                return matcher;
            }
            if (Objects.isNull(cachePatterns) || cachePatterns) {
                this.stringMatcherCache.put(pattern, matcher);
            }
        }
        return matcher;
    }

    /**
     * Given a pattern and a full path, determine the pattern-mapped part. <p>For example: <ul>
     * <li>'{@code /docs/cvs/commit.html}' and '{@code /docs/cvs/commit.html} &rarr; ''</li>
     * <li>'{@code /docs/*}' and '{@code /docs/cvs/commit} &rarr; '{@code cvs/commit}'</li>
     * <li>'{@code /docs/cvs/*.html}' and '{@code /docs/cvs/commit.html} &rarr; '{@code commit.html}'</li>
     * <li>'{@code /docs/**}' and '{@code /docs/cvs/commit} &rarr; '{@code cvs/commit}'</li>
     * <li>'{@code /docs/**\/*.html}' and '{@code /docs/cvs/commit.html} &rarr; '{@code cvs/commit.html}'</li>
     * <li>'{@code /*.html}' and '{@code /docs/cvs/commit.html} &rarr; '{@code docs/cvs/commit.html}'</li>
     * <li>'{@code *.html}' and '{@code /docs/cvs/commit.html} &rarr; '{@code /docs/cvs/commit.html}'</li>
     * <li>'{@code *}' and '{@code /docs/cvs/commit.html} &rarr; '{@code /docs/cvs/commit.html}'</li> </ul>
     * <p>Assumes that {@link #match} returns {@code true} for '{@code pattern}' and '{@code path}', but
     * does <strong>not</strong> enforce this.
     *
     * @param pattern 用于匹配的 Ant 路径模式
     * @param path    待处理的路径
     * @return 通配符匹配到的路径部分
     */
    @Override
    public String extractPathWithinPattern(String pattern, String path) {
        String[] patternParts = StrKit.tokenizeToStringArray(pattern, this.pathSeparator, this.trimTokens, true);
        String[] pathParts = StrKit.tokenizeToStringArray(path, this.pathSeparator, this.trimTokens, true);
        StringBuilder builder = new StringBuilder();
        boolean pathStarted = false;

        for (int segment = 0; segment < patternParts.length; segment++) {
            String patternPart = patternParts[segment];
            if (patternPart.indexOf('*') > -1 || patternPart.indexOf('?') > -1) {
                for (; segment < pathParts.length; segment++) {
                    if (pathStarted || (segment == 0 && !pattern.startsWith(this.pathSeparator))) {
                        builder.append(this.pathSeparator);
                    }
                    builder.append(pathParts[segment]);
                    pathStarted = true;
                }
            }
        }

        return builder.toString();
    }

    /**
     * 提取实际路径与 URI 模板匹配得到的变量。
     *
     * <p>此实现调用 <code>doMatch(pattern, path, true, variables)</code>。</p>
     *
     * @param pattern the path pattern, possibly containing URI templates
     * @param path    the full path to extract template variables from
     * @return 实际返回<code>variables</code>
     */
    @Override
    public Map<String, String> extractUriTemplateVariables(String pattern, String path) {
        Map<String, String> variables = new LinkedHashMap<>();
        boolean result = doMatch(pattern, path, true, variables);
        if (!result) {
            throw new IllegalStateException("Pattern \"" + pattern + "\" is not a match for \"" + path + "\"");
        }
        return variables;
    }

    /**
     * Combine two patterns into a new pattern.
     * <p>This implementation simply concatenates the two patterns, unless
     * the first pattern contains a file extension match (for example, {@code *.html}).
     * In that case, the second pattern will be merged into the first. Otherwise,
     * an {@code IllegalArgumentException} will be thrown.
     * <p><b>Examples</b></p>
     * <table border="1">
     * <caption>Pattern combination examples</caption>
     * <tr><th>Pattern 1</th><th>Pattern 2</th><th>Result</th></tr>
     * <tr><td>{@code null}</td><td>{@code null}</td><td>&nbsp;</td></tr>
     * <tr><td>/hotels</td><td>{@code null}</td><td>/hotels</td></tr>
     * <tr><td>{@code null}</td><td>/hotels</td><td>/hotels</td></tr>
     * <tr><td>/hotels</td><td>/bookings</td><td>/hotels/bookings</td></tr>
     * <tr><td>/hotels</td><td>bookings</td><td>/hotels/bookings</td></tr>
     * <tr><td>/hotels/*</td><td>/bookings</td><td>/hotels/bookings</td></tr>
     * <tr><td>/hotels/&#42;&#42;</td><td>/bookings</td><td>/hotels/&#42;&#42;/bookings</td></tr>
     * <tr><td>/hotels</td><td>{hotel}</td><td>/hotels/{hotel}</td></tr>
     * <tr><td>/hotels/*</td><td>{hotel}</td><td>/hotels/{hotel}</td></tr>
     * <tr><td>/hotels/&#42;&#42;</td><td>{hotel}</td><td>/hotels/&#42;&#42;/{hotel}</td></tr>
     * <tr><td>/*.html</td><td>/hotels.html</td><td>/hotels.html</td></tr>
     * <tr><td>/*.html</td><td>/hotels</td><td>/hotels.html</td></tr>
     * <tr><td>/*.html</td><td>/*.txt</td><td>{@code IllegalArgumentException}</td></tr>
     * </table>
     *
     * @param pattern1 the first pattern
     * @param pattern2 the second pattern
     * @return the combination of the two patterns
     * @throws IllegalArgumentException if the two patterns cannot be combined
     */
    @Override
    public String combine(String pattern1, String pattern2) {
        if (!StrKit.hasText(pattern1) && !StrKit.hasText(pattern2)) {
            return "";
        }
        if (!StrKit.hasText(pattern1)) {
            return pattern2;
        }
        if (!StrKit.hasText(pattern2)) {
            return pattern1;
        }

        boolean pattern1ContainsUriVar = (pattern1.indexOf('{') != -1);
        if (!pattern1.equals(pattern2) && !pattern1ContainsUriVar && match(pattern1, pattern2)) {
            // /* + /hotel -> /hotel ; "/*.*" + "/*.html" -> /*.html
            // However /user + /user -> /usr/user ; /{foo} + /bar -> /{foo}/bar
            return pattern2;
        }

        // /hotels/* + /booking -> /hotels/booking
        // /hotels/* + booking -> /hotels/booking
        if (pattern1.endsWith(this.pathSeparatorPatternCache.getEndsOnWildCard())) {
            return concat(pattern1.substring(0, pattern1.length() - 2), pattern2);
        }

        // /hotels/** + /booking -> /hotels/**/booking
        // /hotels/** + booking -> /hotels/**/booking
        if (pattern1.endsWith(this.pathSeparatorPatternCache.getEndsOnDoubleWildCard())) {
            return concat(pattern1, pattern2);
        }

        int starDotPos1 = pattern1.indexOf("*.");
        if (pattern1ContainsUriVar || starDotPos1 == -1 || this.pathSeparator.equals(".")) {
            // simply concatenate the two patterns
            return concat(pattern1, pattern2);
        }

        String ext1 = pattern1.substring(starDotPos1 + 1);
        int dotPos2 = pattern2.indexOf('.');
        String file2 = (dotPos2 == -1 ? pattern2 : pattern2.substring(0, dotPos2));
        String ext2 = (dotPos2 == -1 ? "" : pattern2.substring(dotPos2));
        boolean ext1All = (ext1.equals(".*") || ext1.isEmpty());
        boolean ext2All = (ext2.equals(".*") || ext2.isEmpty());
        if (!ext1All && !ext2All) {
            throw new IllegalArgumentException("Cannot combine patterns: " + pattern1 + " vs " + pattern2);
        }
        String ext = (ext1All ? ext2 : ext1);
        return file2 + ext;
    }

    /**
     * 按配置分隔符拼接路径片段并处理连接处重复的分隔符。
     *
     * @param path1 左侧路径片段
     * @param path2 右侧路径片段
     * @return 拼接后的路径
     */
    private String concat(String path1, String path2) {
        boolean path1EndsWithSeparator = path1.endsWith(this.pathSeparator);
        boolean path2StartsWithSeparator = path2.startsWith(this.pathSeparator);

        if (path1EndsWithSeparator && path2StartsWithSeparator) {
            return path1 + path2.substring(1);
        } else if (path1EndsWithSeparator || path2StartsWithSeparator) {
            return path1 + path2;
        } else {
            return path1 + this.pathSeparator + path2;
        }
    }

    /**
     * Given a full path, returns a {@link Comparator} suitable for sorting patterns in order of
     * explicitness.
     * <p>This {@code Comparator} will {@linkplain List#sort(Comparator) sort}
     * a list so that more specific patterns (without URI templates or wild cards) come before
     * generic patterns. So given a list with the following patterns, the returned comparator
     * will sort this list so that the order will be as indicated.
     * <ol>
     * <li>{@code /hotels/new}</li>
     * <li>{@code /hotels/{hotel}}</li>
     * <li>{@code /hotels/*}</li>
     * </ol>
     * <p>The full path given as parameter is used to test for exact matches. So when the given path
     * is {@code /hotels/2}, the pattern {@code /hotels/2} will be sorted before {@code /hotels/1}.
     *
     * @param path the full path to use for comparison
     * @return a comparator capable of sorting patterns in order of explicitness
     */
    @Override
    public Comparator<String> getPatternComparator(String path) {
        return new AntPatternComparator(path, this.pathSeparator);
    }


    /**
     * Tests whether a string matches against a pattern via a {@link Pattern}.
     * <p>The pattern may contain special characters: '*' means zero or more characters; '?' means one and
     * only one character; '{' and '}' indicate a URI template pattern. For example {@code /users/{user}}.
     */
    protected static class AntPathStringMatcher {

        /**
         * 未指定正则时捕获 URI 变量的默认模式。
         */
        private static final String DEFAULT_VARIABLE_PATTERN = "((?s).*)";

        /**
         * 编译前的路径段模式。
         */
        private final String rawPattern;

        /**
         * 匹配字符时是否区分大小写。
         */
        private final boolean caseSensitive;

        /**
         * 路径段不含需解释的通配或模板时使用的精确匹配标记。
         */
        private final boolean exactMatch;


        /**
         * 编译后的路径段正则表达式。
         */
        private final Pattern pattern;

        /**
         * 按捕获组顺序保存的 URI 模板变量名。
         */
        private final List<String> variableNames = new ArrayList<>();

        /**
         * 创建路径片段匹配器。
         *
         * @param pattern       Ant 风格模式
         * @param pathSeparator 路径分隔符
         * @param caseSensitive 是否区分大小写
         */
        protected AntPathStringMatcher(String pattern, String pathSeparator, boolean caseSensitive) {
            this.rawPattern = pattern;
            this.caseSensitive = caseSensitive;
            StringBuilder patternBuilder = new StringBuilder();
            Matcher matcher = getGlobPattern(pathSeparator).matcher(pattern);
            int end = 0;
            while (matcher.find()) {
                patternBuilder.append(quote(pattern, end, matcher.start()));
                String match = matcher.group();
                if ("?".equals(match)) {
                    patternBuilder.append('.');
                } else if ("*".equals(match)) {
                    patternBuilder.append(".*");
                } else if (match.startsWith("{") && match.endsWith("}")) {
                    int colonIdx = match.indexOf(':');
                    if (colonIdx == -1) {
                        patternBuilder.append(DEFAULT_VARIABLE_PATTERN);
                        this.variableNames.add(matcher.group(1));
                    } else {
                        String variablePattern = match.substring(colonIdx + 1, match.length() - 1);
                        patternBuilder.append('(');
                        patternBuilder.append(variablePattern);
                        patternBuilder.append(')');
                        String variableName = match.substring(1, colonIdx);
                        this.variableNames.add(variableName);
                    }
                }
                end = matcher.end();
            }
            // No glob pattern was found, this is an exact String match
            if (end == 0) {
                this.exactMatch = true;
                this.pattern = null;
            } else {
                this.exactMatch = false;
                patternBuilder.append(quote(pattern, end, pattern.length()));
                this.pattern = Pattern.compile(patternBuilder.toString(),
                        Pattern.DOTALL | (this.caseSensitive ? 0 : Pattern.CASE_INSENSITIVE));
            }
        }

        /**
         * 构造识别问号、星号和 URI 模板变量的正则。
         *
         * @param pathSeparator 禁止在模板变量中匹配的路径分隔符
         * @return 将 Ant 模式拆分为可处理标记的正则
         */
        private static Pattern getGlobPattern(String pathSeparator) {
            String pattern = "\\?|\\*|\\{((?:\\{[^" + pathSeparator + "]+?\\}|[^" + pathSeparator + "{}]|\\\\[{}])+?)\\}";
            return Pattern.compile(pattern);
        }

        /**
         * 把指定范围的字符作为正则字面量处理。
         *
         * @param s     原始模式
         * @param start 开始索引，包含该位置
         * @param end   结束索引，不含该位置
         * @return 已引用的正则文本，范围为空时返回空串
         */
        private String quote(String s, int start, int end) {
            if (start == end) {
                return "";
            }
            return Pattern.quote(s.substring(start, end));
        }

        /**
         * Main entry point.
         *
         * @param str                  待匹配的字符串
         * @param uriTemplateVariables URI 模板变量映射
         * @return {@code true} if the string matches against the pattern, or {@code false} otherwise.
         */
        public boolean matchStrings(String str, Map<String, String> uriTemplateVariables) {
            if (this.exactMatch) {
                return this.caseSensitive ? this.rawPattern.equals(str) : this.rawPattern.equalsIgnoreCase(str);
            } else if (Objects.nonNull(this.pattern)) {
                Matcher matcher = this.pattern.matcher(str);
                if (matcher.matches()) {
                    if (Objects.nonNull(uriTemplateVariables)) {
                        if (this.variableNames.size() != matcher.groupCount()) {
                            throw new IllegalArgumentException("The number of capturing groups in the pattern segment " +
                                    this.pattern + " does not match the number of URI template variables it defines, " +
                                    "which can occur if capturing groups are used in a URI template regex. " +
                                    "Use non-capturing groups instead.");
                        }
                        for (int i = 1; i <= matcher.groupCount(); i++) {
                            String name = this.variableNames.get(i - 1);
                            if (name.startsWith("*")) {
                                throw new IllegalArgumentException("Capturing patterns (" + name + ") are not " +
                                        "supported by the AntPathMatcher. Use the PathPatternParser instead.");
                            }
                            String value = matcher.group(i);
                            uriTemplateVariables.put(name, value);
                        }
                    }
                    return true;
                }
            }
            return false;
        }

    }


    /**
     * The default {@link Comparator} implementation returned by
     * {@link #getPatternComparator(String)}.
     * <p>In order, the most "generic" pattern is determined by the following:
     * <ul>
     * <li>if it's null or a capture all pattern (i.e. it is equal to "/**")</li>
     * <li>if the other pattern is an actual match</li>
     * <li>if it's a catch-all pattern (i.e. it ends with "**"</li>
     * <li>if it's got more "*" than the other pattern</li>
     * <li>if it's got more "{foo}" than the other pattern</li>
     * <li>if it's shorter than the other pattern</li>
     * </ul>
     */
    protected static class AntPatternComparator implements Comparator<String> {

        /**
         * 用来比较不同模式匹配精确度的实际路径。
         */
        private final String path;

        /**
         * 路径或模式各段之间使用的分隔符。
         */
        private final String pathSeparator;

        /**
         * 创建使用默认分隔符的模式比较器。
         *
         * @param path 待匹配路径
         */
        public AntPatternComparator(String path) {
            this(path, DEFAULT_PATH_SEPARATOR);
        }

        /**
         * 创建模式比较器。
         *
         * @param path          待匹配路径
         * @param pathSeparator 路径分隔符
         */
        public AntPatternComparator(String path, String pathSeparator) {
            this.path = path;
            this.pathSeparator = pathSeparator;
        }

        /**
         * Compare two patterns to determine which should match first, i.e. which
         * is the most specific regarding the current path.
         *
         * @param pattern1 第一个待比较的路径模式
         * @param pattern2 第二个待比较的路径模式
         * @return a negative integer, zero, or a positive integer as pattern1 is
         * more specific, equally specific, or less specific than pattern2.
         */
        @Override
        public int compare(String pattern1, String pattern2) {
            PatternInfo info1 = new PatternInfo(pattern1, this.pathSeparator);
            PatternInfo info2 = new PatternInfo(pattern2, this.pathSeparator);

            if (info1.isLeastSpecific() && info2.isLeastSpecific()) {
                return 0;
            } else if (info1.isLeastSpecific()) {
                return 1;
            } else if (info2.isLeastSpecific()) {
                return -1;
            }

            boolean pattern1EqualsPath = pattern1.equals(this.path);
            boolean pattern2EqualsPath = pattern2.equals(this.path);
            if (pattern1EqualsPath && pattern2EqualsPath) {
                return 0;
            } else if (pattern1EqualsPath) {
                return -1;
            } else if (pattern2EqualsPath) {
                return 1;
            }

            if (info1.isPrefixPattern() && info2.isPrefixPattern()) {
                return info2.getLength() - info1.getLength();
            } else if (info1.isPrefixPattern() && info2.getDoubleWildcards() == 0) {
                return 1;
            } else if (info2.isPrefixPattern() && info1.getDoubleWildcards() == 0) {
                return -1;
            }

            if (info1.getTotalCount() != info2.getTotalCount()) {
                return info1.getTotalCount() - info2.getTotalCount();
            }

            if (info1.getLength() != info2.getLength()) {
                return info2.getLength() - info1.getLength();
            }

            if (info1.getSingleWildcards() < info2.getSingleWildcards()) {
                return -1;
            } else if (info2.getSingleWildcards() < info1.getSingleWildcards()) {
                return 1;
            }

            if (info1.getUriVars() < info2.getUriVars()) {
                return -1;
            } else if (info2.getUriVars() < info1.getUriVars()) {
                return 1;
            }

            return 0;
        }


        /**
         * Value class that holds information about the pattern, for example, number of
         * occurrences of "*", "**", and "{" pattern elements.
         */
        private static class PatternInfo {


            /**
             * 正在分析和比较的 Ant 路径模式。
             */
            private final String pattern;

            /**
             * 模式中 URI 模板变量的计数。
             */
            private int uriVars;

            /**
             * 模式中单星通配符的计数。
             */
            private int singleWildcards;

            /**
             * 模式中双星通配符的计数。
             */
            private int doubleWildcards;

            /**
             * 当前模式是否匹配全部路径。
             */
            private boolean catchAllPattern;

            /**
             * 当前模式是否为以双星结束的前缀模式。
             */
            private boolean prefixPattern;


            /**
             * 比较模式匹配精确度时使用的缓存长度。
             */
            private Integer length;

            /**
             * 分析路径模式中的模板、通配符和后缀特征。
             *
             * @param pattern       待评分的 Ant 路径模式
             * @param pathSeparator 判断通配后缀的路径分隔符
             */
            PatternInfo(String pattern, String pathSeparator) {
                this.pattern = pattern;
                if (Objects.nonNull(this.pattern)) {
                    initCounters();
                    this.catchAllPattern = this.pattern.equals(pathSeparator + "**");
                    this.prefixPattern = !this.catchAllPattern && this.pattern.endsWith(pathSeparator + "**");
                }
                if (this.uriVars == 0) {
                    this.length = (Objects.nonNull(this.pattern) ? this.pattern.length() : 0);
                }
            }

            /**
             * 统计模式中的 URI 模板变量和单星、双星通配符。
             */
            protected void initCounters() {
                int pos = 0;
                if (Objects.nonNull(this.pattern)) {
                    while (pos < this.pattern.length()) {
                        if (this.pattern.charAt(pos) == '{') {
                            this.uriVars++;
                            pos++;
                        } else if (this.pattern.charAt(pos) == '*') {
                            if (pos + 1 < this.pattern.length() && this.pattern.charAt(pos + 1) == '*') {
                                this.doubleWildcards++;
                                pos += 2;
                            } else if (pos > 0 && !this.pattern.substring(pos - 1).equals(".*")) {
                                this.singleWildcards++;
                                pos++;
                            } else {
                                pos++;
                            }
                        } else {
                            pos++;
                        }
                    }
                }
            }

            /**
             * 读取<code>uriVars</code> 字段。
             *
             * @return <code>uriVars</code> 字段
             */
            public int getUriVars() {
                return this.uriVars;
            }

            /**
             * 读取<code>singleWildcards</code> 字段。
             *
             * @return <code>singleWildcards</code> 字段
             */
            public int getSingleWildcards() {
                return this.singleWildcards;
            }

            /**
             * 读取<code>doubleWildcards</code> 字段。
             *
             * @return <code>doubleWildcards</code> 字段
             */
            public int getDoubleWildcards() {
                return this.doubleWildcards;
            }

            /**
             * 判断模式是否应在精确度排序中排到最末。
             *
             * @return 模式为空或匹配全部路径时返回 true
             */
            public boolean isLeastSpecific() {
                return (Objects.isNull(this.pattern) || this.catchAllPattern);
            }

            /**
             * 读取<code>prefixPattern</code> 字段。
             *
             * @return <code>prefixPattern</code> 字段
             */
            public boolean isPrefixPattern() {
                return this.prefixPattern;
            }

            /**
             * 计算模式通配权重，双星计为两项。
             *
             * @return URI 模板变量、单星和双星折算后的计数
             */
            public int getTotalCount() {
                return this.uriVars + this.singleWildcards + (2 * this.doubleWildcards);
            }

            /**
             * Returns the length of the given pattern, where template variables are considered to be 1 long.
             *
             * @return 比较模式时使用的长度
             */
            public int getLength() {
                if (Objects.isNull(this.length)) {
                    this.length = (Objects.nonNull(this.pattern) ?
                            VARIABLE_PATTERN.matcher(this.pattern).replaceAll("#").length() : 0);
                }
                return this.length;
            }
        }
    }


    /**
     * A simple cache for patterns that depend on the configured path separator.
     */
    private static class PathSeparatorPatternCache {

        /**
         * 当前分隔符加单星的模式后缀。
         */
        private final String endsOnWildCard;

        /**
         * 当前分隔符加双星的模式后缀。
         */
        private final String endsOnDoubleWildCard;

        /**
         * 预先计算分隔符与单星、双星的组合。
         *
         * @param pathSeparator 缓存的路径分隔符
         */
        public PathSeparatorPatternCache(String pathSeparator) {
            this.endsOnWildCard = pathSeparator + "*";
            this.endsOnDoubleWildCard = pathSeparator + "**";
        }

        /**
         * 读取<code>endsOnWildCard</code> 字段。
         *
         * @return <code>endsOnWildCard</code> 字段
         */
        public String getEndsOnWildCard() {
            return this.endsOnWildCard;
        }

        /**
         * 读取<code>endsOnDoubleWildCard</code> 字段。
         *
         * @return <code>endsOnDoubleWildCard</code> 字段
         */
        public String getEndsOnDoubleWildCard() {
            return this.endsOnDoubleWildCard;
        }
    }

}
