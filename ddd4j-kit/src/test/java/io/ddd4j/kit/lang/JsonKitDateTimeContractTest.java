package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 Jackson 版本适配后公开日期时间 JSON 契约在三线一致。
 */
class JsonKitDateTimeContractTest {

    /** \u9a8c\u8bc1<code>JsonKit.toJson(value)</code> \u7b49\u4e8e <code>"\"2026-10-13\""</code>\uff0c<code>JsonKit.toObject("\"2026-10-13\"", LocalDate.class)</code> \u7b49\u4e8e <code>value</code>\u3002 */
    @Test
    void localDateUsesTheConfiguredCalendarFormat() {
        LocalDate value = LocalDate.of(2026, 10, 13);
        assertEquals("\"2026-10-13\"", JsonKit.toJson(value));
        assertEquals(value, JsonKit.toObject("\"2026-10-13\"", LocalDate.class));
    }

    /** \u9a8c\u8bc1<code>JsonKit.toJson(value)</code> \u7b49\u4e8e <code>"\"2026-10-13 09:08:07\""</code>\uff0c<code>JsonKit.toObject("\"2026-10-13 09:08:07\"", LocalDateTime.class)</code> \u7b49\u4e8e <code>value</code>\u3002 */
    @Test
    void localDateTimeUsesTheConfiguredSecondPrecisionFormat() {
        LocalDateTime value = LocalDateTime.of(2026, 10, 13, 9, 8, 7);
        assertEquals("\"2026-10-13 09:08:07\"", JsonKit.toJson(value));
        assertEquals(value, JsonKit.toObject("\"2026-10-13 09:08:07\"", LocalDateTime.class));
    }

    /** \u9a8c\u8bc1<code>JsonKit.toJson(value)</code> \u7b49\u4e8e <code>"\"2026-10-13T01:08:07Z\""</code>\uff0c<code>JsonKit.toObject("\"2026-10-13T01:08:07Z\"", Instant.class)</code> \u7b49\u4e8e <code>value</code>\u3002 */
    @Test
    void instantUsesIsoStringFormat() {
        Instant value = Instant.parse("2026-10-13T01:08:07Z");
        assertEquals("\"2026-10-13T01:08:07Z\"", JsonKit.toJson(value));
        assertEquals(value, JsonKit.toObject("\"2026-10-13T01:08:07Z\"", Instant.class));
    }

    /** \u9a8c\u8bc1<code>JsonKit.toJson(value)</code> \u7b49\u4e8e <code>"\"PT1M7S\""</code>\uff0c<code>JsonKit.toObject("\"PT1M7S\"", Duration.class)</code> \u7b49\u4e8e <code>value</code>\u3002 */
    @Test
    void durationUsesIsoStringFormat() {
        Duration value = Duration.ofSeconds(67);
        assertEquals("\"PT1M7S\"", JsonKit.toJson(value));
        assertEquals(value, JsonKit.toObject("\"PT1M7S\"", Duration.class));
    }
}
