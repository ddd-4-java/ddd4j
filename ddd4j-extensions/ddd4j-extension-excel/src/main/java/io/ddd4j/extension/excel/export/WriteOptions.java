package io.ddd4j.extension.excel.export;

import com.alibaba.excel.write.handler.WriteHandler;
import io.ddd4j.extension.excel.style.ExcelStyleTemplate;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Excel 导出选项（声明式参数聚合）。
 *
 * <p>把原先散落在调用点的 {@code registerWriteHandler(...)} 拼接收敛到一个 POJO，
 * 让样式/列宽/冻结/斑马线等选择由"配置驱动"，符合 OCP 与 DRY。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * WriteOptions options = WriteOptions.defaults()
 *     .setSheetName("订单")
 *     .setFreezeHeader(true)
 *     .setAlternatingRow(true)
 *     .setStyleTemplate(ExcelStyleTemplate.FINANCE)
 *     .addHandler(new CommentWriteHandler());   // 用户自定义扩展点
 *
 * byte[] bytes = ExcelKit.export(OrderVO.class, data, options);
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class WriteOptions {

    /**
     * Sheet 名称，默认 "Sheet1"。
     */
    private String sheetName = "Sheet1";

    /**
     * 是否自动适配列宽（默认 true）。
     */
    private boolean autoSizeColumn = true;

    /**
     * 是否冻结表头（默认 false）。
     */
    private boolean freezeHeader = false;

    /**
     * 是否启用斑马线（默认 false）。
     */
    private boolean alternatingRow = false;

    /**
     * 表头行高（1/20 磅，默认 600 = 30 磅）。
     */
    private short headerRowHeight = 600;

    /**
     * 样式模板（默认 DEFAULT）。
     */
    private ExcelStyleTemplate styleTemplate = ExcelStyleTemplate.DEFAULT;

    /**
     * 用户自定义扩展点（按调用顺序追加）。
     */
    private List<WriteHandler> extraHandlers = new ArrayList<>();

    /**
     * 默认配置。
     *
     * @return 新实例
     */
    public static WriteOptions defaults() {
        return new WriteOptions();
    }

    /**
     * 追加一个 {@link WriteHandler}（链式）。
     *
     * @param handler 处理器
     * @return this
     */
    public WriteOptions addHandler(WriteHandler handler) {
        if (Objects.nonNull(handler)) {
            this.extraHandlers.add(handler);
        }
        return this;
    }
}
