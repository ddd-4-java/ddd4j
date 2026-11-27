package io.ddd4j.extension.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 框架适配层可直接使用的不可变文件对象。
 */
public final class DefaultValidatableFile implements ValidatableFile {

    /** 保存 <code>file Name</code> 对应的 <code>String</code> 数据。 */
    private final String fileName;
    /** 保存 <code>content Type</code> 对应的 <code>String</code> 数据。 */
    private final String contentType;
    /** 保存 <code>size</code> 对应的 <code>long</code> 数据。 */
    private final long size;
    /** 保存 <code>input Stream Source</code> 对应的 <code>InputStreamSource</code> 数据。 */
    private final InputStreamSource inputStreamSource;

    /**
     * 初始化 <code>DefaultValidatableFile</code> 使用的 <code>fileName</code>、<code>contentType</code>、<code>size</code>、<code>inputStreamSource</code>。
     *
     * @param fileName 传入此操作的 <code>String</code> 类型 <code>fileName</code>
     * @param contentType 传入此操作的 <code>String</code> 类型 <code>contentType</code>
     * @param size 传入此操作的 <code>long</code> 类型 <code>size</code>
     * @param inputStreamSource 传入此操作的 <code>InputStreamSource</code> 类型 <code>inputStreamSource</code>
     */
    public DefaultValidatableFile(String fileName, String contentType, long size,
                                  InputStreamSource inputStreamSource) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.inputStreamSource = Objects.requireNonNull(inputStreamSource, "inputStreamSource must not be null");
    }

    /**
     * 读取<code>fileName</code> 的当前值。
     *
     * @return 返回表达式 <code>fileName</code>
     */
    @Override
    public String fileName() {
        return fileName;
    }

    /**
     * 读取<code>contentType</code> 的当前值。
     *
     * @return 返回表达式 <code>contentType</code>
     */
    @Override
    public String contentType() {
        return contentType;
    }

    /**
     * 读取<code>size</code> 的当前值。
     *
     * @return 返回表达式 <code>size</code>
     */
    @Override
    public long size() {
        return size;
    }

    /**
     * 执行 <code>open Stream</code>，调用 <code>inputStreamSource.openStream</code>。
     *
     * @return 返回表达式 <code>inputStreamSource.openStream()</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    @Override
    public InputStream openStream() throws IOException {
        return inputStreamSource.openStream();
    }
}
