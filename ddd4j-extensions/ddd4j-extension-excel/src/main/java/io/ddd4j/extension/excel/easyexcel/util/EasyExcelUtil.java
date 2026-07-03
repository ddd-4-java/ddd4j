package io.ddd4j.extension.excel.easyexcel.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import io.ddd4j.extension.excel.easyexcel.style.CellRowHeightStyleStrategy;
import io.ddd4j.extension.excel.easyexcel.style.CellStyleStrategy;
import io.ddd4j.extension.excel.easyexcel.style.CellWidthStyleStrategy;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EasyExcel 工具类
 *
 * <p>提供便捷的 Excel 文件生成方法，包括错误信息导出和空模板导出。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class EasyExcelUtil {

    /**
     * 生成包含异常信息的错误 Excel 文件
     *
     * @param sheetName 工作表名称
     * @param throwable 异常对象，用于提取异常信息
     * @return Excel 文件的字节数组
     * @throws IOException 如果生成过程中发生 IO 异常
     */
    public static byte[] writeErrorExcel(String sheetName, Throwable throwable) throws IOException {
        return writeErrorExcel(sheetName, ExceptionUtils.getRootCauseMessage(throwable));
    }

    /**
     * 生成包含错误消息的 Excel 文件
     *
     * @param sheetName 工作表名称
     * @param message   错误消息内容
     * @return Excel 文件的字节数组
     * @throws IOException 如果生成过程中发生 IO 异常
     */
    public static byte[] writeErrorExcel(String sheetName, String message) throws IOException {
        // 4、开始输出数据到excel
        List<List<String>> head = new ArrayList<>();
        head.add(Collections.singletonList("文档生成失败"));
        List<List<Object>> contentList = new ArrayList<>();
        contentList.add(Collections.singletonList(message));
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            EasyExcel.write(outputStream)
                    .head(head)
                    .registerWriteHandler(new CellRowHeightStyleStrategy(1))
                    .registerWriteHandler(new CellStyleStrategy(Collections.singletonList(0), new WriteCellStyle(), new WriteCellStyle()))
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(sheetName)
                    .doWrite(contentList);
            return outputStream.toByteArray();
        }
    }


    /**
     * 生成空数据模板 Excel 文件
     *
     * @param head          表头数据
     * @param columnIndexes 需要特殊样式处理的列索引列表
     * @param sheetName     工作表名称
     * @return Excel 文件的字节数组
     * @throws IOException 如果生成过程中发生 IO 异常
     */
    public static byte[] writeEmptyExcel(List<List<String>> head, List<Integer> columnIndexes, String sheetName) throws IOException {
        // 4、开始输出数据到excel
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            EasyExcel.write(outputStream)
                    .head(head)
                    .registerWriteHandler(new CellRowHeightStyleStrategy(1))
                    .registerWriteHandler(new CellStyleStrategy(columnIndexes, new WriteCellStyle(), new WriteCellStyle()))
                    .registerWriteHandler(new CellWidthStyleStrategy())
                    .sheet(sheetName)
                    .doWrite(Collections.emptyList());
            return outputStream.toByteArray();
        }
    }
}
