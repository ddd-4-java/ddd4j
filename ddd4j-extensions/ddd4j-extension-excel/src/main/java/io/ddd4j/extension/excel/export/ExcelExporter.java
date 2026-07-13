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
            sheetBuilder.doWrite(data == null ? Collections.emptyList() : data);
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
                List<?> data = dataMap == null ? null : dataMap.get(sheetName);
                WriteSheet sheet = EasyExcel.writerSheet(sheetName).head(head).build();
                writer.write(data == null ? Collections.emptyList() : data, sheet);
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

    private static ExcelWriter buildWriter(ByteArrayOutputStream out, WriteOptions options) {
        ExcelWriterBuilder builder = EasyExcel.write(out);
        applyStyleHandlers(builder, options);
        return builder.build();
    }

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
