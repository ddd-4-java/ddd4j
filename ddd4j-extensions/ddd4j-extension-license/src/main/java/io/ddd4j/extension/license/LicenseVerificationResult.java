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

    private final boolean valid;
    private final Status status;
    private final String message;
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
        VALID,
        DISABLED,
        NOT_INSTALLED,
        ENVIRONMENT_MISMATCH,
        INVALID
    }
}
