package io.ddd4j.extension.excel.importer;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.listener.ReadListener;
import io.ddd4j.core.exception.BizRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Excel 导入 facade（包级可见，对外由 {@code ExcelKit} 暴露）。
 *
 * <p>统一封装 easyexcel {@link EasyExcel#read} 的链式调用，并提供：
 * <ul>
 *   <li>{@link ErrorCollectingReadListener} 默认实现：错误不抛、收集到 {@link ImportResult}</li>
 *   <li>小数据量语法糖 {@link #readAll(InputStream, Class)}（基于 {@code doReadSync()}）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class ExcelImporter {

    private ExcelImporter() {
    }

    /**
     * 用自定义 listener 读取（推荐用于大数据量入库）。
     *
     * @param in       输入流
     * @param head     表头类
     * @param listener 读取监听器（每次调用必须 new 一个，避免状态污染）
     * @param <T>      数据类型
     * @return 导入结果（含 listener 内部收集的数据与错误）
     */
    public static <T> ImportResult<T> importExcel(InputStream in, Class<T> head, ReadListener<T> listener) {
        try {
            EasyExcel.read(in, head, listener).sheet().doRead();
        } catch (Exception e) {
            throw new BizRuntimeException(500, "excel.import.failed", e);
        }
        if (listener instanceof ErrorCollectingReadListener<T> ec) {
            return ec.toResult();
        }
        // 非 ErrorCollectingReadListener 场景：返回空 errors（业务 listener 自行处理）
        return ImportResult.empty();
    }

    /**
     * 默认 listener 读取（错误自动收集）。
     *
     * @param in   输入流
     * @param head 表头类
     * @param <T>  数据类型
     * @return 导入结果
     */
    public static <T> ImportResult<T> importExcel(InputStream in, Class<T> head) {
        ErrorCollectingReadListener<T> listener = new ErrorCollectingReadListener<>();
        return importExcel(in, head, listener);
    }

    /**
     * 同步全量读取（小数据量语法糖，{@code doReadSync}）。
     *
     * <p><b>警告</b>：内部使用 easyexcel {@code doReadSync()}，会把数据全部加载到内存。
     * 大数据量（> 5000 行）请改用 {@link #importExcel(InputStream, Class, ReadListener)}
     * + {@link BatchReadListener}。
     *
     * @param in   输入流
     * @param head 表头类
     * @param <T>  数据类型
     * @return 全部数据
     */
    public static <T> List<T> readAll(InputStream in, Class<T> head) {
        try {
            return EasyExcel.read(in).head(head).sheet().doReadSync();
        } catch (Exception e) {
            throw new BizRuntimeException(500, "excel.import.readall.failed", e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }
}
