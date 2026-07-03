package io.ddd4j.extension.excel.easyexcel.style;

import com.alibaba.excel.write.style.row.AbstractRowHeightStyleStrategy;
import org.apache.poi.ss.usermodel.Row;

/**
 * 设置表头的自动调整行高策略
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class CellRowHeightStyleStrategy extends AbstractRowHeightStyleStrategy {

    /**
     * 默认行高（单位：20 分之 1 磅）
     */
    private static final int DEFAULT_ROW_HEIGHT = 20;

    /**
     * 表头行高（单位：20 分之 1 磅），默认 3240/20 = 162 磅
     */
    private int firstRowHeight = 3240;

    public CellRowHeightStyleStrategy() {
    }

    /**
     * 构造函数，指定表头行高
     *
     * @param firstRowHeight 表头行高（单位：20 分之 1 磅）
     */
    public CellRowHeightStyleStrategy(int firstRowHeight) {
        this.firstRowHeight = firstRowHeight;
    }

    @Override
    protected void setHeadColumnHeight(Row row, int relativeRowIndex) {
        //设置主标题行高为17.7
        if (relativeRowIndex == 0) {
            //如果excel需要显示行高为15，那这里就要设置为15*20=300
            row.setHeight((short) firstRowHeight);
        }
    }

    @Override
    protected void setContentColumnHeight(Row row, int relativeRowIndex) {
    }
}
