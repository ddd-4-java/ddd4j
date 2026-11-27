package io.ddd4j.extension.excel.style;

import com.alibaba.excel.write.style.row.AbstractRowHeightStyleStrategy;
import org.apache.poi.ss.usermodel.Row;

/**
 * 默认行高策略。
 *
 * <p>替换原 {@code CellRowHeightStyleStrategy}：
 * <ul>
 *   <li>从包 {@code io.ddd4j.extension.excel.easyexcel.style} 下移到 {@code style}</li>
 *   <li>方法访问修饰符保持不变，与 easyexcel {@link AbstractRowHeightStyleStrategy} 一致</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DefaultRowHeightStyleStrategy extends AbstractRowHeightStyleStrategy {

    /**
     * 默认表头行高（1/20 磅）。
     */
    public static final short DEFAULT_HEADER_ROW_HEIGHT = 600;

    /** \u9996\u884c\u8868\u5934\u4f7f\u7528\u7684\u9ad8\u5ea6\u3002 */
    private final short headerRowHeight;

    /**
     * 默认构造（使用 {@link #DEFAULT_HEADER_ROW_HEIGHT}）。
     */
    public DefaultRowHeightStyleStrategy() {
        this(DEFAULT_HEADER_ROW_HEIGHT);
    }

    /**
     * 指定表头行高构造。
     *
     * @param headerRowHeight 表头行高（1/20 磅）
     */
    public DefaultRowHeightStyleStrategy(short headerRowHeight) {
        this.headerRowHeight = headerRowHeight;
    }

    /**
     * \u4ec5\u4e3a\u7b2c\u4e00\u884c\u8868\u5934\u8bbe\u7f6e\u914d\u7f6e\u7684\u9ad8\u5ea6\u3002
     * @param row \u5f53\u524d\u6b63\u5728\u5199\u5165\u7684\u884c
     * @param relativeRowIndex \u76f8\u5bf9\u8868\u5934\u6216\u5185\u5bb9\u8d77\u70b9\u7684\u884c\u7d22\u5f15
     */
    @Override
    protected void setHeadColumnHeight(Row row, int relativeRowIndex) {
        if (relativeRowIndex == 0) {
            row.setHeight(headerRowHeight);
        }
    }

    /**
     * \u4fdd\u7559\u5185\u5bb9\u884c\u7684 Excel \u9ed8\u8ba4\u884c\u9ad8\u3002
     * @param row \u5f53\u524d\u6b63\u5728\u5199\u5165\u7684\u884c
     * @param relativeRowIndex \u76f8\u5bf9\u8868\u5934\u6216\u5185\u5bb9\u8d77\u70b9\u7684\u884c\u7d22\u5f15
     */
    @Override
    protected void setContentColumnHeight(Row row, int relativeRowIndex) {
        // 内容行使用 Excel 默认行高
    }
}
