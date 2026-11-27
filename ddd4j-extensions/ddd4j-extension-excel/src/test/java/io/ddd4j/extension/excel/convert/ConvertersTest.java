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
package io.ddd4j.extension.excel.convert;

import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Converter 单元测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ConvertersTest {

    /** \u9a8c\u8bc1<code>cell.getStringValue()</code> \u7b49\u4e8e <code>"2026-07-13"</code>\uff0c<code>parsed</code> \u7b49\u4e8e <code>original</code>\u3002 */
    @Test
    void localDateConverter_should_roundtrip_iso_format() {
        LocalDateConverter c = new LocalDateConverter();
        LocalDate original = LocalDate.of(2026, 7, 13);

        // 写：LocalDate → WriteCellData
        WriteCellData<?> cell = c.convertToExcelData(
                original, null, new GlobalConfiguration());
        assertThat(cell.getStringValue()).isEqualTo("2026-07-13");

        // 读：ReadCellData → LocalDate
        ReadCellData<String> rcd = new ReadCellData<>();
        rcd.setStringValue("2026-07-13");
        LocalDate parsed = c.convertToJavaData(rcd, null, new GlobalConfiguration());
        assertThat(parsed).isEqualTo(original);
    }

    /** \u9a8c\u8bc1<code>cell.getStringValue()</code> \u7b49\u4e8e <code>"2026/07/13"</code>\u3002 */
    @Test
    void localDateConverter_should_support_custom_pattern() {
        LocalDateConverter c = new LocalDateConverter("yyyy/MM/dd");
        WriteCellData<?> cell = c.convertToExcelData(
                LocalDate.of(2026, 7, 13), null, new GlobalConfiguration());
        assertThat(cell.getStringValue()).isEqualTo("2026/07/13");
    }

    /** \u9a8c\u8bc1<code>c.convertToJavaData(rcd, null, new GlobalConfiguration())</code>\u4e3a\u7a7a\uff0c<code>c.convertToExcelData(null, null, new GlobalConfiguration()).getStringValue()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void localDateConverter_null_input_should_return_null() {
        LocalDateConverter c = new LocalDateConverter();
        ReadCellData<String> rcd = new ReadCellData<>();
        rcd.setStringValue("");
        assertThat(c.convertToJavaData(rcd, null, new GlobalConfiguration())).isNull();
        assertThat(c.convertToExcelData(null, null, new GlobalConfiguration()).getStringValue()).isEmpty();
    }

    /** \u9a8c\u8bc1<code>cell.getStringValue()</code> \u7b49\u4e8e <code>"2026-07-13 14:30:00"</code>\uff0c<code>c.convertToJavaData(rcd, null, new GlobalConfiguration())</code> \u7b49\u4e8e <code>original</code>\u3002 */
    @Test
    void localDateTimeConverter_should_roundtrip_default_pattern() {
        LocalDateTimeConverter c = new LocalDateTimeConverter();
        LocalDateTime original = LocalDateTime.of(2026, 7, 13, 14, 30, 0);
        WriteCellData<?> cell = c.convertToExcelData(original, null, new GlobalConfiguration());
        assertThat(cell.getStringValue()).isEqualTo("2026-07-13 14:30:00");

        ReadCellData<String> rcd = new ReadCellData<>();
        rcd.setStringValue("2026-07-13 14:30:00");
        assertThat(c.convertToJavaData(rcd, null, new GlobalConfiguration())).isEqualTo(original);
    }

    /** \u9a8c\u8bc1<code>cell.getStringValue()</code> \u7b49\u4e8e <code>"1,234,567.89"</code>\u3002 */
    @Test
    void bigDecimalStringConverter_should_roundtrip_with_thousands() {
        BigDecimalStringConverter c = new BigDecimalStringConverter();
        BigDecimal original = new BigDecimal("1234567.89");

        // 写：BigDecimal → "1,234,567.89"
        WriteCellData<?> cell = c.convertToExcelData(original, null, new GlobalConfiguration());
        assertThat(cell.getStringValue()).isEqualTo("1,234,567.89");

        // 读：含千分位的字符串 → BigDecimal
        ReadCellData<String> rcd = new ReadCellData<>();
        rcd.setStringValue("1,234,567.89");
        BigDecimal parsed = c.convertToJavaData(rcd, null, new GlobalConfiguration());
        assertThat(parsed).isEqualByComparingTo(original);
    }

    /** \u9a8c\u8bc1<code>cell.getStringValue()</code> \u7b49\u4e8e <code>"ACTIVE"</code>\uff0c<code>c.convertToJavaData(rcd, null, new GlobalConfiguration())</code> \u7b49\u4e8e <code>TestEnum.INACTIVE</code>\u3002 */
    @Test
    void enumNameConverter_should_roundtrip_by_name() {
        TestEnumConverter c = new TestEnumConverter();
        WriteCellData<?> cell = c.convertToExcelData(
                TestEnum.ACTIVE, null, new GlobalConfiguration());
        assertThat(cell.getStringValue()).isEqualTo("ACTIVE");

        ReadCellData<String> rcd = new ReadCellData<>();
        rcd.setStringValue("INACTIVE");
        assertThat(c.convertToJavaData(rcd, null, new GlobalConfiguration())).isEqualTo(TestEnum.INACTIVE);
    }

    /** \u9a8c\u8bc1<code>c.convertToJavaData(rcd, null, new GlobalConfiguration())</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void enumNameConverter_unknown_label_should_throw() {
        TestEnumConverter c = new TestEnumConverter();
        ReadCellData<String> rcd = new ReadCellData<>();
        rcd.setStringValue("UNKNOWN");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        c.convertToJavaData(rcd, null, new GlobalConfiguration()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** \u5c06 <code>ACTIVE</code>\u3001<code>INACTIVE</code>\u3001<code>PENDING</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    enum TestEnum {
        /** \u5f53\u524d\u6d4b\u8bd5\u679a\u4e3e\u7528\u4e8e\u8986\u76d6\u5206\u652f\u7684 <code>ACTIVE</code> \u503c\u3002 */
        ACTIVE,
        /** \u5f53\u524d\u6d4b\u8bd5\u679a\u4e3e\u7528\u4e8e\u8986\u76d6\u5206\u652f\u7684 <code>INACTIVE</code> \u503c\u3002 */
        INACTIVE,
        /** \u5f53\u524d\u6d4b\u8bd5\u679a\u4e3e\u7528\u4e8e\u8986\u76d6\u5206\u652f\u7684 <code>PENDING</code> \u503c\u3002 */
        PENDING}

    /**
     * 用户自定义枚举 converter 子类（典型用法样板）。
     */
    static class TestEnumConverter extends EnumNameConverter<TestEnum> {
        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEnumConverter</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        TestEnumConverter() {
            super(TestEnum.class);
        }
    }
}
