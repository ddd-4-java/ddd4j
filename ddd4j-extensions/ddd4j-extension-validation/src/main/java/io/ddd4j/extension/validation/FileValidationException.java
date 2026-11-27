package io.ddd4j.extension.validation;

import java.util.Objects;

/**
 * 文件未通过校验时由适配层使用的异常。
 */
public class FileValidationException extends RuntimeException {

    /** 保存 <code>failure</code> 对应的 <code>FileValidationFailure</code> 数据。 */
    private final FileValidationFailure failure;

    /**
     * 初始化 <code>FileValidationException</code> 使用的 <code>failure</code>。
     *
     * @param failure 传入此操作的 <code>FileValidationFailure</code> 类型 <code>failure</code>
     */
    public FileValidationException(FileValidationFailure failure) {
        super("Uploaded file validation failed: " + Objects.requireNonNull(failure, "failure must not be null"));
        this.failure = failure;
    }

    /**
     * 读取<code>failure</code> 的当前值。
     *
     * @return 返回表达式 <code>failure</code>
     */
    public FileValidationFailure getFailure() {
        return failure;
    }
}
