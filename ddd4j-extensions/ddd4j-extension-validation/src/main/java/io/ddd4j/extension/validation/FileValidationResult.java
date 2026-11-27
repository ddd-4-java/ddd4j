package io.ddd4j.extension.validation;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 文件校验结果。
 */

public final class FileValidationResult {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** 保存 <code>valid</code> 对应的 <code>boolean</code> 数据。 */
    private final boolean valid;

    /** 保存 <code>failure</code> 对应的 <code>FileValidationFailure</code> 数据。 */
    private final FileValidationFailure failure;

    /** 保存 <code>detected Type</code> 对应的 <code>DetectedFileType</code> 数据。 */
    private final DetectedFileType detectedType;

    /**
     * 创建成功结果。
     *
     * @param detectedType 检测类型
     * @return 成功结果
     */
    public static FileValidationResult valid(DetectedFileType detectedType) {
        return new FileValidationResult(true, null, detectedType);
    }

    /**
     * 创建失败结果。
     *
     * @param failure      失败原因
     * @param detectedType 已检测类型
     * @return 失败结果
     */
    public static FileValidationResult invalid(FileValidationFailure failure, DetectedFileType detectedType) {
        return new FileValidationResult(false, failure, detectedType);
    }

    /**
     * 初始化 <code>FileValidationResult</code> 使用的 <code>valid</code>、<code>failure</code>、<code>detectedType</code>。
     *
     * @param valid \u662f\u5426\u901a\u8fc7
     * @param failure \u5931\u8d25\u539f\u56e0\uff0c\u901a\u8fc7\u65f6\u4e3a\u7a7a
     * @param detectedType \u5185\u5bb9\u68c0\u6d4b\u7ed3\u679c\uff0c\u53ef\u4ee5\u4e3a\u7a7a
     */

    @JsonCreator()
    public FileValidationResult(@JsonProperty("valid") boolean valid, @JsonProperty("failure") FileValidationFailure failure, @JsonProperty("detectedType") DetectedFileType detectedType) {
        this.valid = valid;
        this.failure = failure;
        this.detectedType = detectedType;
    }

    /**
     * 读取<code>valid</code> 的当前值。
     *
     * @return 返回表达式 <code>valid</code>
     */
    @JsonProperty("valid")
    public boolean valid() {
        return valid;
    }

    /**
     * 读取<code>failure</code> 的当前值。
     *
     * @return 返回表达式 <code>failure</code>
     */
    @JsonProperty("failure")
    public FileValidationFailure failure() {
        return failure;
    }

    /**
     * 读取<code>detectedType</code> 的当前值。
     *
     * @return 返回表达式 <code>detectedType</code>
     */
    @JsonProperty("detectedType")
    public DetectedFileType detectedType() {
        return detectedType;
    }

    /**
     * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
     *
     * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
     * @return 按条件返回 <code>true</code>、<code>false</code>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        FileValidationResult other = (FileValidationResult) obj;
        return this.valid == other.valid && Objects.equals(this.failure, other.failure) && Objects.equals(this.detectedType, other.detectedType);
    }

    /**
     * 读取<code>result</code> 的当前值。
     *
     * @return 返回表达式 <code>result</code>
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Boolean.hashCode(valid);
        result = 31 * result + Objects.hashCode(failure);
        result = 31 * result + Objects.hashCode(detectedType);
        return result;
    }

    /**
     * 按 <code>to String</code> 的契约计算并返回结果。
     *
     * @return 当前操作得到的 <code>String</code> 结果
     */
    @Override
    public String toString() {
        return "FileValidationResult[valid=" + valid + ", failure=" + failure + ", detectedType=" + detectedType + "]";
    }
}
