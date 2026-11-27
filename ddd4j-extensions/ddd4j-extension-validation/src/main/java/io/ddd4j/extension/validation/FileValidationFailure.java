package io.ddd4j.extension.validation;

/**
 * 文件校验失败类型，可由各框架转换为统一业务错误码。
 */
public enum FileValidationFailure {
    /** 保存 <code>EMPTY</code> 对应的 <code>FileValidationFailure</code> 数据。 */
    EMPTY,
    /** 保存 <code>SIZE EXCEEDED</code> 对应的 <code>FileValidationFailure</code> 数据。 */
    SIZE_EXCEEDED,
    /** 保存 <code>EXTENSION NOT ALLOWED</code> 对应的 <code>FileValidationFailure</code> 数据。 */
    EXTENSION_NOT_ALLOWED,
    /** 保存 <code>MIME TYPE NOT ALLOWED</code> 对应的 <code>FileValidationFailure</code> 数据。 */
    MIME_TYPE_NOT_ALLOWED,
    /** 保存 <code>TYPE UNDETECTABLE</code> 对应的 <code>FileValidationFailure</code> 数据。 */
    TYPE_UNDETECTABLE,
    /** 保存 <code>SIGNATURE MISMATCH</code> 对应的 <code>FileValidationFailure</code> 数据。 */
    SIGNATURE_MISMATCH,
    /** 保存 <code>CONTENT REJECTED</code> 对应的 <code>FileValidationFailure</code> 数据。 */
    CONTENT_REJECTED,
    /** 保存 <code>READ ERROR</code> 对应的 <code>FileValidationFailure</code> 数据。 */
    READ_ERROR
}
