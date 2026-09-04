package io.ddd4j.core.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link R}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RTest {

    @Test
    void ok_shouldReturnSuccessCodeAndNullData() {
        R<String> r = R.ok();

        assertThat(r.getCode()).isEqualTo(ResultCode.OK.getCode());
        assertThat(r.getMsg()).isEqualTo(ResultCode.OK.getDesc());
        assertThat(r.getData()).isNull();
        assertThat(r.isOk()).isTrue();
        assertThat(r.isEmpty()).isTrue();
    }

    @Test
    void ok_withData_shouldCarryPayload() {
        R<String> r = R.ok("hello");

        assertThat(r.getCode()).isEqualTo(ResultCode.OK.getCode());
        assertThat(r.getData()).isEqualTo("hello");
        assertThat(r.isOk()).isTrue();
        assertThat(r.isEmpty()).isFalse();
    }

    @Test
    void ok_withMsgAndData_shouldUseProvidedMessage() {
        R<String> r = R.ok("custom-msg", "data");

        assertThat(r.getMsg()).isEqualTo("custom-msg");
        assertThat(r.getData()).isEqualTo("data");
        assertThat(r.isOk()).isTrue();
    }

    @Test
    void fail_shouldReturnFailCode() {
        R<String> r = R.fail();

        assertThat(r.getCode()).isEqualTo(ResultCode.FAIL.getCode());
        assertThat(r.isOk()).isFalse();
    }

    @Test
    void fail_withMsg_shouldCarryMessage() {
        R<String> r = R.fail("boom");

        assertThat(r.getCode()).isEqualTo(ResultCode.FAIL.getCode());
        assertThat(r.getMsg()).isEqualTo("boom");
        assertThat(r.isOk()).isFalse();
    }

    @Test
    void fail_withCodeAndMsg_shouldCarryBoth() {
        R<String> r = R.fail(403, "forbidden");

        assertThat(r.getCode()).isEqualTo(403);
        assertThat(r.getMsg()).isEqualTo("forbidden");
        assertThat(r.isOk()).isFalse();
    }

    @Test
    void failed_aliases_shouldMatchFail() {
        assertThat(R.failed().getCode()).isEqualTo(R.fail().getCode());
        assertThat(R.failed("err").getMsg()).isEqualTo(R.fail("err").getMsg());
    }

    @Test
    void isOk_shouldAcceptSuccessCodeToo() {
        R<String> r = new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getDesc(), "data");

        assertThat(r.isOk()).isTrue();
    }

    @Test
    void empty_shouldDetectNullDataOrNullResponse() {
        assertThat(R.empty(null)).isTrue();
        assertThat(R.empty(R.ok())).isTrue();
        assertThat(R.empty(R.ok("data"))).isFalse();
    }

    @Test
    void transform_shouldCopyCodeAndMsg() {
        R<String> source = R.fail("error");

        R<Integer> target = R.transform(source);

        assertThat(target.getCode()).isEqualTo(source.getCode());
        assertThat(target.getMsg()).isEqualTo(source.getMsg());
        assertThat(target.getData()).isNull();
    }
}
