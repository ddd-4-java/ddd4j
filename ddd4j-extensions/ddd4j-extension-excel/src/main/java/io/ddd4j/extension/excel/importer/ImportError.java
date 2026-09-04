package io.ddd4j.extension.excel.importer;

import com.alibaba.excel.exception.ExcelDataConvertException;
import lombok.Getter;

import java.util.Optional;

/**
 * 单行导入错误记录（不可变）。
 *
 * <p>对应 easyexcel 监听器 {@code onException(Exception, AnalysisContext)} 中捕获的异常，
 * 提取关键信息（行号、列号、原始值、错误类型）封装为可读结构。
 *
 * <p>设计原则：导入永远不抛异常到调用方，错误统一通过 {@link ImportResult#getErrors()} 返回。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public final class ImportError {

    /**
     * 出错行号（从 0 开始，含表头行）。
     */
    private final long rowIndex;

    /**
     * 出错列号（-1 表示非单元格级错误）。
     */
    private final int columnIndex;

    /**
     * 原始单元格内容（可能为 null）。
     */
    private final String cellData;

    /**
     * 错误消息（已 {@code getMessage()}）。
     */
    private final String message;

    /**
     * 错误类型名称（短类名）。
     */
    private final String errorType;

    private ImportError(long rowIndex, int columnIndex, String cellData, String message, String errorType) {
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
        this.cellData = cellData;
        this.message = message;
        this.errorType = errorType;
    }

    /**
     * 从任意异常构造错误记录。
     *
     * @param rowIndex  行号
     * @param exception 异常
     * @return {@link ImportError}
     */
    public static ImportError of(long rowIndex, Exception exception) {
        if (exception instanceof ExcelDataConvertException) {
            ExcelDataConvertException edce = (ExcelDataConvertException) exception;
            return new ImportError(
                    edce.getRowIndex(),
                    edce.getColumnIndex(),
                    Optional.ofNullable(edce.getCellData()).map(Object::toString).orElse(null),
                    edce.getMessage(),
                    edce.getClass().getSimpleName()
            );
        }
        return new ImportError(rowIndex, -1, null,
                Optional.ofNullable(exception.getMessage()).orElse(exception.getClass().getSimpleName()),
                exception.getClass().getSimpleName());
    }

    /**
     * 业务校验失败时手动构造错误记录。
     *
     * @param rowIndex 行号
     * @param message  错误消息
     * @return {@link ImportError}
     */
    public static ImportError ofValidation(long rowIndex, String message) {
        return new ImportError(rowIndex, -1, null, message, "ValidationFailed");
    }

    @Override
    public String toString() {
        return "ImportError{row=" + rowIndex + ", col=" + columnIndex
                + ", type=" + errorType + ", msg=" + message + "}";
    }
}
