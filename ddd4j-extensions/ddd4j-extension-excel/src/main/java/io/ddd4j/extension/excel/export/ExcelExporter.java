package io.ddd4j.extension.excel.export;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.builder.ExcelWriterSheetBuilder;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.extension.excel.style.AlternatingRowStyleStrategy;
import io.ddd4j.extension.excel.style.DefaultCellStyleStrategy;
import io.ddd4j.extension.excel.style.DefaultColumnWidthStyleStrategy;
import io.ddd4j.extension.excel.style.DefaultRowHeightStyleStrategy;
import io.ddd4j.extension.excel.style.FreezePaneStyleStrategy;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/**
 * Excel 导出 facade（包级可见，对外由 {@code ExcelKit} 暴露）。
 *
 * <p>基于 easyexcel {@link EasyExcel} 链式 API，把样式策略链按 {@link WriteOptions} 装配，
 * 最终返回 {@code byte[]}（同步、内存聚合）。
 *
 * <p><b>容量建议</b>：单次 {@link #export(Class, List, WriteOptions)} 数据量建议 ≤ 5000 行。
 * 超出请改用 {@link com.alibaba.excel.ExcelWriter} + 多次 {@code writer.write(batch)} 分页写入
 * （由调用方控制）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class ExcelExporter {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>ExcelExporter</code> \u5b9e\u4f8b\u3002 */
    private ExcelExporter() {
    }

    /**
     * 默认导出（DEFAULT 样式模板 + 自动列宽）。
     *
     * @param head 表头类
     * @param data 数据
     * @return xlsx 字节数组
     */
    public static byte[] export(Class<?> head, List<?> data) {
        return export(head, data, WriteOptions.defaults());
    }

    /**
     * 带选项的导出。
     *
     * @param head    表头类
     * @param data    数据
     * @param options 选项
     * @return xlsx 字节数组
     */
    public static byte[] export(Class<?> head, List<?> data, WriteOptions options) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelWriterBuilder builder = EasyExcel.write(out, head);
            applyStyleHandlers(builder, options);

            ExcelWriterSheetBuilder sheetBuilder = builder.sheet(options.getSheetName());
            sheetBuilder.doWrite(Objects.isNull(data) ? Collections.emptyList() : data);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizRuntimeException(500, "excel.export.failed", e);
        }
    }

    /**
     * 多 Sheet 导出。
     *
     * @param headMap sheet 名 → 表头类
     * @param dataMap sheet 名 → 数据
     * @param options 共享样式选项
     * @return xlsx 字节数组
     */
    public static byte[] exportMultiSheet(Map<String, Class<?>> headMap,
                                          Map<String, List<?>> dataMap,
                                          WriteOptions options) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ExcelWriter writer = buildWriter(out, options)) {
            for (Map.Entry<String, Class<?>> entry : headMap.entrySet()) {
                String sheetName = entry.getKey();
                Class<?> head = entry.getValue();
                List<?> data = Objects.isNull(dataMap) ? null : dataMap.get(sheetName);
                WriteSheet sheet = EasyExcel.writerSheet(sheetName).head(head).build();
                writer.write(Objects.isNull(data) ? Collections.emptyList() : data, sheet);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizRuntimeException(500, "excel.export.multisheet.failed", e);
        }
    }

    /**
     * 错误信息导出（单行单列）。
     *
     * @param message 错误消息
     * @return xlsx 字节数组
     */
    public static byte[] exportError(String message) {
        List<List<String>> head = new ArrayList<>();
        head.add(Collections.singletonList("文档生成失败"));
        List<List<Object>> content = new ArrayList<>();
        List<Object> row = new ArrayList<>();
        row.add(message);
        content.add(row);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out)
                    .head(head)
                    .registerWriteHandler(new DefaultRowHeightStyleStrategy((short) 600))
                    .registerWriteHandler(buildErrorStyle())
                    .registerWriteHandler(new DefaultColumnWidthStyleStrategy())
                    .sheet("错误")
                    .doWrite(content);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizRuntimeException(500, "excel.export.error.failed", e);
        }
    }

    /**
     * 空模板导出（仅表头）。
     *
     * @param head 表头类
     * @return xlsx 字节数组
     */
    public static byte[] exportEmptyTemplate(Class<?> head) {
        return export(head, Collections.emptyList(), WriteOptions.defaults());
    }

    /**
     * 异常导出（自动提取根因消息）。
     *
     * @param throwable 异常
     * @return xlsx 字节数组
     */
    public static byte[] exportError(Throwable throwable) {
        return exportError(ExceptionUtils.getRootCauseMessage(throwable));
    }

    // ───────────────────── 内部装配 ─────────────────────

    /**
     * \u6ce8\u518c\u6837\u5f0f\u3001\u8868\u5934\u884c\u9ad8\u4e0e\u9009\u914d\u7684\u81ea\u52a8\u5217\u5bbd\u7b49\u5199\u5165\u5904\u7406\u5668\u3002
     * @param builder \u5f85\u88c5\u914d\u7684 Excel \u5199\u5165\u5668\u6784\u5efa\u5668
     * @param options \u5f53\u524d\u5de5\u4f5c\u7c3f\u7684\u5199\u5165\u9009\u9879
     */
    private static void applyStyleHandlers(ExcelWriterBuilder builder, WriteOptions options) {
        // 样式（基于模板）
        builder.registerWriteHandler(new DefaultCellStyleStrategy(options.getStyleTemplate()));
        // 行高
        builder.registerWriteHandler(new DefaultRowHeightStyleStrategy(options.getHeaderRowHeight()));
        // 自动列宽
        if (options.isAutoSizeColumn()) {
            builder.registerWriteHandler(new DefaultColumnWidthStyleStrategy());
        }
        // 冻结表头
        if (options.isFreezeHeader()) {
            builder.registerWriteHandler(new FreezePaneStyleStrategy());
        }
        // 斑马线
        if (options.isAlternatingRow()) {
            builder.registerWriteHandler(new AlternatingRowStyleStrategy());
        }
        // 用户扩展
        for (WriteHandler h : options.getExtraHandlers()) {
            builder.registerWriteHandler(h);
        }
    }

    /**
     * \u5e94\u7528\u5bfc\u51fa\u914d\u7f6e\u5e76\u521b\u5efa\u5199\u5165\u5185\u5b58\u6d41\u7684 Excel \u5199\u5165\u5668\u3002
     * @param out \u4fdd\u5b58\u5bfc\u51fa\u5de5\u4f5c\u7c3f\u7684\u8f93\u51fa\u6d41
     * @param options \u5e94\u7528\u4e8e\u5de5\u4f5c\u7c3f\u7684\u5bfc\u51fa\u9009\u9879
     * @return \u88c5\u914d\u5b8c\u6210\u7684 Excel \u5199\u5165\u5668
     */
    private static ExcelWriter buildWriter(ByteArrayOutputStream out, WriteOptions options) {
        ExcelWriterBuilder builder = EasyExcel.write(out);
        applyStyleHandlers(builder, options);
        return builder.build();
    }

    /**
     * \u4e3a\u5bfc\u5165\u9519\u8bef\u9875\u6784\u9020\u4ee5\u767d\u5e95\u5de6\u5bf9\u9f50\u4e3a\u4e3b\u7684\u5355\u5143\u683c\u6837\u5f0f\u3002
     * @return \u4f9b\u9519\u8bef\u9875\u5199\u5165\u5668\u6ce8\u518c\u7684\u5355\u5143\u683c\u6837\u5f0f\u7b56\u7565
     */
    private static DefaultCellStyleStrategy buildErrorStyle() {
        // 错误页特殊样式：白底左对齐
        WriteCellStyle head = new WriteCellStyle();
        head.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        head.setHorizontalAlignment(HorizontalAlignment.LEFT);
        WriteFont font = new WriteFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 12);
        head.setWriteFont(font);

        WriteCellStyle content = new WriteCellStyle();
        content.setHorizontalAlignment(HorizontalAlignment.LEFT);
        return new DefaultCellStyleStrategy(head, content);
    }
}
