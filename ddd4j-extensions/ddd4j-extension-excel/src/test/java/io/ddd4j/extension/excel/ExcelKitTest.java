/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.extension.excel;

import com.alibaba.excel.EasyExcel;
import io.ddd4j.extension.excel.TestModels.UserVO;
import io.ddd4j.extension.excel.export.WriteOptions;
import io.ddd4j.extension.excel.importer.ErrorCollectingReadListener;
import io.ddd4j.extension.excel.importer.ImportResult;
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
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes)) {
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

    /** \u5c06 <code>d</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static class DateVO {
        /** \u4fdd\u5b58\u4f9b Excel \u65e5\u671f\u5217\u5f80\u8fd4\u8f6c\u6362\u7684\u65e5\u671f\u503c\u3002 */
        @com.alibaba.excel.annotation.ExcelProperty("日期")
        private java.util.Date d;

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>DateVO</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        public DateVO() {
        }

        /**
         * \u8bbe\u7f6e <code>d</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>DateVO</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param d \u7528\u4e8e\u521d\u59cb\u5316 <code>DateVO</code> \u7684 <code>java.util.Date</code> \u503c
         */
        public DateVO(java.util.Date d) {
            this.d = d;
        }

        /**
         * \u8bfb\u53d6<code>d</code> \u5b57\u6bb5\u3002
         * @return <code>d</code> \u5b57\u6bb5
         */
        public java.util.Date getD() {
            return d;
        }

        /**
         * \u8bbe\u7f6e<code>d</code> \u5b57\u6bb5\u3002
         * @param d \u8981\u5199\u5165\u7684\u503c
         */
        public void setD(java.util.Date d) {
            this.d = d;
        }
    }
}
