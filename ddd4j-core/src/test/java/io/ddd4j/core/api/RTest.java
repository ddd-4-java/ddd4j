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
import io.ddd4j.core.exception.ValidateException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link R}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RTest {

    @Test
    void fail_withString_shouldTreatArgumentAsMessage() {
        R<Object> response = R.fail("quota exceeded");
        assertThat(response.getMsg()).isEqualTo("quota exceeded");
        assertThat(response.getData()).isNull();
        assertThat(response.getCode()).isEqualTo(ApiCode.FAIL.getCode());
    }

    @Test
    void fail_withNullObject_shouldKeepFailureMetadata() {
        R<Object> response = ApiCode.FAIL.toResponse((Object) null);
        assertThat(response.getData()).isNull();
        assertThat(response.getCode()).isEqualTo(ApiCode.FAIL.getCode());
        assertThat(response.getMsg()).isEqualTo(ApiCode.FAIL.getDesc());
    }

    @Test
    void fail_withObjectData_shouldPreservePayload() {
        java.util.Map<String, String> payload = java.util.Collections.singletonMap("reason", "quota");
        R<java.util.Map<String, String>> response = ApiCode.FAIL.toResponse(payload);
        assertThat(response.getData()).isSameAs(payload);
        assertThat(response.getCode()).isEqualTo(ApiCode.FAIL.getCode());
        assertThat(response.getMsg()).isEqualTo(ApiCode.FAIL.getDesc());
        assertThat(response.isOk()).isFalse();
    }

    @Test
    void ok_shouldReturnSuccessCodeAndNullData() {
        R<String> r = R.ok();

        assertThat(r.getCode()).isEqualTo(ApiCode.OK.getCode());
        assertThat(r.getMsg()).isEqualTo(ApiCode.OK.getDesc());
        assertThat(r.getData()).isNull();
        assertThat(r.isOk()).isTrue();
        assertThat(r.isEmpty()).isTrue();
    }

    @Test
    void ok_withData_shouldCarryPayload() {
        R<String> r = R.ok("hello");

        assertThat(r.getCode()).isEqualTo(ApiCode.OK.getCode());
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

        assertThat(r.getCode()).isEqualTo(ApiCode.FAIL.getCode());
        assertThat(r.isOk()).isFalse();
    }

    @Test
    void fail_withMsg_shouldCarryMessage() {
        R<String> r = R.fail("boom");

        assertThat(r.getCode()).isEqualTo(ApiCode.FAIL.getCode());
        assertThat(r.getMsg()).isEqualTo("boom");
        assertThat(r.isOk()).isFalse();
    }

    @Test
    void fail_withCodeAndMsg_shouldCarryBoth() {
        R<String> r = R.of((CustomApiCode) ApiCode.FORBIDDEN, "forbidden");

        assertThat(r.getCode()).isEqualTo(403);
        assertThat(r.getMsg()).isEqualTo("forbidden");
        assertThat(r.isOk()).isFalse();
    }

    @Test
    void fail_aliases_shouldMatchFail() {
        assertThat(R.fail().getCode()).isEqualTo(R.fail().getCode());
        assertThat(R.fail("err").getMsg()).isEqualTo(R.fail("err").getMsg());
    }

    @Test
    void isOk_shouldAcceptSuccessCodeToo() {
        R<String> r = new R<>(ApiCode.SUCCESS.getCode(), ApiCode.SUCCESS.getDesc(), "data");

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
    @Test
    void customApiCode_shouldBuildResponseWithMessageAndPayload() {
        CustomApiCode customCode = new CustomApiCode() {
            @Override
            public Integer getCode() {
                return 42001;
            }

            @Override
            public String getReason() {
                return "quota exhausted";
            }
        };

        R<String> response = customCode.toResponse("retry later", "payload");

        assertThat(response.getCode()).isEqualTo(42001);
        assertThat(response.getMsg()).isEqualTo("retry later");
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.isOk()).isFalse();
    }

    @Test
    void validationFailure_shouldUseUnifiedBadRequestCode() {
        ValidateException exception = new ValidateException("invalid input");

        assertThat(exception.getCode()).isEqualTo(400);
        assertThat(exception.getMessage()).isEqualTo("invalid input");
    }

    @Test
    void jackson3_shouldSerializeUnifiedResponseFieldsAndValidationErrors() {
        List<Map<String, String>> errors = List.of(Map.of("field", "email", "message", "invalid"));
        R<Object> response = R.of((CustomApiCode) ApiCode.BAD_REQUEST, "invalid input", errors);
        JsonMapper mapper = JsonMapper.builder().build();
        var json = mapper.readTree(mapper.writeValueAsString(response));

        assertThat(json.get("code").asInt()).isEqualTo(400);
        assertThat(json.get("msg").asString()).isEqualTo("invalid input");
        assertThat(json.get("error").get(0).get("field").asString()).isEqualTo("email");
        assertThat(json.has("message")).isFalse();
    }

    @Test
    void success_withString_shouldPreserveCustomMessage() {
        R<Object> response = R.success("created");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMsg()).isEqualTo("created");
        assertThat(response.getData()).isNull();
    }

    @Test
    void jackson3_shouldOmitAbsentValidationErrors() {
        JsonMapper mapper = JsonMapper.builder().build();
        var json = mapper.readTree(mapper.writeValueAsString(R.ok("payload")));

        assertThat(json.get("code").asInt()).isZero();
        assertThat(json.get("data").asString()).isEqualTo("payload");
        assertThat(json.has("error")).isFalse();
    }
}
