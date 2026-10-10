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
     * 构造校验通过的结果。
     *
     * @param licenseInfo 解析出的许可证信息
     * @return 校验通过的结果（valid=true）
     */
    public static LicenseVerificationResult success(LicenseInfo licenseInfo) {
        return LicenseVerificationResult.builder()
                .valid(true)
                .status(Status.VALID)
                .message("License 校验通过")
                .licenseInfo(licenseInfo)
                .build();
    }

    /**
     * 构造许可证校验关闭时的结果。
     *
     * @return 校验已关闭的结果（valid=true，状态 DISABLED）
     */
    public static LicenseVerificationResult disabled() {
        return LicenseVerificationResult.builder()
                .valid(true)
                .status(Status.DISABLED)
                .message("License 校验已关闭")
                .build();
    }

    /**
     * 构造校验失败的结果。
     *
     * @param status  失败状态
     * @param message 失败描述信息
     * @return 校验失败的结果（valid=false）
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
     * 许可证校验状态。
     */
    public enum Status {
        /**
         * 校验通过。
         */
        VALID,
        /**
         * 许可证校验已关闭。
         */
        DISABLED,
        /**
         * 尚未安装许可证。
         */
        NOT_INSTALLED,
        /**
         * 运行环境与许可证不匹配。
         */
        ENVIRONMENT_MISMATCH,
        /**
         * 许可证无效。
         */
        INVALID
    }
}
