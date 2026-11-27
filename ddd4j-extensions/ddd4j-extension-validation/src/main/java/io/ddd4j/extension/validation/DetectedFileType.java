package io.ddd4j.extension.validation;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 根据文件内容识别出的真实文件类型。
 */

public final class DetectedFileType {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** 保存 <code>extension</code> 对应的 <code>String</code> 数据。 */
    private final String extension;

    /** 保存 <code>mime Type</code> 对应的 <code>String</code> 数据。 */
    private final String mimeType;

    /**
     * 初始化 <code>DetectedFileType</code> 使用的 <code>extension</code>、<code>mimeType</code>。
     *
     * @param extension \u771f\u5b9e\u6269\u5c55\u540d\uff0c\u4e0d\u542b\u70b9\u53f7
     * @param mimeType \u771f\u5b9e MIME \u7c7b\u578b
     */

    @JsonCreator()
    public DetectedFileType(@JsonProperty("extension") String extension, @JsonProperty("mimeType") String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    /**
     * 读取<code>extension</code> 的当前值。
     *
     * @return 返回表达式 <code>extension</code>
     */
    @JsonProperty("extension")
    public String extension() {
        return extension;
    }

    /**
     * 读取<code>mimeType</code> 的当前值。
     *
     * @return 返回表达式 <code>mimeType</code>
     */
    @JsonProperty("mimeType")
    public String mimeType() {
        return mimeType;
    }

    /**
     * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
     *
     * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
     * @return 按条件返回 <code>true</code>、<code>false</code>、<code>Objects.equals(this.extension, other.extension) &amp;&amp; Objects.equals(this.mimeType, other.mimeType)</code>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        DetectedFileType other = (DetectedFileType) obj;
        return Objects.equals(this.extension, other.extension) && Objects.equals(this.mimeType, other.mimeType);
    }

    /**
     * 读取<code>result</code> 的当前值。
     *
     * @return 返回表达式 <code>result</code>
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(extension);
        result = 31 * result + Objects.hashCode(mimeType);
        return result;
    }

    /**
     * 按 <code>to String</code> 的契约计算并返回结果。
     *
     * @return 当前操作得到的 <code>String</code> 结果
     */
    @Override
    public String toString() {
        return "DetectedFileType[extension=" + extension + ", mimeType=" + mimeType + "]";
    }
}
