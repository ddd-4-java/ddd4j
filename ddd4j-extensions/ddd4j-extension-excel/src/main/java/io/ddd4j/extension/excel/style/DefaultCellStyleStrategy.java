package io.ddd4j.extension.excel.style;

import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;

/**
 * 默认样式策略（修复原 {@code CellStyleStrategy} 的 bug）。
 *
 * <p><b>修复要点</b>：
 * <ul>
 *   <li>原实现 {@code columnIndexes.get(0)} 在传入空列表时 NPE/越界，本类直接基于
 *       {@link ExcelStyleTemplate} 派生样式，不再硬编码"首列特殊"逻辑</li>
 *   <li>样式对象在构造期一次创建，写入阶段共享，避免触发 easyexcel 提示的"6W 样式上限"</li>
 *   <li>"首列特殊"等业务需求请改用 {@link io.ddd4j.extension.excel.export.WriteOptions#addHandler}
 *       注入额外 {@code WriteHandler}</li>
 * </ul>
 *
 * <p>继承 {@link HorizontalCellStyleStrategy} 复用 easyexcel 的水平样式处理流程，
 * 仅在构造期注入预构建的样式对象。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DefaultCellStyleStrategy extends HorizontalCellStyleStrategy {

    /**
     * 用指定模板构造。
     *
     * @param template 样式模板
     */
    public DefaultCellStyleStrategy(ExcelStyleTemplate template) {
        super(buildHeadStyle(template), buildContentStyle(template));
    }

    /**
     * 用默认模板构造。
     */
    public DefaultCellStyleStrategy() {
        this(ExcelStyleTemplate.DEFAULT);
    }

    /**
     * 直接传入两个样式对象（高级用法）。
     *
     * @param headCellStyle    表头样式
     * @param contentCellStyle 内容样式
     */
    public DefaultCellStyleStrategy(WriteCellStyle headCellStyle, WriteCellStyle contentCellStyle) {
        super(headCellStyle, contentCellStyle);
    }

    /**
     * \u6309\u6a21\u677f\u989c\u8272\u6784\u5efa\u5e26\u8fb9\u6846\u3001\u5c45\u4e2d\u5b57\u4f53\u548c\u586b\u5145\u8272\u7684\u8868\u5934\u6837\u5f0f\u3002
     * @param t \u63d0\u4f9b\u8868\u5934\u989c\u8272\u7684\u6837\u5f0f\u6a21\u677f
     * @return \u8868\u5934\u53ef\u590d\u7528\u7684\u5355\u5143\u683c\u6837\u5f0f
     */
    private static WriteCellStyle buildHeadStyle(ExcelStyleTemplate t) {
        WriteCellStyle style = new WriteCellStyle();
        style.setFillForegroundColor(t.headerColor());
        style.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        style.setHorizontalAlignment(HorizontalAlignment.CENTER);
        applyBorder(style);

        WriteFont font = new WriteFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        style.setWriteFont(font);
        return style;
    }

    /**
     * \u6309\u6a21\u677f\u5185\u5bb9\u5bf9\u9f50\u65b9\u5f0f\u6784\u5efa\u5e26\u8fb9\u6846\u7684\u6b63\u6587\u6837\u5f0f\u3002
     * @param t \u63d0\u4f9b\u6b63\u6587\u5bf9\u9f50\u65b9\u5f0f\u7684\u6837\u5f0f\u6a21\u677f
     * @return \u6b63\u6587\u53ef\u590d\u7528\u7684\u5355\u5143\u683c\u6837\u5f0f
     */
    private static WriteCellStyle buildContentStyle(ExcelStyleTemplate t) {
        WriteCellStyle style = new WriteCellStyle();
        style.setHorizontalAlignment(t.contentAlign());
        applyBorder(style);

        WriteFont font = new WriteFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 11);
        style.setWriteFont(font);
        return style;
    }

    /**
     * \u7ed9\u5355\u5143\u683c\u56db\u8fb9\u8bbe\u7f6e\u7ec6\u7ebf\u8fb9\u6846\u3002
     * @param style \u5f85\u66f4\u65b0\u7684\u5355\u5143\u683c\u6837\u5f0f
     */
    private static void applyBorder(WriteCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
