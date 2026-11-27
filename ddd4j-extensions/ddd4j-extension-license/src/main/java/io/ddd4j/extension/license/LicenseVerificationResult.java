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
package io.ddd4j.extension.license;

import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

/**
 * License 结构化验证结果。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
@Builder
public class LicenseVerificationResult {

    /** \u672c\u6b21\u8bb8\u53ef\u8bc1\u9a8c\u8bc1\u662f\u5426\u6210\u529f\u3002 */
    private final boolean valid;
    /** \u5bf9\u5916\u53ef\u533a\u5206\u7684\u8bb8\u53ef\u8bc1\u9a8c\u8bc1\u7ed3\u679c\u7c7b\u578b\u3002 */
    private final Status status;
    /** \u8bb8\u53ef\u8bc1\u9a8c\u8bc1\u7ed3\u679c\u7684\u8bca\u65ad\u8bf4\u660e\u3002 */
    private final String message;
    /** \u9a8c\u8bc1\u6210\u529f\u540e\u4ea4\u7ed9\u8c03\u7528\u65b9\u7684\u8bb8\u53ef\u8bc1\u4fe1\u606f\u3002 */
    private final LicenseInfo licenseInfo;

    /**
     * success() 方法。
     * @param licenseInfo licenseInfo 参数。
     * @return 当前对象自身，支持链式调用。
     */
    public static LicenseVerificationResult success(LicenseInfo licenseInfo) {
        return LicenseVerificationResult.builder()
                .valid(true)
                .status(Status.VALID)
                /**
                 * .message() 方法。
                 * @param 校验通过" 校验通过" 参数。
                 */
                .message("License 校验通过")
                .licenseInfo(licenseInfo)
                .build();
    }

    /**
     * disabled() 方法。
     * @return 当前对象自身，支持链式调用。
     */
    public static LicenseVerificationResult disabled() {
        return LicenseVerificationResult.builder()
                .valid(true)
                .status(Status.DISABLED)
                .message("License 校验已关闭")
                .build();
    }

    /**
     * failure() 方法。
     * @param status 状态。
     * @param message 异常消息。
     * @return 当前对象自身，支持链式调用。
     */
    public static LicenseVerificationResult failure(Status status, String message) {
        Objects.requireNonNull(status, "status 不能为空");
        return LicenseVerificationResult.builder()
                .valid(false)
                .status(status)
                .message(message)
                .build();
    }

    /**
     * 类型说明。
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     * @since 3.0.x
     */
    public enum Status {
        /** \u6821\u9a8c\u901a\u8fc7\u3002 */
        VALID,
        /** \u8bb8\u53ef\u8bc1\u6821\u9a8c\u5df2\u5173\u95ed\u3002 */
        DISABLED,
        /** \u5c1a\u672a\u5b89\u88c5\u8bb8\u53ef\u8bc1\u3002 */
        NOT_INSTALLED,
        /** \u8fd0\u884c\u73af\u5883\u4e0e\u8bb8\u53ef\u8bc1\u4e0d\u5339\u914d\u3002 */
        ENVIRONMENT_MISMATCH,
        /** \u8bb8\u53ef\u8bc1\u65e0\u6548\u3002 */
        INVALID
    }
}
