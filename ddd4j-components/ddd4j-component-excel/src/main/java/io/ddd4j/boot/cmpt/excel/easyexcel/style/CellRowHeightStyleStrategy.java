package io.ddd4j.boot.cmpt.excel.easyexcel.style;

import com.alibaba.excel.write.style.row.AbstractRowHeightStyleStrategy;
import org.apache.poi.ss.usermodel.Row;

/**
 * 设置表头的自动调整行高策略
 */
public class CellRowHeightStyleStrategy extends AbstractRowHeightStyleStrategy {

    private static final int DEFAULT_ROW_HEIGHT = 20;

    private int firstRowHeight = 3240;

    public CellRowHeightStyleStrategy() {
    }

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
