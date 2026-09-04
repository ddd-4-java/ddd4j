package io.ddd4j.core.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResultCode}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ResultCodeTest {

    @Test
    void getDescByCode_shouldReturnMatchingDesc() {
        assertThat(ResultCode.getDescByCode(ResultCode.OK.getCode()))
                .isEqualTo("请求/操作成功");
        assertThat(ResultCode.getDescByCode(ResultCode.UNAUTHORIZED.getCode()))
                .isEqualTo("未登录或token已经失效");
    }

    @Test
    void getDescByCode_shouldReturnEmptyStringForUnknownCode() {
        assertThat(ResultCode.getDescByCode(999999)).isEmpty();
    }

    @Test
    void getDescByCode_shouldReturnEmptyStringForNull() {
        assertThat(ResultCode.getDescByCode(null)).isEmpty();
    }

    @Test
    void getByCode_shouldReturnMatchingEnum() {
        assertThat(ResultCode.getByCode(ResultCode.FAIL.getCode())).isEqualTo(ResultCode.FAIL);
        assertThat(ResultCode.getByCode(ResultCode.FORBIDDEN.getCode())).isEqualTo(ResultCode.FORBIDDEN);
    }

    @Test
    void getByCode_shouldReturnNullForUnknownCode() {
        assertThat(ResultCode.getByCode(999999)).isNull();
    }

    @Test
    void getByCode_shouldReturnNullForNull() {
        assertThat(ResultCode.getByCode(null)).isNull();
    }

    @Test
    void values_shouldContainKeyCodes() {
        assertThat(ResultCode.OK.getCode()).isZero();
        assertThat(ResultCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ResultCode.FAIL.getCode()).isEqualTo(1);
        assertThat(ResultCode.SERVER_ERROR.getCode()).isEqualTo(500);
    }

    @Test
    void values_shouldHaveUniqueCodes() {
        ResultCode[] codes = ResultCode.values();

        long distinct = java.util.Arrays.stream(codes)
                .map(ResultCode::getCode)
                .distinct()
                .count();

        assertThat(distinct).isEqualTo(codes.length);
    }

    @Test
    void getCodeAndDesc_shouldExposeGetters() {
        for (ResultCode code : ResultCode.values()) {
            assertThat(code.getCode()).isNotNull();
            assertThat(code.getDesc()).isNotBlank();
        }
    }

    @Test
    void getDescByCode_shouldRoundTripAllEnumValues() {
        for (ResultCode code : ResultCode.values()) {
            assertThat(ResultCode.getDescByCode(code.getCode())).isEqualTo(code.getDesc());
            assertThat(ResultCode.getByCode(code.getCode())).isEqualTo(code);
        }
    }
}
