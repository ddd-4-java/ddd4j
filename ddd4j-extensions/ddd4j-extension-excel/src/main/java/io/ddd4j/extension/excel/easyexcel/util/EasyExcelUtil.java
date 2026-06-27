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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class EasyExcelUtil {

    public static byte[] writeErrorExcel(String sheetName, Throwable throwable) throws IOException {
        return writeErrorExcel(sheetName, ExceptionUtils.getRootCauseMessage(throwable));
    }

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
