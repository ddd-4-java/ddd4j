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

    /** \u7f13\u5b58\u5e76\u590d\u7528\u7684\u4ea4\u66ff\u5e95\u8272\u5355\u5143\u683c\u6837\u5f0f\u3002 */
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

    /**
     * \u5728\u975e\u8868\u5934\u7684\u5076\u6570\u76f8\u5bf9\u884c\u7ed9\u5185\u5bb9\u5355\u5143\u683c\u5e94\u7528\u4ea4\u66ff\u5e95\u8272\u3002
     * @param context \u5f53\u524d\u5df2\u5199\u5165\u7684\u5355\u5143\u683c\u53ca\u5176\u4f4d\u7f6e
     */
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
