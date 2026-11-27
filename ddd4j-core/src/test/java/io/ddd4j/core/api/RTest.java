package io.ddd4j.core.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link R}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RTest {

    /** \u9a8c\u8bc1<code>r.getCode()</code> \u7b49\u4e8e <code>ApiCode.OK.getCode()</code>\uff0c<code>r.getMsg()</code> \u7b49\u4e8e <code>ApiCode.OK.getDesc()</code>\uff0c<code>r.isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void ok_shouldReturnSuccessCodeAndNullData() {
        R<String> r = R.ok();

        assertThat(r.getCode()).isEqualTo(ResultCode.OK.getCode());
        assertThat(r.getMsg()).isEqualTo(ResultCode.OK.getDesc());
        assertThat(r.getData()).isNull();
        assertThat(r.isOk()).isTrue();
        assertThat(r.isEmpty()).isTrue();
    }

    /** \u9a8c\u8bc1<code>r.getCode()</code> \u7b49\u4e8e <code>ApiCode.OK.getCode()</code>\uff0c<code>r.getData()</code> \u7b49\u4e8e <code>"hello"</code>\uff0c<code>r.isEmpty()</code>\u4e3a\u5047\u3002 */
    @Test
    void ok_withData_shouldCarryPayload() {
        R<String> r = R.ok("hello");

        assertThat(r.getCode()).isEqualTo(ResultCode.OK.getCode());
        assertThat(r.getData()).isEqualTo("hello");
        assertThat(r.isOk()).isTrue();
        assertThat(r.isEmpty()).isFalse();
    }

    /** \u9a8c\u8bc1<code>r.getMsg()</code> \u7b49\u4e8e <code>"custom-msg"</code>\uff0c<code>r.getData()</code> \u7b49\u4e8e <code>"data"</code>\uff0c<code>r.isOk()</code>\u4e3a\u771f\u3002 */
    @Test
    void ok_withMsgAndData_shouldUseProvidedMessage() {
        R<String> r = R.ok("custom-msg", "data");

        assertThat(r.getMsg()).isEqualTo("custom-msg");
        assertThat(r.getData()).isEqualTo("data");
        assertThat(r.isOk()).isTrue();
    }

    /** \u9a8c\u8bc1<code>r.getCode()</code> \u7b49\u4e8e <code>ApiCode.FAIL.getCode()</code>\uff0c<code>r.isOk()</code>\u4e3a\u5047\u3002 */
    @Test
    void fail_shouldReturnFailCode() {
        R<String> r = R.fail();

        assertThat(r.getCode()).isEqualTo(ResultCode.FAIL.getCode());
        assertThat(r.isOk()).isFalse();
    }

    /** \u9a8c\u8bc1<code>r.getCode()</code> \u7b49\u4e8e <code>ApiCode.FAIL.getCode()</code>\uff0c<code>r.getMsg()</code> \u7b49\u4e8e <code>"boom"</code>\uff0c<code>r.isOk()</code>\u4e3a\u5047\u3002 */
    @Test
    void fail_withMsg_shouldCarryMessage() {
        R<String> r = R.fail("boom");

        assertThat(r.getCode()).isEqualTo(ResultCode.FAIL.getCode());
        assertThat(r.getMsg()).isEqualTo("boom");
        assertThat(r.isOk()).isFalse();
    }

    /** \u9a8c\u8bc1<code>r.getCode()</code> \u7b49\u4e8e <code>403</code>\uff0c<code>r.getMsg()</code> \u7b49\u4e8e <code>"forbidden"</code>\uff0c<code>r.isOk()</code>\u4e3a\u5047\u3002 */
    @Test
    void fail_withCodeAndMsg_shouldCarryBoth() {
        R<String> r = R.fail(403, "forbidden");

        assertThat(r.getCode()).isEqualTo(403);
        assertThat(r.getMsg()).isEqualTo("forbidden");
        assertThat(r.isOk()).isFalse();
    }

    /** 验证<code>R.failed().getCode()</code> 等于 <code>R.fail().getCode()</code>，<code>R.failed("err").getMsg()</code> 等于 <code>R.fail("err").getMsg()</code>。 */
    @Test
    void failed_aliases_shouldMatchFail() {
        assertThat(R.failed().getCode()).isEqualTo(R.fail().getCode());
        assertThat(R.failed("err").getMsg()).isEqualTo(R.fail("err").getMsg());
    }

    /** \u9a8c\u8bc1<code>r.isOk()</code>\u4e3a\u771f\u3002 */
    @Test
    void isOk_shouldAcceptSuccessCodeToo() {
        R<String> r = new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getDesc(), "data");

        assertThat(r.isOk()).isTrue();
    }

    /** \u9a8c\u8bc1<code>R.empty(null)</code>\u4e3a\u771f\uff0c<code>R.empty(R.ok())</code>\u4e3a\u771f\uff0c<code>R.empty(R.ok("data"))</code>\u4e3a\u5047\u3002 */
    @Test
    void empty_shouldDetectNullDataOrNullResponse() {
        assertThat(R.empty(null)).isTrue();
        assertThat(R.empty(R.ok())).isTrue();
        assertThat(R.empty(R.ok("data"))).isFalse();
    }

    /** \u9a8c\u8bc1<code>target.getCode()</code> \u7b49\u4e8e <code>source.getCode()</code>\uff0c<code>target.getMsg()</code> \u7b49\u4e8e <code>source.getMsg()</code>\uff0c<code>target.getData()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void transform_shouldCopyCodeAndMsg() {
        R<String> source = R.fail("error");

        R<Integer> target = R.transform(source);

        assertThat(target.getCode()).isEqualTo(source.getCode());
        assertThat(target.getMsg()).isEqualTo(source.getMsg());
        assertThat(target.getData()).isNull();
    }
}
