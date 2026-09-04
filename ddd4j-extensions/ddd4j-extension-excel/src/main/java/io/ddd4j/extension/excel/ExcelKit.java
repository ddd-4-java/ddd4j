package io.ddd4j.extension.excel;

import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.write.metadata.fill.FillConfig;
import io.ddd4j.extension.excel.export.ExcelExporter;
import io.ddd4j.extension.excel.export.WriteOptions;
import io.ddd4j.extension.excel.fill.ExcelFiller;
import io.ddd4j.extension.excel.importer.ExcelImporter;
import io.ddd4j.extension.excel.importer.ImportResult;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * ddd4j-excel 顶层门面（统一对外入口，纯 Java，无 Spring 依赖）。
 *
 * <p>把"阿里 EasyExcel 的链式构建"翻译成意图明确的命名，业务侧不再出现
 * {@code .registerWriteHandler(...)} 这种细节。直接复用 easyexcel 原生注解
 * （{@code @ExcelProperty} / {@code @ExcelIgnore} / {@code @ColumnWidth} / {@code @DateTimeFormat}）。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * // 1. 一行导出
 * byte[] bytes = ExcelKit.export(OrderVO.class, orderService.listAll());
 *
 * // 2. 导入 + 错误回传
 * ImportResult<OrderVO> result = ExcelKit.importExcel(
 *     file.getInputStream(), OrderVO.class,
 *     new BatchReadListener<>(1000, orderService::saveBatch)
 * );
 *
 * // 3. 模板填充
 * byte[] filled = ExcelKit.fill(
 *     getClass().getResourceAsStream("/templates/contract.xlsx"),
 *     Map.of("contractNo", "HT-001")
 * );
 *
 * // 4. 自定义样式
 * byte[] bytes = ExcelKit.export(OrderVO.class, data,
 *     WriteOptions.defaults()
 *         .addHandler(new CommentWriteHandler())
 * );
 * }</pre>
 *
 * <h3>Web 下载/上传</h3>
 * <p>本类为纯 Java，不依赖 Servlet/Spring；HTTP 场景的下载（{@code HttpServletResponse}）
 * 与上传（{@code MultipartFile}）请使用 {@code ddd4j-boot-extension-excel} 模块的
 * {@code io.ddd4j.boot.excel.web.ExcelHttpKit}。
 *
 * <h3>容量建议</h3>
 * <ul>
 *   <li>{@link #export}：单次 ≤ 5000 行；超出请用 {@code ExcelWriter} 分页写入</li>
 *   <li>{@link #readAll}：仅用于小数据量；大数据量用 {@link #importExcel(InputStream, Class, ReadListener)}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class ExcelKit {

    private ExcelKit() {
    }

    // ───────────────────── 导出 ─────────────────────

    /**
     * 默认导出（DEFAULT 样式模板 + 自动列宽）。
     *
     * @param head 表头类
     * @param data 数据列表
     * @return xlsx 字节
     */
    public static byte[] export(Class<?> head, List<?> data) {
        return ExcelExporter.export(head, data);
    }

    /**
     * 带选项的导出。
     *
     * @param head    表头类
     * @param data    数据列表
     * @param options 选项
     * @return xlsx 字节
     */
    public static byte[] export(Class<?> head, List<?> data, WriteOptions options) {
        return ExcelExporter.export(head, data, options);
    }

    /**
     * 多 Sheet 导出。
     *
     * @param headMap sheet 名 → 表头类
     * @param dataMap sheet 名 → 数据列表
     * @return xlsx 字节
     */
    public static byte[] exportMultiSheet(Map<String, Class<?>> headMap, Map<String, List<?>> dataMap) {
        return ExcelExporter.exportMultiSheet(headMap, dataMap, WriteOptions.defaults());
    }

    /**
     * 多 Sheet 导出（带选项）。
     *
     * @param headMap sheet 名 → 表头类
     * @param dataMap sheet 名 → 数据列表
     * @param options 共享样式选项
     * @return xlsx 字节
     */
    public static byte[] exportMultiSheet(Map<String, Class<?>> headMap,
                                          Map<String, List<?>> dataMap,
                                          WriteOptions options) {
        return ExcelExporter.exportMultiSheet(headMap, dataMap, options);
    }

    // ───────────────────── 错误 / 空模板 ─────────────────────

    /**
     * 导出错误信息（单行单列，从异常提取根因消息）。
     *
     * @param throwable 异常
     * @return xlsx 字节
     */
    public static byte[] exportError(Throwable throwable) {
        return ExcelExporter.exportError(throwable);
    }

    /**
     * 导出错误信息（单行单列，纯文本消息）。
     *
     * @param message 错误消息
     * @return xlsx 字节
     */
    public static byte[] exportError(String message) {
        return ExcelExporter.exportError(message);
    }

    /**
     * 导出空模板（仅表头）。
     *
     * @param head 表头类
     * @return xlsx 字节
     */
    public static byte[] exportEmptyTemplate(Class<?> head) {
        return ExcelExporter.exportEmptyTemplate(head);
    }

    // ───────────────────── 导入 ─────────────────────

    /**
     * 用自定义 listener 导入（推荐用于大数据量入库）。
     *
     * @param in       输入流
     * @param head     表头类
     * @param listener 读取监听器（每次调用必须 new 一个）
     * @param <T>      数据类型
     * @return 导入结果
     */
    public static <T> ImportResult<T> importExcel(InputStream in, Class<T> head, ReadListener<T> listener) {
        return ExcelImporter.importExcel(in, head, listener);
    }

    /**
     * 默认 listener 导入（错误自动收集到 {@link ImportResult#getErrors()}）。
     *
     * @param in   输入流
     * @param head 表头类
     * @param <T>  数据类型
     * @return 导入结果
     */
    public static <T> ImportResult<T> importExcel(InputStream in, Class<T> head) {
        return ExcelImporter.importExcel(in, head);
    }

    /**
     * 同步全量读取（小数据量语法糖）。
     *
     * <p><b>警告</b>：内部使用 {@code doReadSync}，会把数据全部加载到内存。
     * 大数据量请改用 {@link #importExcel(InputStream, Class, ReadListener)}。
     *
     * @param in   输入流
     * @param head 表头类
     * @param <T>  数据类型
     * @return 全部数据
     */
    public static <T> List<T> readAll(InputStream in, Class<T> head) {
        return ExcelImporter.readAll(in, head);
    }

    // ───────────────────── 模板填充 ─────────────────────

    /**
     * 填充单值变量。
     *
     * @param template 模板输入流（.xlsx）
     * @param vars     变量 Map（key 与模板 {@code {var}} 占位符一致）
     * @return 填充后的 xlsx 字节
     */
    public static byte[] fill(InputStream template, Map<String, Object> vars) {
        return ExcelFiller.fill(template, vars);
    }

    /**
     * 填充列表变量（默认无 FillConfig）。
     *
     * @param template 模板输入流
     * @param list     数据列表
     * @return 填充后的 xlsx 字节
     */
    public static byte[] fillList(InputStream template, List<?> list) {
        return ExcelFiller.fillList(template, list);
    }

    /**
     * 填充列表变量（带 FillConfig）。
     *
     * @param template   模板输入流
     * @param list       数据列表
     * @param fillConfig 填充配置（可为 null）
     * @return 填充后的 xlsx 字节
     */
    public static byte[] fillList(InputStream template, List<?> list, FillConfig fillConfig) {
        return ExcelFiller.fillList(template, list, fillConfig);
    }

    /**
     * 组合填充：多变量 + 列表。
     *
     * @param template 模板输入流
     * @param vars     头部变量
     * @param list     列表数据
     * @return 填充后的 xlsx 字节
     */
    public static byte[] fillComposite(InputStream template, Map<String, Object> vars, List<?> list) {
        return ExcelFiller.fillComposite(template, vars, list);
    }
}
