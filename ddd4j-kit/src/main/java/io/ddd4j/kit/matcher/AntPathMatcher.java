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

    /** \u81ea\u52a8\u505c\u7528\u6a21\u5f0f\u7f13\u5b58\u7684\u6761\u76ee\u6570\u9608\u503c\u3002 */
    private static final int CACHE_TURNOFF_THRESHOLD = 65536;

    /** \u5339\u914d\u4e0d\u8de8\u8def\u5f84\u5206\u9694\u7b26\u7684 URI \u6a21\u677f\u53d8\u91cf\u3002 */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{[^/]+?\\}");

    /** \u8868\u793a\u901a\u914d\u6216 URI \u6a21\u677f\u5f00\u59cb\u7684\u5b57\u7b26\u3002 */
    private static final char[] WILDCARD_CHARS = {'*', '?', '{'};
    /** \u7f13\u5b58\u8def\u5f84\u6bb5\u6a21\u5f0f\u7f16\u8bd1\u540e\u7684\u5339\u914d\u5668\u3002 */
    final Map<String, AntPathStringMatcher> stringMatcherCache = new ConcurrentHashMap<>(256);
    /** \u7f13\u5b58\u6309\u8def\u5f84\u5206\u9694\u7b26\u5207\u5206\u7684\u6a21\u5f0f\u7247\u6bb5\u3002 */
    private final Map<String, String[]> tokenizedPatternCache = new ConcurrentHashMap<>(256);
    /** \u8def\u5f84\u6216\u6a21\u5f0f\u5404\u6bb5\u4e4b\u95f4\u4f7f\u7528\u7684\u5206\u9694\u7b26\u3002 */
    private String pathSeparator;
    /** \u4fdd\u5b58\u5f53\u524d\u5206\u9694\u7b26\u5bf9\u5e94\u7684\u901a\u914d\u540e\u7f00\u3002 */
    private PathSeparatorPatternCache pathSeparatorPatternCache;
    /** \u5339\u914d\u5b57\u7b26\u65f6\u662f\u5426\u533a\u5206\u5927\u5c0f\u5199\u3002 */
    private boolean caseSensitive = true;
    /** \u5206\u5272\u8def\u5f84\u6bb5\u540e\u662f\u5426\u53bb\u6389\u6bb5\u9996\u5c3e\u7a7a\u767d\u3002 */
    private boolean trimTokens = false;
    /** \u63a7\u5236\u6a21\u5f0f\u7f13\u5b58\u542f\u7528\u3001\u505c\u7528\u6216\u81ea\u52a8\u9009\u62e9\u7684\u914d\u7f6e\u3002 */
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
     * @since 4.2
     * @param caseSensitive 是否区分大小写
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Specify whether to trim tokenized paths and patterns.
     * <p>Default is {@code false}.
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
     * @see #getStringMatcher(String)
     * @since 4.0.1
     * @param cachePatterns 是否缓存字符串匹配器
     */
    public void setCachePatterns(boolean cachePatterns) {
        this.cachePatterns = cachePatterns;
    }

    /** \u505c\u7528\u6a21\u5f0f\u7f13\u5b58\u5e76\u6e05\u7a7a\u5df2\u7ecf\u7f13\u5b58\u7684\u8def\u5f84\u5206\u6bb5\u548c\u5339\u914d\u5668\u3002 */
    private void deactivatePatternCache() {
        this.cachePatterns = false;
        this.tokenizedPatternCache.clear();
        this.stringMatcherCache.clear();
    }


    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public boolean match(String pattern, String path) {
        return doMatch(pattern, path, true, null);
    }

    /** {@inheritDoc} */
    @Override
    public boolean matchStart(String pattern, String path) {
        return doMatch(pattern, path, false, null);
    }

    /**
     * Actually match the given {@code path} against the given {@code pattern}.
     *
     * @param pattern   the pattern to match against
     * @param path      the path to test
     * @param fullMatch whether a full pattern match is required (else a pattern match
     *                  as far as the given base path goes is sufficient)
     * @return {@code true} if the supplied {@code path} matched, {@code false} if it didn't
     * @param uriTemplateVariables URI 模板变量映射，用于承接模式中捕获的变量值
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
     * \u68c0\u67e5\u8def\u5f84\u53ca\u6a21\u5f0f\u7684\u5b9e\u4e49\u524d\u7f00\uff0c\u4ee5\u63d0\u524d\u6392\u9664\u4e0d\u5339\u914d\u7684\u5019\u9009\u3002
     * @param path \u5f85\u5339\u914d\u7684\u8def\u5f84
     * @param pattDirs \u6309\u8def\u5f84\u5206\u9694\u7b26\u5207\u5206\u7684\u6a21\u5f0f\u7247\u6bb5
     * @return \u8fd8\u53ef\u80fd\u4e0e\u6a21\u5f0f\u5339\u914d\u65f6\u8fd4\u56de true
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
     * \u7edf\u8ba1\u6a21\u5f0f\u5b9e\u4e49\u524d\u7f00\u4e2d\u53ef\u4e0e\u5f53\u524d\u4f4d\u7f6e\u5339\u914d\u7684\u5b57\u7b26\uff0c\u9047\u5230\u901a\u914d\u7b26\u65f6\u505c\u6b62\u3002
     * @param path \u5f53\u524d\u88ab\u5339\u914d\u7684\u8def\u5f84
     * @param pos \u8981\u5f00\u59cb\u5339\u914d\u7684\u8def\u5f84\u504f\u79fb
     * @param prefix \u5f85\u5339\u914d\u7684\u6a21\u5f0f\u7247\u6bb5
     * @return \u53ef\u4ee5\u8df3\u8fc7\u7684\u5b57\u7b26\u6570
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
     * \u4ece\u7ed9\u5b9a\u504f\u79fb\u8df3\u8fc7\u8fde\u7eed\u51fa\u73b0\u7684\u8def\u5f84\u5206\u9694\u7b26\u3002
     * @param path \u542b\u8def\u5f84\u5206\u9694\u7b26\u7684\u5b57\u7b26\u4e32
     * @param pos \u5f00\u59cb\u68c0\u67e5\u7684\u504f\u79fb
     * @param separator \u8981\u8df3\u8fc7\u7684\u5206\u9694\u7b26
     * @return \u8df3\u8fc7\u7684\u5b57\u7b26\u6570
     */
    private int skipSeparator(String path, int pos, String separator) {
        int skipped = 0;
        while (path.startsWith(separator, pos + skipped)) {
            skipped += separator.length();
        }
        return skipped;
    }

    /**
     * \u5224\u65ad\u5b57\u7b26\u662f\u5426\u4e3a Ant \u901a\u914d\u7b26\u6216 URI \u6a21\u677f\u7684\u5f00\u59cb\u6807\u8bb0\u3002
     * @param c \u5f85\u68c0\u67e5\u5b57\u7b26
     * @return \u5c5e\u4e8e\u901a\u914d\u6216\u6a21\u677f\u5b57\u7b26\u65f6\u8fd4\u56de true
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
     * @param pattern the pattern to match against (never {@code null})
     * @param str the String which must be matched against the pattern (never {@code null})
     * @return {@code true} if the string matches against the pattern, or {@code false} otherwise
     *
     * @param uriTemplateVariables \u63a5\u6536\u5df2\u5339\u914d\u6a21\u677f\u53d8\u91cf\u53ca\u5176\u503c\u7684\u6620\u5c04
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
     *  <li>'{@code /docs/cvs/commit.html}' and '{@code /docs/cvs/commit.html} &rarr; ''</li>
     *  <li>'{@code /docs/*}' and '{@code /docs/cvs/commit} &rarr; '{@code cvs/commit}'</li>
     *  <li>'{@code /docs/cvs/*.html}' and '{@code /docs/cvs/commit.html} &rarr; '{@code commit.html}'</li>
     *  <li>'{@code /docs/**}' and '{@code /docs/cvs/commit} &rarr; '{@code cvs/commit}'</li>
     *  <li>'{@code /docs/**\/*.html}' and '{@code /docs/cvs/commit.html} &rarr; '{@code cvs/commit.html}'</li>
     *  <li>'{@code /*.html}' and '{@code /docs/cvs/commit.html} &rarr; '{@code docs/cvs/commit.html}'</li>
     *  <li>'{@code *.html}' and '{@code /docs/cvs/commit.html} &rarr; '{@code /docs/cvs/commit.html}'</li>
     *  <li>'{@code *}' and '{@code /docs/cvs/commit.html} &rarr; '{@code /docs/cvs/commit.html}'</li> </ul>
     *  <p>Assumes that {@link #match} returns {@code true} for '{@code pattern}' and '{@code path}', but
     *  does <strong>not</strong> enforce this.
     *
     * @param pattern \u7528\u4e8e\u5339\u914d\u7684 Ant \u8def\u5f84\u6a21\u5f0f
     * @param path \u5f85\u5904\u7406\u7684\u8def\u5f84
     * @return \u901a\u914d\u7b26\u5339\u914d\u5230\u7684\u8def\u5f84\u90e8\u5206
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

    /** {@inheritDoc} */
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
     * \u6309\u914d\u7f6e\u5206\u9694\u7b26\u62fc\u63a5\u8def\u5f84\u7247\u6bb5\u5e76\u5904\u7406\u8fde\u63a5\u5904\u91cd\u590d\u7684\u5206\u9694\u7b26\u3002
     * @param path1 \u5de6\u4fa7\u8def\u5f84\u7247\u6bb5
     * @param path2 \u53f3\u4fa7\u8def\u5f84\u7247\u6bb5
     * @return \u62fc\u63a5\u540e\u7684\u8def\u5f84
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

        /** \u672a\u6307\u5b9a\u6b63\u5219\u65f6\u6355\u83b7 URI \u53d8\u91cf\u7684\u9ed8\u8ba4\u6a21\u5f0f\u3002 */
        private static final String DEFAULT_VARIABLE_PATTERN = "((?s).*)";

        /** \u7f16\u8bd1\u524d\u7684\u8def\u5f84\u6bb5\u6a21\u5f0f\u3002 */
        private final String rawPattern;

        /** \u5339\u914d\u5b57\u7b26\u65f6\u662f\u5426\u533a\u5206\u5927\u5c0f\u5199\u3002 */
        private final boolean caseSensitive;

        /** \u8def\u5f84\u6bb5\u4e0d\u542b\u9700\u89e3\u91ca\u7684\u901a\u914d\u6216\u6a21\u677f\u65f6\u4f7f\u7528\u7684\u7cbe\u786e\u5339\u914d\u6807\u8bb0\u3002 */
        private final boolean exactMatch;


        /** \u7f16\u8bd1\u540e\u7684\u8def\u5f84\u6bb5\u6b63\u5219\u8868\u8fbe\u5f0f\u3002 */
        private final Pattern pattern;

        /** \u6309\u6355\u83b7\u7ec4\u987a\u5e8f\u4fdd\u5b58\u7684 URI \u6a21\u677f\u53d8\u91cf\u540d\u3002 */
        private final List<String> variableNames = new ArrayList<>();

        /**
         * \u521b\u5efa\u8def\u5f84\u7247\u6bb5\u5339\u914d\u5668\u3002
         * @param pattern Ant \u98ce\u683c\u6a21\u5f0f
         * @param pathSeparator \u8def\u5f84\u5206\u9694\u7b26
         * @param caseSensitive \u662f\u5426\u533a\u5206\u5927\u5c0f\u5199
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
         * \u6784\u9020\u8bc6\u522b\u95ee\u53f7\u3001\u661f\u53f7\u548c URI \u6a21\u677f\u53d8\u91cf\u7684\u6b63\u5219\u3002
         * @param pathSeparator \u7981\u6b62\u5728\u6a21\u677f\u53d8\u91cf\u4e2d\u5339\u914d\u7684\u8def\u5f84\u5206\u9694\u7b26
         * @return \u5c06 Ant \u6a21\u5f0f\u62c6\u5206\u4e3a\u53ef\u5904\u7406\u6807\u8bb0\u7684\u6b63\u5219
         */
        private static Pattern getGlobPattern(String pathSeparator) {
            String pattern = "\\?|\\*|\\{((?:\\{[^" + pathSeparator + "]+?\\}|[^" + pathSeparator + "{}]|\\\\[{}])+?)\\}";
            return Pattern.compile(pattern);
        }

        /**
         * \u628a\u6307\u5b9a\u8303\u56f4\u7684\u5b57\u7b26\u4f5c\u4e3a\u6b63\u5219\u5b57\u9762\u91cf\u5904\u7406\u3002
         * @param s \u539f\u59cb\u6a21\u5f0f
         * @param start \u5f00\u59cb\u7d22\u5f15\uff0c\u5305\u542b\u8be5\u4f4d\u7f6e
         * @param end \u7ed3\u675f\u7d22\u5f15\uff0c\u4e0d\u542b\u8be5\u4f4d\u7f6e
         * @return \u5df2\u5f15\u7528\u7684\u6b63\u5219\u6587\u672c\uff0c\u8303\u56f4\u4e3a\u7a7a\u65f6\u8fd4\u56de\u7a7a\u4e32
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
         * @return {@code true} if the string matches against the pattern, or {@code false} otherwise.
     * @param str                  待匹配的字符串
     * @param uriTemplateVariables URI 模板变量映射
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

        /** \u7528\u6765\u6bd4\u8f83\u4e0d\u540c\u6a21\u5f0f\u5339\u914d\u7cbe\u786e\u5ea6\u7684\u5b9e\u9645\u8def\u5f84\u3002 */
        private final String path;

        /** \u8def\u5f84\u6216\u6a21\u5f0f\u5404\u6bb5\u4e4b\u95f4\u4f7f\u7528\u7684\u5206\u9694\u7b26\u3002 */
        private final String pathSeparator;

        /**
         * \u521b\u5efa\u4f7f\u7528\u9ed8\u8ba4\u5206\u9694\u7b26\u7684\u6a21\u5f0f\u6bd4\u8f83\u5668\u3002
         * @param path \u5f85\u5339\u914d\u8def\u5f84
         */
        public AntPatternComparator(String path) {
            this(path, DEFAULT_PATH_SEPARATOR);
        }

        /**
         * \u521b\u5efa\u6a21\u5f0f\u6bd4\u8f83\u5668\u3002
         * @param path \u5f85\u5339\u914d\u8def\u5f84
         * @param pathSeparator \u8def\u5f84\u5206\u9694\u7b26
         */
        public AntPatternComparator(String path, String pathSeparator) {
            this.path = path;
            this.pathSeparator = pathSeparator;
        }

        /**
         * Compare two patterns to determine which should match first, i.e. which
         *  is the most specific regarding the current path.
         * @return a negative integer, zero, or a positive integer as pattern1 is
         *  more specific, equally specific, or less specific than pattern2.
         *
         * @param pattern1 \u7b2c\u4e00\u4e2a\u5f85\u6bd4\u8f83\u7684\u8def\u5f84\u6a21\u5f0f
         * @param pattern2 \u7b2c\u4e8c\u4e2a\u5f85\u6bd4\u8f83\u7684\u8def\u5f84\u6a21\u5f0f
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


            /** \u6b63\u5728\u5206\u6790\u548c\u6bd4\u8f83\u7684 Ant \u8def\u5f84\u6a21\u5f0f\u3002 */
            private final String pattern;

            /** \u6a21\u5f0f\u4e2d URI \u6a21\u677f\u53d8\u91cf\u7684\u8ba1\u6570\u3002 */
            private int uriVars;

            /** \u6a21\u5f0f\u4e2d\u5355\u661f\u901a\u914d\u7b26\u7684\u8ba1\u6570\u3002 */
            private int singleWildcards;

            /** \u6a21\u5f0f\u4e2d\u53cc\u661f\u901a\u914d\u7b26\u7684\u8ba1\u6570\u3002 */
            private int doubleWildcards;

            /** \u5f53\u524d\u6a21\u5f0f\u662f\u5426\u5339\u914d\u5168\u90e8\u8def\u5f84\u3002 */
            private boolean catchAllPattern;

            /** \u5f53\u524d\u6a21\u5f0f\u662f\u5426\u4e3a\u4ee5\u53cc\u661f\u7ed3\u675f\u7684\u524d\u7f00\u6a21\u5f0f\u3002 */
            private boolean prefixPattern;


            /** \u6bd4\u8f83\u6a21\u5f0f\u5339\u914d\u7cbe\u786e\u5ea6\u65f6\u4f7f\u7528\u7684\u7f13\u5b58\u957f\u5ea6\u3002 */
            private Integer length;

            /**
             * \u5206\u6790\u8def\u5f84\u6a21\u5f0f\u4e2d\u7684\u6a21\u677f\u3001\u901a\u914d\u7b26\u548c\u540e\u7f00\u7279\u5f81\u3002
             * @param pattern \u5f85\u8bc4\u5206\u7684 Ant \u8def\u5f84\u6a21\u5f0f
             * @param pathSeparator \u5224\u65ad\u901a\u914d\u540e\u7f00\u7684\u8def\u5f84\u5206\u9694\u7b26
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

            /** \u7edf\u8ba1\u6a21\u5f0f\u4e2d\u7684 URI \u6a21\u677f\u53d8\u91cf\u548c\u5355\u661f\u3001\u53cc\u661f\u901a\u914d\u7b26\u3002 */
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
             * \u8bfb\u53d6<code>uriVars</code> \u5b57\u6bb5\u3002
             * @return <code>uriVars</code> \u5b57\u6bb5
             */
            public int getUriVars() {
                return this.uriVars;
            }

            /**
             * \u8bfb\u53d6<code>singleWildcards</code> \u5b57\u6bb5\u3002
             * @return <code>singleWildcards</code> \u5b57\u6bb5
             */
            public int getSingleWildcards() {
                return this.singleWildcards;
            }

            /**
             * \u8bfb\u53d6<code>doubleWildcards</code> \u5b57\u6bb5\u3002
             * @return <code>doubleWildcards</code> \u5b57\u6bb5
             */
            public int getDoubleWildcards() {
                return this.doubleWildcards;
            }

            /**
             * \u5224\u65ad\u6a21\u5f0f\u662f\u5426\u5e94\u5728\u7cbe\u786e\u5ea6\u6392\u5e8f\u4e2d\u6392\u5230\u6700\u672b\u3002
             * @return \u6a21\u5f0f\u4e3a\u7a7a\u6216\u5339\u914d\u5168\u90e8\u8def\u5f84\u65f6\u8fd4\u56de true
             */
            public boolean isLeastSpecific() {
                return (Objects.isNull(this.pattern) || this.catchAllPattern);
            }

            /**
             * \u8bfb\u53d6<code>prefixPattern</code> \u5b57\u6bb5\u3002
             * @return <code>prefixPattern</code> \u5b57\u6bb5
             */
            public boolean isPrefixPattern() {
                return this.prefixPattern;
            }

            /**
             * \u8ba1\u7b97\u6a21\u5f0f\u901a\u914d\u6743\u91cd\uff0c\u53cc\u661f\u8ba1\u4e3a\u4e24\u9879\u3002
             * @return URI \u6a21\u677f\u53d8\u91cf\u3001\u5355\u661f\u548c\u53cc\u661f\u6298\u7b97\u540e\u7684\u8ba1\u6570
             */
            public int getTotalCount() {
                return this.uriVars + this.singleWildcards + (2 * this.doubleWildcards);
            }

            /**
             * Returns the length of the given pattern, where template variables are considered to be 1 long.
             *
             * @return \u6bd4\u8f83\u6a21\u5f0f\u65f6\u4f7f\u7528\u7684\u957f\u5ea6
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

        /** \u5f53\u524d\u5206\u9694\u7b26\u52a0\u5355\u661f\u7684\u6a21\u5f0f\u540e\u7f00\u3002 */
        private final String endsOnWildCard;

        /** \u5f53\u524d\u5206\u9694\u7b26\u52a0\u53cc\u661f\u7684\u6a21\u5f0f\u540e\u7f00\u3002 */
        private final String endsOnDoubleWildCard;

        /**
         * \u9884\u5148\u8ba1\u7b97\u5206\u9694\u7b26\u4e0e\u5355\u661f\u3001\u53cc\u661f\u7684\u7ec4\u5408\u3002
         * @param pathSeparator \u7f13\u5b58\u7684\u8def\u5f84\u5206\u9694\u7b26
         */
        public PathSeparatorPatternCache(String pathSeparator) {
            this.endsOnWildCard = pathSeparator + "*";
            this.endsOnDoubleWildCard = pathSeparator + "**";
        }

        /**
         * \u8bfb\u53d6<code>endsOnWildCard</code> \u5b57\u6bb5\u3002
         * @return <code>endsOnWildCard</code> \u5b57\u6bb5
         */
        public String getEndsOnWildCard() {
            return this.endsOnWildCard;
        }

        /**
         * \u8bfb\u53d6<code>endsOnDoubleWildCard</code> \u5b57\u6bb5\u3002
         * @return <code>endsOnDoubleWildCard</code> \u5b57\u6bb5
         */
        public String getEndsOnDoubleWildCard() {
            return this.endsOnDoubleWildCard;
        }
    }

}
