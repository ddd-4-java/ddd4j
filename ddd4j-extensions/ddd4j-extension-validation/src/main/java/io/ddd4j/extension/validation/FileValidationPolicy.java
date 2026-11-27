package io.ddd4j.extension.validation;

import io.ddd4j.kit.lang.StrKit;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 不可变文件校验策略。
 */
public final class FileValidationPolicy {

    /** 保存 <code>required</code> 对应的 <code>boolean</code> 数据。 */
    private final boolean required;
    /** 保存 <code>strict</code> 对应的 <code>boolean</code> 数据。 */
    private final boolean strict;
    /** 保存 <code>max Size Bytes</code> 对应的 <code>long</code> 数据。 */
    private final long maxSizeBytes;
    /** 保存 <code>allowed Extensions</code> 对应的 <code>Set&lt;String&gt;</code> 数据。 */
    private final Set<String> allowedExtensions;
    /** 保存 <code>allowed Mime Types</code> 对应的 <code>Set&lt;String&gt;</code> 数据。 */
    private final Set<String> allowedMimeTypes;

    /**
     * 初始化 <code>FileValidationPolicy</code> 使用的 <code>required</code>、<code>strict</code>、<code>maxSizeBytes</code>、<code>allowedExtensions</code>、<code>allowedMimeTypes</code>。
     *
     * @param builder 传入此操作的 <code>Builder</code> 类型 <code>builder</code>
     */
    private FileValidationPolicy(Builder builder) {
        this.required = builder.required;
        this.strict = builder.strict;
        this.maxSizeBytes = builder.maxSizeBytes;
        this.allowedExtensions = immutableNormalized(builder.allowedExtensions);
        this.allowedMimeTypes = immutableNormalized(builder.allowedMimeTypes);
    }

    /**
     * 按 <code>builder</code> 的契约计算并返回结果。
     *
     * @return 当前操作得到的 <code>Builder</code> 结果
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 读取<code>required</code> 的当前值。
     *
     * @return 返回表达式 <code>required</code>
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * 读取<code>strict</code> 的当前值。
     *
     * @return 返回表达式 <code>strict</code>
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * 读取<code>maxSizeBytes</code> 的当前值。
     *
     * @return 返回表达式 <code>maxSizeBytes</code>
     */
    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    /**
     * 读取<code>allowedExtensions</code> 的当前值。
     *
     * @return 返回表达式 <code>allowedExtensions</code>
     */
    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    /**
     * 读取<code>allowedMimeTypes</code> 的当前值。
     *
     * @return 返回表达式 <code>allowedMimeTypes</code>
     */
    public Set<String> getAllowedMimeTypes() {
        return allowedMimeTypes;
    }

    /**
     * 执行 <code>immutable Normalized</code>，调用 <code>values.stream</code>、<code>StrKit.trim</code>、<code>Collectors.toCollection</code>。
     *
     * @param values 查找指定值时使用的候选集合
     * @return 返回表达式 <code>Collections.unmodifiableSet(normalized)</code>
     */
    private static Set<String> immutableNormalized(Set<String> values) {
        LinkedHashSet<String> normalized = values.stream()
                .filter(Objects::nonNull)
                .filter(StrKit::isNotBlank)
                .map(value -> StrKit.trim(value).toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(normalized);
    }

    /**
     * 文件校验策略构建器。
     */
    public static final class Builder {

        /** 保存 <code>true</code>，供 <code>Builder</code> 的处理流程复用。 */
        private boolean required = true;
        /** 保存 <code>true</code>，供 <code>Builder</code> 的处理流程复用。 */
        private boolean strict = true;
        /** 保存 <code>2L * 1024L * 1024L</code>，供 <code>Builder</code> 的处理流程复用。 */
        private long maxSizeBytes = 2L * 1024L * 1024L;
        /** 保存 <code>allowed Extensions</code> 对应的 <code>Set&lt;String&gt;</code> 数据。 */
        private Set<String> allowedExtensions = new LinkedHashSet<>();
        /** 保存 <code>allowed Mime Types</code> 对应的 <code>Set&lt;String&gt;</code> 数据。 */
        private Set<String> allowedMimeTypes = new LinkedHashSet<>();

        /**
         * 读取<code>this</code> 的当前值。
         *
         * @param required 传入此操作的 <code>boolean</code> 类型 <code>required</code>
         * @return 返回表达式 <code>this</code>
         */
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * 读取<code>this</code> 的当前值。
         *
         * @param strict 传入此操作的 <code>boolean</code> 类型 <code>strict</code>
         * @return 返回表达式 <code>this</code>
         */
        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        /**
         * 读取<code>this</code> 的当前值。
         *
         * @param maxSizeBytes 传入此操作的 <code>long</code> 类型 <code>maxSizeBytes</code>
         * @return 返回表达式 <code>this</code>
         */
        public Builder maxSizeBytes(long maxSizeBytes) {
            if (maxSizeBytes < 0) {
                throw new IllegalArgumentException("maxSizeBytes must be greater than or equal to zero");
            }
            this.maxSizeBytes = maxSizeBytes;
            return this;
        }

        /**
         * 读取<code>this</code> 的当前值。
         *
         * @param allowedExtensions 传入此操作的 <code>String[]</code> 类型 <code>allowedExtensions</code>
         * @return 返回表达式 <code>this</code>
         */
        public Builder allowedExtensions(String... allowedExtensions) {
            this.allowedExtensions = new LinkedHashSet<>(Arrays.asList(allowedExtensions));
            return this;
        }

        /**
         * 读取<code>this</code> 的当前值。
         *
         * @param allowedMimeTypes 传入此操作的 <code>String[]</code> 类型 <code>allowedMimeTypes</code>
         * @return 返回表达式 <code>this</code>
         */
        public Builder allowedMimeTypes(String... allowedMimeTypes) {
            this.allowedMimeTypes = new LinkedHashSet<>(Arrays.asList(allowedMimeTypes));
            return this;
        }

        /**
         * 按 <code>build</code> 的契约计算并返回结果。
         *
         * @return 当前操作得到的 <code>FileValidationPolicy</code> 结果
         */
        public FileValidationPolicy build() {
            return new FileValidationPolicy(this);
        }
    }
}
