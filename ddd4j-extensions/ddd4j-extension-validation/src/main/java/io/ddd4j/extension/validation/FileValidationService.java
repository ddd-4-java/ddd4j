package io.ddd4j.extension.validation;

import io.ddd4j.kit.lang.StrKit;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 框架无关的上传文件校验服务。
 */
public final class FileValidationService {

    /** 保存 <code>file Type Detector</code> 对应的 <code>FileTypeDetector</code> 数据。 */
    private final FileTypeDetector fileTypeDetector;
    /** 保存 <code>content Check Providers</code> 对应的 <code>Collection&lt;FileContentCheckProvider&gt;</code> 数据。 */
    private final Collection<FileContentCheckProvider> contentCheckProviders;

    /** 初始化 <code>FileValidationService</code> 并委托其默认构造路径。 */
    public FileValidationService() {
        this(new OfficeFileTypeDetector(), Collections.emptyList());
    }

    /**
     * 初始化 <code>FileValidationService</code> 使用的 <code>fileTypeDetector</code>、<code>contentCheckProviders</code>。
     *
     * @param fileTypeDetector 传入此操作的 <code>FileTypeDetector</code> 类型 <code>fileTypeDetector</code>
     * @param contentCheckProviders 传入此操作的 <code>Collection&lt;FileContentCheckProvider&gt;</code> 类型 <code>contentCheckProviders</code>
     */
    public FileValidationService(FileTypeDetector fileTypeDetector,
                                 Collection<FileContentCheckProvider> contentCheckProviders) {
        this.fileTypeDetector = Objects.requireNonNull(fileTypeDetector, "fileTypeDetector must not be null");
        this.contentCheckProviders = ListSupport.copyOf(contentCheckProviders);
    }

    /**
     * 按策略校验单个文件。
     *
     * @param file   文件，可以为空
     * @param policy 校验策略
     * @return 校验结果
     */
    public FileValidationResult validate(ValidatableFile file, FileValidationPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        if (Objects.isNull(file) || file.size() <= 0) {
            return policy.isRequired()
                    ? FileValidationResult.invalid(FileValidationFailure.EMPTY, null)
                    : FileValidationResult.valid(null);
        }
        if (policy.getMaxSizeBytes() > 0 && file.size() > policy.getMaxSizeBytes()) {
            return FileValidationResult.invalid(FileValidationFailure.SIZE_EXCEEDED, null);
        }

        String declaredExtension = extensionOf(file.fileName());
        if (!policy.getAllowedExtensions().isEmpty()
                && !policy.getAllowedExtensions().contains(declaredExtension)) {
            return FileValidationResult.invalid(FileValidationFailure.EXTENSION_NOT_ALLOWED, null);
        }

        try {
            DetectedFileType detectedType = null;
            if (policy.isStrict()) {
                Optional<DetectedFileType> detected = fileTypeDetector.detect(file);
                if (detected.isEmpty()) {
                    return FileValidationResult.invalid(FileValidationFailure.TYPE_UNDETECTABLE, null);
                }
                detectedType = detected.get();
                if (!declaredExtension.equals(normalize(detectedType.extension()))) {
                    return FileValidationResult.invalid(FileValidationFailure.SIGNATURE_MISMATCH, detectedType);
                }
            }

            String mimeType = policy.isStrict() && Objects.nonNull(detectedType)
                    ? normalize(detectedType.mimeType())
                    : normalize(file.contentType());
            if (!policy.getAllowedMimeTypes().isEmpty()
                    && !policy.getAllowedMimeTypes().contains(mimeType)) {
                return FileValidationResult.invalid(FileValidationFailure.MIME_TYPE_NOT_ALLOWED, detectedType);
            }

            if (Objects.nonNull(detectedType) && !checkContent(file, detectedType)) {
                return FileValidationResult.invalid(FileValidationFailure.CONTENT_REJECTED, detectedType);
            }
            return FileValidationResult.valid(detectedType);
        } catch (IOException | RuntimeException exception) {
            return FileValidationResult.invalid(FileValidationFailure.READ_ERROR, null);
        }
    }

    /**
     * 校验失败时抛出统一异常。
     *
     * @param file   文件
     * @param policy 校验策略
     * @return 原文件
     */
    public ValidatableFile validateOrThrow(ValidatableFile file, FileValidationPolicy policy) {
        FileValidationResult result = validate(file, policy);
        if (!result.valid()) {
            throw new FileValidationException(result.failure());
        }
        return file;
    }

    /**
     * 执行 <code>check Content</code>，调用 <code>provider.supports</code>、<code>detectedType.extension</code>、<code>provider.check</code>。
     *
     * @param file 传入此操作的 <code>ValidatableFile</code> 类型 <code>file</code>
     * @param detectedType 传入此操作的 <code>DetectedFileType</code> 类型 <code>detectedType</code>
     * @return 按条件返回 <code>false</code>、<code>true</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private boolean checkContent(ValidatableFile file, DetectedFileType detectedType) throws IOException {
        for (FileContentCheckProvider provider : contentCheckProviders) {
            if (provider.supports(detectedType.extension()) && !provider.check(file, detectedType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行 <code>extension Of</code>，调用 <code>StrKit.isBlank</code>、<code>fileName.lastIndexOf</code>、<code>fileName.length</code>。
     *
     * @param fileName 传入此操作的 <code>String</code> 类型 <code>fileName</code>
     * @return 按条件返回 <code>""</code>、<code>""</code>、<code>normalize(fileName.substring(extensionIndex + 1))</code>
     */
    private String extensionOf(String fileName) {
        if (Objects.isNull(fileName) || StrKit.isBlank(fileName)) {
            return "";
        }
        int separatorIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= separatorIndex || extensionIndex == fileName.length() - 1) {
            return "";
        }
        return normalize(fileName.substring(extensionIndex + 1));
    }

    /**
     * 执行 <code>normalize</code>，调用 <code>StrKit.trim</code>。
     *
     * @param value 由当前成员保存或用于计算的值
     * @return 返回表达式 <code>Objects.isNull(value) ? "" : StrKit.trim(value).toLowerCase(Locale.ROOT)</code>
     */
    private String normalize(String value) {
        return Objects.isNull(value) ? "" : StrKit.trim(value).toLowerCase(Locale.ROOT);
    }

    /** 集中保存 <code>ListSupport</code> 的声明，供当前模块使用。 */
    private static final class ListSupport {

        /** 初始化 <code>ListSupport</code> 并委托其默认构造路径。 */
        private ListSupport() {
        }

        /**
         * 执行 <code>copy Of</code>，调用 <code>Collections.emptyList</code>、<code>List.copyOf</code>。
         *
         * @param <T> 当前操作使用的泛型类型
         * @param source 传入此操作的 <code>Collection&lt;T&gt;</code> 类型 <code>source</code>
         * @return 返回表达式 <code>Objects.isNull(source) ? Collections.emptyList() : java.util.List.copyOf(source)</code>
         */
        private static <T> Collection<T> copyOf(Collection<T> source) {
            return Objects.isNull(source) ? Collections.emptyList() : java.util.List.copyOf(source);
        }
    }
}
