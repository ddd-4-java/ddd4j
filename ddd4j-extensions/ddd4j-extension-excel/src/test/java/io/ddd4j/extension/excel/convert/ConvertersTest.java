package io.ddd4j.extension.excel.convert;

import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import io.ddd4j.extension.excel.convert.LocalDateConverter;
import io.ddd4j.extension.excel.convert.LocalDateTimeConverter;
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

    @Test
    void localDateConverter_should_support_custom_pattern() {
        LocalDateConverter c = new LocalDateConverter("yyyy/MM/dd");
        WriteCellData<?> cell = c.convertToExcelData(
                LocalDate.of(2026, 7, 13), null, new GlobalConfiguration());
        assertThat(cell.getStringValue()).isEqualTo("2026/07/13");
    }

    @Test
    void localDateConverter_null_input_should_return_null() {
        LocalDateConverter c = new LocalDateConverter();
        ReadCellData<String> rcd = new ReadCellData<>();
        rcd.setStringValue("");
        assertThat(c.convertToJavaData(rcd, null, new GlobalConfiguration())).isNull();
        assertThat(c.convertToExcelData(null, null, new GlobalConfiguration()).getStringValue()).isEmpty();
    }

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

    @Test
    void enumNameConverter_unknown_label_should_throw() {
        TestEnumConverter c = new TestEnumConverter();
        ReadCellData<String> rcd = new ReadCellData<>();
        rcd.setStringValue("UNKNOWN");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                c.convertToJavaData(rcd, null, new GlobalConfiguration()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    enum TestEnum { ACTIVE, INACTIVE, PENDING }

    /**
     * 用户自定义枚举 converter 子类（典型用法样板）。
     */
    static class TestEnumConverter extends EnumNameConverter<TestEnum> {
        TestEnumConverter() { super(TestEnum.class); }
    }
}
