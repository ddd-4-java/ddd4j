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

    @Test
    void getReasonByCode_shouldReturnMatchingDesc() {
        assertThat(ApiCode.getReasonByCode(ApiCode.OK.getCode()))
                .isEqualTo("请求/操作成功");
        assertThat(ApiCode.getReasonByCode(ApiCode.UNAUTHORIZED.getCode()))
                .isEqualTo("未登录或token已经失效");
    }

    @Test
    void getReasonByCode_shouldReturnEmptyStringForUnknownCode() {
        assertThat(ApiCode.getReasonByCode(999999)).isEmpty();
    }

    @Test
    void getReasonByCode_shouldReturnEmptyStringForNull() {
        assertThat(ApiCode.getReasonByCode(null)).isEmpty();
    }

    @Test
    void getByCode_shouldReturnMatchingEnum() {
        assertThat(ApiCode.getByCode(ApiCode.FAIL.getCode())).isEqualTo(ApiCode.FAIL);
        assertThat(ApiCode.getByCode(ApiCode.FORBIDDEN.getCode())).isEqualTo(ApiCode.FORBIDDEN);
    }

    @Test
    void getByCode_shouldReturnNullForUnknownCode() {
        assertThat(ApiCode.getByCode(999999)).isNull();
    }

    @Test
    void getByCode_shouldReturnNullForNull() {
        assertThat(ApiCode.getByCode(null)).isNull();
    }

    @Test
    void values_shouldContainKeyCodes() {
        assertThat(ApiCode.OK.getCode()).isZero();
        assertThat(ApiCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ApiCode.FAIL.getCode()).isEqualTo(1);
        assertThat(ApiCode.SERVER_ERROR.getCode()).isEqualTo(500);
    }

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

    @Test
    void getCodeAndDesc_shouldExposeGetters() {
        for (ApiCode code : ApiCode.values()) {
            assertThat(code.getCode()).isNotNull();
            assertThat(code.getDesc()).isNotBlank();
        }
    }

    @Test
    void getReasonByCode_shouldResolveAllEnumValues() {
        for (ApiCode code : ApiCode.values()) {
            assertThat(ApiCode.getReasonByCode(code.getCode())).isNotEmpty();
            // 异常映射码段共享 HTTP 码值（如 400/500/1000），getByCode 对共享码值返回声明在前的第一个条目
            assertThat(ApiCode.getByCode(code.getCode())).isNotNull();
        }
    }

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
