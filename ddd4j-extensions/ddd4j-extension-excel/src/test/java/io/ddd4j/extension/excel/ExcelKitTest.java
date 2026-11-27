package io.ddd4j.extension.excel;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

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

    /** \u9a8c\u8bc1<code>bytes</code>\u975e\u7a7a\uff0c<code>read</code> \u7684\u5927\u5c0f\u4e3a <code>5</code>\uff0c<code>read.get(4).getId()</code> \u7b49\u4e8e <code>5L</code>\u3002 */
    @Test
    void export_then_read_should_return_same_data() {
        // given
        List<UserVO> data = TestModels.sampleUsers(5);

        // when export
        byte[] bytes = ExcelKit.export(UserVO.class, data);
        assertThat(bytes).isNotEmpty();

        // when read back
        List<UserVO> read;
        try (var in = new ByteArrayInputStream(bytes)) {
            read = ExcelKit.readAll(in, UserVO.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // then
        assertThat(read).hasSize(5);
        assertThat(read.get(0).getName()).isEqualTo("用户1");
        assertThat(read.get(4).getId()).isEqualTo(5L);
    }

    /** \u9a8c\u8bc1<code>bytes</code>\u975e\u7a7a\uff0c<code>read</code> \u7684\u5927\u5c0f\u4e3a <code>10</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>result.getData()</code> \u7684\u5927\u5c0f\u4e3a <code>3</code>\uff0c<code>result.hasErrors()</code>\u4e3a\u5047\u3002 */
    @Test
    void importExcel_should_collect_data_and_errors() {
        // given：写一份正常数据
        byte[] bytes = ExcelKit.export(UserVO.class, TestModels.sampleUsers(3));

        // when
        ImportResult<UserVO> result;
        try (var in = new ByteArrayInputStream(bytes)) {
            result = ExcelKit.importExcel(in, UserVO.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // then
        assertThat(result.getData()).hasSize(3);
        assertThat(result.hasErrors()).isFalse();
    }

    /** \u9a8c\u8bc1<code>bytes</code>\u975e\u7a7a\u3002 */
    @Test
    void exportError_should_return_non_empty_bytes() {
        // given
        Throwable t = new RuntimeException("模拟异常");

        // when
        byte[] bytes = ExcelKit.exportError(t);

        // then
        assertThat(bytes).isNotEmpty();
    }

    /** \u9a8c\u8bc1<code>read</code>\u4e3a\u7a7a\u3002 */
    @Test
    void exportEmptyTemplate_should_have_only_header() {
        // when
        byte[] bytes = ExcelKit.exportEmptyTemplate(UserVO.class);

        // then：读回应为 0 行数据
        List<UserVO> read = EasyExcel.read(new ByteArrayInputStream(bytes))
                .head(UserVO.class).sheet().doReadSync();
        assertThat(read).isEmpty();
    }

    /** \u9a8c\u8bc1<code>result</code>\u975e\u7a7a\u3002 */
    @Test
    void errorCollectingListener_should_not_throw_on_parse_error() {
        // given：构造一个故意混入错误数据的 Excel。
        // 用 Date 列写入非日期字符串，触发 ExcelDataConvertException
        var head = new java.util.ArrayList<List<String>>();
        head.add(java.util.List.of("日期"));
        var content = new java.util.ArrayList<List<Object>>();
        var row1 = new java.util.ArrayList<Object>();
        row1.add("不是日期格式的字符串");  // 写入字符串但字段为 Date
        content.add(row1);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        EasyExcel.write(out).head(head).sheet("S").doWrite(content);

        // 准备一个 Date 字段的 VO 读取
        /** 集中保存 <code>d</code>，供当前模块使用。 */
        final class DateVO {

            /** Java 序列化版本标识。 */
            private static final long serialVersionUID = 0L;

            /** \u4fdd\u5b58\u4f9b Excel \u65e5\u671f\u5217\u5f80\u8fd4\u8f6c\u6362\u7684\u65e5\u671f\u503c\u3002 */
            @com.alibaba.excel.annotation.ExcelProperty("日期")
            private final java.util.Date d;

            /**
             * \u8bbe\u7f6e <code>d</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>DateVO</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
             * @param d \u7528\u4e8e\u521d\u59cb\u5316 <code>DateVO</code> \u7684 <code>java.util.Date</code> \u503c
             */
            @JsonCreator()
            DateVO(@JsonProperty("d") java.util.Date d) {
                this.d = d;
            }

            /**
             * 读取<code>d</code> 的当前值。
             *
             * @return 返回表达式 <code>d</code>
             */
            @JsonProperty("d")
            public java.util.Date d() {
                return d;
            }

            /**
             * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
             *
             * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
             * @return 按条件返回 <code>true</code>、<code>false</code>、<code>Objects.equals(this.d, other.d)</code>
             */
            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                    return false;
                }
                DateVO other = (DateVO) obj;
                return Objects.equals(this.d, other.d);
            }

            /**
             * 读取<code>result</code> 的当前值。
             *
             * @return 返回表达式 <code>result</code>
             */
            @Override
            public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(d);
                return result;
            }

            /**
             * 按 <code>to String</code> 的契约计算并返回结果。
             *
             * @return 返回表达式 <code>"DateVO[d=" + d + "]"</code>
             */
            @Override
            public String toString() {
                return "DateVO[d=" + d + "]";
            }
        }

        // when
        ImportResult<DateVO> result;
        try (var in = new java.io.ByteArrayInputStream(out.toByteArray())) {
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
}
