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
package io.ddd4j.extension.validation;
import java.util.Set;
import java.util.Objects;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Arrays;

import io.ddd4j.kit.lang.StrKit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 不可变文件校验策略。
 */
public final class FileValidationPolicy {

    private final boolean required;
    private final boolean strict;
    private final long maxSizeBytes;
    private final Set<String> allowedExtensions;
    private final Set<String> allowedMimeTypes;

    private FileValidationPolicy(Builder builder) {
        this.required = builder.required;
        this.strict = builder.strict;
        this.maxSizeBytes = builder.maxSizeBytes;
        this.allowedExtensions = immutableNormalized(builder.allowedExtensions);
        this.allowedMimeTypes = immutableNormalized(builder.allowedMimeTypes);
    }

    /** @return 新的策略构建器 */
    public static Builder builder() {
        return new Builder();
    }

    private static Set<String> immutableNormalized(Set<String> values) {
        LinkedHashSet<String> normalized = values.stream()
                .filter(Objects::nonNull)
                .filter(StrKit::isNotBlank)
                .map(value -> StrKit.trim(value).toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(normalized);
    }

    /** @return 是否要求文件非空 */
    public boolean isRequired() {
        return required;
    }

    /** @return 是否检查文件实际类型 */
    public boolean isStrict() {
        return strict;
    }

    /** @return 最大文件字节数，零表示不限制 */
    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    /** @return 允许的扩展名集合 */
    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    /** @return 允许的 MIME 类型集合 */
    public Set<String> getAllowedMimeTypes() {
        return allowedMimeTypes;
    }

    /**
     * 文件校验策略构建器。
     */
    public static final class Builder {

        private boolean required = true;
        private boolean strict = true;
        private long maxSizeBytes = 2L * 1024L * 1024L;
        private Set<String> allowedExtensions = new LinkedHashSet<>();
        private Set<String> allowedMimeTypes = new LinkedHashSet<>();

        /**
         * @param required 是否要求文件非空
         * @return 当前构建器
         */
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * @param strict 是否检查文件实际类型
         * @return 当前构建器
         */
        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        /**
         * @param maxSizeBytes 最大文件字节数，零表示不限制
         * @return 当前构建器
         */
        public Builder maxSizeBytes(long maxSizeBytes) {
            if (maxSizeBytes < 0) {
                throw new IllegalArgumentException("maxSizeBytes must be greater than or equal to zero");
            }
            this.maxSizeBytes = maxSizeBytes;
            return this;
        }

        /**
         * @param allowedExtensions 允许的扩展名
         * @return 当前构建器
         */
        public Builder allowedExtensions(String... allowedExtensions) {
            this.allowedExtensions = new LinkedHashSet<>(Arrays.asList(allowedExtensions));
            return this;
        }

        /**
         * @param allowedMimeTypes 允许的 MIME 类型
         * @return 当前构建器
         */
        public Builder allowedMimeTypes(String... allowedMimeTypes) {
            this.allowedMimeTypes = new LinkedHashSet<>(Arrays.asList(allowedMimeTypes));
            return this;
        }

        /** @return 不可变文件校验策略 */
        public FileValidationPolicy build() {
            return new FileValidationPolicy(this);
        }
    }
}
