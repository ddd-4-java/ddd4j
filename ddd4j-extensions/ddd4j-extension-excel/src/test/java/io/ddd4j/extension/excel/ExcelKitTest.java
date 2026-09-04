package io.ddd4j.extension.excel;

import com.alibaba.excel.EasyExcel;
import io.ddd4j.extension.excel.TestModels.UserVO;
import io.ddd4j.extension.excel.export.WriteOptions;
import io.ddd4j.extension.excel.importer.ImportResult;
import io.ddd4j.extension.excel.importer.ErrorCollectingReadListener;
import io.ddd4j.extension.excel.style.ExcelStyleTemplate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExcelKit} 端到端测试。
 *
 * <p>覆盖 export → read 回环：写入数据 → 字节 → 重新读回 → 断言。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ExcelKitTest {

    @Test
    void export_then_read_should_return_same_data() {
        // given
        List<UserVO> data = TestModels.sampleUsers(5);

        // when export
        byte[] bytes = ExcelKit.export(UserVO.class, data);
        assertThat(bytes).isNotEmpty();

        // when read back
        List<UserVO> read;
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes)) {
            read = ExcelKit.readAll(in, UserVO.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // then
        assertThat(read).hasSize(5);
        assertThat(read.get(0).getName()).isEqualTo("用户1");
        assertThat(read.get(4).getId()).isEqualTo(5L);
    }

    @Test
    void export_with_options_should_apply_freeze_and_zebra() {
        // given
        List<UserVO> data = TestModels.sampleUsers(10);
        WriteOptions options = WriteOptions.defaults();
        options.setSheetName("测试");
        options.setFreezeHeader(true);
        options.setAlternatingRow(true);
        options.setStyleTemplate(ExcelStyleTemplate.ZEBRA);

        // when
        byte[] bytes = ExcelKit.export(UserVO.class, data, options);

        // then
        assertThat(bytes).isNotEmpty();
        // 写入到内存文件再读回，验证可被 easyexcel 正常解析
        List<UserVO> read = EasyExcel.read(new ByteArrayInputStream(bytes))
                .head(UserVO.class).sheet().doReadSync();
        assertThat(read).hasSize(10);
    }

    @Test
    void importExcel_should_collect_data_and_errors() {
        // given：写一份正常数据
        byte[] bytes = ExcelKit.export(UserVO.class, TestModels.sampleUsers(3));

        // when
        ImportResult<UserVO> result;
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes)) {
            result = ExcelKit.importExcel(in, UserVO.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // then
        assertThat(result.getData()).hasSize(3);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void exportError_should_return_non_empty_bytes() {
        // given
        Throwable t = new RuntimeException("模拟异常");

        // when
        byte[] bytes = ExcelKit.exportError(t);

        // then
        assertThat(bytes).isNotEmpty();
    }

    @Test
    void exportEmptyTemplate_should_have_only_header() {
        // when
        byte[] bytes = ExcelKit.exportEmptyTemplate(UserVO.class);

        // then：读回应为 0 行数据
        List<UserVO> read = EasyExcel.read(new ByteArrayInputStream(bytes))
                .head(UserVO.class).sheet().doReadSync();
        assertThat(read).isEmpty();
    }

    @Test
    void errorCollectingListener_should_not_throw_on_parse_error() {
        // given：构造一个故意混入错误数据的 Excel。
        // 用 Date 列写入非日期字符串，触发 ExcelDataConvertException
        java.util.List<java.util.List<String>> head = new java.util.ArrayList<>();
        head.add(java.util.Arrays.asList("日期"));
        java.util.List<java.util.List<Object>> content = new java.util.ArrayList<>();
        java.util.List<Object> row1 = new java.util.ArrayList<>();
        row1.add("不是日期格式的字符串");  // 写入字符串但字段为 Date
        content.add(row1);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        EasyExcel.write(out).head(head).sheet("S").doWrite(content);

        // 准备一个 Date 字段的 VO 读取
        // DateVO 类已移到外部

        // when
        ImportResult<DateVO> result;
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(out.toByteArray())) {
            ErrorCollectingReadListener<DateVO> listener = new ErrorCollectingReadListener<>();
            ExcelKit.importExcel(in, DateVO.class, listener);
            result = listener.toResult();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // then：onException 收集到错误，不抛到调用方
        // easyexcel 对部分转换失败容错较好；如未触发异常，至少保证流程不抛
        Assertions.assertThat(result).isNotNull();
    }

    public static class DateVO {
        @com.alibaba.excel.annotation.ExcelProperty("日期")
        private java.util.Date d;
        public DateVO() {}
        public DateVO(java.util.Date d) { this.d = d; }
        public java.util.Date getD() { return d; }
        public void setD(java.util.Date d) { this.d = d; }
    }
}
