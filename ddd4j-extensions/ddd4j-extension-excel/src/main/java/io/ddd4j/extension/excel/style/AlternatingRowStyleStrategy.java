package io.ddd4j.extension.excel.style;

import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.util.Objects;

/**
 * 斑马线（隔行变色）策略。
 *
 * <p>偶数数据行（rowIndex % 2 == 0）填充浅灰色底，奇数行保持默认。
 * 仅作用于<b>内容单元格</b>（不动表头）。
 *
 * <pre>{@code
 * EasyExcel.write(out, OrderVO.class)
 *     .registerWriteHandler(new AlternatingRowStyleStrategy())
 *     .sheet("订单").doWrite(data);
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AlternatingRowStyleStrategy implements CellWriteHandler {

    private final WriteCellStyle zebraStyle;

    /**
     * 默认斑马线颜色（IndexedColors.GREY_25_PERCENT）。
     */
    public AlternatingRowStyleStrategy() {
        this(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    /**
     * 自定义斑马线颜色（POI IndexedColors 索引）。
     *
     * @param colorIndex POI 颜色索引
     */
    public AlternatingRowStyleStrategy(short colorIndex) {
        WriteCellStyle style = new WriteCellStyle();
        style.setFillForegroundColor(colorIndex);
        style.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        this.zebraStyle = style;
    }

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        // 跳过表头
        if (Boolean.TRUE.equals(context.getHead())) {
            return;
        }
        // relativeRowIndex 在内容区域从 0 开始；偶数行加底色
        int relativeRowIndex = Objects.isNull(context.getRelativeRowIndex()) ? -1 : context.getRelativeRowIndex();
        if (relativeRowIndex >= 0 && relativeRowIndex % 2 == 0) {
            WriteCellStyle.merge(zebraStyle, context.getFirstCellData().getOrCreateStyle());
        }
    }
}
