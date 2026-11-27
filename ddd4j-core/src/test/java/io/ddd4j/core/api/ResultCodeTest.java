package io.ddd4j.core.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResultCode}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ResultCodeTest {

    /** 验证<code>ResultCode.getDescByCode(ResultCode.OK.getCode())</code> 等于 <code>"请求/操作成功"</code>，<code>ResultCode.getDescByCode(ResultCode.UNAUTHORIZED.getCode())</code> 等于 <code>"未登录或token已经失效"</code>。 */
    @Test
    void getDescByCode_shouldReturnMatchingDesc() {
        assertThat(ResultCode.getDescByCode(ResultCode.OK.getCode()))
                .isEqualTo("请求/操作成功");
        assertThat(ResultCode.getDescByCode(ResultCode.UNAUTHORIZED.getCode()))
                .isEqualTo("未登录或token已经失效");
    }

    /** 验证<code>ResultCode.getDescByCode(999999)</code>为空。 */
    @Test
    void getDescByCode_shouldReturnEmptyStringForUnknownCode() {
        assertThat(ResultCode.getDescByCode(999999)).isEmpty();
    }

    /** 验证<code>ResultCode.getDescByCode(null)</code>为空。 */
    @Test
    void getDescByCode_shouldReturnEmptyStringForNull() {
        assertThat(ResultCode.getDescByCode(null)).isEmpty();
    }

    /** 验证<code>ResultCode.getByCode(ResultCode.FAIL.getCode())</code> 等于 <code>ResultCode.FAIL</code>，<code>ResultCode.getByCode(ResultCode.FORBIDDEN.getCode())</code> 等于 <code>ResultCode.FORBIDDEN</code>。 */
    @Test
    void getByCode_shouldReturnMatchingEnum() {
        assertThat(ResultCode.getByCode(ResultCode.FAIL.getCode())).isEqualTo(ResultCode.FAIL);
        assertThat(ResultCode.getByCode(ResultCode.FORBIDDEN.getCode())).isEqualTo(ResultCode.FORBIDDEN);
    }

    /** 验证<code>ResultCode.getByCode(999999)</code>为空。 */
    @Test
    void getByCode_shouldReturnNullForUnknownCode() {
        assertThat(ResultCode.getByCode(999999)).isNull();
    }

    /** 验证<code>ResultCode.getByCode(null)</code>为空。 */
    @Test
    void getByCode_shouldReturnNullForNull() {
        assertThat(ResultCode.getByCode(null)).isNull();
    }

    /** 验证<code>ResultCode.OK.getCode()</code>为零，<code>ResultCode.SUCCESS.getCode()</code> 等于 <code>200</code>，<code>ResultCode.FAIL.getCode()</code> 等于 <code>1</code>。 */
    @Test
    void values_shouldContainKeyCodes() {
        assertThat(ResultCode.OK.getCode()).isZero();
        assertThat(ResultCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ResultCode.FAIL.getCode()).isEqualTo(1);
        assertThat(ResultCode.SERVER_ERROR.getCode()).isEqualTo(500);
    }

    /** 验证<code>distinct</code> 等于 <code>codes.length</code>。 */
    @Test
    void values_shouldHaveUniqueCodes() {
        ResultCode[] codes = ResultCode.values();

        long distinct = java.util.Arrays.stream(codes)
                .map(ResultCode::getCode)
                .distinct()
                .count();

        assertThat(distinct).isEqualTo(codes.length);
    }

    /** 验证<code>code.getCode()</code>非空。 */
    @Test
    void getCodeAndDesc_shouldExposeGetters() {
        for (ResultCode code : ResultCode.values()) {
            assertThat(code.getCode()).isNotNull();
            assertThat(code.getDesc()).isNotBlank();
        }
    }

    /** 验证<code>ResultCode.getDescByCode(code.getCode())</code> 等于 <code>code.getDesc()</code>，<code>ResultCode.getByCode(code.getCode())</code> 等于 <code>code</code>。 */
    @Test
    void getDescByCode_shouldRoundTripAllEnumValues() {
        for (ResultCode code : ResultCode.values()) {
            assertThat(ResultCode.getDescByCode(code.getCode())).isEqualTo(code.getDesc());
            assertThat(ResultCode.getByCode(code.getCode())).isEqualTo(code);
        }
    }
}
