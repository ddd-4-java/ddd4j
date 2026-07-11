package io.ddd4j.extension.jackson.ser;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.extension.jackson.annotation.Sensitive;
import io.ddd4j.extension.jackson.annotation.SensitiveStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SensitiveJsonSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMaskDifferentFieldsWithoutChangingSourceObject() throws Exception {
        SensitivePayload payload = new SensitivePayload(
                "张三丰", "13812345678", "6222021234567890", "tester@example.com");

        String json = objectMapper.writeValueAsString(payload);

        assertEquals(
                "{\"name\":\"张**\",\"phone\":\"138****5678\","
                        + "\"bankCard\":\"6222********7890\",\"email\":\"t*****@example.com\"}",
                json);
        assertEquals("张三丰", payload.name);
        assertEquals("13812345678", payload.phone);
        assertEquals("6222021234567890", payload.bankCard);
        assertEquals("tester@example.com", payload.email);
    }

    @Test
    void shouldKeepContextualStrategiesIsolatedUnderConcurrency() throws Exception {
        SensitivePayload payload = new SensitivePayload(
                "张三丰", "13812345678", "6222021234567890", "tester@example.com");
        String expected = objectMapper.writeValueAsString(payload);
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < 200; index++) {
                tasks.add(() -> objectMapper.writeValueAsString(payload));
            }
            for (Future<String> future : executorService.invokeAll(tasks)) {
                assertEquals(expected, future.get());
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldSupportAllStandardMaskingStrategies() {
        assertEquals("raw", SensitiveStrategy.NONE.mask("raw"));
        assertEquals("a****f", SensitiveStrategy.DEFAULT.mask("abcdef"));
        assertEquals("a****f", SensitiveStrategy.USERNAME.mask("abcdef"));
        assertEquals("张**", SensitiveStrategy.CHINESE_NAME.mask("张三丰"));
        assertEquals("130***********3232", SensitiveStrategy.ID_CARD.mask("130722199102323232"));
        assertEquals("138****5678", SensitiveStrategy.PHONE.mask("13812345678"));
        assertEquals("01****5678", SensitiveStrategy.FIXED_PHONE.mask("0112345678"));
        assertEquals("北京市海淀区***", SensitiveStrategy.ADDRESS.mask("北京市海淀区中关村"));
        assertEquals("t*****@example.com", SensitiveStrategy.EMAIL.mask("tester@example.com"));
        assertEquals("6222********7890", SensitiveStrategy.BANK_CARD.mask("6222021234567890"));
        assertEquals("1234********", SensitiveStrategy.CNAPS_CODE.mask("123456789012"));
        assertEquals("190313******640590", SensitiveStrategy.PAY_SIGN_NO.mask("190313172733640590"));
    }

    private static final class SensitivePayload {

        @Sensitive(strategy = SensitiveStrategy.CHINESE_NAME)
        private final String name;
        @Sensitive(strategy = SensitiveStrategy.PHONE)
        private final String phone;
        @Sensitive(strategy = SensitiveStrategy.BANK_CARD)
        private final String bankCard;
        @Sensitive(strategy = SensitiveStrategy.EMAIL)
        private final String email;

        private SensitivePayload(String name, String phone, String bankCard, String email) {
            this.name = name;
            this.phone = phone;
            this.bankCard = bankCard;
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public String getPhone() {
            return phone;
        }

        public String getBankCard() {
            return bankCard;
        }

        public String getEmail() {
            return email;
        }
    }
}
