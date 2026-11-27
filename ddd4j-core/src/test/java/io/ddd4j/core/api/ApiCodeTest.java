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
package io.ddd4j.core.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiCode}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ApiCodeTest {

    /** \u9a8c\u8bc1<code>ApiCode.getReasonByCode(ApiCode.OK.getCode())</code> \u7b49\u4e8e <code>"\u8bf7\u6c42/\u64cd\u4f5c\u6210\u529f"</code>\uff0c<code>ApiCode.getReasonByCode(ApiCode.UNAUTHORIZED.getCode())</code> \u7b49\u4e8e <code>"\u672a\u767b\u5f55\u6216token\u5df2\u7ecf\u5931\u6548"</code>\u3002 */
    @Test
    void getReasonByCode_shouldReturnMatchingDesc() {
        assertThat(ApiCode.getReasonByCode(ApiCode.OK.getCode()))
                .isEqualTo("请求/操作成功");
        assertThat(ApiCode.getReasonByCode(ApiCode.UNAUTHORIZED.getCode()))
                .isEqualTo("未登录或token已经失效");
    }

    /** \u9a8c\u8bc1<code>ApiCode.getReasonByCode(999999)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getReasonByCode_shouldReturnEmptyStringForUnknownCode() {
        assertThat(ApiCode.getReasonByCode(999999)).isEmpty();
    }

    /** \u9a8c\u8bc1<code>ApiCode.getReasonByCode(null)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getReasonByCode_shouldReturnEmptyStringForNull() {
        assertThat(ApiCode.getReasonByCode(null)).isEmpty();
    }

    /** \u9a8c\u8bc1<code>ApiCode.getByCode(ApiCode.FAIL.getCode())</code> \u7b49\u4e8e <code>ApiCode.FAIL</code>\uff0c<code>ApiCode.getByCode(ApiCode.FORBIDDEN.getCode())</code> \u7b49\u4e8e <code>ApiCode.FORBIDDEN</code>\u3002 */
    @Test
    void getByCode_shouldReturnMatchingEnum() {
        assertThat(ApiCode.getByCode(ApiCode.FAIL.getCode())).isEqualTo(ApiCode.FAIL);
        assertThat(ApiCode.getByCode(ApiCode.FORBIDDEN.getCode())).isEqualTo(ApiCode.FORBIDDEN);
    }

    /** \u9a8c\u8bc1<code>ApiCode.getByCode(999999)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getByCode_shouldReturnNullForUnknownCode() {
        assertThat(ApiCode.getByCode(999999)).isNull();
    }

    /** \u9a8c\u8bc1<code>ApiCode.getByCode(null)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getByCode_shouldReturnNullForNull() {
        assertThat(ApiCode.getByCode(null)).isNull();
    }

    /** \u9a8c\u8bc1<code>ApiCode.OK.getCode()</code>\u4e3a\u96f6\uff0c<code>ApiCode.SUCCESS.getCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>ApiCode.SERVER_ERROR.getCode()</code> \u7b49\u4e8e <code>500</code>\u3002 */
    @Test
    void values_shouldContainKeyCodes() {
        assertThat(ApiCode.OK.getCode()).isZero();
        assertThat(ApiCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ApiCode.FAIL.getCode()).isEqualTo(1);
        assertThat(ApiCode.SERVER_ERROR.getCode()).isEqualTo(500);
    }

    /** \u9a8c\u8bc1<code>ApiCode.OK.getCode()</code>\u4e3a\u96f6\uff0c<code>ApiCode.SUCCESS.getCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>ApiCode.NETWORK_AUTHENTICATION_REQUIRED.getCode()</code> \u7b49\u4e8e <code>511</code>\u3002 */
    @Test
    void values_shouldContainKeyCodesFromBothCatalogs() {
        // 通用码段（原 ResultCode）
        assertThat(ApiCode.OK.getCode()).isZero();
        assertThat(ApiCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ApiCode.FAIL.getCode()).isEqualTo(1);
        assertThat(ApiCode.BAD_REQUEST.getCode()).isEqualTo(400);
        // SC_ 码段（原 ApiCode 收编）
        assertThat(ApiCode.NOT_FOUND.getCode()).isEqualTo(404);
        assertThat(ApiCode.METHOD_ARGUMENT_NOT_VALID.getCode()).isEqualTo(400);
        assertThat(ApiCode.EMPTY.getCode()).isEqualTo(1000);
        assertThat(ApiCode.NETWORK_AUTHENTICATION_REQUIRED.getCode()).isEqualTo(511);
    }

    /** \u9a8c\u8bc1<code>code.getCode()</code>\u975e\u7a7a\u3002 */
    @Test
    void getCodeAndDesc_shouldExposeGetters() {
        for (ApiCode code : ApiCode.values()) {
            assertThat(code.getCode()).isNotNull();
            assertThat(code.getDesc()).isNotBlank();
        }
    }

    /** \u9a8c\u8bc1<code>ApiCode.getReasonByCode(code.getCode())</code>\u975e\u7a7a\uff0c<code>ApiCode.getByCode(code.getCode())</code>\u975e\u7a7a\u3002 */
    @Test
    void getReasonByCode_shouldResolveAllEnumValues() {
        for (ApiCode code : ApiCode.values()) {
            assertThat(ApiCode.getReasonByCode(code.getCode())).isNotEmpty();
            // 异常映射码段共享 HTTP 码值（如 400/500/1000），getByCode 对共享码值返回声明在前的第一个条目
            assertThat(ApiCode.getByCode(code.getCode())).isNotNull();
        }
    }

    /** \u9a8c\u8bc1<code>ok.getCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>ok.isOk()</code>\u4e3a\u771f\uff0c<code>withMsgAndData.getData()</code> \u7b49\u4e8e <code>"payload"</code>\u3002 */
    @Test
    void toResponse_shouldBuildRFromCustomApiCodeContract() {
        // ResultCode 作为 CustomApiCode 实现，默认方法直接构建 R
        R<String> ok = ApiCode.SUCCESS.toResponse();
        assertThat(ok.getCode()).isEqualTo(200);
        assertThat(ok.isOk()).isTrue();

        R<String> notFound = ApiCode.NOT_FOUND.toResponse("地址不存在");
        assertThat(notFound.getCode()).isEqualTo(404);
        assertThat(notFound.getMsg()).isEqualTo("地址不存在");
        assertThat(notFound.isOk()).isFalse();

        R<Integer> withData = ApiCode.NOT_FOUND.toResponse(42);
        assertThat(withData.getData()).isEqualTo(42);

        R<String> withMsgAndData = ApiCode.NOT_FOUND.toResponse("缺少参数", "payload");
        assertThat(withMsgAndData.getMsg()).isEqualTo("缺少参数");
        assertThat(withMsgAndData.getData()).isEqualTo("payload");
    }
}
