package io.ddd4j.extension.excel.fill;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.fill.FillConfig;
import com.alibaba.excel.write.metadata.WriteSheet;
import io.ddd4j.core.exception.BizRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Excel 模板填充 facade。
 *
 * <p>封装 easyexcel {@code withTemplate + fill} 流程，支持：
 * <ul>
 *   <li>{@link #fill(InputStream, Map)}：填充单值变量（模板 {@code {name}} / {@code {date}} 等）</li>
 *   <li>{@link #fillList(InputStream, List)}：填充列表（模板 {@code {.}} 占位）</li>
 *   <li>{@link #fillList(InputStream, List, FillConfig)}：带配置（如 {@code forceNewRow=true}）</li>
 * </ul>
 *
 * <h3>模板语法</h3>
 * <pre>
 *   {name}        普通变量
 *   {.}           列表占位符（默认纵向展开）
 *   {data1.}      带前缀的多列表
 *   \{  \}        转义字面量字符
 * </pre>
 *
 * <p>详细模板设计规范参见 easyexcel-fill Skill。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class ExcelFiller {

    private ExcelFiller() {
    }

    /**
     * 填充单值变量。
     *
     * @param template 模板输入流（.xlsx）
     * @param vars     变量 Map（key 与模板 {@code {var}} 占位符一致）
     * @return 填充后的 xlsx 字节
     */
    public static byte[] fill(InputStream template, Map<String, Object> vars) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out).withTemplate(template).sheet().doFill(vars);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizRuntimeException(500, "excel.fill.failed", e);
        }
    }

    /**
     * 填充列表变量（默认无 FillConfig）。
     *
     * @param template 模板输入流
     * @param list     数据列表
     * @return 填充后的 xlsx 字节
     */
    public static byte[] fillList(InputStream template, List<?> list) {
        return fillList(template, list, null);
    }

    /**
     * 填充列表变量（带 FillConfig）。
     *
     * <p>{@code FillConfig.builder().forceNewRow(true).build()} 适用于"list 后还有内容"的复杂模板；
     * 普通场景可传 null。
     *
     * @param template   模板输入流
     * @param list       数据列表
     * @param fillConfig 填充配置（可为 null）
     * @return 填充后的 xlsx 字节
     */
    public static byte[] fillList(InputStream template, List<?> list, FillConfig fillConfig) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             InputStream tpl = toNonClosingStream(template);
             ExcelWriter writer = EasyExcel.write(out).withTemplate(tpl).build()) {
            WriteSheet sheet = EasyExcel.writerSheet().build();
            if (fillConfig == null) {
                writer.fill(list, sheet);
            } else {
                writer.fill(list, fillConfig, sheet);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizRuntimeException(500, "excel.fill.list.failed", e);
        }
    }

    /**
     * 组合填充：多变量 + 列表 + 可选后续 write。
     *
     * <p>适合"对账单"等复杂报表：模板含头部变量 + 列表占位 + 后续统计行（统计行由调用方追加）。
     *
     * @param template 模板输入流
     * @param vars     头部变量
     * @param list     列表数据
     * @return 填充后的 xlsx 字节
     */
    public static byte[] fillComposite(InputStream template, Map<String, Object> vars, List<?> list) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             InputStream tpl = toNonClosingStream(template);
             ExcelWriter writer = EasyExcel.write(out).withTemplate(tpl).build()) {
            WriteSheet sheet = EasyExcel.writerSheet().build();
            writer.fill(list, sheet);
            writer.fill(vars, sheet);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizRuntimeException(500, "excel.fill.composite.failed", e);
        }
    }

    /**
     * 多次填充同一模板（省内存场景，文件缓存）。
     *
     * @param template 模板输入流
     * @param batches  多批列表数据
     * @return 填充后的 xlsx 字节
     */
    public static byte[] fillListInBatches(InputStream template, List<List<?>> batches) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             InputStream tpl = toNonClosingStream(template);
             ExcelWriter writer = EasyExcel.write(out).withTemplate(tpl).build()) {
            WriteSheet sheet = EasyExcel.writerSheet().build();
            for (List<?> batch : batches) {
                writer.fill(batch, sheet);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizRuntimeException(500, "excel.fill.batch.failed", e);
        }
    }

    /**
     * 把模板流包装为可重复读的内存流（填充场景下 easyexcel 会多次读取）。
     */
    private static InputStream toNonClosingStream(InputStream in) throws IOException {
        if (in instanceof ByteArrayInputStream) {
            return in;
        }
        return new ByteArrayInputStream(in.readAllBytes());
    }
}
