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

    /**
     * 验证<code>cell.getStringValue()</code> 等于 <code>"2026-07-13"</code>，<code>parsed</code> 等于 <code>original</code>。
     */
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

    /**
     * 验证<code>cell.getStringValue()</code> 等于 <code>"2026/07/13"</code>。
     */
    @Test
    void localDateConverter_should_support_custom_pattern() {
        LocalDateConverter c = new LocalDateConverter("yyyy/MM/dd");
        WriteCellData<?> cell = c.convertToExcelData(
                LocalDate.of(2026, 7, 13), null, new GlobalConfiguration());
        assertThat(cell.getStringValue()).isEqualTo("2026/07/13");
    }

    /**
     * 验证<code>c.convertToJavaData(rcd, null, new GlobalConfiguration())</code>为空，<code>c.convertToExcelData(null, null, new GlobalConfiguration()).getStringValue()</code>为空。
     */
    @Test
    void localDateConverter_null_input_should_return_null() {
        LocalDateConverter c = new LocalDateConverter();
        ReadCellData<String> rcd = new ReadCellData<>();
        rcd.setStringValue("");
        assertThat(c.convertToJavaData(rcd, null, new GlobalConfiguration())).isNull();
        assertThat(c.convertToExcelData(null, null, new GlobalConfiguration()).getStringValue()).isEmpty();
    }

    /**
     * 验证<code>cell.getStringValue()</code> 等于 <code>"2026-07-13 14:30:00"</code>，<code>c.convertToJavaData(rcd, null, new GlobalConfiguration())</code> 等于 <code>original</code>。
     */
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

    /**
     * 验证<code>cell.getStringValue()</code> 等于 <code>"1,234,567.89"</code>。
     */
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

    /**
     * 验证<code>cell.getStringValue()</code> 等于 <code>"ACTIVE"</code>，<code>c.convertToJavaData(rcd, null, new GlobalConfiguration())</code> 等于 <code>TestEnum.INACTIVE</code>。
     */
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

    /**
     * 验证<code>c.convertToJavaData(rcd, null, new GlobalConfiguration())</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void enumNameConverter_unknown_label_should_throw() {
        TestEnumConverter c = new TestEnumConverter();
        ReadCellData<String> rcd = new ReadCellData<>();
        rcd.setStringValue("UNKNOWN");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        c.convertToJavaData(rcd, null, new GlobalConfiguration()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 将 <code>ACTIVE</code>、<code>INACTIVE</code>、<code>PENDING</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    enum TestEnum {/** 当前测试枚举用于覆盖分支的 <code>ACTIVE</code> 值。 */ ACTIVE, /** 当前测试枚举用于覆盖分支的 <code>INACTIVE</code> 值。 */ INACTIVE, /** 当前测试枚举用于覆盖分支的 <code>PENDING</code> 值。 */ PENDING}

    /**
     * 用户自定义枚举 converter 子类（典型用法样板）。
     */
    static class TestEnumConverter extends EnumNameConverter<TestEnum> {
        /**
         * 使用 <code>super()</code> 初始化 <code>TestEnumConverter</code> 测试夹具。
         */
        TestEnumConverter() {
            super(TestEnum.class);
        }
    }
}
